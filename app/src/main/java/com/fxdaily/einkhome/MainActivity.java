package com.fxdaily.einkhome;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout pageIndicator;
    private LinearLayout hotseat;
    private View deleteZone;
    
    private static final String PREFS_NAME = "LauncherPrefs";
    private static final String DOCK_KEY = "DockPackages";
    private static final String GRID_KEY = "GridPackages";
    
    private int columns = 5;
    private int rows = 6; 
    private static final int DOCK_COUNT = 5;
    private static final int HOTSEAT_HEIGHT_DP = 90;

    private List<String> dockApps = new ArrayList<>();
    private List<String> gridApps = new ArrayList<>();
    private boolean isDraggingSystemApp = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        viewPager = findViewById(R.id.view_pager);
        pageIndicator = findViewById(R.id.page_indicator);
        hotseat = findViewById(R.id.hotseat);
        deleteZone = findViewById(R.id.delete_zone);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drag_layer), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            calculateOptimalRows(systemBars.top, systemBars.bottom);
            refreshUI();
            return insets;
        });

        hotseat.setOnDragListener(new AppDragListener());
        findViewById(R.id.drag_layer).setOnDragListener(new AppDragListener());
    }

    private void refreshUI() {
        loadData();
        renderDock();
        renderGridPages();
    }

    private void renderGridPages() {
        int pageSize = columns * rows;
        int pageCount = (int) Math.ceil((double) gridApps.size() / pageSize);
        if (pageCount == 0) pageCount = 1;

        GridPagerAdapter adapter = new GridPagerAdapter(pageCount);
        viewPager.setAdapter(adapter);
        
        new TabLayoutMediator(pageIndicator, viewPager, (tab, position) -> {}).attach();
    }

    private class GridPagerAdapter extends RecyclerView.Adapter<GridPagerAdapter.ViewHolder> {
        private final int pageCount;

        GridPagerAdapter(int count) { this.pageCount = count; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            GridLayout grid = new GridLayout(MainActivity.this);
            grid.setColumnCount(columns);
            grid.setRowCount(rows);
            grid.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            grid.setOnDragListener(new AppDragListener());
            return new ViewHolder(grid);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            GridLayout grid = (GridLayout) holder.itemView;
            grid.removeAllViews();
            grid.setTag(position); // 记录页码

            int pageSize = columns * rows;
            int start = position * pageSize;
            for (int i = 0; i < pageSize; i++) {
                int dataIdx = start + i;
                String pkg = (dataIdx < gridApps.size()) ? gridApps.get(dataIdx) : "null";
                View iconView = createIconView(pkg, false, dataIdx);
                
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = 0; lp.height = 0;
                lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                iconView.setLayoutParams(lp);
                grid.addView(iconView);
            }
        }

        @Override
        public int getItemCount() { return pageCount; }

        class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(View v) { super(v); }
        }
    }

    private void loadData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String dockStr = prefs.getString(DOCK_KEY, "");
        String[] savedDock = dockStr.isEmpty() ? new String[0] : dockStr.split("\\|", -1);
        dockApps.clear();
        for (int i = 0; i < DOCK_COUNT; i++) dockApps.add((i < savedDock.length) ? savedDock[i] : "null");

        if (!prefs.contains(GRID_KEY)) {
            initializeGridWithInstalledApps();
        } else {
            String gridStr = prefs.getString(GRID_KEY, "");
            String[] savedGrid = gridStr.isEmpty() ? new String[0] : gridStr.split("\\|", -1);
            gridApps.clear();
            for (String s : savedGrid) gridApps.add(s);
            reconcileApps();
        }
    }

    private void reconcileApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> installedApps = pm.queryIntentActivities(intent, 0);
        List<String> installedPkgs = new ArrayList<>();
        for (ResolveInfo ri : installedApps) installedPkgs.add(ri.activityInfo.packageName);

        boolean changed = false;
        for (int i = 0; i < dockApps.size(); i++) {
            if (!dockApps.get(i).equals("null") && !installedPkgs.contains(dockApps.get(i))) {
                dockApps.set(i, "null"); changed = true;
            }
        }
        for (int i = 0; i < gridApps.size(); i++) {
            if (!gridApps.get(i).equals("null") && !installedPkgs.contains(gridApps.get(i))) {
                gridApps.set(i, "null"); changed = true;
            }
        }
        for (String pkg : installedPkgs) {
            if (!dockApps.contains(pkg) && !gridApps.contains(pkg)) {
                int nullIdx = gridApps.indexOf("null");
                if (nullIdx != -1) gridApps.set(nullIdx, pkg);
                else gridApps.add(pkg);
                changed = true;
            }
        }
        // 补齐最后一页空位
        int pageSize = columns * rows;
        while (gridApps.size() % pageSize != 0 || gridApps.isEmpty()) gridApps.add("null");

        if (changed) saveData();
    }

    private class AppDragListener implements View.OnDragListener {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            float touchY = event.getY();
            int[] viewLoc = new int[2]; v.getLocationOnScreen(viewLoc);
            float screenY = viewLoc[1] + touchY;
            float threshold = 180 * getResources().getDisplayMetrics().density;

            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_LOCATION:
                    if (screenY < threshold && !isDraggingSystemApp) {
                        deleteZone.setVisibility(View.VISIBLE);
                    } else {
                        deleteZone.setVisibility(View.GONE);
                    }
                    deleteZone.getParent().requestLayout();
                    break;
                case DragEvent.ACTION_DROP:
                    String data = event.getClipData().getItemAt(0).getText().toString();
                    String[] parts = data.split(":");
                    String pkg = parts[0];
                    String fromType = parts[1];
                    int fromIdx = Integer.parseInt(parts[2]);

                    if (screenY < threshold && !isDraggingSystemApp) {
                        initiateUninstall(pkg);
                    } else if (v == hotseat) {
                        int toIdx = (int) (event.getX() / (hotseat.getWidth() / DOCK_COUNT));
                        handleMove(pkg, fromType, fromIdx, "dock", toIdx);
                    } else if (v instanceof GridLayout) {
                        int page = (int) v.getTag();
                        int col = (int) (event.getX() / (v.getWidth() / columns));
                        int row = (int) (event.getY() / (v.getHeight() / rows));
                        int toIdx = page * (columns * rows) + (row * columns + col);
                        handleMove(pkg, fromType, fromIdx, "grid", toIdx);
                    }
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    deleteZone.setVisibility(View.GONE);
                    deleteZone.getParent().requestLayout();
                    refreshUI();
                    break;
            }
            return true;
        }
    }

    // --- 保持原有的 createIconView, startDragging, handleMove, saveData 等逻辑并进行微调 ---
    private View createIconView(String pkg, boolean isDock, int index) {
        View view = LayoutInflater.from(this).inflate(isDock ? R.layout.item_dock_slot : R.layout.item_app_icon, null);
        ImageView iconView = view.findViewById(isDock ? R.id.dock_slot_icon : R.id.app_icon);
        TextView labelView = view.findViewById(isDock ? R.id.dock_slot_label : R.id.app_label);
        if (pkg != null && !pkg.equals("null")) {
            final String finalPkg = pkg;
            try {
                PackageManager pm = getPackageManager();
                iconView.setImageDrawable(pm.getApplicationIcon(finalPkg));
                labelView.setText(pm.getApplicationLabel(pm.getApplicationInfo(finalPkg, 0)));
                view.setOnClickListener(v -> {
                    Intent i = pm.getLaunchIntentForPackage(finalPkg);
                    if (i != null) startActivity(i);
                });
                view.setOnLongClickListener(v -> {
                    isDraggingSystemApp = isSystemApp(finalPkg);
                    ClipData cd = ClipData.newPlainText("app_info", finalPkg + ":" + (isDock ? "dock" : "grid") + ":" + index);
                    v.startDragAndDrop(cd, new View.DragShadowBuilder(v), null, 0);
                    v.setVisibility(View.INVISIBLE);
                    return true;
                });
            } catch (Exception e) { pkg = "null"; }
        }
        if (pkg == null || pkg.equals("null")) {
            iconView.setImageResource(isDock ? android.R.drawable.ic_menu_add : android.R.color.transparent);
            iconView.setAlpha(isDock ? 0.2f : 0.0f);
            labelView.setText("");
        }
        return view;
    }

    private boolean isSystemApp(String pkg) {
        try { return (getPackageManager().getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0; }
        catch (Exception e) { return false; }
    }

    private void handleMove(String pkg, String fromType, int fromIdx, String toType, int toIdx) {
        String target = toType.equals("dock") ? dockApps.get(toIdx) : gridApps.get(toIdx);
        if (target != null && !target.equals("null")) return;
        if (fromType.equals("dock")) dockApps.set(fromIdx, "null"); else gridApps.set(fromIdx, "null");
        if (toType.equals("dock")) dockApps.set(toIdx, pkg); else gridApps.set(toIdx, pkg);
        saveData(); refreshUI();
    }

    private void saveData() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        StringBuilder ds = new StringBuilder();
        for (int i = 0; i < dockApps.size(); i++) ds.append(dockApps.get(i)).append(i == 4 ? "" : "|");
        editor.putString(DOCK_KEY, ds.toString());
        StringBuilder gs = new StringBuilder();
        for (int i = 0; i < gridApps.size(); i++) gs.append(gridApps.get(i)).append(i == gridApps.size() - 1 ? "" : "|");
        editor.putString(GRID_KEY, gs.toString());
        editor.apply();
    }

    private void initializeGridWithInstalledApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
        gridApps.clear();
        for (ResolveInfo app : apps) gridApps.add(app.activityInfo.packageName);
        int pageSize = columns * rows;
        while (gridApps.size() % pageSize != 0 || gridApps.isEmpty()) gridApps.add("null");
        saveData();
    }

    private void calculateOptimalRows(int topInset, int bottomInset) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int availableHeightPx = metrics.heightPixels - topInset - bottomInset - (int)(HOTSEAT_HEIGHT_DP * metrics.density) - (int)(30 * metrics.density);
        rows = Math.max(4, availableHeightPx / (int)(105 * metrics.density));
    }

    private void initiateUninstall(String pkg) {
        try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))); } catch (Exception e) {}
    }

    private void renderDock() {
        hotseat.removeAllViews();
        for (int i = 0; i < DOCK_COUNT; i++) {
            View slot = createIconView(dockApps.get(i), true, i);
            hotseat.addView(slot, new LinearLayout.LayoutParams(0, -1, 1.0f));
        }
    }
}