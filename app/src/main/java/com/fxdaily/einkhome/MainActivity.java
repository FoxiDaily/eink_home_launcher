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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
    private static final String KEY_COLOR_ICONS = "color_icons";

    private int columns = 5;
    private int rows = 6;
    private static final int DOCK_COUNT = 5;

    private List<AppModel> dockModels = new ArrayList<>();
    private List<AppModel> gridModels = new ArrayList<>();
    private final Map<String, AppModel> appCache = new HashMap<>();

    private GridPagerAdapter gridAdapter;
    private boolean isRefreshing = false;
    private boolean isResumed = false; // 标记桌面是否处于活跃状态

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

        findViewById(R.id.search_text_view).setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.launcher_settings_button).setOnClickListener(v -> {
            Intent intent = new Intent(this, LauncherSettings.class);
            startActivity(intent);
        });

        viewPager.setPageTransformer((page, position) -> {
            page.setTranslationX(-position * page.getWidth());
            page.setTranslationZ(1f - Math.abs(position));
            if (position < -1 || position > 1) {
                page.setAlpha(0f);
            } else {
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
    protected void onPause() {
        super.onPause();
        isResumed = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
        findViewById(R.id.drag_layer).post(this::refreshUI);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 只有当桌面已经在前台（用户已经在看桌面）时按 Home 键，才回到第 0 页
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (isResumed && viewPager != null && viewPager.getCurrentItem() != 0) {
                viewPager.setCurrentItem(0, true);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(packageReceiver);
        } catch (Exception e) {}
    }

    private synchronized void refreshUI() {
        if (isRefreshing) return;
        isRefreshing = true;
        
        try {
            final int lastPage = viewPager.getCurrentItem();
            
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
            
            // 保持当前页面索引
            if (lastPage < totalPageCount) {
                viewPager.setCurrentItem(lastPage, false);
            } else {
                viewPager.setCurrentItem(totalPageCount - 1, false);
            }

            // 更新指示器状态
            TabLayout.Tab firstTab = pageIndicator.getTabAt(0);
            if (firstTab != null) {
                if (viewPager.getCurrentItem() == 0) {
                    firstTab.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_home_selected));
                } else {
                    firstTab.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_home_unselected));
                }
            }
            
            viewPager.setOffscreenPageLimit(totalPageCount);
            viewPager.post(() -> viewPager.requestLayout());
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isRefreshing = false;
        }
    }

    private void loadAllData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> installedApps = pm.queryIntentActivities(intent, 0);
        
        if (installedApps == null || installedApps.isEmpty()) return;

        Map<String, ResolveInfo> installedMap = new HashMap<>();
        for (ResolveInfo ri : installedApps) installedMap.put(ri.activityInfo.packageName, ri);

        String dockStr = prefs.getString(DOCK_KEY, "");
        String[] savedDock = dockStr.isEmpty() ? new String[0] : dockStr.split("\\|", -1);
        
        List<AppModel> newDock = new ArrayList<>();
        for (int i = 0; i < DOCK_COUNT; i++) {
            String pkg = i < savedDock.length ? savedDock[i] : "null";
            newDock.add(getOrLoadModel(pkg, installedMap));
        }
        dockModels = newDock;

        List<AppModel> newGrid = new ArrayList<>();
        String myPkg = getPackageName();
        for (ResolveInfo ri : installedApps) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(myPkg)) continue;
            if (!isPkgInDock(pkg)) newGrid.add(getOrLoadModel(pkg, installedMap));
        }

        final Collator collator = Collator.getInstance(Locale.CHINA);
        final AlphabeticIndex.ImmutableIndex<String> index;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            index = new AlphabeticIndex<String>(Locale.CHINA).addLabels(Locale.ENGLISH).buildImmutableIndex();
        } else {
            index = null;
        }

        Collections.sort(newGrid, (a, b) -> {
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
        while (newGrid.size() % pageSize != 0 || newGrid.isEmpty()) newGrid.add(AppModel.createNull());
        gridModels = newGrid;
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
                try { model.icon = ResourcesCompat.getDrawable(getResources(), resId, null); } catch (Exception e) { model.icon = ri.loadIcon(pm); }
            } else { model.icon = ri.loadIcon(pm); }
            if (model.icon == null) model.icon = pm.getDefaultActivityIcon();
        } else return AppModel.createNull();
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
        for (int i = 0; i < dockModels.size(); i++) hotseat.addView(createIconView(dockModels.get(i), true, i));
    }

    private class GridPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HOME = 0, TYPE_GRID = 1;
        private int pageCount;
        GridPagerAdapter(int count) { this.pageCount = count; }
        void setPageCount(int count) { this.pageCount = count; }
        @Override public int getItemViewType(int position) { return position == 0 ? TYPE_HOME : TYPE_GRID; }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HOME) return new HomeViewHolder(LayoutInflater.from(MainActivity.this).inflate(R.layout.item_home_page, parent, false));
            CellLayout cl = new CellLayout(MainActivity.this);
            cl.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            cl.setBackgroundColor(Color.WHITE);
            return new GridViewHolder(cl);
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof GridViewHolder) {
                int gridPosition = position - 1;
                CellLayout cl = (CellLayout) holder.itemView;
                cl.setGridSize(columns, rows);
                cl.removeAllViews();
                int pageSize = columns * rows;
                int start = gridPosition * Math.max(1, pageSize);
                for (int i = 0; i < pageSize; i++) {
                    int dataIdx = start + i;
                    AppModel model = dataIdx < gridModels.size() ? gridModels.get(dataIdx) : AppModel.createNull();
                    cl.addView(createIconView(model, false, dataIdx));
                }
            }
        }
        @Override public int getItemCount() { return pageCount; }
        @Override public long getItemId(int position) { return position; }
        class HomeViewHolder extends RecyclerView.ViewHolder { HomeViewHolder(View v) { super(v); } }
        class GridViewHolder extends RecyclerView.ViewHolder { GridViewHolder(View v) { super(v); } }
    }

    private View createIconView(AppModel model, boolean isDock, int index) {
        View view = LayoutInflater.from(this).inflate(isDock ? R.layout.item_dock_slot : R.layout.item_app_icon, null);
        AppIconView iconView = view.findViewById(isDock ? R.id.dock_slot_icon : R.id.app_icon);
        TextView labelView = view.findViewById(isDock ? R.id.dock_slot_label : R.id.app_label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, isDock ? dockTextSizeSp : appTextSizeSp);
        iconView.setForceShowMask(isDock);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isColor = prefs.getBoolean(KEY_COLOR_ICONS, false);
        iconView.setGrayscale(!isColor);
        if (!model.isNull) {
            iconView.setImageDrawable(model.icon);
            labelView.setText(model.label);
            view.setOnClickListener(v -> { try { Intent i = getPackageManager().getLaunchIntentForPackage(model.pkg); if (i != null) startActivity(i); } catch (Exception e) {} });
        } else {
            iconView.setImageDrawable(null); labelView.setText("");
            if (isDock) view.setClickable(true); else { view.setClickable(false); view.setBackground(null); }
        }
        view.setOnLongClickListener(v -> { if (!model.isNull) showAppOptionsDialog(model, isDock, index); else if (isDock) showAppPickerForDock(index); return true; });
        if (!model.isNull || isDock) {
            view.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) iconView.setPressed(true);
                else if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) iconView.setPressed(false);
                return false;
            });
        }
        return view;
    }

    private void showAppOptionsDialog(AppModel model, boolean isDock, int index) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_menu, null);
        TextView titleView = dialogView.findViewById(R.id.menu_title);
        RecyclerView recyclerView = dialogView.findViewById(R.id.menu_list);
        titleView.setText(model.label);
        List<MenuOption> options = new ArrayList<>();
        options.add(new MenuOption("卸载", R.drawable.ic_menu_delete));
        options.add(new MenuOption("详细信息", R.drawable.ic_information));
        if (isDock) options.add(new MenuOption("设置常用应用", R.drawable.ic_add_apps));
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.EInkDialog).setView(dialogView).create();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new MenuOptionsAdapter(options, option -> {
            dialog.dismiss();
            if (option.equals("卸载")) { Intent intent = new Intent(Intent.ACTION_DELETE); intent.setData(Uri.parse("package:" + model.pkg)); startActivity(intent); }
            else if (option.equals("详细信息")) { Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); intent.setData(Uri.parse("package:" + model.pkg)); startActivity(intent); }
            else if (option.equals("设置常用应用")) showAppPickerForDock(index);
        }));
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setDimAmount(0f);
    }

    static class MenuOption { String title; int iconRes; MenuOption(String title, int iconRes) { this.title = title; this.iconRes = iconRes; } }

    private class MenuOptionsAdapter extends RecyclerView.Adapter<MenuOptionsAdapter.ViewHolder> {
        private List<MenuOption> options; private java.util.function.Consumer<String> onClick;
        MenuOptionsAdapter(List<MenuOption> options, java.util.function.Consumer<String> onClick) { this.options = options; this.onClick = onClick; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_menu_option, parent, false)); }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MenuOption option = options.get(position);
            holder.text.setText(option.title); holder.icon.setImageResource(option.iconRes);
            holder.itemView.setOnClickListener(v -> onClick.accept(option.title));
        }
        @Override public int getItemCount() { return options.size(); }
        class ViewHolder extends RecyclerView.ViewHolder { ImageView icon; TextView text; ViewHolder(View v) { super(v); icon = v.findViewById(R.id.option_icon); text = v.findViewById(R.id.option_text); } }
    }

    private void showAppPickerForDock(int index) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_picker, null);
        dialogView.setBackgroundResource(R.drawable.dialog_background);
        EditText searchInput = dialogView.findViewById(R.id.picker_search);
        RecyclerView recyclerView = dialogView.findViewById(R.id.picker_list);
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null); intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> installedApps = pm.queryIntentActivities(intent, 0);
        Collections.sort(installedApps, new ResolveInfo.DisplayNameComparator(pm));
        List<PickerItem> allItems = new ArrayList<>();
        allItems.add(new PickerItem("清除此位置", "null", ContextCompat.getDrawable(this, R.drawable.ic_menu_delete)));
        for (ResolveInfo ri : installedApps) allItems.add(new PickerItem(ri.loadLabel(pm).toString(), ri.activityInfo.packageName, ri.loadIcon(pm)));
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.EInkDialog).setView(dialogView).create();
        PickerAdapter adapter = new PickerAdapter(allItems, pkg -> { updateDockSlot(index, pkg); dialog.dismiss(); });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { adapter.filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setDimAmount(0f);
    }

    static class PickerItem { String label, pkg; Drawable icon; PickerItem(String label, String pkg, Drawable icon) { this.label = label; this.pkg = pkg; this.icon = icon; } }

    private class PickerAdapter extends RecyclerView.Adapter<PickerAdapter.ViewHolder> {
        private List<PickerItem> allItems, filteredItems; private java.util.function.Consumer<String> onItemClick;
        PickerAdapter(List<PickerItem> items, java.util.function.Consumer<String> callback) { this.allItems = items; this.filteredItems = new ArrayList<>(items); this.onItemClick = callback; }
        void filter(String query) {
            filteredItems.clear();
            if (query.isEmpty()) filteredItems.addAll(allItems);
            else { String lowerQuery = query.toLowerCase(); for (PickerItem item : allItems) { if (item.label.toLowerCase().contains(lowerQuery)) filteredItems.add(item); } }
            notifyDataSetChanged();
        }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_picker_app, parent, false)); }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PickerItem item = filteredItems.get(position);
            holder.label.setText(item.label); holder.icon.setImageDrawable(item.icon); holder.icon.setForceShowMask(true);
            holder.itemView.setOnClickListener(v -> { if (onItemClick != null) onItemClick.accept(item.pkg); });
        }
        @Override public int getItemCount() { return filteredItems.size(); }
        class ViewHolder extends RecyclerView.ViewHolder { AppIconView icon; TextView label; ViewHolder(View v) { super(v); icon = v.findViewById(R.id.picker_app_icon); label = v.findViewById(R.id.picker_app_label); } }
    }

    private void updateDockSlot(int index, String pkg) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String dockStr = prefs.getString(DOCK_KEY, "");
        String[] savedDock = new String[DOCK_COUNT]; java.util.Arrays.fill(savedDock, "null");
        if (!dockStr.isEmpty()) { String[] split = dockStr.split("\\|", -1); for (int i = 0; i < Math.min(split.length, DOCK_COUNT); i++) savedDock[i] = split[i]; }
        savedDock[index] = pkg;
        StringBuilder sb = new StringBuilder(); for (int i = 0; i < DOCK_COUNT; i++) { sb.append(savedDock[i]); if (i < DOCK_COUNT - 1) sb.append("|"); }
        prefs.edit().putString(DOCK_KEY, sb.toString()).apply();
        appCache.clear(); refreshUI();
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
