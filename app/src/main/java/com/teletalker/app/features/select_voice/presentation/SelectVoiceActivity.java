package com.teletalker.app.features.select_voice.presentation;

import static java.util.Collections.emptyList;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.teletalker.app.R;
import com.teletalker.app.databinding.ActivitySelectVoiceBinding;
import com.teletalker.app.features.select_voice.data.SelectVoiceAdapter;

public class SelectVoiceActivity extends AppCompatActivity {
    ActivitySelectVoiceBinding binding;
    SelectVoiceViewModel viewModel;
    SelectVoiceAdapter adapter = new SelectVoiceAdapter(emptyList());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivitySelectVoiceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(SelectVoiceViewModel.class);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        viewModel.getVoices();

        observes();
    }

    private void observes() {
        viewModel.typeVoicesList.observe(this, list -> {
            adapter = new SelectVoiceAdapter(list);
            binding.recyclerView.setAdapter(adapter);
            adapter.notifyDataSetChanged();
        });
    }
}