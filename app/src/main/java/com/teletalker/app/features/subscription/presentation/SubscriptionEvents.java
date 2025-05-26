package com.teletalker.app.features.subscription.presentation;

public abstract class SubscriptionEvents {
    public static final class PopBackStack extends SubscriptionEvents{
        public static final PopBackStack INSTANCE = new PopBackStack();
        private PopBackStack() {}
    }
}
