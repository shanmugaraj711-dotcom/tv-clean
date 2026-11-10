package ai.promptstudio.tvclean.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// The TV launcher's one screen (TV Clean 0.2): the TV's home screen. It lists every app the TV
// itself shows in a launcher (the leanback apps), in a grid the remote's D-pad can move through,
// and starts the one you pick. The background is the look the phone sent: wallpaper.jpg in this
// app's own files (written through the ThemeProvider), or plain dark when there is none.
//
// v1 is deliberately small: every leanback app, alphabetical, no dock order yet (the dock the
// phone sends in config.json is not parsed here — the apps are all shown, so nothing is lost).
// App list refreshes on resume, so an app installed or removed by the phone's plan shows up.
public final class LauncherActivity extends Activity {

    private static final String WALLPAPER = "wallpaper.jpg";
    private static final int COLUMNS = 4;

    private GridView grid;
    private AppAdapter adapter;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        grid = new GridView(this);
        grid.setNumColumns(COLUMNS);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(24));
        grid.setHorizontalSpacing(dp(24));
        grid.setPadding(dp(48), dp(48), dp(48), dp(48));
        grid.setClipToPadding(false);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ResolveInfo app = adapter.appAt(position);
                if (app == null) return;
                Intent launch = getPackageManager().getLaunchIntentForPackage(app.activityInfo.packageName);
                if (launch != null) startActivity(launch);
            }
        });
        applyWallpaper();
        setContentView(grid);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshApps();
        applyWallpaper();
        grid.requestFocus();
    }

    // The apps the TV shows in a launcher, alphabetical by the name the TV shows.
    private void refreshApps() {
        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps = getPackageManager().queryIntentActivities(leanback, 0);
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return label(a).toString().compareToIgnoreCase(label(b).toString());
            }
        });
        adapter.setApps(apps);
    }

    private CharSequence label(ResolveInfo app) {
        return app.loadLabel(getPackageManager());
    }

    // The wallpaper the phone sent, or nothing (the dark theme shows through).
    private void applyWallpaper() {
        File wallpaper = new File(getExternalFilesDir(null), WALLPAPER);
        if (!wallpaper.isFile()) return;
        Bitmap bitmap = BitmapFactory.decodeFile(wallpaper.getAbsolutePath());
        if (bitmap == null) return;
        grid.setBackground(new BitmapDrawable(getResources(), bitmap));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class AppAdapter extends BaseAdapter {
        private final List<ResolveInfo> apps = new ArrayList<>();

        void setApps(List<ResolveInfo> next) {
            apps.clear();
            apps.addAll(next);
            notifyDataSetChanged();
        }

        ResolveInfo appAt(int position) {
            return position >= 0 && position < apps.size() ? apps.get(position) : null;
        }

        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int position) { return apps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView name;
            if (convertView instanceof LinearLayout) {
                cell = (LinearLayout) convertView;
                icon = (ImageView) cell.getChildAt(0);
                name = (TextView) cell.getChildAt(1);
            } else {
                cell = new LinearLayout(LauncherActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setFocusable(true);
                cell.setFocusableInTouchMode(true);
                icon = new ImageView(LauncherActivity.this);
                icon.setAdjustViewBounds(true);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(96), dp(96));
                iconParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                icon.setLayoutParams(iconParams);
                name = new TextView(LauncherActivity.this);
                name.setTextColor(0xFFFFFFFF);
                name.setTextSize(18);
                name.setGravity(android.view.Gravity.CENTER);
                name.setPadding(0, dp(8), 0, 0);
                cell.addView(icon);
                cell.addView(name);
            }
            ResolveInfo app = apps.get(position);
            PackageManager pm = getPackageManager();
            icon.setImageDrawable(app.loadIcon(pm));
            name.setText(app.loadLabel(pm));
            return cell;
        }
    }
}
