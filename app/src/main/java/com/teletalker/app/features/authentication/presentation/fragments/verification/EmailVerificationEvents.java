package com.teletalker.app.features.authentication.presentation.fragments.verification;

public abstract class EmailVerificationEvents {

    public static final class NavigateToLoginScreen extends EmailVerificationEvents {
        public static final NavigateToLoginScreen INSTANCE = new NavigateToLoginScreen();
        private NavigateToLoginScreen() {}
    }

    public static final class PopBackStack extends EmailVerificationEvents {
        public static final PopBackStack INSTANCE = new PopBackStack();
        private PopBackStack() {}
    }
}