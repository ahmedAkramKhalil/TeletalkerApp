package com.teletalker.app.features.home.presentation.fragments.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.teletalker.app.R;
import com.teletalker.app.databinding.FragmentHomeBinding;
import com.teletalker.app.features.home.data.models.CallHistoryModel;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {


    private FragmentHomeBinding binding;
    private CallHistoryAdapter adapter;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new CallHistoryAdapter(getFakeData());
        binding.recyclerView.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    List<CallHistoryModel> getFakeData(){
        List<CallHistoryModel> fakeList = new ArrayList<>();
        fakeList.add(
                new CallHistoryModel(
                        1,
                        "Gladys",
                        "1233434",
                        R.drawable.glays_image,
                        "",
                        1,
                        45455,
                        "1:21"
                )

        );
        fakeList.add(
                new CallHistoryModel(
                        2,
                        "John",
                        "1233434",
                        R.drawable.glays_image,
                        "",
                        1,
                        45455,
                        "1:21")

        );

        fakeList.add(
                new CallHistoryModel(
                        3,
                        "John",
                        "1233434",
                        R.drawable.glays_image,
                        "",
                        1,
                        45455,
                        "1:21")

        );

        return fakeList;
    }
}