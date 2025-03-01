package com.teletalker.app.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.teletalker.app.R;
import com.teletalker.app.databinding.ActivityForgotPasswordBinding;


public class ForgotPasswordActivity extends AppCompatActivity {


    ActivityForgotPasswordBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_forgot_password);

        binding.send.setAlpha(0.5F);
        binding.send.setActivated(false);

        binding.send.setVisibility(View.VISIBLE);
        binding.username.setVisibility(View.VISIBLE);
        binding.textView2.setVisibility(View.VISIBLE);
        binding.success.setText(R.string.email_sent);
        binding.success.setVisibility(View.GONE);
        binding.loginTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(ForgotPasswordActivity.this, LoginActivity.class));
                finish();

            }
        });


        binding.username.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (binding.username.getText().toString() != null) {
                    if (isUserNameValid(binding.username.getText().toString())) {
                        binding.send.setAlpha(1F);
                        binding.send.setActivated(true);

                    } else {
                        {
                            binding.send.setAlpha(0.5F);
                            binding.send.setActivated(false);
                        }
                    }
                } else {
                    binding.send.setAlpha(0.5F);
                    binding.send.setActivated(false);
                }
            }
        });

        binding.send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FirebaseAuth auth = FirebaseAuth.getInstance();


                auth.sendPasswordResetEmail(binding.username.getText().toString())
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                if (task.isSuccessful()) {
                                    binding.send.setVisibility(View.GONE);
                                    binding.username.setVisibility(View.GONE);
                                    binding.textView2.setVisibility(View.GONE);
                                    binding.success.setText(R.string.email_sent);
                                    binding.success.setVisibility(View.VISIBLE);
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                Thread.sleep(6000);
                                                startActivity(new Intent(ForgotPasswordActivity.this, LoginActivity.class));
                                            } catch (InterruptedException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    }).start();

                                } else {
                                    Toast.makeText(getApplicationContext(), R.string.error_invalid_email, Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        });

        binding.exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
    }

    private boolean isUserNameValid(String username) {
        if (username == null) {
            return false;
        }
        if (username.contains("@")) {
            return Patterns.EMAIL_ADDRESS.matcher(username).matches();
        } else {
            return false;
        }
    }

}