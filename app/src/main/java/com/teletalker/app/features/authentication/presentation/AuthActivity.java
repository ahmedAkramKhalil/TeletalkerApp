package com.teletalker.app.features.authentication.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.teletalker.app.R;
import com.teletalker.app.databinding.ActivityAuthBinding;
import com.teletalker.app.features.home.HomeActivity;
import com.teletalker.app.utils.AuthStateManager;

/**
 * Authentication Activity - Checks Firebase session on launch
 */
public class AuthActivity extends AppCompatActivity {
    private static final String TAG = "AuthActivity";

    private NavController navController;
    private ActivityAuthBinding binding;
    private AuthStateManager authStateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authStateManager = new AuthStateManager(this);

        // ✅ Check if user is already logged in (Firebase session persists)
        if (authStateManager.isUserLoggedIn()) {
            Log.d(TAG, "✅ User session found, redirecting to home");
            redirectToHome();
            return;
        }

        // ❌ No session - show login UI
        Log.d(TAG, "❌ No session, showing auth screen");
        setupAuthUI();
    }

    /**
     * Redirect to HomeActivity
     */
    private void redirectToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Setup authentication UI
     */
    private void setupAuthUI() {
        EdgeToEdge.enable(this);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initNavController();
    }

    private void initNavController() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.auth_nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }
    }
}