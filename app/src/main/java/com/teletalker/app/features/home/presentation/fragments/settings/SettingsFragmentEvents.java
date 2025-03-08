package com.teletalker.app.features.home.presentation.fragments.settings;

import com.teletalker.app.features.home.presentation.fragments.home.HomeFragmentEvents;

public abstract class SettingsFragmentEvents {

    public static final class NavigateToAgentTypeActivity extends SettingsFragmentEvents {
        public static final NavigateToAgentTypeActivity INSTANCE = new NavigateToAgentTypeActivity();
        private NavigateToAgentTypeActivity() {}
    }

    public static final class NavigateToSelectVoiceActivity extends SettingsFragmentEvents {
        public static final NavigateToSelectVoiceActivity INSTANCE = new NavigateToSelectVoiceActivity();
        private NavigateToSelectVoiceActivity() {}
    }


}
