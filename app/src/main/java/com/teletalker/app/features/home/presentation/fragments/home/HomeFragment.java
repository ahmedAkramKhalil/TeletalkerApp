package com.teletalker.app.features.home.presentation.fragments.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.teletalker.app.R;
import com.teletalker.app.databinding.FragmentHomeBinding;
import com.teletalker.app.features.home.data.models.CallHistoryModel;
import com.teletalker.app.features.subscription.presentation.SubscriptionActivity;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {


    private FragmentHomeBinding binding;
    private CallHistoryAdapter adapter;

    private NavController navController;

    private HomeViewModel homeViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new CallHistoryAdapter(getFakeData());
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        binding.recyclerView.setAdapter(adapter);
        navController = Navigation.findNavController(view);

        binding.subscribeButton.setOnClickListener(v -> homeViewModel.navigateToSubscriptionScreen());
        binding.seeAllHistoryTv.setOnClickListener(v -> homeViewModel.navigateToCallHistoryScreen());

        observes();

    }

    private void observes() {
        homeViewModel.events.observe(getViewLifecycleOwner(), event -> {
            if (event instanceof HomeFragmentEvents.NavigateToSubscriptionScreen) {
                Intent intent = new Intent(getActivity(), SubscriptionActivity.class);
                startActivity(intent);
                homeViewModel.clearNavigationState();
            }else if (event instanceof HomeFragmentEvents.NavigateToCallHistoryScreen) {
                navController.navigate(R.id.action_navigation_home_to_navigation_call_history);
                homeViewModel.clearNavigationState();
            }
        });
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
                        "John",
                        "1233434",
                        R.drawable.glays_image,
                        "Indin",
                        1,
                        R.drawable.glays_image,
                        1,
                        "1:21"
                )

        );
        fakeList.add(
                new CallHistoryModel(
                        2,
                        "John",
                        "1233434",
                        R.drawable.glays_image,
                        "Indin",
                        1,
                        R.drawable.glays_image,
                        1,
                        "1:21"
                )

        );

        fakeList.add(
                new CallHistoryModel(
                        3,
                        "John",
                        "1233434",
                        R.drawable.glays_image,
                        "Indin",
                        1,
                        R.drawable.glays_image,
                        1,
                        "1:21"
                )

        );

        fakeList.add(
                new CallHistoryModel(
                        4,
                        "John",
                        "1233434",
                        R.drawable.glays_image,
                        "Indin",
                        1,
                        R.drawable.glays_image,
                        1,
                        "1:21"
                )

        );

        return fakeList;
    }
}