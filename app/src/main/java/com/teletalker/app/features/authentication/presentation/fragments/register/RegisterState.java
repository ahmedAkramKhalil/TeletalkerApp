package com.teletalker.app.features.authentication.presentation.fragments.register;

public abstract class RegisterState {

    public static final class Idle extends RegisterState {
        public static final Idle INSTANCE = new Idle();
        private Idle() {}
    }

    public static final class Loading extends RegisterState {
        public static final Loading INSTANCE = new Loading();
        private Loading() {}
    }

    public static final class Success extends RegisterState {
        private final String userId;
        private final String email;

        public Success(String userId, String email) {
            this.userId = userId;
            this.email = email;
        }

        public String getUserId() { return userId; }
        public String getEmail() { return email; }
    }

    public static final class Error extends RegisterState {
        private final String message;

        public Error(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
    }
}
