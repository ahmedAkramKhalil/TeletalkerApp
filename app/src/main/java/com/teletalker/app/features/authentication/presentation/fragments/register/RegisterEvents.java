package com.teletalker.app.features.authentication.presentation.fragments.register;

public abstract class RegisterEvents {

    public static final class NavigateToLoginScreen extends RegisterEvents {
        public static final NavigateToLoginScreen INSTANCE = new NavigateToLoginScreen();
        private NavigateToLoginScreen() {}
    }

    public static final class PopBackStack extends RegisterEvents {
        public static final PopBackStack INSTANCE = new PopBackStack();
        private PopBackStack() {}
    }

}
