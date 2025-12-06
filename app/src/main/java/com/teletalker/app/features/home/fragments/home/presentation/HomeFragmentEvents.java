package com.teletalker.app.features.home.fragments.home.presentation;

import com.teletalker.app.features.home.fragments.settings.SettingsFragmentEvents;

public class HomeFragmentEvents {
    public static final class NavigateToSubscriptionScreen extends HomeFragmentEvents {
        public static final NavigateToSubscriptionScreen INSTANCE = new NavigateToSubscriptionScreen();
        private NavigateToSubscriptionScreen() {}
    }

    public static final class NavigateToCallHistoryScreen extends HomeFragmentEvents {
        public static final NavigateToCallHistoryScreen INSTANCE = new NavigateToCallHistoryScreen();
        private NavigateToCallHistoryScreen() {}
    }

    public static final class NavigateToAgentTypeActivity extends HomeFragmentEvents {
        public static final NavigateToAgentTypeActivity INSTANCE = new NavigateToAgentTypeActivity();
        private NavigateToAgentTypeActivity() {}
    }


}
