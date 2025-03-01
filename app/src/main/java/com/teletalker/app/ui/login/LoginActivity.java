package com.teletalker.app.ui.login;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.teletalker.app.databinding.ActivityLoginBinding;


public class LoginActivity extends AppCompatActivity {

    private LoginViewModel loginViewModel;
    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;
    private BeginSignInRequest signInRequest;
    private SignInClient oneTapClient;

    private static final int REQ_ONE_TAP = 2;  // Can be any integer unique to the Activity.
    private boolean showOneTapUI = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_login);
        mAuth = FirebaseAuth.getInstance();

        loginViewModel = new ViewModelProvider(this, new LoginViewModelFactory())
                .get(LoginViewModel.class);

        final EditText usernameEditText = binding.username;
        final EditText passwordEditText = binding.password;
        final ProgressBar loadingProgressBar = binding.loading;
        binding.showPasswordBtn.setImageResource(R.drawable.ic_show_password);
        binding.showPasswordBtn.setTag(R.drawable.ic_show_password);

        oneTapClient = Identity.getSignInClient(this);
        signInRequest = BeginSignInRequest.builder()
                .setPasswordRequestOptions(BeginSignInRequest.PasswordRequestOptions.builder()
                        .setSupported(true)
                        .build())
                .setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        // Your server's client ID, not your Android client ID.
                        .setServerClientId(getString(R.string.default_web_client_id))
//                        .setServerClientId("AIzaSyCQvvAS2bU5MHWRgpDiGyLcmRCH5Wj43GU")
                        // Only show accounts previously used to sign in.
                        .setFilterByAuthorizedAccounts(true)
                        .build())
                // Automatically sign in when exactly one credential is retrieved.
                .setAutoSelectEnabled(true)
                .build();


        binding.googleLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (FirebaseConnectivityChecker.isFirebaseNotConnected(getApplicationContext()))
                    return;

                signInWithGoogle();
            }
        });
        binding.exitBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBackPressed();
                }
            });
            binding.forgetPasswordTv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (FirebaseConnectivityChecker.isFirebaseNotConnected(getApplicationContext()))
                        return;

                    startActivity(new Intent(LoginActivity.this,ForgotPasswordActivity.class));
                    finish();
                }
            });
            binding.registerTv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (FirebaseConnectivityChecker.isFirebaseNotConnected(getApplicationContext()))
                        return;

                    startActivity(new Intent(LoginActivity.this,RegisterActivity.class));
                    finish();
                }
            });

        binding.showPasswordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Log.d( "string ","SJDLSKND");
                Integer integer = (Integer) binding.showPasswordBtn.getTag();
                integer = integer == null ? 0 : integer;
                switch (integer){
                    case  R.drawable.ic_hide_password:
                        binding.password.setTransformationMethod( PasswordTransformationMethod.getInstance());
                        binding.showPasswordBtn.setImageResource(R.drawable.ic_show_password);
                        binding.showPasswordBtn.setTag(R.drawable.ic_show_password);
                        break;
                    case R.drawable.ic_show_password:
                        binding.password.setTransformationMethod( HideReturnsTransformationMethod.getInstance());
                        binding.showPasswordBtn.setImageResource(R.drawable.ic_hide_password);
                        binding.showPasswordBtn.setTag(R.drawable.ic_hide_password);
                        break;
                }
            }
        });

        loginViewModel.getLoginFormState().observe(this, new Observer<LoginFormState>() {
            @Override
            public void onChanged(@Nullable LoginFormState loginFormState) {
                if (loginFormState == null) {
                    return;
                }
                binding.login.setEnabled(loginFormState.isDataValid());
                if (loginFormState.getUsernameError() != null) {
                    // Log.d("ERRO",getString(loginFormState.getUsernameError())) ;
                    usernameEditText.setError(getString(loginFormState.getUsernameError()));
                }
                if (loginFormState.getPasswordError() != null) {
                    // Log.d("ERRO",getString(loginFormState.getPasswordError())) ;

                    passwordEditText.setError(getString(loginFormState.getPasswordError()));
                }
            }
        });

        loginViewModel.getLoginResult().observe(this, new Observer<FirebaseUser>() {
            @Override
            public void onChanged(FirebaseUser firebaseUser) {
                loadingProgressBar.setVisibility(View.GONE);
                if (firebaseUser == null) {
                    showLoginFailed();
                    return;
                }
                setResult(Activity.RESULT_OK);
                CashHelper.putBoolean(getApplicationContext(), AppUtils.LOGIN_TYPE_GUEST_KEY, false);
                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                finish();

            }
        });

        TextWatcher afterTextChangedListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // ignore
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // ignore
            }

            @Override
            public void afterTextChanged(Editable s) {
                loginViewModel.loginDataChanged(usernameEditText.getText().toString(),
                        passwordEditText.getText().toString());
            }
        };
        usernameEditText.addTextChangedListener(afterTextChangedListener);
        passwordEditText.addTextChangedListener(afterTextChangedListener);
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    loginViewModel.login(usernameEditText.getText().toString(),
                            passwordEditText.getText().toString());
                }
                return false;
            }
        });

        binding.login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (FirebaseConnectivityChecker.isFirebaseNotConnected(getApplicationContext()))
                    return;

                loadingProgressBar.setVisibility(View.VISIBLE);
                loginViewModel.login(usernameEditText.getText().toString(),
                        passwordEditText.getText().toString());
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(LoginActivity.this, OnBoardingActivity.class));
        finish();
    }

    private void signInWithGoogle() {
        signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        // Your server's client ID, not your Android client ID.
                        .setServerClientId(getString(R.string.default_web_client_id))
                        // Only show accounts previously used to sign in.
                        .setFilterByAuthorizedAccounts(true)
                        .build())
                .build();

        oneTapClient.beginSignIn(signInRequest)
                .addOnSuccessListener(this, new OnSuccessListener<BeginSignInResult>() {
                    @Override
                    public void onSuccess(BeginSignInResult result) {
                        try {
                            startIntentSenderForResult(
                                    result.getPendingIntent().getIntentSender(), REQ_ONE_TAP,
                                    null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            Log.e("TAG", "Couldn't start One Tap UI: " + e.getLocalizedMessage());
                        }
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // No saved credentials found. Launch the One Tap sign-up flow, or
                        // do nothing and continue presenting the signed-out UI.
                        // Log.d("TAG", e.getLocalizedMessage());
                    }
                });


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Log.d("Actibitresul","");
        switch (requestCode) {

            case REQ_ONE_TAP:
                try {
                    SignInCredential googleCredential = oneTapClient.getSignInCredentialFromIntent(data);
                    String idToken = googleCredential.getGoogleIdToken();
                    if (idToken !=  null) {
                        // Got an ID token from Google. Use it to authenticate
                        // with Firebase.
                        AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(idToken, null);
                        mAuth.signInWithCredential(firebaseCredential)
                                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                                    @Override
                                    public void onComplete(@NonNull Task<AuthResult> task) {
                                        if (task.isSuccessful()) {
                                            // Sign in success, update UI with the signed-in user's information
                                            // Log.d("TAG", "signInWithCredential:success");
                                            FirebaseUser user = mAuth.getCurrentUser();
//                                            updateUI(user);
                                        } else {
                                            // If sign in fails, display a message to the user.
                                            Log.w("TAG", "signInWithCredential:failure", task.getException());
//                                            updateUI(null);
                                        }
                                    }
                                });
                    }
                } catch (ApiException e) {
                    // ...
                }
                break;
        }

}

    private void login(String email, String password){
    }
    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (!FirebaseManager.isAnonymouslyLoggedIn(getApplicationContext()) || FirebaseManager.getLoggedInUser(getApplicationContext()) != null) {
            Intent intent  = new Intent(LoginActivity.this, HomeActivity.class);
            intent.putExtra("user",currentUser);
            startActivity(intent);
            finish();
        }
    }

    private void updateUiWithUser(LoggedInUserView model) {
        String welcome = getString(R.string.welcome) + model.getDisplayName();
        // TODO : initiate successful logged in experience
        Toast.makeText(getApplicationContext(), welcome, Toast.LENGTH_LONG).show();
    }

    private void showLoginFailed( ) {
        Toast.makeText(getApplicationContext(), R.string.username_or_email, Toast.LENGTH_SHORT).show();
    }
}