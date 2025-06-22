package com.teletalker.app.features.home;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.teletalker.app.R;
import com.teletalker.app.databinding.ActivityHomeBinding;
import com.teletalker.app.services.AICallRecorder;
import com.teletalker.app.services.CallDetector;
import com.teletalker.app.services.ServiceManager;
import com.teletalker.app.services.VoIPCallService;
import com.teletalker.app.utils.AIConfigurationHelper;
import com.teletalker.app.utils.PermissionUtils;
import com.teletalker.app.utils.RootPermissionManager;
import com.teletalker.app.utils.RootSetupManager;

import java.util.List;

/**
 * Main home activity that handles app initialization, permissions, and navigation setup
 */
public class HomeActivity extends AppCompatActivity implements
        com.teletalker.app.features.home.PermissionManager.PermissionCallback,
        RootSetupManager.RootSetupCallback {

    private static final String TAG = "HomeActivity";

    // Request codes
    private static final int REQUEST_PERMISSIONS_CODE = 1001;

    // UI components
    private ActivityHomeBinding binding;
    private NavController navController;

    // Managers
    private com.teletalker.app.features.home.PermissionManager permissionManager;
    private RootSetupManager rootSetupManager;
    private ServiceManager serviceManager;

    // State tracking
    private boolean isInitialized = false;

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupUI();
        initializeManagers();
        startInitializationFlow();
        //TODO: remove this
        setupAiAgent();
    }

    private void setupAiAgent() {
        AIConfigurationHelper.configureAI(
                this,
                "sk_6789e30ae9d555245395c3769d9e317999c5cc21e84fa890",
                "agent_01jxyf9nbrffcbm8z8a34qmqvt",
                AICallRecorder.AIMode.SMART_ASSISTANT,
                true // AI enabled
        );


    }

    // ============ UI SETUP ============

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

    // ============ MANAGER INITIALIZATION ============

    private void initializeManagers() {
        permissionManager = new com.teletalker.app.features.home.PermissionManager(this);
        rootSetupManager = new RootSetupManager(this);
        serviceManager = new ServiceManager(this);
    }

    // ============ INITIALIZATION FLOW ============

    private void startInitializationFlow() {
        Log.d(TAG, "Starting app initialization flow");

        // Step 1: Check device capabilities
        checkDeviceCapabilities();
    }

    private void checkDeviceCapabilities() {
        boolean isRooted = RootPermissionManager.isDeviceRooted();

        if (isRooted) {
            Log.d(TAG, "✅ Rooted device detected");
            showRootDeviceDialog();
        } else {
            Log.w(TAG, "⚠️ Non-rooted device detected");
            showNonRootDeviceDialog();
        }
    }

    // ============ DEVICE CAPABILITY DIALOGS ============

    private void showRootDeviceDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🔓 Rooted Device Detected")
                .setMessage("Your device is rooted! This app can access full call audio for AI processing.\n\n" +
                        "The app will request root permissions to:\n" +
                        "• Record both sides of phone calls\n" +
                        "• Enable real-time AI conversation assistance\n" +
                        "• Provide high-quality call recording")
                .setPositiveButton("Enable Full Features", (dialog, which) -> {
                    rootSetupManager.startRootSetup(this);
                })
                .setNegativeButton("Use Standard Mode", (dialog, which) -> {
                    proceedWithStandardPermissions();
                })
                .setCancelable(false)
                .show();
    }

    private void showNonRootDeviceDialog() {
        new AlertDialog.Builder(this)
                .setTitle("📱 Standard Device")
                .setMessage("Your device is not rooted. The app will work with limited functionality:\n\n" +
                        "• Microphone recording only\n" +
                        "• AI can hear your voice clearly\n" +
                        "• Other party's voice may be faint\n" +
                        "• Use speakerphone for best results")
                .setPositiveButton("Continue", (dialog, which) -> {
                    proceedWithStandardPermissions();
                })
                .setNeutralButton("Learn About Rooting", (dialog, which) -> {
                    showRootingInfoDialog();
                })
                .show();
    }

    private void showRootingInfoDialog() {
        new AlertDialog.Builder(this)
                .setTitle("📖 About Device Rooting")
                .setMessage("Rooting allows apps to access system-level features like call audio recording.\n\n" +
                        "⚠️ Important:\n" +
                        "• Rooting voids warranty\n" +
                        "• Requires technical knowledge\n" +
                        "• Can brick your device if done incorrectly\n\n" +
                        "For full call recording, consider:\n" +
                        "• Using speakerphone mode\n" +
                        "• External recording devices\n" +
                        "• Rooted custom ROMs (advanced users)")
                .setPositiveButton("Continue Standard Mode", (dialog, which) -> {
                    proceedWithStandardPermissions();
                })
                .show();
    }

    // ============ PERMISSION FLOW ============

    private void proceedWithStandardPermissions() {
        Log.d(TAG, "Proceeding with standard permission setup");
        permissionManager.checkAndRequestAllPermissions(this);
    }

    // ============ PERMISSION CALLBACKS ============

    @Override
    public void onPermissionsGranted() {
        Log.d(TAG, "✅ All standard permissions granted");
        Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show();
        proceedToAccessibilitySetup();
    }

    @Override
    public void onPermissionsMissing(List<String> missingPermissions) {
        Log.w(TAG, "⚠️ Missing permissions: " + missingPermissions.size());
        // Permission manager will automatically request them
    }

    @Override
    public void onPermissionsDenied(List<String> deniedPermissions) {
        Log.w(TAG, "❌ Denied permissions: " + deniedPermissions.size());

        if (deniedPermissions.size() == 0) {
            proceedToAccessibilitySetup();
        } else {
            showPermissionDeniedDialog(deniedPermissions);
        }
    }

    private void showPermissionDeniedDialog(List<String> deniedPermissions) {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ Permissions Required")
                .setMessage("Some permissions were denied. The app may not work properly.\n\n" +
                        "Denied permissions: " + deniedPermissions.size() + "\n" +
                        "You can grant them later in Settings.")
                .setPositiveButton("Continue Anyway", (dialog, which) -> {
                    proceedToAccessibilitySetup();
                })
                .setNeutralButton("Open Settings", (dialog, which) -> {
                    openAppSettings();
                })
                .show();
    }


    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Opened app settings");
        } catch (Exception e) {
            Log.e(TAG, "Failed to open app settings: " + e.getMessage());
            Toast.makeText(this, "Cannot open settings. Please manually go to Settings > Apps > TeleTalker", Toast.LENGTH_LONG).show();
        }
    }
    // ============ ROOT SETUP CALLBACKS ============

    @Override
    public void onRootSetupCompleted(boolean success, int grantedCount, int totalCount) {
        if (success) {
            Log.d(TAG, "🎉 Root setup completed successfully!");
            showRootSetupSuccessDialog();
        } else {
            Log.w(TAG, "⚠️ Root setup partially completed: " + grantedCount + "/" + totalCount);
            showRootSetupPartialDialog(grantedCount, totalCount);
        }

        // Continue with standard permissions after root setup
        proceedWithStandardPermissions();
    }

    @Override
    public void onRootSetupFailed(String reason) {
        Log.e(TAG, "❌ Root setup failed: " + reason);
        showRootSetupFailedDialog(reason);

        // Fallback to standard permissions
        proceedWithStandardPermissions();
    }

    private void showRootSetupSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🎉 Root Setup Complete!")
                .setMessage("All system permissions granted successfully!\n\n" +
                        "✅ Call audio recording enabled\n" +
                        "✅ Real-time AI processing ready\n" +
                        "✅ Full conversation access")
                .setPositiveButton("Continue", null)
                .show();
    }

    private void showRootSetupPartialDialog(int granted, int total) {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ Partial Root Setup")
                .setMessage("Some root permissions were granted (" + granted + "/" + total + ").\n\n" +
                        "The app will work with reduced functionality.")
                .setPositiveButton("Continue", null)
                .setNeutralButton("Retry Root Setup", (dialog, which) -> {
                    rootSetupManager.startRootSetup(this);
                })
                .show();
    }

    private void showRootSetupFailedDialog(String reason) {
        new AlertDialog.Builder(this)
                .setTitle("❌ Root Setup Failed")
                .setMessage("Root setup failed: " + reason + "\n\n" +
                        "The app will continue with standard functionality.")
                .setPositiveButton("Continue", null)
                .show();
    }

    // ============ ACCESSIBILITY SETUP ============

    private void proceedToAccessibilitySetup() {
        if (!isAccessibilityServiceEnabled(this, VoIPCallService.class)) {
            showAccessibilityServiceDialog();
        } else {
            completeInitialization();
        }
    }

    private void showAccessibilityServiceDialog() {
        new AlertDialog.Builder(this)
                .setTitle("📞 VoIP Call Detection")
                .setMessage("Enable accessibility service for VoIP call detection?\n\n" +
                        "This allows the app to detect calls from:\n" +
                        "• WhatsApp\n" +
                        "• Telegram\n" +
                        "• Skype\n" +
                        "• Other VoIP apps")
                .setPositiveButton("Enable", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                    completeInitialization();
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    Log.w(TAG, "Accessibility service skipped");
                    completeInitialization();
                })
                .show();
    }

    // ============ FINALIZATION ============

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

    // ============ PERMISSION RESULT HANDLING ============

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            permissionManager.handlePermissionResult(permissions, grantResults);
        }
    }

    // ============ UTILITY METHODS ============

    private boolean isAccessibilityServiceEnabled(Context context, Class<? extends AccessibilityService> service) {
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

    // ============ LIFECYCLE METHODS ============

    @Override
    protected void onResume() {
        super.onResume();

        // Check if accessibility service was enabled while away
        if (isInitialized && !isAccessibilityServiceEnabled(this, VoIPCallService.class)) {
            // Could show a subtle notification that accessibility service is disabled
            Log.d(TAG, "Accessibility service is disabled");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Cleanup if needed
        if (serviceManager != null) {
            // Could stop services if required
        }
    }
}