package com.teletalker.app.features.booking;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.teletalker.app.R;
import com.teletalker.app.features.booking.tutorial.TutorialPageFragment;
import com.teletalker.app.utils.PreferencesManager;

/**
 * 4-page intro to the booking feature. Stored "seen" so we only show it once
 * automatically. The user can re-open it any time from the Booking page.
 */
public class BookingTutorialActivity extends AppCompatActivity {

    private static final String PREF_SEEN = "booking_tutorial_seen";

    public static final int[] ICONS = {
            R.drawable.ic_tutorial_share,
            R.drawable.ic_tutorial_book,
            R.drawable.ic_tutorial_call,
            R.drawable.ic_tutorial_done,
    };

    public static final int[] TITLES = {
            R.string.booking_tutorial_title_1,
            R.string.booking_tutorial_title_2,
            R.string.booking_tutorial_title_3,
            R.string.booking_tutorial_title_4,
    };

    public static final int[] BODIES = {
            R.string.booking_tutorial_body_1,
            R.string.booking_tutorial_body_2,
            R.string.booking_tutorial_body_3,
            R.string.booking_tutorial_body_4,
    };

    private ViewPager2 pager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_tutorial);

        pager = findViewById(R.id.tutorialPager);
        TabLayout dots = findViewById(R.id.tutorialDots);
        View skip = findViewById(R.id.tutorialSkip);
        View next = findViewById(R.id.tutorialNext);
        View done = findViewById(R.id.tutorialDone);

        pager.setAdapter(new TutorialAdapter(this));

        new TabLayoutMediator(dots, pager, (tab, position) -> { /* dots only */ }).attach();

        skip.setOnClickListener(v -> finishAfterMarking());
        next.setOnClickListener(v -> {
            int p = pager.getCurrentItem();
            if (p < TITLES.length - 1) pager.setCurrentItem(p + 1, true);
        });
        done.setOnClickListener(v -> finishAfterMarking());

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                boolean last = position == TITLES.length - 1;
                next.setVisibility(last ? View.GONE : View.VISIBLE);
                done.setVisibility(last ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void finishAfterMarking() {
        PreferencesManager.getInstance(this).putBoolean(PREF_SEEN, true);
        finish();
    }

    /** Helper: open the tutorial only the first time the user reaches Booking. */
    public static boolean shouldAutoOpen(android.content.Context ctx) {
        return !PreferencesManager.getInstance(ctx).getBoolean(PREF_SEEN, false);
    }

    public static void open(android.content.Context ctx) {
        ctx.startActivity(new Intent(ctx, BookingTutorialActivity.class));
    }

    private static class TutorialAdapter extends FragmentStateAdapter {
        TutorialAdapter(@NonNull androidx.fragment.app.FragmentActivity a) { super(a); }
        @NonNull @Override
        public androidx.fragment.app.Fragment createFragment(int position) {
            return TutorialPageFragment.newInstance(
                    ICONS[position], TITLES[position], BODIES[position]);
        }
        @Override public int getItemCount() { return TITLES.length; }
    }
}
