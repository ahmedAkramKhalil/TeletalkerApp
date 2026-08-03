package com.teletalker.app.features.booking.tutorial;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.teletalker.app.R;

public class TutorialPageFragment extends Fragment {

    private static final String ARG_ICON = "icon";
    private static final String ARG_TITLE = "title";
    private static final String ARG_BODY = "body";

    public static TutorialPageFragment newInstance(int iconRes, int titleRes, int bodyRes) {
        TutorialPageFragment f = new TutorialPageFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_ICON, iconRes);
        b.putInt(ARG_TITLE, titleRes);
        b.putInt(ARG_BODY, bodyRes);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_tutorial_page, container, false);
        Bundle args = getArguments();
        if (args != null) {
            ((ImageView) v.findViewById(R.id.tutorialIcon)).setImageResource(args.getInt(ARG_ICON));
            ((TextView) v.findViewById(R.id.tutorialTitle)).setText(args.getInt(ARG_TITLE));
            ((TextView) v.findViewById(R.id.tutorialBody)).setText(args.getInt(ARG_BODY));
        }
        return v;
    }
}
