package com.fxdaily.einkhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.icu.text.AlphabeticIndex;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    static class AppModel {
        String pkg;
        CharSequence label;
        Drawable icon;
        boolean isNull = false;

        static AppModel createNull() {
            AppModel m = new AppModel();
            m.pkg = "null";
            m.isNull = true;
            m.label = "";
            return m;
        }
    }

    private ViewPager2 viewPager;
    private TabLayout pageIndicator;
    private CellLayout hotseat;

    private static final String PREFS_NAME = "LauncherPrefs";
    private static final String DOCK_KEY = "DockPackages";

    private int columns = 5;
    private int rows = 6;
    private static final int DOCK_COUNT = 5;
    private static final int HOTSEAT_HEIGHT_DP = 90;

    private List<AppModel> dockModels = new ArrayList<>();
    private List<AppModel> gridModels = new ArrayList<>();
    private final Map<String, AppModel> appCache = new HashMap<>();

    private GridPagerAdapter gridAdapter;
    private boolean isRefreshing = false;

    private float appTextSizeSp = 13f;
    private float dockTextSizeSp = 12f;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            appCache.clear();
            refreshUI();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.view_pager);
        pageIndicator = findViewById(R.id.page_indicator);
        hotseat = findViewById(R.id.hotseat);

        findViewById(R.id.search_bar_container).setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivity(intent);
        });

        viewPager.setPageTransformer((page, position) -> {
            page.setTranslationX(-position * page.getWidth());
            page.setTranslationZ(1f - Math.abs(position));
            if (position <= -0.7f || position >= 0.7f) {
                page.setVisibility(View.INVISIBLE);
            } else {
                page.setVisibility(View.VISIBLE);
                page.setAlpha(1f);
            }
        });

        View child = viewPager.getChildAt(0);
        if (child instanceof RecyclerView) {
            child.setOverScrollMode(View.OVER_SCROLL_NEVER);
            ((RecyclerView) child).setItemViewCacheSize(20);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drag_layer), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            
            View bottomContainer = findViewById(R.id.bottom_container);
            if (bottomContainer != null) {
                bottomContainer.setPadding(0, 0, 0, systemBars.bottom);
            }
            
            calculateOptimalRows(systemBars.top, systemBars.bottom);
            v.post(this::refreshUI);
            return insets;
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (viewPager != null) {
                viewPager.setCurrentItem(0, true);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(packageReceiver);
    }

    private synchronized void refreshUI() {
        if (isRefreshing) return;
        isRefreshing = true;
        
        int currentPage = viewPager.getCurrentItem();
        
        loadAllData();
        renderDock();

        int pageSize = columns * rows;
        if (pageSize <= 0) pageSize = 30;
        int gridPageCount = Math.max(1, (int) Math.ceil((double) gridModels.size() / pageSize));
        int totalPageCount = 1 + gridPageCount;
        
        if (gridAdapter == null) {
            gridAdapter = new GridPagerAdapter(totalPageCount);
            viewPager.setAdapter(gridAdapter);
            new TabLayoutMediator(pageIndicator, viewPager, (tab, position) -> {
                if (position == 0) {
                    tab.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_home_unselected));
                } else {
                    tab.setIcon(ContextCompat.getDrawable(this, R.drawable.page_indicator_dot));
                }
            }).attach();

            pageIndicator.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    if (tab.getPosition() == 0) {
                        tab.setIcon(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_home_selected));
                    }
                }
                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                    if (tab.getPosition() == 0) {
                        tab.setIcon(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_home_unselected));
                    }
                }
                @Override
                public void onTabReselected(TabLayout.Tab tab) {}
            });
        } else {
            gridAdapter.setPageCount(totalPageCount);
            gridAdapter.notifyDataSetChanged();
        }
        
        TabLayout.Tab firstTab = pageIndicator.getTabAt(0);
        if (firstTab != null) {
            if (viewPager.getCurrentItem() == 0) {
                firstTab.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_home_selected));
            } else {
                firstTab.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_home_unselected));
            }
        }

        if (currentPage < totalPageCount) {
            viewPager.setCurrentItem(currentPage, false);
        } else {
            viewPager.setCurrentItem(totalPageCount - 1, false);
        }
        
        viewPager.setOffscreenPageLimit(totalPageCount);
        isRefreshing = false;
    }

    private void loadAllData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> installedApps = pm.queryIntentActivities(intent, 0);
        Map<String, ResolveInfo> installedMap = new HashMap<>();
        for (ResolveInfo ri : installedApps) installedMap.put(ri.activityInfo.packageName, ri);

        String dockStr = prefs.getString(DOCK_KEY, "");
        String[] savedDock = dockStr.isEmpty() ? new String[0] : dockStr.split("\\|", -1);
        dockModels.clear();
        for (int i = 0; i < DOCK_COUNT; i++) {
            String pkg = i < savedDock.length ? savedDock[i] : "null";
            dockModels.add(getOrLoadModel(pkg, installedMap));
        }

        gridModels.clear();
        for (ResolveInfo ri : installedApps) {
            String pkg = ri.activityInfo.packageName;
            if (!isPkgInDock(pkg)) {
                gridModels.add(getOrLoadModel(pkg, installedMap));
            }
        }

        final Collator collator = Collator.getInstance(Locale.CHINA);
        final AlphabeticIndex.ImmutableIndex<String> index;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            index = new AlphabeticIndex<String>(Locale.CHINA)
                    .addLabels(Locale.ENGLISH)
                    .buildImmutableIndex();
        } else {
            index = null;
        }

        Collections.sort(gridModels, (a, b) -> {
            if (a.isNull && b.isNull) return 0;
            if (a.isNull) return 1;
            if (b.isNull) return -1;
            String s1 = a.label != null ? a.label.toString() : "";
            String s2 = b.label != null ? b.label.toString() : "";
            if (index != null) {
                int b1 = index.getBucketIndex(s1);
                int b2 = index.getBucketIndex(s2);
                if (b1 != b2) return Integer.compare(b1, b2);
            }
            return collator.compare(s1, s2);
        });

        int pageSize = columns * rows;
        if (pageSize <= 0) pageSize = 30;
        while (gridModels.size() % pageSize != 0 || gridModels.isEmpty()) {
            gridModels.add(AppModel.createNull());
        }
    }

    private AppModel getOrLoadModel(String pkg, Map<String, ResolveInfo> installedMap) {
        if (pkg.equals("null")) return AppModel.createNull();
        if (appCache.containsKey(pkg)) return appCache.get(pkg);

        AppModel model = new AppModel();
        model.pkg = pkg;
        ResolveInfo ri = installedMap.get(pkg);
        if (ri != null) {
            PackageManager pm = getPackageManager();
            model.label = ri.loadLabel(pm);
            
            String resName = pkg.replace(".", "_").toLowerCase();
            int resId = getResources().getIdentifier(resName, "drawable", getPackageName());
            
            if (resId != 0) {
                try {
                    model.icon = ResourcesCompat.getDrawable(getResources(), resId, null);
                } catch (Exception e) {
                    model.icon = ri.loadIcon(pm);
                }
            } else {
                model.icon = ri.loadIcon(pm);
            }
            
            if (model.icon == null) {
                model.icon = pm.getDefaultActivityIcon();
            }
        } else {
            return AppModel.createNull();
        }
        appCache.put(pkg, model);
        return model;
    }

    private boolean isPkgInDock(String pkg) {
        for (AppModel m : dockModels) if (pkg.equals(m.pkg)) return true;
        return false;
    }

    private void renderDock() {
        hotseat.setGridSize(DOCK_COUNT, 1);
        hotseat.removeAllViews();
        for (int i = 0; i < dockModels.size(); i++) {
            hotseat.addView(createIconView(dockModels.get(i), true, i));
        }
    }

    private class GridPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HOME = 0;
        private static final int TYPE_GRID = 1;
        private int pageCount;
        
        GridPagerAdapter(int count) { this.pageCount = count; }
        void setPageCount(int count) { this.pageCount = count; }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_HOME : TYPE_GRID;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HOME) {
                View v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_home_page, parent, false);
                return new HomeViewHolder(v);
            } else {
                CellLayout cellLayout = new CellLayout(MainActivity.this);
                cellLayout.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
                cellLayout.setBackgroundColor(Color.WHITE);
                return new GridViewHolder(cellLayout);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof GridViewHolder) {
                int gridPosition = position - 1;
                GridViewHolder gridHolder = (GridViewHolder) holder;
                CellLayout cellLayout = (CellLayout) gridHolder.itemView;
                cellLayout.setGridSize(columns, rows);
                cellLayout.setTag(gridPosition);
                cellLayout.removeAllViews();

                int pageSize = columns * rows;
                if (pageSize <= 0) pageSize = 30;
                int start = gridPosition * pageSize;
                for (int i = 0; i < pageSize; i++) {
                    int dataIdx = start + i;
                    AppModel model = dataIdx < gridModels.size() ? gridModels.get(dataIdx) : AppModel.createNull();
                    cellLayout.addView(createIconView(model, false, dataIdx));
                }
            }
        }

        @Override
        public int getItemCount() { return pageCount; }
        
        class HomeViewHolder extends RecyclerView.ViewHolder { HomeViewHolder(View v) { super(v); } }
        class GridViewHolder extends RecyclerView.ViewHolder { GridViewHolder(View v) { super(v); } }
    }

    private View createIconView(AppModel model, boolean isDock, int index) {
        View view = LayoutInflater.from(this).inflate(isDock ? R.layout.item_dock_slot : R.layout.item_app_icon, null);
        AppIconView iconView = view.findViewById(isDock ? R.id.dock_slot_icon : R.id.app_icon);
        TextView labelView = view.findViewById(isDock ? R.id.dock_slot_label : R.id.app_label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, isDock ? dockTextSizeSp : appTextSizeSp);
        iconView.setForceShowMask(isDock);

        if (!model.isNull) {
            iconView.setImageDrawable(model.icon);
            labelView.setText(model.label);
            view.setOnClickListener(v -> {
                Intent i = getPackageManager().getLaunchIntentForPackage(model.pkg);
                if (i != null) startActivity(i);
            });
        } else {
            iconView.setImageDrawable(null);
            labelView.setText("");
            if (isDock) {
                view.setClickable(true);
            } else {
                view.setClickable(false);
            }
        }

        if (isDock) {
            view.setOnLongClickListener(v -> {
                showAppPickerForDock(index);
                return true;
            });
        }

        if (!model.isNull || isDock) {
            view.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    iconView.setPressed(true);
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    iconView.setPressed(false);
                }
                return false;
            });
        }

        return view;
    }

    private void showAppPickerForDock(int index) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_picker, null);
        // 为对话框根视图设置 3dp 描边和 2dp 圆角背景
        dialogView.setBackgroundResource(R.drawable.dialog_background);
        
        EditText searchInput = dialogView.findViewById(R.id.picker_search);
        RecyclerView recyclerView = dialogView.findViewById(R.id.picker_list);

        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> installedApps = pm.queryIntentActivities(intent, 0);
        Collections.sort(installedApps, new ResolveInfo.DisplayNameComparator(pm));

        List<PickerItem> allItems = new ArrayList<>();
        allItems.add(new PickerItem("清除此位置", "null", ContextCompat.getDrawable(this, R.drawable.ic_menu_delete)));
        for (ResolveInfo ri : installedApps) {
            allItems.add(new PickerItem(ri.loadLabel(pm).toString(), ri.activityInfo.packageName, ri.loadIcon(pm)));
        }

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.EInkDialog)
                .setView(dialogView)
                .create();

        PickerAdapter adapter = new PickerAdapter(allItems, pkg -> {
            updateDockSlot(index, pkg);
            dialog.dismiss();
        });
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.show();
        
        // 取消阴影并调整窗口
        Window window = dialog.getWindow();
        if (window != null) {
            window.setDimAmount(0f); // 取消背景变暗/阴影
        }
    }

    static class PickerItem {
        String label;
        String pkg;
        Drawable icon;
        PickerItem(String label, String pkg, Drawable icon) {
            this.label = label;
            this.pkg = pkg;
            this.icon = icon;
        }
    }

    private class PickerAdapter extends RecyclerView.Adapter<PickerAdapter.ViewHolder> {
        private List<PickerItem> allItems;
        private List<PickerItem> filteredItems;
        private java.util.function.Consumer<String> onItemClick;

        PickerAdapter(List<PickerItem> items, java.util.function.Consumer<String> callback) {
            this.allItems = items;
            this.filteredItems = new ArrayList<>(items);
            this.onItemClick = callback;
        }

        void filter(String query) {
            filteredItems.clear();
            if (query.isEmpty()) {
                filteredItems.addAll(allItems);
            } else {
                String lowerQuery = query.toLowerCase();
                for (PickerItem item : allItems) {
                    if (item.label.toLowerCase().contains(lowerQuery)) {
                        filteredItems.add(item);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_picker_app, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PickerItem item = filteredItems.get(position);
            holder.label.setText(item.label);
            holder.icon.setImageDrawable(item.icon);
            holder.icon.setForceShowMask(true);
            holder.itemView.setOnClickListener(v -> {
                if (onItemClick != null) {
                    onItemClick.accept(item.pkg);
                }
            });
        }

        @Override
        public int getItemCount() { return filteredItems.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            AppIconView icon;
            TextView label;
            ViewHolder(View v) {
                super(v);
                icon = v.findViewById(R.id.picker_app_icon);
                label = v.findViewById(R.id.picker_app_label);
            }
        }
    }

    private void updateDockSlot(int index, String pkg) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String dockStr = prefs.getString(DOCK_KEY, "");
        String[] savedDock = new String[DOCK_COUNT];
        java.util.Arrays.fill(savedDock, "null");
        
        if (!dockStr.isEmpty()) {
            String[] split = dockStr.split("\\|", -1);
            for (int i = 0; i < Math.min(split.length, DOCK_COUNT); i++) {
                savedDock[i] = split[i];
            }
        }

        savedDock[index] = pkg;
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < DOCK_COUNT; i++) {
            sb.append(savedDock[i]);
            if (i < DOCK_COUNT - 1) sb.append("|");
        }
        prefs.edit().putString(DOCK_KEY, sb.toString()).apply();
        
        appCache.clear();
        refreshUI();
    }

    private void calculateOptimalRows(int topInset, int bottomInset) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int availableHeightPx = metrics.heightPixels - topInset - bottomInset - 140 * (int)metrics.density - 30 * (int)metrics.density;
        rows = Math.max(4, availableHeightPx / (int)(105 * metrics.density));
        float baseSize = (metrics.density >= 2.0f) ? 13f : 14f; 
        appTextSizeSp = Math.max(10f, baseSize - (rows > 6 ? 1f : 0f));
        dockTextSizeSp = Math.max(9f, appTextSizeSp - 1f);
    }
}
