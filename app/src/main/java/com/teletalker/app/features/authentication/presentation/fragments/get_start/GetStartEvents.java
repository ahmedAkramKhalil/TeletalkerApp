package com.teletalker.app.features.authentication.presentation.fragments.get_start;

public abstract class GetStartEvents {

    public static final class NavigateToLoginScreen extends GetStartEvents {
        public static final NavigateToLoginScreen INSTANCE = new NavigateToLoginScreen();
        private NavigateToLoginScreen() {}
    }

    public static final class NavigateToRegisterScreen extends GetStartEvents {
        public static final NavigateToRegisterScreen INSTANCE = new NavigateToRegisterScreen();
        private NavigateToRegisterScreen() {}
    }

}
