package com.teletalker.app.features.home.presentation.fragments.callhistory;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.teletalker.app.R;
import com.teletalker.app.databinding.FragmentCallHistoryBinding;
import com.teletalker.app.features.home.data.models.CallHistoryModel;

import java.util.ArrayList;
import java.util.List;

public class CallHistoryFragment extends Fragment {

    private FragmentCallHistoryBinding binding;

    SeeAllHistoryAdapter adapter;

    CallHistoryViewModel viewModel;

    List<CallHistoryModel> fakeList = new ArrayList<>();

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
        viewModel.getCallHistoryList();


    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    void initializeVariables(){
        viewModel = new ViewModelProvider(this).get(CallHistoryViewModel.class);
        adapter = new SeeAllHistoryAdapter(fakeList);
        binding.recyclerView.setAdapter(adapter);
    }

    @SuppressLint("NotifyDataSetChanged")
    void observers(){
        viewModel.callHistoryList.observe(getViewLifecycleOwner(), list -> {
            fakeList.clear();
            fakeList.addAll(list);
            adapter.notifyDataSetChanged();
        });
    }

}
