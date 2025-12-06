package com.teletalker.app.features.home.fragments.settings;

public abstract class SettingsFragmentEvents {

    public static final class NavigateToAgentTypeActivity extends SettingsFragmentEvents {
        public static final NavigateToAgentTypeActivity INSTANCE = new NavigateToAgentTypeActivity();
        private NavigateToAgentTypeActivity() {}
    }

    public static final class NavigateToSelectVoiceActivity extends SettingsFragmentEvents {
        public static final NavigateToSelectVoiceActivity INSTANCE = new NavigateToSelectVoiceActivity();
        private NavigateToSelectVoiceActivity() {}
    }


    // NEW EVENTS
    public static final class NavigateToSubscriptionActivity extends SettingsFragmentEvents {
        public static final NavigateToSubscriptionActivity INSTANCE = new NavigateToSubscriptionActivity();
        private NavigateToSubscriptionActivity() {}
    }

    public static final class Logout extends SettingsFragmentEvents {
        public static final Logout INSTANCE = new Logout();
        private Logout() {}
    }




}
