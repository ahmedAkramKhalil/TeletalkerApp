package com.teletalker.app.features.agent_type;

import android.icu.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;


// Agent.java - Refactored Agent model
public  class Agent {
    private String agent_id;
    private String name;
    private List<String> tags;
    private long created_at_unix_secs;
    private AccessInfo access_info;

    // Additional fields for local use (not from API)
    private String description; // For local display
    private String avatarUrl;   // For local display
    private boolean isSelected; // For UI state

    // Default constructor for JSON parsing
    public Agent() {
        this.tags = new ArrayList<>();
    }

    // Constructor from API response
    public Agent(String agentId, String name, List<String> tags, long createdAtUnixSecs, AccessInfo accessInfo) {
        this.agent_id = agentId;
        this.name = name;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        this.created_at_unix_secs = createdAtUnixSecs;
        this.access_info = accessInfo;
        this.isSelected = false;
    }

    // Constructor for manual creation
    public Agent(String agentId, String name, String description) {
        this.agent_id = agentId;
        this.name = name;
        this.description = description;
        this.tags = new ArrayList<>();
        this.created_at_unix_secs = System.currentTimeMillis() / 1000;
        this.access_info = new AccessInfo(true, "User", "", "admin");
        this.isSelected = false;
    }

    // Core getters matching API response
    public String getAgentId() { return agent_id; }
    public String getName() { return name; }
    public List<String> getTags() { return new ArrayList<>(tags); }
    public long getCreatedAtUnixSecs() { return created_at_unix_secs; }
    public AccessInfo getAccessInfo() { return access_info; }

    // Additional getters for local use
    public String getDescription() {
        if (description != null) return description;
        return tags.isEmpty() ? "AI Assistant" : String.join(", ", tags);
    }
    public String getAvatarUrl() { return avatarUrl; }
    public boolean isSelected() { return isSelected; }

    // Setters for JSON parsing
    public void setAgent_id(String agent_id) { this.agent_id = agent_id; }
    public void setName(String name) { this.name = name; }
    public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }
    public void setCreated_at_unix_secs(long created_at_unix_secs) { this.created_at_unix_secs = created_at_unix_secs; }
    public void setAccess_info(AccessInfo access_info) { this.access_info = access_info; }

    // Setters for local use
    public void setDescription(String description) { this.description = description; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public void setSelected(boolean selected) { this.isSelected = selected; }

    // Utility methods
    public void addTag(String tag) {
        if (tag != null && !tags.contains(tag)) {
            tags.add(tag);
        }
    }

    public void removeTag(String tag) {
        tags.remove(tag);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public String getTagsAsString() {
        return String.join(", ", tags);
    }

    public Date getCreatedDate() {
        return new Date(created_at_unix_secs * 1000);
    }

    public String getFormattedCreatedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        return sdf.format(getCreatedDate());
    }

    public boolean isOwnedByUser() {
        return access_info != null && access_info.isCreator();
    }

    public String getCreatorInfo() {
        if (access_info == null) return "Unknown";
        if (access_info.isCreator()) return "You";
        return access_info.getCreatorName() != null ? access_info.getCreatorName() : "Unknown";
    }

    // For backwards compatibility with existing code
    @Deprecated
    public String getId() { return agent_id; }

    @Deprecated
    public String getType() {
        return tags.isEmpty() ? "Assistant" : tags.get(0);
    }

    @Deprecated
    public String getLanguage() {
        return "English"; // Default, could be enhanced to detect from tags
    }

    @Deprecated
    public boolean isActive() {
        return true; // Assume all fetched agents are active
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Agent agent = (Agent) obj;
        return Objects.equals(agent_id, agent.agent_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agent_id);
    }

    @Override
    public String toString() {
        return "Agent{" +
                "agent_id='" + agent_id + '\'' +
                ", name='" + name + '\'' +
                ", tags=" + tags +
                ", created_at_unix_secs=" + created_at_unix_secs +
                ", access_info=" + access_info +
                '}';
    }

    // AgentResponse.java - Main response wrapper
    public class AgentResponse {
        private List<Agent> agents;
        private boolean has_more;
        private String next_cursor;

        // Default constructor for JSON parsing
        public AgentResponse() {}

        public AgentResponse(List<Agent> agents, boolean hasMore, String nextCursor) {
            this.agents = agents;
            this.has_more = hasMore;
            this.next_cursor = nextCursor;
        }

        // Getters
        public List<Agent> getAgents() { return agents; }
        public boolean hasMore() { return has_more; }
        public String getNextCursor() { return next_cursor; }

        // Setters for JSON parsing
        public void setAgents(List<Agent> agents) { this.agents = agents; }
        public void setHas_more(boolean has_more) { this.has_more = has_more; }
        public void setNext_cursor(String next_cursor) { this.next_cursor = next_cursor; }

        @Override
        public String toString() {
            return "AgentResponse{" +
                    "agents=" + (agents != null ? agents.size() : 0) + " agents" +
                    ", has_more=" + has_more +
                    ", next_cursor='" + next_cursor + '\'' +
                    '}';
        }
    }

    // AccessInfo.java - Nested access information
    public static class AccessInfo {
        private boolean is_creator;
        private String creator_name;
        private String creator_email;
        private String role;

        // Default constructor for JSON parsing
        public AccessInfo() {}

        public AccessInfo(boolean isCreator, String creatorName, String creatorEmail, String role) {
            this.is_creator = isCreator;
            this.creator_name = creatorName;
            this.creator_email = creatorEmail;
            this.role = role;
        }

        // Getters
        public boolean isCreator() { return is_creator; }
        public String getCreatorName() { return creator_name; }
        public String getCreatorEmail() { return creator_email; }
        public String getRole() { return role; }

        // Setters for JSON parsing
        public void setIs_creator(boolean is_creator) { this.is_creator = is_creator; }
        public void setCreator_name(String creator_name) { this.creator_name = creator_name; }
        public void setCreator_email(String creator_email) { this.creator_email = creator_email; }
        public void setRole(String role) { this.role = role; }

        @Override
        public String toString() {
            return "AccessInfo{" +
                    "is_creator=" + is_creator +
                    ", creator_name='" + creator_name + '\'' +
                    ", creator_email='" + creator_email + '\'' +
                    ", role='" + role + '\'' +
                    '}';
        }
    }

}
