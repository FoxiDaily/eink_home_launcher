package com.fxdaily.einkhome;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchActivity extends AppCompatActivity {

    private EditText searchInput;
    private RecyclerView searchResults;
    private SearchAdapter adapter;
    private List<AppInfo> allApps = new ArrayList<>();

    static class AppInfo {
        String pkg;
        String label;
        Drawable icon;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_search);

        View root = findViewById(R.id.search_root);
        searchInput = findViewById(R.id.search_input);
        searchResults = findViewById(R.id.search_results);

        // 处理窗口缩进，避让状态栏
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        // 采用两栏网格排列
        searchResults.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new SearchAdapter();
        searchResults.setAdapter(adapter);

        loadApps();

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        searchInput.requestFocus();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);

        for (ResolveInfo ri : apps) {
            AppInfo info = new AppInfo();
            info.pkg = ri.activityInfo.packageName;
            info.label = ri.loadLabel(pm).toString();
            
            String resName = info.pkg.replace(".", "_").toLowerCase();
            int resId = getResources().getIdentifier(resName, "drawable", getPackageName());
            if (resId != 0) {
                try {
                    info.icon = ResourcesCompat.getDrawable(getResources(), resId, null);
                } catch (Exception e) {
                    info.icon = ri.loadIcon(pm);
                }
            } else {
                info.icon = ri.loadIcon(pm);
            }
            allApps.add(info);
        }
    }

    private void filterApps(String query) {
        List<AppInfo> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase(Locale.getDefault());
        for (AppInfo info : allApps) {
            if (info.label.toLowerCase(Locale.getDefault()).contains(lowerQuery)) {
                filtered.add(info);
            }
        }
        adapter.setApps(filtered);
    }

    private class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        private List<AppInfo> apps = new ArrayList<>();

        void setApps(List<AppInfo> apps) {
            this.apps = apps;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_icon, parent, false);
            // 修改为横向布局：图标居左，文字居右
            if (v instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) v;
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
                // 调低上下间距（垂直 padding 减小）
                layout.setPadding(16, 8, 16, 8);
                
                // 调整布局参数以适应网格项
                GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams) v.getLayoutParams();
                if (lp == null) {
                    lp = new GridLayoutManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                } else {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                v.setLayoutParams(lp);
            }
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppInfo info = apps.get(position);
            holder.label.setText(info.label);
            holder.icon.setImageDrawable(info.icon);
            holder.itemView.setOnClickListener(v -> {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(info.pkg);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                    finish();
                }
            });
        }

        @Override
        public int getItemCount() { return apps.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView label;
            ViewHolder(View v) {
                super(v);
                icon = v.findViewById(R.id.app_icon);
                label = v.findViewById(R.id.app_label);
                
                // 文字样式调整：左对齐，垂直居中
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
                label.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
                label.setPadding(20, 0, 0, 0);
                
                // 确保图标大小固定
                ViewGroup.LayoutParams iconLp = icon.getLayoutParams();
                iconLp.width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());
                iconLp.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());
                icon.setLayoutParams(iconLp);
            }
        }
    }
}
