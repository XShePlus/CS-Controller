package io.github.xsheeee.cs_controller

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.xsheeee.cs_controller.SwitchAdapter.SwitchViewHolder
import io.github.xsheeee.cs_controller.Tools.Tools

class SwitchAdapter(
    context: Context?,
    private val keys: List<String>,
    private val configMap: MutableMap<String, Boolean>,
    private val configFilePath: String
) :
    RecyclerView.Adapter<SwitchViewHolder>() {
    private val tools = Tools(context)

    // 翻译映射
    private var keyDisplayMap: Map<String, String> = HashMap()

    // 提供翻译映射的 setter
    fun setKeyDisplayMap(keyDisplayMap: Map<String, String>) {
        this.keyDisplayMap = keyDisplayMap
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwitchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.switch_list_item, parent, false)
        return SwitchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SwitchViewHolder, position: Int) {
        val key = keys[position]
        val displayName = keyDisplayMap.getOrDefault(key, key)

        holder.materialSwitch.text = displayName

        val isChecked = configMap[key]
        if (isChecked != null) {
            holder.materialSwitch.isChecked = isChecked
            holder.materialSwitch.isEnabled = true
            holder.materialSwitch.alpha = 1.0f
        } else {
            holder.materialSwitch.isEnabled = false
            holder.materialSwitch.alpha = 0.5f
        }

        holder.materialSwitch.setOnCheckedChangeListener { buttonView: CompoundButton?, isCheckedState: Boolean ->
            configMap[key] =
                isCheckedState
            val newValue = if (isCheckedState) "true" else "false"
            tools.updateConfigEntry(configFilePath, key, newValue)
        }
    }

    override fun getItemCount(): Int {
        return keys.size
    }

    class SwitchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var materialSwitch: MaterialSwitch =
            itemView.findViewById(R.id.material_switch)
    }
}