package com.teletalker.app;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.teletalker.app.features.home.ThemeManager;
import com.teletalker.app.services.ServiceManager;
import com.teletalker.app.services.scheduler.CallSchedulerManager;


public class
TeletalkerApplication extends Application {

    private ServiceManager serviceManager;

    public TeletalkerApplication() {
        super();
        // CRASHES HERE! The context is not ready to provide resources.
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            FirebaseApp.initializeApp(this);
            ThemeManager themeManager = ThemeManager.getInstance(this);
            themeManager.applyTheme();
            initializeScheduledCallsSystem();

//            serviceManager = new ServiceManager(this);
//            serviceManager.startCallDetectorService();



        }catch (Exception e ){
            Log.e("TeleTalkerApp", "❌ Failed to initialize scheduled calls system: " + e.getMessage(), e);

        }

    }



    private void initializeScheduledCallsSystem() {
        try {
             CallSchedulerManager schedulerManager;
            // Create scheduler manager
            schedulerManager = new CallSchedulerManager(this);
            // Initialize periodic background check for scheduled calls
            schedulerManager.initializePeriodicCheck();
            Log.d("TeleTalkerApp", "✅ Scheduled calls system initialized");
        } catch (Exception e) {
            Log.e("TeleTalkerApp", "❌ Failed to initialize scheduled calls system: " + e.getMessage(), e);
        }
    }



}
