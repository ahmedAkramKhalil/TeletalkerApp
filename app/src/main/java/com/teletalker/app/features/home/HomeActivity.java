package com.teletalker.app.features.home;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.teletalker.app.R;
import com.teletalker.app.databinding.ActivityHomeBinding;
import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.database.CallDatabase;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;
import com.teletalker.app.features.home.fragments.callhistory.data.repository.CallRepository;
import com.teletalker.app.features.home.fragments.callhistory.data.repository.CallRepositoryImpl;
import com.teletalker.app.services.CallDetector;
import com.teletalker.app.services.VoIPCallService;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;

public class HomeActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS_CODE = 1;

    private ActivityHomeBinding binding;
    private NavController navController;

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (!isDeviceRooted()) {
            new AlertDialog.Builder(this)
                    .setTitle("Root Required")
                    .setMessage("This app requires root access to work.")
                    .setCancelable(false)
                    .setPositiveButton("Exit", (dialog, which) -> finish())
                    .show();
        }



        requestPermissionsIfNeeded();

        initNavigation();

        initBottomNavView();


        Intent serviceIntent = new Intent(this, CallDetector.class);
        startService(serviceIntent);


        if (!isAccessibilityServiceEnabled(this, VoIPCallService.class)) {
            new AlertDialog.Builder(this)
                    .setTitle("Accessibility Service Required")
                    .setMessage("This app requires accessibility service to work.")
                    .setCancelable(false)
                    .setPositiveButton("Open Settings", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                                startActivity(intent);
                            }

                    )
                    .setNegativeButton("Exit", (dialog, which) -> finish())
                    .show();

        }

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

    private void requestPermissionsIfNeeded() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.CALL_PHONE,
                            Manifest.permission.READ_CALL_LOG,
                            Manifest.permission.ANSWER_PHONE_CALLS,
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.RECORD_AUDIO
                    },
                    REQUEST_PERMISSIONS_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Permissions", permissions[i] + " granted.");
                } else {
                    Log.e("Permissions", permissions[i] + " denied.");
                }
            }
        }
    }

    private boolean isDeviceRooted() {
        String[] paths = {
                "/sbin/su", "/system/bin/su", "/system/xbin/su",
                "/data/local/xbin/su", "/data/local/bin/su",
                "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su"};
        for (String path : paths) {
            if (new File(path).exists()) {
                return true;
            }
        }
        return false;
    }

    public boolean isAccessibilityServiceEnabled(Context context, Class<? extends AccessibilityService> service) {
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

            if (enabledService != null && enabledService.equals(expectedComponentName))
                return true;
        }

        return false;
    }


}
