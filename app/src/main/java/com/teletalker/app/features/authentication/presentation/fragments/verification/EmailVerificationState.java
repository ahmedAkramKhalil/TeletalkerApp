package com.teletalker.app.features.authentication.presentation.fragments.verification;

public abstract class EmailVerificationState {

    public static final class Idle extends EmailVerificationState {
        public static final Idle INSTANCE = new Idle();
        private Idle() {}
    }

    public static final class Loading extends EmailVerificationState {
        public static final Loading INSTANCE = new Loading();
        private Loading() {}
    }

    public static final class Success extends EmailVerificationState {
        private final String message;

        public Success(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
    }

    public static final class Error extends EmailVerificationState {
        private final String message;

        public Error(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
    }
}