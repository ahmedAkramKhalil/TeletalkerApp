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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.teletalker.app.R;

import java.util.List;

public class AgentAdapter extends RecyclerView.Adapter<AgentAdapter.AgentViewHolder> {

    private List<Agent> agents;
    private OnAgentSelectedListener listener;
    private String selectedAgentId = null;

    public interface OnAgentSelectedListener {
        void onAgentSelected(Agent agent);
    }

    public AgentAdapter(List<Agent> agents, OnAgentSelectedListener listener) {
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
        Agent agent = agents.get(position);
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
            if (agents.get(i).getAgentId().equals(agentId) ||
                    (previousSelected != null && agents.get(i).getAgentId().equals(previousSelected))) {
                notifyItemChanged(i);
            }
        }
    }

    public void updateAgents(List<Agent> newAgents) {
        this.agents = newAgents;
        notifyDataSetChanged();
    }

    class AgentViewHolder extends RecyclerView.ViewHolder {
        private MaterialCardView cardView;
        private TextView nameTextView;
        private TextView descriptionTextView;
//        private TextView createdTextView;
//        private TextView creatorTextView;
        private ChipGroup tagsChipGroup;
        private RadioButton radioButton;
        private ImageView avatarImageView;
        private View statusIndicator;

        public AgentViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.agentCardView);
            nameTextView = itemView.findViewById(R.id.agentName);
            descriptionTextView = itemView.findViewById(R.id.agentDescription);
//            createdTextView = itemView.findViewById(R.id.agentCreated);
//            creatorTextView = itemView.findViewById(R.id.agentCreator);
            tagsChipGroup = itemView.findViewById(R.id.agentTags);
            radioButton = itemView.findViewById(R.id.agentRadioButton);
            avatarImageView = itemView.findViewById(R.id.agentAvatar);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
        }

        public void bind(Agent agent) {
            // Set basic info
            nameTextView.setText(agent.getName());
            descriptionTextView.setText(agent.getDescription());

            // Set creation info
//            if (createdTextView != null) {
//                createdTextView.setText("Created " + agent.getFormattedCreatedDate());
//            }
//
//            // Set creator info
//            if (creatorTextView != null) {
//                creatorTextView.setText("By " + agent.getCreatorInfo());
//            }

            // Set tags as chips
            setupTagChips(agent);

            // Set radio button state
            boolean isSelected = agent.getAgentId().equals(selectedAgentId);
            radioButton.setChecked(isSelected);

            // Set status indicator (all fetched agents are considered active)
            if (statusIndicator != null) {
                statusIndicator.setVisibility(View.VISIBLE);
            }

            // Set avatar
            setupAvatar(agent);

            // Set click listeners
            View.OnClickListener clickListener = v -> {
                if (!agent.getAgentId().equals(selectedAgentId)) {
                    setSelectedAgentId(agent.getAgentId());
                    if (listener != null) {
                        listener.onAgentSelected(agent);
                    }
                }
            };

            cardView.setOnClickListener(clickListener);
            radioButton.setOnClickListener(clickListener);

            // Update card appearance based on selection
            updateCardAppearance(isSelected);
        }

        private void setupTagChips(Agent agent) {
            if (tagsChipGroup == null) return;

            tagsChipGroup.removeAllViews();

            List<String> tags = agent.getTags();
            if (tags != null && !tags.isEmpty()) {
                for (String tag : tags) {
                    Chip chip = new Chip(itemView.getContext());
                    chip.setText(tag);
                    chip.setChipBackgroundColorResource(R.color.chip_background);
                    chip.setTextColor(itemView.getContext().getColor(R.color.chip_text));
                    chip.setChipStrokeColorResource(R.color.chip_stroke);
                    chip.setChipStrokeWidth(1f);
                    chip.setTextSize(12f);
                    chip.setClickable(false);
                    chip.setCheckable(false);

                    tagsChipGroup.addView(chip);
                }
                tagsChipGroup.setVisibility(View.VISIBLE);
            } else {
                tagsChipGroup.setVisibility(View.GONE);
            }
        }

        private void setupAvatar(Agent agent) {
            if (avatarImageView == null) return;

            if (agent.getAvatarUrl() != null && !agent.getAvatarUrl().isEmpty()) {
                // Load image with your preferred library
                // Glide.with(itemView.getContext()).load(agent.getAvatarUrl()).into(avatarImageView);
                avatarImageView.setVisibility(View.VISIBLE);
            } else {
                // Set default avatar based on primary tag
                setDefaultAvatar(agent);
            }
        }

        private void setDefaultAvatar(Agent agent) {
            if (avatarImageView == null) return;

            int avatarResource = R.drawable.ic_agent_default;

            // Use primary tag to determine avatar
            List<String> tags = agent.getTags();
//            if (tags != null && !tags.isEmpty()) {
//                String primaryTag = tags.get(0).toLowerCase();
//
////                switch (primaryTag) {
////                    case "customer support":
////                        avatarResource = R.drawable.ic_agent_support;
////                        break;
////                    case "technical help":
////                        avatarResource = R.drawable.ic_agent_technical;
////                        break;
////                    case "sales":
////                        avatarResource = R.drawable.ic_agent_sales;
////                        break;
////                    case "multilingual":
////                        avatarResource = R.drawable.ic_agent_multilingual;
////                        break;
////                    default:
////                        // Try to match language-specific tags
////                        if (primaryTag.contains("english")) {
////                            avatarResource = R.drawable.ic_agent_english;
////                        } else if (primaryTag.contains("arabic") || primaryTag.contains("arab")) {
////                            avatarResource = R.drawable.ic_agent_arabic;
////                        } else if (primaryTag.contains("spanish")) {
////                            avatarResource = R.drawable.ic_agent_spanish;
////                        } else {
////                            avatarResource = R.drawable.ic_agent_default;
////                        }
////                        break;
////                }
//            }

            avatarImageView.setImageResource(avatarResource);
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

    // Helper methods for filtering agents
    public void filterByTag(String tag) {
        List<Agent> filteredAgents = agents.stream()
                .filter(agent -> agent.hasTag(tag))
                .collect(java.util.stream.Collectors.toList());
        updateAgents(filteredAgents);
    }

    public void filterByCreator(boolean userCreatedOnly) {
        if (userCreatedOnly) {
            List<Agent> userAgents = agents.stream()
                    .filter(Agent::isOwnedByUser)
                    .collect(java.util.stream.Collectors.toList());
            updateAgents(userAgents);
        } else {
            notifyDataSetChanged(); // Show all agents
        }
    }

    public void clearFilters(List<Agent> originalAgents) {
        updateAgents(originalAgents);
    }
}
