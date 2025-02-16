package io.github.xsheeee.cs_controller;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.xsheeee.cs_controller.Tools.AppInfo;

public class AppListActivity extends AppCompatActivity {
    private static final String CONFIG_FILE_PATH = 
        "/storage/emulated/0/Android/CSController/app_config.json";
    private static final int PRELOAD_AHEAD_ITEMS = 20;
    private static final int ICON_CACHE_SIZE = 200;
    private static final long SEARCH_DEBOUNCE_TIME_MS = 300;
    private static final int LOADING_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    private RecyclerView recyclerView;
    private final List<AppInfo> data = Collections.synchronizedList(new ArrayList<>());
    private final List<AppInfo> filteredData = Collections.synchronizedList(new ArrayList<>());
    private FrameLayout loadingView;
    private PackageManager packageManager;
    private AppListAdapter adapter;
    private ExecutorService executorService;
    private LruCache<String, WeakReference<Drawable>> iconCache;
    private final Map<String, String> performanceModes = Collections.synchronizedMap(new HashMap<>());
    private FileObserver configFileObserver;
    private volatile boolean isLoading = false;

    private Runnable searchRunnable;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);

        initializeViews();
        initializeCache();
        setupRecyclerView();
        setupConfigFileObserver();
        loadAppInfos();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recycler_view);
        loadingView = findViewById(R.id.loading_view);
        packageManager = getPackageManager();

        // 优化线程池配置
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            LOADING_POOL_SIZE,
            LOADING_POOL_SIZE * 2,
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(40),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        executorService = executor;

        Toolbar toolbar = findViewById(R.id.backButton);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initializeCache() {
        iconCache = new LruCache<String, WeakReference<Drawable>>(ICON_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, WeakReference<Drawable> value) {
                return 1; // 简化大小计算
            }
        };

        // 预加载系统应用图标
        executorService.execute(() -> {
            try {
                List<ApplicationInfo> apps = packageManager.getInstalledApplications(0);
                for (ApplicationInfo app : apps) {
                    if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        Drawable icon = app.loadIcon(packageManager);
                        iconCache.put(app.packageName, new WeakReference<>(icon));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void setupConfigFileObserver() {
    String configDir = CONFIG_FILE_PATH.substring(0, CONFIG_FILE_PATH.lastIndexOf('/'));
    File configFile = new File(configDir);

    configFileObserver = new FileObserver(configFile, 
        FileObserver.MODIFY | FileObserver.CLOSE_WRITE) {
        @Override
        public void onEvent(int event, @Nullable String path) {
            if (path == null) return;

            String fileName = CONFIG_FILE_PATH.substring(CONFIG_FILE_PATH.lastIndexOf('/') + 1);
            if (!path.equals(fileName)) return;

            if ((event & FileObserver.MODIFY) != 0 || 
                (event & FileObserver.CLOSE_WRITE) != 0) {
                runOnUiThread(() -> {
                    performanceModes.clear();
                    loadAppInfos();
                });
            }
        }
    };
}

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        recyclerView.setItemAnimator(null);
        
        adapter = new AppListAdapter();
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (!isLoading) {
                    loadVisibleIcons();
                }
            }
        });
    }

    private void readConfigFile() {
        File configFile = new File(CONFIG_FILE_PATH);
        if (!configFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(configFile)))) {
            
            StringBuilder sb = new StringBuilder(1024);
            char[] buffer = new char[1024];
            int read;
            
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }

            JSONObject json = new JSONObject(sb.toString());
            performanceModes.clear();
	  
            String[] modes = {"powersave", "balance", "performance", "fast"};
            for (String mode : modes) {
                JSONArray apps = json.optJSONArray(mode);
                if (apps != null) {
                    for (int i = 0; i < apps.length(); i++) {
                        performanceModes.put(apps.getString(i), mode);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadAppInfos() {
        if (isLoading) return;
        isLoading = true;
        loadingView.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            readConfigFile();
            List<AppInfo> loadedData = getAllAppInfos();

            Collections.sort(loadedData, (app1, app2) -> {
                if (app1.isPriority() && !app2.isPriority()) return -1;
                if (!app1.isPriority() && app2.isPriority()) return 1;
                return app1.getAppName().compareTo(app2.getAppName());
            });

            runOnUiThread(() -> {
                updateAppData(loadedData);
                loadingView.setVisibility(View.GONE);
                isLoading = false;
            });
        });
    }

    protected List<AppInfo> getAllAppInfos() {
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_ALL
        );

        return resolveInfos.parallelStream()
            .map(ri -> {
                String packageName = ri.activityInfo.packageName;
                String appName = ri.loadLabel(packageManager).toString();
                AppInfo appInfo = new AppInfo(null, appName, packageName);
                
                if (performanceModes.containsKey(packageName)) {
                    appInfo.setPerformanceMode(performanceModes.get(packageName));
                    appInfo.setPriority(true);
                }
                
                return appInfo;
            })
            .collect(Collectors.toList());
    }

    private void loadVisibleIcons() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null) return;

        int firstVisible = Math.max(0, layoutManager.findFirstVisibleItemPosition());
        int lastVisible = Math.min(layoutManager.findLastVisibleItemPosition(), 
            filteredData.size() - 1);

        // 使用批量加载
        List<AppInfo> toLoad = new ArrayList<>();
        for (int i = firstVisible; i <= lastVisible; i++) {
            AppInfo appInfo = filteredData.get(i);
            if (appInfo.getIcon() == null && !isIconLoading(appInfo)) {
                toLoad.add(appInfo);
            }
        }

        // 预加载
        int endPosition = Math.min(lastVisible + PRELOAD_AHEAD_ITEMS, filteredData.size());
        for (int i = lastVisible + 1; i < endPosition; i++) {
            AppInfo appInfo = filteredData.get(i);
            if (appInfo.getIcon() == null && !isIconLoading(appInfo)) {
                toLoad.add(appInfo);
            }
        }

        // 批量加载图标
        if (!toLoad.isEmpty()) {
            executorService.execute(() -> {
                for (AppInfo appInfo : toLoad) {
                    loadIconForAppInfo(appInfo);
                }
            });
        }
    }

    private void loadIconForAppInfo(AppInfo appInfo) {
        try {
            WeakReference<Drawable> cachedIconRef = iconCache.get(appInfo.getPackageName());
            if (cachedIconRef != null) {
                Drawable cachedIcon = cachedIconRef.get();
                if (cachedIcon != null) {
                    appInfo.setIcon(cachedIcon);
                    notifyItemChanged(appInfo);
                    return;
                }
                iconCache.remove(appInfo.getPackageName());
            }

            Drawable icon = packageManager.getApplicationIcon(appInfo.getPackageName());
            appInfo.setIcon(icon);
            iconCache.put(appInfo.getPackageName(), new WeakReference<>(icon));
            notifyItemChanged(appInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isIconLoading(AppInfo appInfo) {
        return appInfo.getIcon() != null || executorService.isShutdown();
    }

    private void updateAppData(List<AppInfo> loadedData) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
            new DiffCallback(data, loadedData), true);
        
        data.clear();
        data.addAll(loadedData);
        filteredData.clear();
        filteredData.addAll(data);
        
        diffResult.dispatchUpdatesTo(adapter);
        loadVisibleIcons();
    }

    private void notifyItemChanged(AppInfo appInfo) {
        runOnUiThread(() -> {
            int position = filteredData.indexOf(appInfo);
            if (position != -1) {
                adapter.notifyItemChanged(position);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.top_app_bar, menu);
        setupSearchView(menu);
        return true;
    }

    private void setupSearchView(Menu menu) {
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        Objects.requireNonNull(searchView).setQueryHint(getString(R.string.search_text));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> filter(newText);
                searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_TIME_MS);
                return true;
            }
        });
    }

    private void filter(String text) {
        if (isLoading) return;
        
        String lowerCaseText = text.toLowerCase();
        List<AppInfo> newFilteredData = text.isEmpty() 
            ? new ArrayList<>(data)
            : data.parallelStream()
                .filter(appInfo ->
                    appInfo.getAppName().toLowerCase().contains(lowerCaseText) ||
                    appInfo.getPackageName().toLowerCase().contains(lowerCaseText))
                .collect(Collectors.toList());

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
            new DiffCallback(filteredData, newFilteredData), true);
            
        filteredData.clear();
        filteredData.addAll(newFilteredData);
        diffResult.dispatchUpdatesTo(adapter);
        loadVisibleIcons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (configFileObserver != null) {
            configFileObserver.startWatching();
        }
        refreshPerformanceModes();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (configFileObserver != null) {
            configFileObserver.stopWatching();
        }
    }

   @Override
    protected void onDestroy() {
        super.onDestroy();
        if (configFileObserver != null) {
            configFileObserver.stopWatching();
            configFileObserver = null;
        }
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        if (iconCache != null) {
            iconCache.evictAll();
        }
    }

    private void refreshPerformanceModes() {
        if (isLoading) return;
        
        executorService.execute(() -> {
            readConfigFile();
            
            boolean needsSort = false;
            for (AppInfo appInfo : data) {
                String newMode = performanceModes.get(appInfo.getPackageName());
                if (!Objects.equals(appInfo.getPerformanceMode(), newMode)) {
                    appInfo.setPerformanceMode(newMode == null ? "" : newMode);
                    appInfo.setPriority(newMode != null);
                    needsSort = true;
                }
            }

            if (needsSort) {
                Collections.sort(data, (app1, app2) -> {
                    if (app1.isPriority() && !app2.isPriority()) return -1;
                    if (!app1.isPriority() && app2.isPriority()) return 1;
                    return app1.getAppName().compareTo(app2.getAppName());
                });

                runOnUiThread(() -> {
                    filteredData.clear();
                    filteredData.addAll(data);
                    adapter.notifyDataSetChanged();
                });
            }
        });
    }

    static class DiffCallback extends DiffUtil.Callback {
        private final List<AppInfo> oldList;
        private final List<AppInfo> newList;

        DiffCallback(List<AppInfo> oldList, List<AppInfo> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getPackageName()
                .equals(newList.get(newItemPosition).getPackageName());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            AppInfo oldItem = oldList.get(oldItemPosition);
            AppInfo newItem = newList.get(newItemPosition);
            
            if (!oldItem.getAppName().equals(newItem.getAppName())) return false;
            if (!Objects.equals(oldItem.getPerformanceMode(), 
                newItem.getPerformanceMode())) return false;
            
            Drawable oldIcon = oldItem.getIcon();
            Drawable newIcon = newItem.getIcon();
            if (oldIcon == null && newIcon == null) return true;
            if (oldIcon == null || newIcon == null) return false;
            
            return oldIcon.getConstantState() == newIcon.getConstantState();
        }
    }

    class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
        private static final int MAX_POOL_SIZE = 20;

        AppListAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return filteredData.get(position).getPackageName().hashCode();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.app_info_layout, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppInfo appInfo = filteredData.get(position);
            holder.bind(appInfo);

            if (appInfo.getIcon() == null && !isIconLoading(appInfo)) {
                holder.imageView.setImageDrawable(null);
                loadIconForAppInfo(appInfo);
            }
        }

        @Override
        public int getItemCount() {
            return filteredData.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView imageView;
            private final TextView textView;
            private final TextView packageNameView;
            private final TextView performanceModeView;
            private String boundPackageName;

            ViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.app_icon);
                textView = itemView.findViewById(R.id.app_name);
                packageNameView = itemView.findViewById(R.id.pck_name);
                performanceModeView = itemView.findViewById(R.id.performance_mode);
            }

            void bind(AppInfo appInfo) {
                boundPackageName = appInfo.getPackageName();
                
                if (appInfo.getIcon() != null) {
                    imageView.setImageDrawable(appInfo.getIcon());
                } else {
                    imageView.setImageDrawable(null);
                }
                
                textView.setText(appInfo.getAppName());
                packageNameView.setText(boundPackageName);

                String performanceMode = appInfo.getPerformanceMode();
                if (!performanceMode.isEmpty()) {
                    performanceModeView.setVisibility(View.VISIBLE);
                    performanceModeView.setText(performanceMode);
                } else {
                    performanceModeView.setVisibility(View.GONE);
                }

                itemView.setOnClickListener(v -> {
                    if (!isLoading) {
                        Intent intent = new Intent(AppListActivity.this, AppConfigActivity.class);
                        intent.putExtra("aName", appInfo.getAppName());
                        intent.putExtra("pName", boundPackageName);
                        startActivity(intent);
                    }
                });
            }
        }
    }
}