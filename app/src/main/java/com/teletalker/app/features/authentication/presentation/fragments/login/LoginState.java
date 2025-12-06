package com.teletalker.app.features.authentication.presentation.fragments.login;

public abstract class LoginState {

    public static final class Idle extends LoginState {
        public static final Idle INSTANCE = new Idle();
        private Idle() {}
    }

    public static final class Loading extends LoginState {
        public static final Loading INSTANCE = new Loading();
        private Loading() {}
    }

    public static final class Success extends LoginState {
        private final String userId;
        private final String email;
        private final String password;

        public Success(String userId, String email,String password) {
            this.userId = userId;
            this.password = password;
            this.email = email;
        }

        public String getUserId() { return userId; }
        public String getEmail() { return email; }
        public String getPassword() { return password; }
    }

    public static final class Error extends LoginState {
        private final String message;

        public Error(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
    }
}
