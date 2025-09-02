package com.teletalker.app.features.agent_type;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.teletalker.app.R;

import java.util.List;

public class AgentAdapter extends RecyclerView.Adapter<AgentAdapter.AgentViewHolder> {

    private List<AgentTypeActivity.Agent> agents;
    private OnAgentSelectedListener listener;
    private String selectedAgentId = null;

    public interface OnAgentSelectedListener {
        void onAgentSelected(AgentTypeActivity.Agent agent);
    }

    public AgentAdapter(List<AgentTypeActivity.Agent> agents, OnAgentSelectedListener listener) {
        this.agents = agents;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AgentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_agent, parent, false);
        return new AgentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AgentViewHolder holder, int position) {
        AgentTypeActivity.Agent agent = agents.get(position);
        holder.bind(agent);
    }

    @Override
    public int getItemCount() {
        return agents.size();
    }

    public void setSelectedAgentId(String agentId) {
        String previousSelected = this.selectedAgentId;
        this.selectedAgentId = agentId;

        // Notify changes for radio button updates
        for (int i = 0; i < agents.size(); i++) {
            if (agents.get(i).getId().equals(agentId) ||
                    (previousSelected != null && agents.get(i).getId().equals(previousSelected))) {
                notifyItemChanged(i);
            }
        }
    }

    class AgentViewHolder extends RecyclerView.ViewHolder {
        private MaterialCardView cardView;
        private TextView nameTextView;
        private TextView descriptionTextView;
        private TextView languageTextView;
        private TextView typeTextView;
        private RadioButton radioButton;
        private ImageView avatarImageView;
        private View statusIndicator;

        public AgentViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.agentCardView);
            nameTextView = itemView.findViewById(R.id.agentName);
            descriptionTextView = itemView.findViewById(R.id.agentDescription);
            languageTextView = itemView.findViewById(R.id.agentLanguage);
            typeTextView = itemView.findViewById(R.id.agentType);
            radioButton = itemView.findViewById(R.id.agentRadioButton);
            avatarImageView = itemView.findViewById(R.id.agentAvatar);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
        }

        public void bind(AgentTypeActivity.Agent agent) {
            // Set basic info
            nameTextView.setText(agent.getName());
            descriptionTextView.setText(agent.getDescription());

            // Set language display
            String languageDisplay = getLanguageDisplay(agent.getLanguage());
            languageTextView.setText(languageDisplay);

            // Set type (capitalize first letter)
            String typeDisplay = agent.getType().substring(0, 1).toUpperCase() +
                    agent.getType().substring(1).toLowerCase();
            typeTextView.setText(typeDisplay);

            // Set radio button state
            boolean isSelected = agent.getId().equals(selectedAgentId);
            radioButton.setChecked(isSelected);

            // Set status indicator
            if (statusIndicator != null) {
                statusIndicator.setVisibility(agent.isActive() ? View.VISIBLE : View.GONE);
            }

            // Set avatar (if you have image loading library like Glide or Picasso)
            if (avatarImageView != null && agent.getAvatarUrl() != null && !agent.getAvatarUrl().isEmpty()) {
                // Load image with your preferred library
                // Glide.with(itemView.getContext()).load(agent.getAvatarUrl()).into(avatarImageView);
                avatarImageView.setVisibility(View.VISIBLE);
            } else {
                // Set default avatar based on type
                setDefaultAvatar(agent.getType());
            }

            // Set click listeners
            View.OnClickListener clickListener = v -> {
                if (!agent.getId().equals(selectedAgentId)) {
                    setSelectedAgentId(agent.getId());
                    if (listener != null) {
                        listener.onAgentSelected(agent);
                    }
                }
            };

            cardView.setOnClickListener(clickListener);
            radioButton.setOnClickListener(clickListener);

            // Update card appearance based on selection
            updateCardAppearance(isSelected);

            // Show additional info if available
            if (agent.getCapabilities() != null && !agent.getCapabilities().isEmpty()) {
                // You can add a capabilities view if needed
            }
        }

        private void setDefaultAvatar(String agentType) {
            if (avatarImageView == null) return;

            int avatarResource = R.drawable.ic_agent_default; // Default avatar

//            switch (agentType.toLowerCase()) {
//                case "english":
//                    avatarResource = R.drawable.ic_agent_english;
//                    break;
//                case "arab":
//                case "arabic":
//                    avatarResource = R.drawable.ic_agent_arabic;
//                    break;
//                case "spanish":
//                    avatarResource = R.drawable.ic_agent_spanish;
//                    break;
//                case "japanese":
//                    avatarResource = R.drawable.ic_agent_japanese;
//                    break;
//                case "french":
//                    avatarResource = R.drawable.ic_agent_french;
//                    break;
//                case "german":
//                    avatarResource = R.drawable.ic_agent_german;
//                    break;
//                default:
//                    avatarResource = R.drawable.ic_agent_default;
//                    break;
//            }

            avatarImageView.setImageResource(avatarResource);
        }

        private String getLanguageDisplay(String languageCode) {
            switch (languageCode.toLowerCase()) {
                case "en":
                    return "English";
                case "ar":
                    return "العربية";
                case "es":
                    return "Español";
                case "ja":
                    return "日本語";
                case "fr":
                    return "Français";
                case "de":
                    return "Deutsch";
                case "zh":
                    return "中文";
                case "ko":
                    return "한국어";
                case "it":
                    return "Italiano";
                case "pt":
                    return "Português";
                case "ru":
                    return "Русский";
                case "hi":
                    return "हिन्दी";
                default:
                    return languageCode.toUpperCase();
            }
        }

        private void updateCardAppearance(boolean isSelected) {
            if (isSelected) {
                cardView.setStrokeWidth(3);
                cardView.setStrokeColor(itemView.getContext().getColor(R.color.purple_700));
                cardView.setCardElevation(8f);
            } else {
                cardView.setStrokeWidth(0);
                cardView.setCardElevation(4f);
            }
        }
    }
}