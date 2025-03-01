package com.teletalker.app.ui.login;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
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


import com.teletalker.app.databinding.ActivityRegisterBinding;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class RegisterActivity extends AppCompatActivity {

    ActivityRegisterBinding binding;
    private FirebaseAuth mAuth;
    private LoginViewModel loginViewModel;
    private String termsUrl = "https://samaei.app/terms-and-conditions?view=simple";
    private String privacyUrl = "https://samaei.app/privacy-policy?view=simple";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_register);
        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();



        loginViewModel = new ViewModelProvider(this, new LoginViewModelFactory())
                .get(LoginViewModel.class);

        final EditText usernameEditText = binding.username;
        final EditText passwordEditText = binding.password;
        final ProgressBar loadingProgressBar = binding.loading;
        binding.showPasswordBtn.setImageResource(R.drawable.ic_show_password);
        binding.showPasswordBtn.setTag(R.drawable.ic_show_password);
        binding.termsTv.setOnClickListener(v -> openUrlInBrowser(termsUrl));
        binding.privacyTv.setOnClickListener(v -> openUrlInBrowser(privacyUrl));

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
                    binding.email.setError(getString(loginFormState.getUsernameError()));
                }
                if (loginFormState.getPasswordError() != null) {
                    // Log.d("ERRO",getString(loginFormState.getPasswordError())) ;

                    passwordEditText.setError(getString(loginFormState.getPasswordError()));
                }
            }
        });


        binding.exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
        binding.countryCode.setCountryForNameCode("su");

        binding.loginTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (FirebaseConnectivityChecker.isFirebaseNotConnected(getApplicationContext()))
                    return;

                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
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
                loginViewModel.loginDataChanged(binding.email.getText().toString(),
                        passwordEditText.getText().toString());
            }
        };
        binding.email.addTextChangedListener(afterTextChangedListener);
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

                if (!binding.termsCheckBox.isChecked()){
                    Toast.makeText(getApplicationContext(),R.string.have_to_accept_terms,Toast.LENGTH_SHORT).show();
                    return;
                }

                loadingProgressBar.setVisibility(View.VISIBLE);
                if (binding.email.getText().toString() == null ||binding.email.getText().toString().trim() ==  ""
                || binding.password.getText().toString() == null ||  binding.password.getText().toString().trim()== ""
                        || binding.countryCode.getSelectedCountryCode() == null||  binding.countryCode.getSelectedCountryCode().trim() == ""
                        || binding.phone.getText().toString() == null ||  binding.phone.getText().toString().trim() == ""
                        || binding.username.getText().toString() == null || binding.username.getText().toString().trim() == "")
                {
                    Toast.makeText(getApplicationContext(),R.string.error_register,Toast.LENGTH_SHORT).show();
                    return;
                }
                createUser(binding.email.getText().toString(),binding.password.getText().toString(),binding.username.getText().toString(),binding.countryCode.getSelectedCountryCode().toString() + " " + binding.phone.getText().toString());
            }
        });

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(RegisterActivity.this, OnBoardingActivity.class));
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (!FirebaseManager.isAnonymouslyLoggedIn(getApplicationContext()) || FirebaseManager.getLoggedInUser(getApplicationContext()) != null) {
            startActivity(new Intent(RegisterActivity.this, HomeActivity.class));
            finish();
        }
    }


    public void addDataToFirestor(FirebaseUser firebaseUser,String name,String phone,String dop){
        String uid = firebaseUser.getUid();
//        String fcm = firebaseUser.get;
//        String birthOfDate = firebaseUser.dat;
        int coinsBalance = 0;
        String email = firebaseUser.getEmail();
//        String name = firebaseUser.getDisplayName();
//        String phone = "+970-567117232";
        String appVersion = BuildConfig.VERSION_NAME;
        boolean isOAD = false;
        boolean isCamy = false;
        String kalemat = "unknown";
        boolean isOudrec = false;
        boolean isSamaei = true;
        String device = Build.MODEL;
        String language = Locale.getDefault().getLanguage();
        String system = "Android";
        String systemVersion = Build.VERSION.RELEASE;
        String uuid = MainApp.getInstance().getId();
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        createUserDocument(uid, "", dop, coinsBalance, email, name, phone, appVersion, isOAD, isCamy, kalemat, isOudrec,isSamaei, device, language, system, systemVersion, uuid);

                        Log.w("TAG", "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    // Get new FCM registration token
                    String token = task.getResult();
                    Log.d("TAG", "Refreshed token: " + token);
                    createUserDocument(uid, token, dop, coinsBalance, email, name, phone, appVersion, isOAD, isCamy, kalemat, isOudrec,isSamaei, device, language, system, systemVersion, uuid);
                    // Log and use the token as needed
                    // Log.d("TAG", "FCM Token: " + token);
                });
    }


    public void createUserDocument(String uid, String fcm, String birthOfDate, int coinsBalance, String email, String name, String phone, String appVersion, boolean isOAD, boolean isCamy, String kalemat, boolean isOudrec,boolean isSamaei, String device, String language, String system, String systemVersion, String uuid) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Check if the user already exists in Firestore
        DocumentReference userRef = db.collection("users").document(uid).collection("data").document();
        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot doc = task.getResult();
                if (!doc.exists()) {
                    // User does not exist, create a new document
                    Map<String, Object> user = new HashMap<>();
                    user.put("FCM", fcm);
                    user.put("birthOfDate", birthOfDate);
                    user.put("coinsBalance", coinsBalance);
                    user.put("createdAt", FieldValue.serverTimestamp());
                    user.put("email", email);
                    user.put("name", name);
                    user.put("phone", phone);
                    Map<String, Object> systemMap = new HashMap<>();
                    systemMap.put("appVersion", appVersion);
                    Map<String, Object> appsMap = new HashMap<>();
                    appsMap.put("OAD", isOAD);
                    appsMap.put("camy", isCamy);
                    appsMap.put("kalemat", kalemat);
                    appsMap.put("oudrec", isOudrec);
                    appsMap.put("isSamaei", isSamaei);
                    systemMap.put("apps", appsMap);
                    systemMap.put("device", device);
                    systemMap.put("language", language);
                    systemMap.put("system", system);
                    systemMap.put("systemVersion", systemVersion);
                    user.put("system", systemMap);
                    user.put("uuid", uuid);
                    userRef.set(user)
                            .addOnSuccessListener(aVoid ->  Log.d("TAG", "User document created"))
                            .addOnFailureListener(e -> Log.e("TAG", "Error creating user document", e));
                } else {
                    // User already exists, do nothing
                    // Log.d("TAG", "User already exists: " + doc.getData());
                }
            } else {
                Log.e("TAG", "Error getting document: ", task.getException());
            }
        });
    }
    private void createUser(String email, String password, String name, String phone) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {

                            // Sign in success, update UI with the signed-in user's information
                            // Log.d("TAG", "createUserWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name + "%?" + phone)
                                    .build();

                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    user.updateProfile(profileUpdates)
                                            .addOnCompleteListener(new OnCompleteListener<Void>() {
                                                @Override
                                                public void onComplete(@NonNull Task<Void> task) {
                                                    if (task.isSuccessful()) {
                                                        // Log.d("TAG", "User profile updated.");
                                                    }
                                                    Intent in = new Intent(RegisterActivity.this, LoginActivity.class);
                                                    in.putExtra("user", user);
                                                    startActivity(in);
                                                    finish();
                                                }
                                            });


                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            addDataToFirestor(user,name,phone,"");
                                        }
                                    }).start();


                                }
                            }).start();

                        } else {
                            try {
                                throw task.getException();
                            } catch (FirebaseAuthWeakPasswordException e) {
                                binding.password.setError(getString(R.string.error_weak_password));
                                binding.password.requestFocus();
                            } catch (FirebaseAuthInvalidCredentialsException e) {
                                binding.email.setError(getString(R.string.error_invalid_email));
                                binding.email.requestFocus();
                            } catch (FirebaseAuthUserCollisionException e) {
                                binding.email.setError(getString(R.string.error_user_exists));
                                binding.email.requestFocus();
                            } catch (Exception e) {
                                Log.e("TAG", e.getMessage());
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    binding.loading.setVisibility(View.GONE);
                                }
                            });
                            // If sign in fails, display a message to the user.
                            Log.w("TAG", "createUserWithEmail:failure", task.getException());
                            Toast.makeText(RegisterActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();

                        }
                    }
                });
    }

    private void openUrlInBrowser(String url) {
        Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        if (intent.resolveActivity(this.getPackageManager()) != null) {
            startActivity(intent);
        }
    }

}