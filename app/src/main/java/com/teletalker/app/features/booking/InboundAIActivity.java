package com.teletalker.app.features.booking;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.teletalker.app.R;
import com.teletalker.app.network.ElevenLabsApiClient;
import com.teletalker.app.utils.PreferencesManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-agent inbound script editor. The merchant picks one of their ElevenLabs
 * agents, configures tone + first message + role + rules + advanced prompt,
 * and saves it. One preset is marked active for inbound calls.
 *
 * Storage layout in Firestore:
 *   users/{uid}.inboundActiveAgentId: "agent_xxx"
 *   users/{uid}.inboundAgentPresets: {
 *     "agent_xxx": { tone, firstMessage, promptAddition, customRules[], advancedPrompt, label },
 *     "agent_yyy": { ... }
 *   }
 *
 * The active preset is also mirrored to SharedPreferences so the inbound
 * AICallRecorder path can apply it without a Firestore round-trip on call.
 */
public class InboundAIActivity extends AppCompatActivity {

    private static final String TAG = "InboundAI";

    // Same shape we use for booking customization, kept private here so this
    // activity stays self-contained.
    static final class Preset {
        String label = "";
        String tone = "warm";
        String firstMessage = "";
        String promptAddition = "";
        List<String> customRules = new ArrayList<>();
        String advancedPrompt = "";

        @SuppressWarnings("unchecked")
        static Preset from(Object raw) {
            Preset p = new Preset();
            if (!(raw instanceof Map)) return p;
            Map<String, Object> m = (Map<String, Object>) raw;
            Object o;
            if ((o = m.get("label"))          instanceof String) p.label          = (String) o;
            if ((o = m.get("tone"))           instanceof String) p.tone           = (String) o;
            if ((o = m.get("firstMessage"))   instanceof String) p.firstMessage   = (String) o;
            if ((o = m.get("promptAddition")) instanceof String) p.promptAddition = (String) o;
            if ((o = m.get("advancedPrompt")) instanceof String) p.advancedPrompt = (String) o;
            Object r = m.get("customRules");
            if (r instanceof List) {
                for (Object x : (List<?>) r) {
                    if (x instanceof String && !((String) x).isEmpty()) {
                        p.customRules.add((String) x);
                    }
                }
            }
            return p;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("label",          label);
            m.put("tone",           tone);
            m.put("firstMessage",   firstMessage);
            m.put("promptAddition", promptAddition);
            m.put("advancedPrompt", advancedPrompt);
            m.put("customRules",    customRules);
            return m;
        }
    }

    private ProgressBar loading;
    private Spinner agentSpinner;
    private TextView agentIdSub;
    private ChipGroup toneChips;
    private EditText firstMsgEt, promptAdditionEt, advancedEt;
    private LinearLayout rulesList, firstMsgVarChips, agentScopeContainer;
    private MaterialButton addRuleBtn, saveBtn;
    private SwitchCompat advancedToggle, activeToggle;
    private LinearLayout advancedGroup;

    /** Agents loaded from ElevenLabs (id → display name). Keeps insertion order. */
    private final LinkedHashMap<String, String> agentsById = new LinkedHashMap<>();
    /** Presets keyed by agentId (mirrors Firestore map). */
    private final Map<String, Preset> presets = new HashMap<>();
    private String activeAgentId = "";
    private String selectedAgentId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbound_ai);

        Toolbar tb = findViewById(R.id.inboundToolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.inbound_ai_title);
        }
        tb.setNavigationOnClickListener(v -> finish());

        loading             = findViewById(R.id.inboundLoading);
        agentSpinner        = findViewById(R.id.inboundAgentSpinner);
        agentIdSub          = findViewById(R.id.inboundAgentIdSub);
        toneChips           = findViewById(R.id.inboundToneChips);
        firstMsgEt          = findViewById(R.id.inboundFirstMessageEt);
        promptAdditionEt    = findViewById(R.id.inboundPromptAdditionEt);
        advancedEt          = findViewById(R.id.inboundAdvancedEt);
        rulesList           = findViewById(R.id.inboundRulesList);
        firstMsgVarChips    = findViewById(R.id.inboundFirstMsgVarChips);
        addRuleBtn          = findViewById(R.id.inboundAddRuleBtn);
        saveBtn             = findViewById(R.id.inboundSaveBtn);
        advancedToggle      = findViewById(R.id.inboundAdvancedToggle);
        activeToggle        = findViewById(R.id.inboundActiveToggle);
        advancedGroup       = findViewById(R.id.inboundAdvancedGroup);
        agentScopeContainer = findViewById(R.id.inboundAgentScopeContainer);

        buildFirstMsgChips();
        wireFieldListeners();

        advancedToggle.setOnCheckedChangeListener((b, on) ->
                advancedGroup.setVisibility(on ? View.VISIBLE : View.GONE));
        addRuleBtn.setOnClickListener(v -> addRuleRow(""));
        saveBtn.setOnClickListener(v -> savePreset());

        loadEverything();
    }

    // -----------------------------------------------------------------------
    // Load
    // -----------------------------------------------------------------------

    private void loadEverything() {
        loading.setVisibility(View.VISIBLE);
        agentScopeContainer.setVisibility(View.GONE);

        // 1) Fetch agent list from ElevenLabs.
        String apiKey = PreferencesManager.getInstance(this).getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            loading.setVisibility(View.GONE);
            Toast.makeText(this, R.string.booking_agent_no_key, Toast.LENGTH_LONG).show();
            return;
        }

        new ElevenLabsApiClient().listAgents(apiKey, new ElevenLabsApiClient.OnAgents() {
            @Override public void onAgents(@NonNull List<ElevenLabsApiClient.Agent> agents) {
                agentsById.clear();
                for (ElevenLabsApiClient.Agent a : agents) {
                    agentsById.put(a.id, a.name);
                }
                // 2) Now fetch saved presets from Firestore.
                loadPresetsFromFirestore();
            }
            @Override public void onError(@NonNull String message) {
                loading.setVisibility(View.GONE);
                Toast.makeText(InboundAIActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadPresetsFromFirestore() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) {
            loading.setVisibility(View.GONE);
            finish();
            return;
        }
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(snap -> {
                    parsePresets(snap);
                    renderAgentSpinner();
                    loading.setVisibility(View.GONE);
                    agentScopeContainer.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    loading.setVisibility(View.GONE);
                    Toast.makeText(this, "Couldn't load presets: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    @SuppressWarnings("unchecked")
    private void parsePresets(DocumentSnapshot snap) {
        presets.clear();
        if (!snap.exists()) return;
        Object active = snap.get("inboundActiveAgentId");
        if (active instanceof String) activeAgentId = (String) active;

        Object raw = snap.get("inboundAgentPresets");
        if (raw instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) raw).entrySet()) {
                presets.put(e.getKey(), Preset.from(e.getValue()));
            }
        }
    }

    private void renderAgentSpinner() {
        // Build spinner labels — show "(Active)" suffix on the active agent
        // and "(no preset yet)" on agents without saved settings.
        List<String> ids = new ArrayList<>(agentsById.keySet());
        List<String> labels = new ArrayList<>();
        for (String id : ids) {
            String name = agentsById.get(id);
            String suffix = id.equals(activeAgentId) ? "  (Active)"
                          : (presets.containsKey(id) ? "" : "  (no preset)");
            labels.add(name + suffix);
        }
        if (labels.isEmpty()) {
            Toast.makeText(this, "No agents found on this ElevenLabs account.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        agentSpinner.setAdapter(adapter);

        int selectIdx = 0;
        if (!activeAgentId.isEmpty()) {
            int i = ids.indexOf(activeAgentId);
            if (i >= 0) selectIdx = i;
        }
        agentSpinner.setSelection(selectIdx, false);
        selectedAgentId = ids.get(selectIdx);
        hydrateForAgent(selectedAgentId);

        agentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                  int position, long id) {
                selectedAgentId = ids.get(position);
                hydrateForAgent(selectedAgentId);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void hydrateForAgent(String agentId) {
        agentIdSub.setText(agentId);
        Preset p = presets.get(agentId);
        if (p == null) {
            p = new Preset();
            p.label = agentsById.get(agentId);
        }
        selectToneChip(p.tone);
        firstMsgEt.setText(p.firstMessage);
        promptAdditionEt.setText(p.promptAddition);
        advancedEt.setText(p.advancedPrompt);
        boolean hasAdv = !p.advancedPrompt.isEmpty();
        advancedToggle.setOnCheckedChangeListener(null);
        advancedToggle.setChecked(hasAdv);
        advancedGroup.setVisibility(hasAdv ? View.VISIBLE : View.GONE);
        advancedToggle.setOnCheckedChangeListener((b, on) ->
                advancedGroup.setVisibility(on ? View.VISIBLE : View.GONE));

        activeToggle.setOnCheckedChangeListener(null);
        activeToggle.setChecked(agentId.equals(activeAgentId));
        activeToggle.setOnCheckedChangeListener(null);  // re-attached on save click

        rulesList.removeAllViews();
        for (String r : p.customRules) addRuleRow(r);
    }

    // -----------------------------------------------------------------------
    // Field listeners — keep the in-memory preset in sync with the editor
    // -----------------------------------------------------------------------

    private void wireFieldListeners() {
        toneChips.setOnCheckedStateChangeListener((g, ids) -> {
            getOrCreatePreset(selectedAgentId).tone = readSelectedTone();
        });
        firstMsgEt.addTextChangedListener(simpleWatcher(s ->
                getOrCreatePreset(selectedAgentId).firstMessage = s.trim()));
        promptAdditionEt.addTextChangedListener(simpleWatcher(s ->
                getOrCreatePreset(selectedAgentId).promptAddition = s.trim()));
        advancedEt.addTextChangedListener(simpleWatcher(s ->
                getOrCreatePreset(selectedAgentId).advancedPrompt = s));
    }

    private Preset getOrCreatePreset(String agentId) {
        Preset p = presets.get(agentId);
        if (p == null) {
            p = new Preset();
            p.label = agentsById.get(agentId);
            presets.put(agentId, p);
        }
        return p;
    }

    private interface OnChange { void on(String s); }
    private TextWatcher simpleWatcher(OnChange cb) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { cb.on(s.toString()); }
        };
    }

    // -----------------------------------------------------------------------
    // Chips + rules
    // -----------------------------------------------------------------------

    private void buildFirstMsgChips() {
        firstMsgVarChips.removeAllViews();
        addInsertChip(firstMsgVarChips, getString(R.string.booking_ai_var_business), "{business_name}", firstMsgEt);
    }

    private void addInsertChip(LinearLayout container, String label, String token, EditText target) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setChipIcon(null);
        chip.setOnClickListener(v -> {
            int start = Math.max(0, target.getSelectionStart());
            int end   = Math.max(start, target.getSelectionEnd());
            Editable e = target.getText();
            e.replace(start, end, token);
            target.setSelection(start + token.length());
            target.requestFocus();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(8);
        container.addView(chip, lp);
    }

    private String readSelectedTone() {
        int id = toneChips.getCheckedChipId();
        if (id == R.id.inboundToneProfessional) return "professional";
        if (id == R.id.inboundToneCasual)       return "casual";
        if (id == R.id.inboundToneFriendly)     return "friendly";
        return "warm";
    }
    private void selectToneChip(String tone) {
        int id;
        if ("professional".equals(tone))      id = R.id.inboundToneProfessional;
        else if ("casual".equals(tone))       id = R.id.inboundToneCasual;
        else if ("friendly".equals(tone))     id = R.id.inboundToneFriendly;
        else                                  id = R.id.inboundToneWarm;
        toneChips.check(id);
    }

    private void addRuleRow(String initial) {
        Preset p = getOrCreatePreset(selectedAgentId);
        if (p.customRules.size() >= 10) {
            Toast.makeText(this, "Max 10 rules", Toast.LENGTH_SHORT).show();
            return;
        }
        if (initial == null) initial = "";
        p.customRules.add(initial);
        final int snapshotIdx = p.customRules.size() - 1;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = 6;
        row.setLayoutParams(rowLp);

        EditText et = new EditText(this);
        et.setHint(R.string.booking_ai_rule_hint);
        et.setBackgroundResource(R.drawable.booking_input_bg);
        et.setPadding(24, 18, 24, 18);
        et.setTextSize(14f);
        et.setSingleLine(true);
        et.setText(initial);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        et.setLayoutParams(etLp);

        et.addTextChangedListener(simpleWatcher(s -> {
            int idx = rulesList.indexOfChild(row);
            Preset cur = getOrCreatePreset(selectedAgentId);
            if (idx >= 0 && idx < cur.customRules.size()) {
                cur.customRules.set(idx, s.trim());
            }
        }));

        ImageButton del = new ImageButton(this);
        del.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        del.setBackgroundResource(android.R.color.transparent);
        del.setColorFilter(getResources().getColor(R.color.text_secondary));
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        delLp.setMarginStart(6);
        del.setLayoutParams(delLp);
        del.setOnClickListener(v -> {
            int idx = rulesList.indexOfChild(row);
            Preset cur = getOrCreatePreset(selectedAgentId);
            if (idx >= 0 && idx < cur.customRules.size()) cur.customRules.remove(idx);
            rulesList.removeView(row);
        });

        row.addView(et);
        row.addView(del);
        rulesList.addView(row);
    }

    // -----------------------------------------------------------------------
    // Save
    // -----------------------------------------------------------------------

    private void savePreset() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null || selectedAgentId.isEmpty()) return;

        boolean setActive = activeToggle.isChecked();
        if (setActive) activeAgentId = selectedAgentId;

        Map<String, Object> presetMaps = new HashMap<>();
        for (Map.Entry<String, Preset> e : presets.entrySet()) {
            presetMaps.put(e.getKey(), e.getValue().toMap());
        }

        Map<String, Object> update = new HashMap<>();
        update.put("inboundAgentPresets", presetMaps);
        if (setActive) update.put("inboundActiveAgentId", activeAgentId);

        saveBtn.setEnabled(false);
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(update, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    saveBtn.setEnabled(true);
                    cacheActivePresetLocally();
                    Toast.makeText(this, R.string.inbound_ai_saved, Toast.LENGTH_SHORT).show();
                    renderAgentSpinner(); // refresh "(Active)" label
                })
                .addOnFailureListener(e -> {
                    saveBtn.setEnabled(true);
                    Toast.makeText(this, "Save failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    /** Mirror the active preset into SharedPreferences so AICallRecorder can
     *  read it on the next inbound call without a Firestore round-trip. */
    private void cacheActivePresetLocally() {
        PreferencesManager prefs = PreferencesManager.getInstance(this);
        Preset p = presets.get(activeAgentId);
        if (p == null) p = new Preset();
        prefs.putString("inbound_active_agent_id",     activeAgentId == null ? "" : activeAgentId);
        prefs.putString("inbound_active_tone",         p.tone == null ? "warm" : p.tone);
        prefs.putString("inbound_active_first_msg",    p.firstMessage == null ? "" : p.firstMessage);
        prefs.putString("inbound_active_prompt_add",   p.promptAddition == null ? "" : p.promptAddition);
        prefs.putString("inbound_active_advanced",     p.advancedPrompt == null ? "" : p.advancedPrompt);
        StringBuilder rules = new StringBuilder();
        for (String r : p.customRules) {
            if (r != null && !r.isEmpty()) rules.append(r).append("\n");
        }
        prefs.putString("inbound_active_rules", rules.toString());
    }
}
