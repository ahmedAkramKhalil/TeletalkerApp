package com.teletalker.app.features.home;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.firebase.auth.FirebaseUser;
import com.teletalker.app.R;
import com.teletalker.app.billing.BillingManager;
import com.teletalker.app.databinding.ActivityHomeBinding;
import com.teletalker.app.services.VoIPCallService;
import com.teletalker.app.services.ServiceManager;
import com.teletalker.app.utils.AuthStateManager;
import com.teletalker.app.utils.PermissionManager;
import com.teletalker.app.utils.PreferencesManager;
import com.teletalker.app.utils.RootPermissionManager;
import com.teletalker.app.utils.RootSetupManager;
import com.teletalker.app.utils.SubscriptionCacheManager;
import com.teletalker.app.utils.SubscriptionManager;

import java.util.List;

public class HomeActivity extends BaseThemedActivity implements
        PermissionManager.PermissionCallback,
        SubscriptionManager.SubscriptionListener,
        RootSetupManager.RootSetupCallback {

    private static final String TAG = "HomeActivity";
    private static final int REQUEST_PERMISSIONS_CODE = 1001;

    private PreferencesManager prefsManager;
    private ActivityHomeBinding binding;
    private NavController navController;


    private PermissionManager permissionManager;
    private RootSetupManager rootSetupManager;
    private AuthStateManager authStateManager;
    private boolean isFirstLaunch = true;

    private ServiceManager serviceManager;

    private boolean isInitialized = false;
    private SubscriptionManager subscriptionManager;

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefsManager = PreferencesManager.getInstance(this);
        authStateManager = new AuthStateManager(this);

        subscriptionManager = SubscriptionManager.getInstance(this);
        subscriptionManager.addListener(this);

        // Initial sync
        subscriptionManager.sync();


        // Check authentication FIRST
        if (!authStateManager.isUserLoggedIn()) {
            // User not logged in - redirect to login
            redirectToLogin();
            return;
        }



        setupUI();
        initializeManagers();
        setupAiAgent();
        // Start initialization flow (without default dialer)
        startInitializationFlow();

        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Installation Error")
                    .setMessage("The app features will not work because the app is installed as a user app, not a system app.\n\nPlease uninstall this app and reinstall using the Magisk module.")
                    .setCancelable(false)
                    .setPositiveButton("OK", (dialog, which) -> finish())
                    .show();
            return;
        }

    }


    private void setupAuthStateListener() {
        authStateManager.setupAuthListener(new AuthStateManager.AuthStateCallback() {
            @Override
            public void onUserLoggedIn(FirebaseUser user) {
                Log.d(TAG, "✅ User authenticated: " + user.getEmail());
                // User logged in - stay on current screen
            }

            @Override
            public void onUserLoggedOut() {
                Log.d(TAG, "⚠️ User logged out");
                // Redirect to login
                redirectToLogin();
            }
        });
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("payment_success", false)) {
            // Refresh balance
//            BillingManager.getInstance(this).syncBalance();
            subscriptionManager.sync();

            Toast.makeText(this, "Balance updated!", Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onBalanceChanged(double newBalance) {
        Log.d(TAG, "Balance changed: " + newBalance);
        // Fragments will receive this via their own listeners
    }

    @Override
    public void onPlanChanged(String newPlan) {
        Log.d(TAG, "Plan changed: " + newPlan);

        // Theme already updated by SubscriptionManager
        // Activity will recreate automatically due to theme change
    }

    // ADD THIS METHOD
    private void redirectToLogin() {
        Intent intent = new Intent(this,
                com.teletalker.app.features.authentication.presentation.AuthActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    private void setupAiAgent() {
        PreferencesManager.getInstance(this)
                .saveApiKey("sk_691d29f40ed72ac79857e3132d83dceb494cc2af495d5fae");
    }

    private void setupUI() {
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initNavigation();
        initBottomNavView();
    }

    private void initNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.home_nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }
    }

    private void initBottomNavView() {
        binding.navView.setItemIconTintList(null);
        NavigationUI.setupWithNavController(binding.navView, navController);
    }

    private void initializeManagers() {
        permissionManager = new PermissionManager(this);
        rootSetupManager = new RootSetupManager(this);
        serviceManager = new ServiceManager(this);
    }

    // ============ INITIALIZATION FLOW (NO DEFAULT DIALER) ============

    private void startInitializationFlow() {
        Log.d(TAG, "🚀 Starting TeleTalker initialization flow");
        Log.d(TAG, "📋 Flow: Root → Standard Permissions → Accessibility → Services");

        // Step 1: Check root and request root permissions
        proceedToRootSetup();
    }

    // ============ STEP 1: ROOT PERMISSIONS ============

    private void proceedToRootSetup() {
        Log.d(TAG, "🔐 Step 1: Checking root access...");
        try {
            // Request root and grant system permissions
            rootSetupManager.startRootSetup(this);
        } catch (Exception e) {
            Log.e(TAG, "❌ Root setup failed", e);
            // Continue without root
            onRootSetupFailed("Root setup error: " + e.getMessage());
        }
    }

    @Override
    public void onRootSetupCompleted(boolean success, int grantedCount, int totalCount) {
        Log.d(TAG, "✅ Root setup completed");
        Log.d(TAG, "   Success: " + success);
        Log.d(TAG, "   Granted: " + grantedCount + "/" + totalCount);

        if (success) {
            Toast.makeText(this,
                    "Root permissions granted: " + grantedCount + "/" + totalCount,
                    Toast.LENGTH_SHORT).show();
        }

        // Proceed to standard permissions regardless of root status
        proceedToStandardPermissions();
    }

    @Override
    public void onRootSetupFailed(String reason) {
        Log.w(TAG, "⚠️ Root setup failed: " + reason);
        Toast.makeText(this,
                "Running without root: " + reason,
                Toast.LENGTH_LONG).show();

        // Continue to standard permissions even without root
        proceedToStandardPermissions();
    }

    // ============ STEP 2: STANDARD ANDROID PERMISSIONS ============

    private void proceedToStandardPermissions() {
        Log.d(TAG, "📱 Step 2: Requesting standard Android permissions...");
        permissionManager.checkAndRequestAllPermissions(this);
    }

    @Override
    public void onPermissionsGranted() {
        Log.d(TAG, "✅ All standard permissions granted");
        Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show();
        proceedToAccessibilitySetup();
    }

    public void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivityForResult(intent, 2002);
            } catch (Exception e) {
                Log.e(TAG, "Failed to open exact alarm settings: " + e.getMessage());
                openAppSettings(this);
            }
        }
    }


    public void openAppSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    @Override
    public void onPermissionsMissing(List<String> missingPermissions) {
        Log.d(TAG, "⚠️ Requesting " + missingPermissions.size() + " permissions");

        // Log which permissions are missing
        for (String permission : missingPermissions) {
            Log.d(TAG, "   Missing: " + permission);
        }
    }

    @Override
    public void onPermissionsDenied(List<String> deniedPermissions) {
        Log.w(TAG, "⚠️ Some permissions denied: " + deniedPermissions.size());

        // Log which permissions were denied
        for (String permission : deniedPermissions) {
            Log.w(TAG, "   Denied: " + permission);
        }

        if (permissionManager.hasMinimumRequiredPermissions()) {
            Log.d(TAG, "Has minimum permissions, continuing...");
            proceedToAccessibilitySetup();
        } else {
            showPermissionGuideDialog(deniedPermissions);
        }
    }

    private void showPermissionGuideDialog(List<String> deniedPermissions) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        StringBuilder message = new StringBuilder("TeleTalker needs these permissions:\n\n");
        for (String permission : deniedPermissions) {
            String permName = permission.substring(permission.lastIndexOf('.') + 1);
            message.append("• ").append(permName).append("\n");
        }
        message.append("\nPlease grant them in Settings.");

        new AlertDialog.Builder(this)
                .setTitle("⚠️ Permissions Needed")
                .setMessage(message.toString())
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    openAppSettings();
                })
                .setNegativeButton("Continue", (dialog, which) -> {
                    proceedToAccessibilitySetup();
                })
                .setCancelable(false)
                .show();
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open settings", e);
        }
    }

    // ============ STEP 3: ACCESSIBILITY SERVICE ============

    private void proceedToAccessibilitySetup() {
        Log.d(TAG, "🔊 Step 3: Checking accessibility service...");
        if (!isAccessibilityServiceEnabled(this, VoIPCallService.class)) {
            showAccessibilityServiceDialog();
        } else {
            Log.d(TAG, "✅ Accessibility service already enabled");
            completeInitialization();
        }

        if (!hasExactAlarmPermission(getApplicationContext())) {
            openExactAlarmSettings();
        }
    }

    public boolean hasExactAlarmPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            boolean canSchedule = alarmManager != null && alarmManager.canScheduleExactAlarms();
            Log.d(TAG, "SCHEDULE_EXACT_ALARM permission granted: " + canSchedule);
            return canSchedule;
        }
        // Always allowed on older Android versions
        return true;
    }


    private void showAccessibilityServiceDialog() {
        if (isFinishing() || isDestroyed()) {
            completeInitialization();
            return;
        }

        try {
            new AlertDialog.Builder(this)
                    .setTitle("📞 Enable Call Detection")
                    .setMessage("Enable accessibility service for:\n\n" +
                            "• Background call detection\n" +
                            "• Automatic call handling\n" +
                            "• AI assistant activation")
                    .setPositiveButton("Enable", (dialog, which) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                            startActivity(intent);
                            Toast.makeText(this,
                                    "Find 'TeleTalker' and enable it",
                                    Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Log.e(TAG, "Error opening accessibility settings", e);
                        }
                        completeInitialization();
                    })
                    .setNegativeButton("Skip", (dialog, which) -> {
                        completeInitialization();
                    })
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing accessibility dialog", e);
            completeInitialization();
        }
    }

    private boolean isAccessibilityServiceEnabled(Context context,
                                                  Class<? extends AccessibilityService> service) {
        ComponentName expectedComponentName = new ComponentName(context, service);

        String enabledServicesSetting = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        if (enabledServicesSetting == null) return false;

        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);

        while (colonSplitter.hasNext()) {
            String componentNameString = colonSplitter.next();
            ComponentName enabledService = ComponentName.unflattenFromString(componentNameString);

            if (enabledService != null && enabledService.equals(expectedComponentName)) {
                return true;
            }
        }

        return false;
    }

    // ============ STEP 4: START SERVICES ============

    private void completeInitialization() {
        if (isInitialized) {
            return; // Prevent multiple initialization
        }

        Log.d(TAG, "🚀 Completing app initialization");

        // Start required services
        serviceManager.startCallDetectorService();

        // Mark as initialized
        isInitialized = true;

        Toast.makeText(this, "AI Call Assistant ready!", Toast.LENGTH_SHORT).show();
    }

    /**
     * Debug method to log all permission statuses
     */
    private void logPermissionStatus() {
        Log.d(TAG, "========== PERMISSION STATUS ==========");

        String[] criticalPermissions = {
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.RECORD_AUDIO};

        for (String permission : criticalPermissions) {
            boolean granted = ActivityCompat.checkSelfPermission(this, permission)
                    == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, (granted ? "✅ " : "❌ ") + permission);
        }

        // Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean notifGranted = ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, (notifGranted ? "✅ " : "❌ ") + "POST_NOTIFICATIONS");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            boolean notifGranted = ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, (notifGranted ? "✅ " : "❌ ") + "POST_NOTIFICATIONS");
        }

        // Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d(TAG, "⚠️ Android 14+ detected - FOREGROUND_SERVICE_PHONE_CALL required in manifest");
        }

        Log.d(TAG, "======================================");
    }

    // ============ ACTIVITY RESULTS ============

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            Log.d(TAG, "Permission result received for " + permissions.length + " permissions");
            permissionManager.handlePermissionResult(permissions, grantResults);
        }
    }

    // ============ LIFECYCLE MANAGEMENT ============

    @Override
    protected void onResume() {
        super.onResume();
        subscriptionManager.sync();

        if (isInitialized) {
            Log.d(TAG, "App resumed");

            // Re-check if service is still running
//            if (com.teletalker.app.services.CallDetector.isServiceRunning()) {
//                Log.d(TAG, "✅ Service still running");
//            } else {
//                Log.w(TAG, "⚠️ Service not running, attempting restart...");
////                try {
////                    serviceManager.startCallDetectorService();
////                } catch (Exception e) {
////                    Log.e(TAG, "Failed to restart service", e);
////                }
//            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (rootSetupManager != null) {
            try {
                rootSetupManager.dismissAllDialogs();
            } catch (Exception e) {
                Log.e(TAG, "Error dismissing dialogs", e);
            }
        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        subscriptionManager.removeListener(this);

        Log.d(TAG, "Activity destroying, cleaning up...");
        if (authStateManager != null) {
            authStateManager.removeAuthListener();  // ADD THIS
        }

        if (rootSetupManager != null) {
            try {
                rootSetupManager.cleanup();
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up", e);
            }
            rootSetupManager = null;
        }

        permissionManager = null;
//        serviceManager = null;
        prefsManager = null;
        binding = null;
    }
}