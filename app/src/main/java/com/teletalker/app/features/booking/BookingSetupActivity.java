package com.teletalker.app.features.booking;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import androidx.appcompat.app.AlertDialog;

import com.teletalker.app.R;
import com.teletalker.app.features.booking.adapter.TemplatePickerAdapter;
import com.teletalker.app.features.booking.data.BookingTemplate;
import com.teletalker.app.features.booking.data.DayRange;
import com.teletalker.app.features.booking.data.MerchantLogoUploader;
import com.teletalker.app.features.booking.data.MerchantProfile;
import com.teletalker.app.features.booking.data.MerchantProfileRepository;
import com.teletalker.app.features.booking.data.ServiceItem;
import com.teletalker.app.features.home.fragments.settings.SubscriptionPlanActivity;
import com.teletalker.app.network.ElevenLabsApiClient;
import com.teletalker.app.network.FirebaseFunctionsManager;
import com.teletalker.app.utils.PreferencesManager;
import com.teletalker.app.utils.SubscriptionManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single scrollable activity that mirrors the web settings page:
 *  - Share link card (top)
 *  - Profile (slug, name, description, timezone, phone, address, website)
 *  - Services (dynamic list with name/duration/price/description)
 *  - Hours (7 weekdays, checkbox + open/close)
 *  - Booking mode (interval / slots / anytime + slot interval picker)
 */
public class BookingSetupActivity extends AppCompatActivity {

    private static final String[] DAY_KEYS = { "mon","tue","wed","thu","fri","sat","sun" };
    private static final String[] DAY_LABELS = {
            "Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"
    };
    private static final int[] INTERVALS = { 15, 30, 45, 60 };
    private static final String[] TIMEZONES = {
            "UTC", "Asia/Manila", "Asia/Singapore", "Asia/Hong_Kong", "Asia/Dubai",
            "Europe/London", "Europe/Berlin",
            "America/New_York", "America/Chicago", "America/Los_Angeles",
    };

    private final MerchantProfile profile = new MerchantProfile();
    private final MerchantProfileRepository repo = new MerchantProfileRepository();

    // Header
    private TextView slugLine, shareTitle, businessLine;
    private MaterialButton shareBtn, copyBtn, openBtn, tutorialBtn;
    private View shareCard, shareEmpty;
    private CircularProgressIndicator loader;
    private View formScroll;

    // Profile fields
    private EditText slugEt, nameEt, descEt, phoneEt, addressEt, websiteEt;
    private Spinner timezoneSpinner;

    // Template picker
    private RecyclerView templatePickerRv;
    private TemplatePickerAdapter templateAdapter;

    // Look & feel
    private EditText coverEt, logoEt;
    private ImageView coverPreview, logoPreview;
    private MaterialButton logoChooseBtn, logoRemoveBtn;
    private LinearProgressIndicator logoUploadProgress;

    // Logo picker launcher — registered in onCreate so it's available
    // before bindViews wires the button click handler.
    private ActivityResultLauncher<String> pickLogoLauncher;

    // Agent picker
    private TextView agentCurrentTv;
    private MaterialButton chooseAgentBtn;

    // AI script & personality
    private com.google.android.material.chip.ChipGroup aiToneChips;
    private EditText aiFirstMessageEt, aiVoicemailEt, aiAdvancedEt;
    private LinearLayout aiRulesList, firstMsgVarChips, voicemailVarChips, aiAdvancedGroup;
    private MaterialButton aiAddRuleBtn;
    private androidx.appcompat.widget.SwitchCompat aiAdvancedToggle;

    // Services
    private LinearLayout servicesContainer;
    private MaterialButton addServiceBtn;

    // Hours
    private LinearLayout hoursContainer;

    // Booking mode
    private RadioGroup modeGroup;
    private View intervalRow;
    private LinearLayout intervalChips;
    private View slotsHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register the logo picker BEFORE any setContentView path so the
        // ActivityResultRegistry has it ready regardless of which layout
        // we render below. Safe to register even if Lite users won't use it.
        pickLogoLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) onLogoChosen(uri); });

        // Booking is a Standard-plan-only feature. Show an upgrade screen for
        // Lite users instead of the editor — they can come back after upgrade.
        if (SubscriptionManager.getInstance(this).isLite()) {
            setContentView(R.layout.activity_booking_upgrade_required);
            findViewById(R.id.upgradeBack).setOnClickListener(v -> finish());
            findViewById(R.id.upgradeCta).setOnClickListener(v ->
                    startActivity(new Intent(this, SubscriptionPlanActivity.class)));
            return;
        }

        setContentView(R.layout.activity_booking_setup);

        bindViews();
        wireListeners();
        populateTimezoneSpinner();
        renderIntervalChips();

        loader.setVisibility(View.VISIBLE);
        formScroll.setVisibility(View.GONE);
        repo.load(new MerchantProfileRepository.OnProfileLoaded() {
            @Override public void onLoaded(@NonNull MerchantProfile p, boolean isNew) {
                runOnUiThread(() -> {
                    copyProfile(p);
                    bindProfileToFields();
                    loader.setVisibility(View.GONE);
                    formScroll.setVisibility(View.VISIBLE);
                    // First-time visit → tutorial
                    if (BookingTutorialActivity.shouldAutoOpen(BookingSetupActivity.this)) {
                        BookingTutorialActivity.open(BookingSetupActivity.this);
                    }
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> {
                    loader.setVisibility(View.GONE);
                    formScroll.setVisibility(View.VISIBLE);
                    Toast.makeText(BookingSetupActivity.this,
                            "Could not load profile: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void bindViews() {
        slugLine     = findViewById(R.id.shareSlugLine);
        shareTitle   = findViewById(R.id.shareTitle);
        businessLine = findViewById(R.id.shareBusinessLine);
        shareCard    = findViewById(R.id.shareCard);
        shareEmpty   = findViewById(R.id.shareEmptyHint);
        shareBtn     = findViewById(R.id.shareBtn);
        copyBtn      = findViewById(R.id.copyBtn);
        openBtn      = findViewById(R.id.openBtn);
        tutorialBtn  = findViewById(R.id.tutorialBtn);

        loader     = findViewById(R.id.loader);
        formScroll = findViewById(R.id.formScroll);

        slugEt        = findViewById(R.id.slugEt);
        nameEt        = findViewById(R.id.nameEt);
        descEt        = findViewById(R.id.descEt);
        phoneEt       = findViewById(R.id.phoneEt);
        addressEt     = findViewById(R.id.addressEt);
        websiteEt     = findViewById(R.id.websiteEt);
        timezoneSpinner = findViewById(R.id.timezoneSpinner);

        coverEt           = findViewById(R.id.coverEt);
        logoEt            = findViewById(R.id.logoEt);
        coverPreview      = findViewById(R.id.coverPreview);
        logoPreview       = findViewById(R.id.logoPreview);
        logoChooseBtn     = findViewById(R.id.logoChooseBtn);
        logoRemoveBtn     = findViewById(R.id.logoRemoveBtn);
        logoUploadProgress = findViewById(R.id.logoUploadProgress);

        templatePickerRv  = findViewById(R.id.templatePickerRv);
        templatePickerRv.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        templateAdapter = new TemplatePickerAdapter(profile.templateId, template -> {
            profile.templateId = template.id;
            refreshShareCard();
        });
        templatePickerRv.setAdapter(templateAdapter);

        agentCurrentTv = findViewById(R.id.agentCurrentTv);
        chooseAgentBtn = findViewById(R.id.chooseAgentBtn);

        aiToneChips        = findViewById(R.id.aiToneChips);
        aiFirstMessageEt   = findViewById(R.id.aiFirstMessageEt);
        aiVoicemailEt      = findViewById(R.id.aiVoicemailEt);
        aiAdvancedEt       = findViewById(R.id.aiAdvancedEt);
        aiRulesList        = findViewById(R.id.aiRulesList);
        firstMsgVarChips   = findViewById(R.id.firstMsgVarChips);
        voicemailVarChips  = findViewById(R.id.voicemailVarChips);
        aiAdvancedGroup    = findViewById(R.id.aiAdvancedGroup);
        aiAddRuleBtn       = findViewById(R.id.aiAddRuleBtn);
        aiAdvancedToggle   = findViewById(R.id.aiAdvancedToggle);

        setupAiCustomization();

        servicesContainer = findViewById(R.id.servicesContainer);
        addServiceBtn     = findViewById(R.id.addServiceBtn);

        hoursContainer = findViewById(R.id.hoursContainer);

        modeGroup      = findViewById(R.id.modeGroup);
        intervalRow    = findViewById(R.id.intervalRow);
        intervalChips  = findViewById(R.id.intervalChips);
        slotsHint      = findViewById(R.id.slotsHint);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        findViewById(R.id.saveBtn).setOnClickListener(v -> save());
    }

    private void wireListeners() {
        shareBtn.setOnClickListener(v ->
                ShareLinkHelper.share(this, profile.slug, profile.businessName));
        copyBtn.setOnClickListener(v -> ShareLinkHelper.copy(this, profile.slug));
        openBtn.setOnClickListener(v -> {
            String url = ShareLinkHelper.urlFor(profile.slug);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        });
        tutorialBtn.setOnClickListener(v -> BookingTutorialActivity.open(this));

        addServiceBtn.setOnClickListener(v -> {
            profile.services.add(ServiceItem.of("New service", 30));
            renderServices();
        });

        modeGroup.setOnCheckedChangeListener((g, id) -> {
            if (id == R.id.modeInterval) profile.bookingMode = MerchantProfile.MODE_INTERVAL;
            else if (id == R.id.modeSlots) profile.bookingMode = MerchantProfile.MODE_SLOTS;
            else if (id == R.id.modeAnytime) profile.bookingMode = MerchantProfile.MODE_ANYTIME;
            refreshModeSubsections();
        });

        slugEt.addTextChangedListener(textWatcher(s -> {
            profile.slug = s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
            refreshShareCard();
        }));
        nameEt.addTextChangedListener(textWatcher(s -> {
            profile.businessName = s == null ? "" : s.trim();
            refreshShareCard();
        }));
        descEt.addTextChangedListener(textWatcher(s -> profile.description = s == null ? "" : s));
        phoneEt.addTextChangedListener(textWatcher(s -> profile.phone = s == null ? "" : s));
        addressEt.addTextChangedListener(textWatcher(s -> profile.address = s == null ? "" : s));
        websiteEt.addTextChangedListener(textWatcher(s -> profile.website = s == null ? "" : s));

        coverEt.addTextChangedListener(textWatcher(s -> {
            profile.coverImageUrl = s == null ? "" : s.trim();
            refreshImagePreview(coverPreview, profile.coverImageUrl);
        }));
        logoEt.addTextChangedListener(textWatcher(s -> {
            profile.logoUrl = s == null ? "" : s.trim();
            refreshImagePreview(logoPreview, profile.logoUrl);
            refreshLogoButtons();
        }));

        logoChooseBtn.setOnClickListener(v -> {
            try {
                pickLogoLauncher.launch("image/*");
            } catch (Exception e) {
                Toast.makeText(this, "Couldn't open picker: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
        logoRemoveBtn.setOnClickListener(v -> {
            profile.logoUrl = "";
            logoEt.setText("");
            refreshImagePreview(logoPreview, "");
            refreshLogoButtons();
        });

        chooseAgentBtn.setOnClickListener(v -> openAgentPicker());

        View viewBookings = findViewById(R.id.viewBookingsCard);
        if (viewBookings != null) {
            viewBookings.setOnClickListener(v ->
                    startActivity(new Intent(BookingSetupActivity.this, BookingsListActivity.class)));
        }

    }

    // ------------------------------------------------------------------
    //  AI script & personality
    // ------------------------------------------------------------------

    private void setupAiCustomization() {
        // Variable chips inserted into whichever EditText was last focused.
        buildVariableChips(firstMsgVarChips, aiFirstMessageEt);
        buildVariableChips(voicemailVarChips, aiVoicemailEt);

        aiToneChips.setOnCheckedStateChangeListener((group, ids) -> {
            profile.aiTone = readSelectedTone();
        });

        aiFirstMessageEt.addTextChangedListener(textWatcher(s -> {
            profile.aiFirstMessage = s == null ? "" : s;
        }));
        aiVoicemailEt.addTextChangedListener(textWatcher(s -> {
            profile.aiVoicemailMessage = s == null ? "" : s;
        }));
        aiAdvancedEt.addTextChangedListener(textWatcher(s -> {
            profile.aiAdvancedPrompt = s == null ? "" : s;
        }));

        aiAdvancedToggle.setOnCheckedChangeListener((btn, on) ->
                aiAdvancedGroup.setVisibility(on ? View.VISIBLE : View.GONE));

        aiAddRuleBtn.setOnClickListener(v -> addRuleRow(""));
    }

    private void buildVariableChips(LinearLayout container, EditText target) {
        container.removeAllViews();
        addInsertChip(container, getString(R.string.booking_ai_var_customer),    "{customer_name}",   target);
        addInsertChip(container, getString(R.string.booking_ai_var_business),    "{business_name}",   target);
        addInsertChip(container, getString(R.string.booking_ai_var_service),     "{service_name}",    target);
        addInsertChip(container, getString(R.string.booking_ai_var_appointment), "{appointment}",     target);
    }

    private void addInsertChip(LinearLayout container, String label, String token, EditText target) {
        com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
        chip.setText(label);
        chip.setChipIcon(null);
        chip.setOnClickListener(v -> insertAtCursor(target, token));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(8);
        container.addView(chip, lp);
    }

    private void insertAtCursor(EditText et, String token) {
        int start = Math.max(0, et.getSelectionStart());
        int end   = Math.max(start, et.getSelectionEnd());
        android.text.Editable e = et.getText();
        e.replace(start, end, token);
        et.setSelection(start + token.length());
        et.requestFocus();
    }

    private String readSelectedTone() {
        int id = aiToneChips.getCheckedChipId();
        if (id == R.id.toneProfessional) return "professional";
        if (id == R.id.toneCasual)       return "casual";
        if (id == R.id.toneFriendly)     return "friendly";
        return "warm";
    }

    private void selectToneChip(String tone) {
        int id;
        if ("professional".equals(tone))      id = R.id.toneProfessional;
        else if ("casual".equals(tone))       id = R.id.toneCasual;
        else if ("friendly".equals(tone))     id = R.id.toneFriendly;
        else                                  id = R.id.toneWarm;
        aiToneChips.check(id);
    }

    private void addRuleRow(String initial) {
        if (profile.aiCustomRules == null) {
            profile.aiCustomRules = new java.util.ArrayList<>();
        }
        if (profile.aiCustomRules.size() >= 10) {
            Toast.makeText(this, "Max 10 rules", Toast.LENGTH_SHORT).show();
            return;
        }
        final int rowIndex = profile.aiCustomRules.size();
        profile.aiCustomRules.add(initial == null ? "" : initial);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = 6;
        row.setLayoutParams(rowLp);

        final EditText et = new EditText(this);
        et.setHint(R.string.booking_ai_rule_hint);
        et.setBackgroundResource(R.drawable.booking_input_bg);
        et.setPadding(24, 18, 24, 18);
        et.setTextSize(14f);
        et.setSingleLine(true);
        et.setText(initial == null ? "" : initial);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        et.setLayoutParams(etLp);

        et.addTextChangedListener(textWatcher(s -> {
            // Find this row's current index by scanning siblings (handles deletes).
            int idx = aiRulesList.indexOfChild(row);
            if (idx >= 0 && idx < profile.aiCustomRules.size()) {
                profile.aiCustomRules.set(idx, s == null ? "" : s.trim());
            }
        }));

        android.widget.ImageButton del = new android.widget.ImageButton(this);
        del.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        del.setBackgroundResource(android.R.color.transparent);
        del.setColorFilter(getResources().getColor(R.color.text_secondary));
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        delLp.setMarginStart(6);
        del.setLayoutParams(delLp);
        del.setOnClickListener(v -> {
            int idx = aiRulesList.indexOfChild(row);
            if (idx >= 0 && idx < profile.aiCustomRules.size()) {
                profile.aiCustomRules.remove(idx);
            }
            aiRulesList.removeView(row);
        });

        row.addView(et);
        row.addView(del);
        aiRulesList.addView(row);
    }

    /** Renders the saved customization back into the UI. Called after the
     *  profile is loaded from Firestore. */
    private void hydrateAiCustomization() {
        selectToneChip(profile.aiTone == null ? "warm" : profile.aiTone);
        aiFirstMessageEt.setText(profile.aiFirstMessage == null ? "" : profile.aiFirstMessage);
        aiVoicemailEt.setText(profile.aiVoicemailMessage == null ? "" : profile.aiVoicemailMessage);
        aiAdvancedEt.setText(profile.aiAdvancedPrompt == null ? "" : profile.aiAdvancedPrompt);
        boolean hasAdv = profile.aiAdvancedPrompt != null && !profile.aiAdvancedPrompt.isEmpty();
        aiAdvancedToggle.setChecked(hasAdv);
        aiAdvancedGroup.setVisibility(hasAdv ? View.VISIBLE : View.GONE);

        aiRulesList.removeAllViews();
        java.util.List<String> snapshot =
                profile.aiCustomRules == null ? new java.util.ArrayList<>()
                                              : new java.util.ArrayList<>(profile.aiCustomRules);
        profile.aiCustomRules = new java.util.ArrayList<>();
        for (String r : snapshot) addRuleRow(r);
    }

    /** Lists agents from ElevenLabs and lets the user pick one. */
    private void openAgentPicker() {
        String apiKey = PreferencesManager.getInstance(this).getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            Toast.makeText(this, R.string.booking_agent_no_key, Toast.LENGTH_LONG).show();
            return;
        }

        // While loading, swap the button label so the user knows something's happening.
        CharSequence original = chooseAgentBtn.getText();
        chooseAgentBtn.setEnabled(false);
        chooseAgentBtn.setText(R.string.booking_agent_loading);

        // Push the API key to Firestore so the web dashboard can use the
        // same agent list. Best-effort — ignore failures.
        FirebaseFunctionsManager.getInstance().syncElevenLabsApiKey(
                apiKey,
                new FirebaseFunctionsManager.OnSyncApiKeyCallback() {
                    @Override public void onSuccess() {}
                    @Override public void onError(String error) {}
                });

        new ElevenLabsApiClient().listAgents(apiKey, new ElevenLabsApiClient.OnAgents() {
            @Override public void onAgents(@NonNull java.util.List<ElevenLabsApiClient.Agent> agents) {
                chooseAgentBtn.setEnabled(true);
                chooseAgentBtn.setText(original);
                if (agents.isEmpty()) {
                    Toast.makeText(BookingSetupActivity.this,
                            R.string.booking_agent_load_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                String[] names = new String[agents.size()];
                int currentIdx = -1;
                for (int i = 0; i < agents.size(); i++) {
                    names[i] = agents.get(i).name;
                    if (agents.get(i).id.equals(profile.bookingAgentId)) currentIdx = i;
                }
                new AlertDialog.Builder(BookingSetupActivity.this)
                        .setTitle(R.string.booking_agent_dialog_title)
                        .setSingleChoiceItems(names, currentIdx, (d, which) -> {
                            ElevenLabsApiClient.Agent chosen = agents.get(which);
                            profile.bookingAgentId = chosen.id;
                            profile.bookingAgentName = chosen.name;
                            agentCurrentTv.setText(chosen.name);
                            d.dismiss();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
            @Override public void onError(@NonNull String message) {
                chooseAgentBtn.setEnabled(true);
                chooseAgentBtn.setText(original);
                Toast.makeText(BookingSetupActivity.this,
                        message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Lightweight preview: only show the ImageView if the URL looks plausible
     * (http/https + has a body). We avoid pulling in a heavy image-loading
     * library and just call the system download manager on demand. For the
     * preview we use a simple AsyncTask-less approach via Picasso isn't
     * available — so we just toggle visibility based on URL validity. The
     * web booking page renders the real image.
     */
    private void refreshImagePreview(ImageView view, String url) {
        if (view == null) return;
        if (url == null || url.isEmpty()) {
            view.setVisibility(View.GONE);
            view.setImageDrawable(null);
            return;
        }
        boolean looksLikeUrl = url.startsWith("http://") || url.startsWith("https://");
        view.setVisibility(looksLikeUrl ? View.VISIBLE : View.GONE);
        if (looksLikeUrl) {
            Glide.with(view.getContext())
                    .load(url)
                    .placeholder(R.drawable.booking_input_inner_bg)
                    .error(R.drawable.booking_input_inner_bg)
                    .into(view);
        }
    }

    // ------------------------------------------------------------------
    //  Logo upload (Firebase Storage)
    // ------------------------------------------------------------------

    private void refreshLogoButtons() {
        boolean hasLogo = profile.logoUrl != null && !profile.logoUrl.isEmpty();
        if (logoChooseBtn != null) {
            logoChooseBtn.setText(hasLogo
                    ? R.string.booking_logo_change : R.string.booking_logo_choose);
        }
        if (logoRemoveBtn != null) {
            logoRemoveBtn.setVisibility(hasLogo ? View.VISIBLE : View.GONE);
        }
    }

    private void onLogoChosen(@NonNull Uri uri) {
        if (logoChooseBtn != null) logoChooseBtn.setEnabled(false);
        if (logoUploadProgress != null) logoUploadProgress.setVisibility(View.VISIBLE);

        MerchantLogoUploader.upload(this, uri, new MerchantLogoUploader.Callback() {
            @Override public void onUploaded(@NonNull String downloadUrl) {
                runOnUiThread(() -> {
                    if (logoUploadProgress != null) logoUploadProgress.setVisibility(View.GONE);
                    if (logoChooseBtn != null) logoChooseBtn.setEnabled(true);
                    profile.logoUrl = downloadUrl;
                    // logoEt's TextWatcher will also refresh preview + buttons.
                    logoEt.setText(downloadUrl);
                });
            }
            @Override public void onError(@NonNull String message) {
                runOnUiThread(() -> {
                    if (logoUploadProgress != null) logoUploadProgress.setVisibility(View.GONE);
                    if (logoChooseBtn != null) logoChooseBtn.setEnabled(true);
                    Toast.makeText(BookingSetupActivity.this,
                            getString(R.string.booking_logo_upload_failed, message),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ----------------- populate / render -----------------

    private void copyProfile(MerchantProfile p) {
        profile.slug          = p.slug;
        profile.businessName  = p.businessName;
        profile.timezone      = p.timezone;
        profile.description   = p.description;
        profile.phone         = p.phone;
        profile.address       = p.address;
        profile.website       = p.website;
        profile.coverImageUrl = p.coverImageUrl;
        profile.logoUrl       = p.logoUrl;
        profile.socials       = p.socials;
        profile.templateId    = p.templateId == null || p.templateId.isEmpty()
                ? "dental-clinic" : p.templateId;
        profile.bookingMode   = p.bookingMode;
        profile.slotIntervalMin = p.slotIntervalMin;
        profile.services      = p.services.isEmpty()
                ? new ArrayList<>(Arrays.asList(ServiceItem.of("Consultation", 30)))
                : new ArrayList<>(p.services);
        profile.hours = p.hours.isEmpty() ? defaultHours() : new LinkedHashMap<>(p.hours);
        profile.manualSlots = new LinkedHashMap<>(p.manualSlots);
        profile.accentColor  = p.accentColor;
        profile.accentColor2 = p.accentColor2;
    }

    private void bindProfileToFields() {
        slugEt.setText(profile.slug);
        nameEt.setText(profile.businessName);
        descEt.setText(profile.description);
        phoneEt.setText(profile.phone);
        addressEt.setText(profile.address);
        websiteEt.setText(profile.website);
        coverEt.setText(profile.coverImageUrl);
        logoEt.setText(profile.logoUrl);
        refreshImagePreview(coverPreview, profile.coverImageUrl);
        refreshImagePreview(logoPreview, profile.logoUrl);
        refreshLogoButtons();

        if (templateAdapter != null) {
            templateAdapter.setSelectedId(
                    profile.templateId == null || profile.templateId.isEmpty()
                            ? "dental-clinic" : profile.templateId);
        }

        agentCurrentTv.setText(profile.bookingAgentName == null || profile.bookingAgentName.isEmpty()
                ? getString(R.string.booking_agent_none)
                : profile.bookingAgentName);

        hydrateAiCustomization();

        // Timezone spinner
        int tzIdx = 0;
        for (int i = 0; i < TIMEZONES.length; i++) {
            if (TIMEZONES[i].equals(profile.timezone)) { tzIdx = i; break; }
        }
        timezoneSpinner.setSelection(tzIdx);

        if (MerchantProfile.MODE_SLOTS.equals(profile.bookingMode)) {
            modeGroup.check(R.id.modeSlots);
        } else if (MerchantProfile.MODE_ANYTIME.equals(profile.bookingMode)) {
            modeGroup.check(R.id.modeAnytime);
        } else {
            modeGroup.check(R.id.modeInterval);
        }

        refreshShareCard();
        renderServices();
        renderHours();
        renderIntervalChips();
        refreshModeSubsections();
    }

    private void refreshShareCard() {
        boolean has = !TextUtils.isEmpty(profile.slug);
        shareCard.setVisibility(has ? View.VISIBLE : View.GONE);
        shareEmpty.setVisibility(has ? View.GONE : View.VISIBLE);
        if (has) {
            slugLine.setText(getString(R.string.booking_slug_url, profile.slug));
            shareTitle.setText(R.string.booking_share_card_title);
            businessLine.setText(TextUtils.isEmpty(profile.businessName)
                    ? getString(R.string.app_name) : profile.businessName);
        }
    }

    private void renderServices() {
        servicesContainer.removeAllViews();
        for (int i = 0; i < profile.services.size(); i++) {
            ServiceItem item = profile.services.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.item_booking_service, servicesContainer, false);
            EditText nameEt = row.findViewById(R.id.serviceName);
            EditText durEt  = row.findViewById(R.id.serviceDuration);
            EditText priceEt = row.findViewById(R.id.servicePrice);
            EditText descEt  = row.findViewById(R.id.serviceDescription);
            View removeBtn = row.findViewById(R.id.serviceRemove);

            nameEt.setText(item.name);
            durEt.setText(String.valueOf(item.durationMin));
            priceEt.setText(item.price == null ? "" : String.valueOf(item.price));
            descEt.setText(item.description);

            nameEt.addTextChangedListener(textWatcher(s -> item.name = s == null ? "" : s));
            durEt.addTextChangedListener(textWatcher(s -> {
                try { item.durationMin = Integer.parseInt(s == null ? "" : s); }
                catch (NumberFormatException ignored) { item.durationMin = 30; }
            }));
            priceEt.addTextChangedListener(textWatcher(s -> {
                if (TextUtils.isEmpty(s)) { item.price = null; return; }
                try { item.price = Double.parseDouble(s); } catch (NumberFormatException ignored) {}
            }));
            descEt.addTextChangedListener(textWatcher(s -> item.description = s == null ? "" : s));

            removeBtn.setEnabled(profile.services.size() > 1);
            removeBtn.setAlpha(profile.services.size() > 1 ? 1f : 0.4f);
            final int idx = i;
            removeBtn.setOnClickListener(v -> {
                if (profile.services.size() <= 1) return;
                profile.services.remove(idx);
                renderServices();
            });

            servicesContainer.addView(row);
        }
    }

    private void renderHours() {
        hoursContainer.removeAllViews();
        for (int i = 0; i < DAY_KEYS.length; i++) {
            final String key = DAY_KEYS[i];
            View row = LayoutInflater.from(this).inflate(R.layout.item_booking_day_hours, hoursContainer, false);
            CheckBox cb = row.findViewById(R.id.dayCheckbox);
            TextView openTv = row.findViewById(R.id.openTime);
            TextView closeTv = row.findViewById(R.id.closeTime);
            View timeWrap = row.findViewById(R.id.timeWrap);
            TextView closedLabel = row.findViewById(R.id.closedLabel);

            cb.setText(DAY_LABELS[i]);

            List<DayRange> ranges = profile.hours.get(key);
            boolean has = ranges != null && !ranges.isEmpty();
            cb.setChecked(has);
            timeWrap.setVisibility(has ? View.VISIBLE : View.GONE);
            closedLabel.setVisibility(has ? View.GONE : View.VISIBLE);

            DayRange r = has ? ranges.get(0) : new DayRange("09:00", "17:00");
            openTv.setText(r.open);
            closeTv.setText(r.close);

            cb.setOnCheckedChangeListener((b, checked) -> {
                if (checked) {
                    profile.hours.put(key,
                            new ArrayList<>(Arrays.asList(new DayRange("09:00", "17:00"))));
                } else {
                    profile.hours.remove(key);
                }
                renderHours();
            });

            View.OnClickListener openClick = v -> showTimePicker(openTv.getText().toString(), val -> {
                openTv.setText(val);
                List<DayRange> cur = profile.hours.get(key);
                if (cur == null || cur.isEmpty()) {
                    profile.hours.put(key, new ArrayList<>(Arrays.asList(new DayRange(val, "17:00"))));
                } else {
                    cur.get(0).open = val;
                }
            });
            View.OnClickListener closeClick = v -> showTimePicker(closeTv.getText().toString(), val -> {
                closeTv.setText(val);
                List<DayRange> cur = profile.hours.get(key);
                if (cur == null || cur.isEmpty()) {
                    profile.hours.put(key, new ArrayList<>(Arrays.asList(new DayRange("09:00", val))));
                } else {
                    cur.get(0).close = val;
                }
            });
            openTv.setOnClickListener(openClick);
            closeTv.setOnClickListener(closeClick);

            hoursContainer.addView(row);
        }
    }

    private void renderIntervalChips() {
        intervalChips.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        for (int n : INTERVALS) {
            View chip = inf.inflate(R.layout.item_interval_chip, intervalChips, false);
            TextView tv = chip.findViewById(R.id.chipLabel);
            tv.setText(getString(R.string.booking_minutes_short, n));
            boolean on = profile.slotIntervalMin == n;
            chip.setSelected(on);
            chip.setOnClickListener(v -> {
                profile.slotIntervalMin = n;
                renderIntervalChips();
            });
            intervalChips.addView(chip);
        }
    }

    private void refreshModeSubsections() {
        intervalRow.setVisibility(MerchantProfile.MODE_INTERVAL.equals(profile.bookingMode)
                ? View.VISIBLE : View.GONE);
        slotsHint.setVisibility(MerchantProfile.MODE_SLOTS.equals(profile.bookingMode)
                ? View.VISIBLE : View.GONE);
    }

    private void populateTimezoneSpinner() {
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TIMEZONES);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        timezoneSpinner.setAdapter(ad);
        timezoneSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                profile.timezone = TIMEZONES[pos];
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    // ----------------- save -----------------

    private void save() {
        if (TextUtils.isEmpty(profile.slug) ||
                !profile.slug.matches("^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])?$")) {
            Toast.makeText(this, R.string.booking_invalid_slug, Toast.LENGTH_LONG).show();
            return;
        }
        if (TextUtils.isEmpty(profile.businessName)) {
            Toast.makeText(this, R.string.booking_missing_name, Toast.LENGTH_SHORT).show();
            return;
        }
        if (profile.services.isEmpty()) {
            Toast.makeText(this, R.string.booking_missing_service, Toast.LENGTH_SHORT).show();
            return;
        }

        loader.setVisibility(View.VISIBLE);
        repo.save(profile, new MerchantProfileRepository.OnProfileSaved() {
            @Override public void onSaved() {
                runOnUiThread(() -> {
                    loader.setVisibility(View.GONE);
                    Toast.makeText(BookingSetupActivity.this, R.string.saved, Toast.LENGTH_SHORT).show();
                    refreshShareCard();
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> {
                    loader.setVisibility(View.GONE);
                    String msg = (error != null && error.contains("slug_taken"))
                            ? getString(R.string.booking_slug_taken)
                            : (error == null ? "Save failed" : error);
                    Toast.makeText(BookingSetupActivity.this, msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ----------------- helpers -----------------

    private void showTimePicker(String current, OnTimePicked onPicked) {
        int h = 9, m = 0;
        if (current != null && current.matches("\\d{1,2}:\\d{2}")) {
            String[] parts = current.split(":");
            h = Integer.parseInt(parts[0]); m = Integer.parseInt(parts[1]);
        }
        new TimePickerDialog(this, (view, hour, minute) ->
                onPicked.onPicked(String.format(Locale.ROOT, "%02d:%02d", hour, minute)),
                h, m, true).show();
    }

    private static Map<String, List<DayRange>> defaultHours() {
        Map<String, List<DayRange>> h = new LinkedHashMap<>();
        for (String k : Arrays.asList("mon","tue","wed","thu","fri")) {
            h.put(k, new ArrayList<>(Arrays.asList(new DayRange("09:00", "17:00"))));
        }
        return h;
    }

    private interface OnTimePicked { void onPicked(String hhmm); }
    private interface OnString { void apply(String s); }

    private static TextWatcher textWatcher(final OnString cb) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { cb.apply(s == null ? "" : s.toString()); }
        };
    }
}
