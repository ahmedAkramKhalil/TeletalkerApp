package com.teletalker.app.features.home.fragments.callhistory.presentation;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.teletalker.app.databinding.FragmentCallHistoryBinding;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;
import com.teletalker.app.features.home.fragments.callhistory.presentation.adapters.SeeAllHistoryAdapter;
import com.teletalker.app.services.CallRecorderManager;

import java.util.ArrayList;
import java.util.List;

public class CallHistoryFragment extends Fragment {

    private FragmentCallHistoryBinding binding;

    SeeAllHistoryAdapter adapter;

    CallRecorderManager recorderManager;

    CallHistoryViewModel viewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCallHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeVariables();
        observers();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        recorderManager.stopRecording();
        recorderManager.stopPreviousPlayback();
    }


    void initializeVariables(){
        recorderManager = new CallRecorderManager(requireContext());
        viewModel = new ViewModelProvider(this).get(CallHistoryViewModel.class);
        adapter = new SeeAllHistoryAdapter(new ArrayList<>(),
                new SeeAllHistoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(CallEntity item) {
                Log.d("CallHistoryFragment", "Item clicked: = " + item.recordingFilePath);
                recorderManager.togglePlay(item.recordingFilePath);
            }
        });
        binding.recyclerView.setAdapter(adapter);
    }

    @SuppressLint("NotifyDataSetChanged")
    void observers(){
        viewModel.getCallHistoryLiveData().observe(getViewLifecycleOwner(), callHistoryItems -> {
           adapter.updateData(callHistoryItems);
        });
    }

}
