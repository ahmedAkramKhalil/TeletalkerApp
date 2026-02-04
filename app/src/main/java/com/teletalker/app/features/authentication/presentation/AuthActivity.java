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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.teletalker.app.R;
import com.teletalker.app.databinding.ActivityAuthBinding;
import com.teletalker.app.features.home.HomeActivity;

public class AuthActivity extends AppCompatActivity {
    private static final String TAG = "AuthActivity";

    private NavController navController;
    private ActivityAuthBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Check if user exists
        if (currentUser != null) {
            // Reload to get fresh data
            currentUser.reload().addOnCompleteListener(task -> {
                if (task.isSuccessful() && currentUser.isEmailVerified()) {
                    // Email is verified - go to home
                    Log.d(TAG, "✅ User verified - Going to home");
                    redirectToHome();
                } else {
                    // Not verified - sign out and show auth
                    Log.d(TAG, "❌ User NOT verified - Showing auth");
                    FirebaseAuth.getInstance().signOut();
                    setupAuthUI();
                }
            });
        } else {
            // No user - show auth
            Log.d(TAG, "❌ No user - Showing auth");
            setupAuthUI();
        }
    }

    private void redirectToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

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