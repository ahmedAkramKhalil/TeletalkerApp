package com.teletalker.app.features.authentication.presentation.fragments.login;

public class LoginEvents {
    public static final class NavigateToRegisterScreen extends LoginEvents {
        public static final NavigateToRegisterScreen INSTANCE = new NavigateToRegisterScreen();
        private NavigateToRegisterScreen() {}
    }

    public static final class PopBackStack extends LoginEvents {
        public static final PopBackStack INSTANCE = new PopBackStack();
        private PopBackStack() {}
    }

    public static final class NavigateToHomeScreen extends LoginEvents {
        public static final NavigateToHomeScreen INSTANCE = new NavigateToHomeScreen();
        private NavigateToHomeScreen() {}
    }

}
