package io.github.xsheeee.cs_controller;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.xsheeee.cs_controller.Tools.Tools;

public class SwitchAdapter extends RecyclerView.Adapter<SwitchAdapter.SwitchViewHolder> {
    private final List<String> keys;
    private final Map<String, Boolean> configMap;
    private final String configFilePath;
    private final Tools tools;

    // 翻译映射
    private Map<String, String> keyDisplayMap = new HashMap<>();

    public SwitchAdapter(Context context, List<String> keys, Map<String, Boolean> configMap, String configFilePath) {
        this.keys = keys;
        this.configMap = configMap;
        this.configFilePath = configFilePath;
        this.tools = new Tools(context);
    }

    // 提供翻译映射的 setter
    public void setKeyDisplayMap(Map<String, String> keyDisplayMap) {
        this.keyDisplayMap = keyDisplayMap;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SwitchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.switch_list_item, parent, false);
        return new SwitchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SwitchViewHolder holder, int position) {
        String key = keys.get(position);
        String displayName = keyDisplayMap.getOrDefault(key, key);

        holder.materialSwitch.setText(displayName);

        Boolean isChecked = configMap.get(key);
        if (isChecked != null) {
            holder.materialSwitch.setChecked(isChecked);
            holder.materialSwitch.setEnabled(true);
            holder.materialSwitch.setAlpha(1.0f);
        } else {
            holder.materialSwitch.setEnabled(false);
            holder.materialSwitch.setAlpha(0.5f);
        }

        holder.materialSwitch.setOnCheckedChangeListener((buttonView, isCheckedState) -> {
            configMap.put(key, isCheckedState);
            String newValue = isCheckedState ? "true" : "false";
            tools.updateConfigEntry(configFilePath, key, newValue);
        });
    }

    @Override
    public int getItemCount() {
        return keys.size();
    }

    static class SwitchViewHolder extends RecyclerView.ViewHolder {
        MaterialSwitch materialSwitch;

        public SwitchViewHolder(@NonNull View itemView) {
            super(itemView);
            materialSwitch = itemView.findViewById(R.id.material_switch);
        }
    }
}