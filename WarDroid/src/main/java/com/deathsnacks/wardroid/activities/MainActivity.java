package com.deathsnacks.wardroid.activities;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;

import com.deathsnacks.wardroid.Constants;
import com.deathsnacks.wardroid.R;
import com.deathsnacks.wardroid.fragments.AlertsFragment;
import com.deathsnacks.wardroid.fragments.BadlandsFragment;
import com.deathsnacks.wardroid.fragments.InvasionFragment;
import com.deathsnacks.wardroid.fragments.NewsFragment;
import com.deathsnacks.wardroid.services.NotificationsUpdateReceiver;
import com.deathsnacks.wardroid.services.PollingAlarmReceiver;

import java.util.ArrayList;

/**
 * Created by Admin on 23/01/14.
 */
public class MainActivity extends ActionBarActivity {
    private static final String TAG = "MainActivity";
    private ActionBar mActionBar;
    private String[] mDrawerTitles;
    private SharedPreferences mPreferences;
    private ViewPager mPager;
    private FragmentManager mFragmentManager;
    private TabsAdapter mPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_pager);

        mDrawerTitles = getResources().getStringArray(R.array.drawer_titles);
        mActionBar = getSupportActionBar();
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setOffscreenPageLimit(3);
        mFragmentManager = getSupportFragmentManager();
        mPagerAdapter = new TabsAdapter(this, mPager);
        mPagerAdapter.addTab(mActionBar.newTab().setText(mDrawerTitles[0]), NewsFragment.class, null);
        mPagerAdapter.addTab(mActionBar.newTab().setText(mDrawerTitles[1]), AlertsFragment.class, null);
        mPagerAdapter.addTab(mActionBar.newTab().setText(mDrawerTitles[2]), InvasionFragment.class, null);
        mPagerAdapter.addTab(mActionBar.newTab().setText(mDrawerTitles[3]), BadlandsFragment.class, null);

        (new PreloadData(this)).execute();
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (savedInstanceState == null) {
            Log.d(TAG, "no saved instance state");
            if (mPreferences.getBoolean(Constants.PREF_ALERT_ENABLED, false)) {
                Log.d(TAG, "starting alarm");
                Intent alarmIntent = new Intent(this, PollingAlarmReceiver.class);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                if (pendingIntent != null) {
                    try {
                        SharedPreferences httpPrefs = getSharedPreferences(Constants.SHARED_PREF_POLLING, MODE_PRIVATE);
                        SharedPreferences.Editor edit = httpPrefs.edit();
                        edit.clear();
                        edit.commit();
                        Log.d(TAG, "forcing start of alarm");
                        boolean mDismissible = !mPreferences.getBoolean("dismissible", false);
                        NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext())
                                .setSmallIcon(R.drawable.ic_notification)
                                .setContentTitle(getString(R.string.notification_title))
                                .setContentText(getString(R.string.notification_starting))
                                .setOngoing(mDismissible);
                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        intent.putExtra("drawer_position", 1);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        PendingIntent pendingIntent2 = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                        mBuilder.setContentIntent(pendingIntent2);
                        mNotificationManager.notify(1, mBuilder.build());
                        (new PollingAlarmReceiver()).onReceive(this.getApplicationContext(), new Intent().putExtra("force", true));
                        if (!mPreferences.getBoolean(Constants.PREF_PUSH, false)) {
                            ((AlarmManager) getSystemService(ALARM_SERVICE)).setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                                    SystemClock.elapsedRealtime(), AlarmManager.INTERVAL_FIFTEEN_MINUTES, pendingIntent);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            Intent intent = getIntent();
            int startPos = intent.getIntExtra("drawer_position", -1);
            if (startPos == -1) {
                String defaultValue = mPreferences.getString("default_window", "news");
                if (defaultValue.equals("news"))
                    startPos = 0;
                else if (defaultValue.equals("alerts"))
                    startPos = 1;
                else if (defaultValue.equals("invasions"))
                    startPos = 2;
                else if (defaultValue.equals("badlands"))
                    startPos = 3;
            }
            if (startPos == -1) {
                startPos = 0;
            }
            mActionBar.setSelectedNavigationItem(startPos);
        } else {
            int curtab = savedInstanceState.getInt("tab", 0);
            if (curtab == -1) {
                curtab = 0;
            }
            mActionBar.setSelectedNavigationItem(curtab);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", getSupportActionBar().getSelectedNavigationIndex());
        outState.putLong("time", System.currentTimeMillis());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                //getSupportFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsActivity()).commit();
                return true;
            case R.id.exit:
                new AlertDialog.Builder(this)
                        .setTitle(this.getString(R.string.menu_exit))
                        .setMessage(getString(R.string.menu_exit_message))
                        .setPositiveButton(this.getString(android.R.string.yes), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).cancel(PendingIntent.getBroadcast(
                                        getApplicationContext(), 0,
                                        new Intent(getApplicationContext(), PollingAlarmReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT));
                                ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).cancel(PendingIntent.getBroadcast(
                                        getApplicationContext(), 0,
                                        new Intent(getApplicationContext(), NotificationsUpdateReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT));
                                NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                                mNotificationManager.cancel(1);
                                finish();
                                System.exit(0);
                            }
                        })
                        .setNegativeButton(this.getString(android.R.string.no), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.cancel();
                            }
                        }).show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public class PreloadData extends AsyncTask<Void, Void, Void> {
        private Activity mActivity;

        public PreloadData(Activity act) {
            mActivity = act;
        }

        @Override
        protected Void doInBackground(Void... params) {
            //preload string data
            return null;
        }

        @Override
        protected void onPostExecute(Void voi) {
        }

        @Override
        protected void onCancelled() {
        }
    }

    public static class TabsAdapter extends FragmentPagerAdapter
            implements ActionBar.TabListener, ViewPager.OnPageChangeListener {
        private final Context mContext;
        private final ActionBarActivity mActivity;
        private final ActionBar mActionBar;
        private final ViewPager mViewPager;
        private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();

        static final class TabInfo {
            private final Class<?> clss;
            private final Bundle args;

            TabInfo(Class<?> _class, Bundle _args) {
                clss = _class;
                args = _args;
            }
        }

        public TabsAdapter(ActionBarActivity activity, ViewPager pager) {
            super(activity.getSupportFragmentManager());
            mContext = activity;
            mActivity = activity;
            mActionBar = activity.getSupportActionBar();
            mViewPager = pager;
            mViewPager.setAdapter(this);
            mViewPager.setOnPageChangeListener(this);
        }

        public void addTab(ActionBar.Tab tab, Class<?> clss, Bundle args) {
            TabInfo info = new TabInfo(clss, args);
            tab.setTag(info);
            tab.setTabListener(this);
            mTabs.add(info);
            mActionBar.addTab(tab);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mTabs.size();
        }

        @Override
        public Fragment getItem(int position) {
            TabInfo info = mTabs.get(position);
            return Fragment.instantiate(mContext, info.clss.getName(), info.args);
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            mActivity.supportInvalidateOptionsMenu();
            mActionBar.setSelectedNavigationItem(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }

        @Override
        public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
            Object tag = tab.getTag();
            for (int i = 0; i < mTabs.size(); i++) {
                if (mTabs.get(i) == tag) {
                    if (mViewPager.getCurrentItem() != i)
                        mViewPager.setCurrentItem(i);
                }
            }
        }

        @Override
        public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
        }

        @Override
        public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
        }
    }
}
