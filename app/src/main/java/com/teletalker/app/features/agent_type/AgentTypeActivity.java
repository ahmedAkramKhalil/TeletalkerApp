package com.teletalker.app.features.agent_type;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.teletalker.app.R;
import com.teletalker.app.utils.PreferencesManager;
import com.teletalker.app.network.ApiService;

import java.util.ArrayList;
import java.util.List;

public class AgentTypeActivity extends AppCompatActivity implements AgentAdapter.OnAgentSelectedListener {

    // UI Components
    private MaterialCardView backButton;
    private RecyclerView agentsRecyclerView;
    private ProgressBar progressBar;
    private androidx.appcompat.widget.AppCompatButton applyButton;

    private PreferencesManager prefsManager;
    private ApiService apiService;
    private AgentAdapter agentAdapter;
    private List<Agent> agents;
    private Agent selectedAgent = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_agent_type);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initComponents();
        setupRecyclerView();
        setupClickListeners();
        loadAgents();
    }

    private void initComponents() {
        // Initialize preferences and API service
        prefsManager = PreferencesManager.getInstance(this);
        apiService = ApiService.getInstance();
        agents = new ArrayList<>();

        // Initialize UI components
        backButton = findViewById(R.id.materialCardView);
        agentsRecyclerView = findViewById(R.id.agentsRecyclerView);
        progressBar = findViewById(R.id.progressBar);
        applyButton = findViewById(R.id.subscribeButton);
    }

    private void setupRecyclerView() {
        agentAdapter = new AgentAdapter(agents, this);
        agentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        agentsRecyclerView.setAdapter(agentAdapter);

        // Load saved selection
        String savedAgentId = prefsManager.getSelectedAgentId();
        if (savedAgentId != null) {
            agentAdapter.setSelectedAgentId(savedAgentId);
        }
    }

    private void setupClickListeners() {
        // Back button
        backButton.setOnClickListener(v -> finish());

        // Apply button
        applyButton.setOnClickListener(v -> saveSelectedAgent());
    }

    @Override
    public void onAgentSelected(Agent agent) {
        selectedAgent = agent;
        // Enable apply button when an agent is selected
        applyButton.setEnabled(true);
        applyButton.setAlpha(1.0f);
    }


    private void loadAgents() {
        String apiKey = prefsManager.getApiKey();
        if (apiKey == null) {
            Toast.makeText(this, "API Key not found. Please login again.", Toast.LENGTH_LONG).show();
//            loadDefaultAgents();
            return;
        }

        // Show loading
        progressBar.setVisibility(View.VISIBLE);
        agentsRecyclerView.setVisibility(View.GONE);
        applyButton.setEnabled(false);
        applyButton.setAlpha(0.5f);


        apiService.getAgents(apiKey, new ApiService.ApiCallback<Agent.AgentResponse>() {
            @Override
            public void onSuccess(Agent.AgentResponse agentResponse) {
                runOnUiThread(() -> {
//                    allAgents = agentResponse.getAgents();
//                    agentAdapter.updateAgents(allAgents);


                    progressBar.setVisibility(View.GONE);
                    agentsRecyclerView.setVisibility(View.VISIBLE);

                    agents.clear();
                    agents.addAll(agentResponse.getAgents());
                    agentAdapter.notifyDataSetChanged();


                    // Restore saved selection
                    restoreSavedSelection();

                    if (agents.isEmpty()) {
                        Toast.makeText(AgentTypeActivity.this, "No agents available", Toast.LENGTH_SHORT).show();
                    }



                    // Handle pagination if needed
                    if (agentResponse.hasMore()) {
                        // Load more agents using next_cursor
                        String nextCursor = agentResponse.getNextCursor();
                        // loadMoreAgents(nextCursor);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    // Handle error
                    Toast.makeText(AgentTypeActivity.this, "Failed to load agents: " + error,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }


//    private void loadDefaultAgents() {
//        agents.clear();
//        agents.add(new Agent("default_english", "English Agent", "english", "en", "Default English speaking agent"));
//        agents.add(new Agent("default_arab", "Arabic Agent", "arab", "ar", "Default Arabic speaking agent"));
//        agents.add(new Agent("default_spanish", "Spanish Agent", "spanish", "es", "Default Spanish speaking agent"));
//        agents.add(new Agent("default_japanese", "Japanese Agent", "japanese", "ja", "Default Japanese speaking agent"));
//        agents.add(new Agent("default_french", "French Agent", "french", "fr", "Default French speaking agent"));
//        agents.add(new Agent("default_german", "German Agent", "german", "de", "Default German speaking agent"));
//
//        agentAdapter.notifyDataSetChanged();
//        restoreSavedSelection();
//    }

    private void restoreSavedSelection() {
        String savedAgentId = prefsManager.getSelectedAgentId();
        if (savedAgentId != null) {
            for (Agent agent : agents) {
                if (agent.getId().equals(savedAgentId)) {
                    selectedAgent = agent;
                    agentAdapter.setSelectedAgentId(savedAgentId);
                    applyButton.setEnabled(true);
                    applyButton.setAlpha(1.0f);
                    break;
                }
            }
        }
    }

    private void saveSelectedAgent() {
        if (selectedAgent == null) {
            Toast.makeText(this, "Please select an agent", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save all agent details to preferences
        prefsManager.saveSelectedAgent(
                selectedAgent.getId(),
                selectedAgent.getName(),
                selectedAgent.getType(),
                selectedAgent.getLanguage()
        );

        Log.d("SAVEAGENT", "AGENTID=" + selectedAgent.getId());

        // Save additional details if needed
        prefsManager.saveString("selected_agent_description", selectedAgent.getDescription());

        // Optional: Send selection to server
//        String apiKey = prefsManager.getApiKey();
//        if (apiKey != null) {
//            apiService.selectAgent(apiKey, selectedAgent.getId(), new ApiService.ApiCallback<Boolean>() {
//                @Override
//                public void onSuccess(Boolean result) {
//                    // Selection saved to server successfully
//                }
//
//                @Override
//                public void onError(String error) {
//                    // Server update failed, but local save succeeded
//                    // Could show a subtle warning or retry later
//                }
//            });
//        }

        Toast.makeText(this, "Agent selected: " + selectedAgent.getName(),
                Toast.LENGTH_SHORT).show();

        // Return result to calling activity
        setResult(RESULT_OK);
        finish();
    }

    // Agent model class with all details
}