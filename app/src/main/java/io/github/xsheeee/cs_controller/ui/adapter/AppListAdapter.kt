package io.github.xsheeee.cs_controller.ui.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.xsheeee.cs_controller.ui.AppConfigActivity
import io.github.xsheeee.cs_controller.ui.AppListActivity
import io.github.xsheeee.cs_controller.R
import io.github.xsheeee.cs_controller.tools.AppInfo
import java.util.concurrent.ExecutorService

class AppListAdapter(
    private val activity: AppListActivity,
    private val filteredData: List<AppInfo>,
    private val executorService: ExecutorService?,
    private val iconLoadCallback: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return filteredData[position].packageName.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.app_info_layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appInfo = filteredData[position]
        holder.bind(appInfo)

        if (appInfo.icon == null && !isIconLoading(appInfo)) {
            holder.imageView.setImageDrawable(null)
            iconLoadCallback(appInfo)
        }
    }

    override fun getItemCount(): Int {
        return filteredData.size
    }

    private fun isIconLoading(appInfo: AppInfo): Boolean {
        return appInfo.icon != null || executorService!!.isShutdown
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.app_icon)
        private val textView: TextView = itemView.findViewById(R.id.app_name)
        private val packageNameView: TextView = itemView.findViewById(R.id.pck_name)
        private val performanceModeView: TextView = itemView.findViewById(R.id.performance_mode)
        private var boundPackageName: String? = null

        fun bind(appInfo: AppInfo) {
            boundPackageName = appInfo.packageName

            if (appInfo.icon != null) {
                imageView.setImageDrawable(appInfo.icon)
            } else {
                imageView.setImageDrawable(null)
            }

            textView.text = appInfo.appName
            packageNameView.text = boundPackageName

            val performanceMode = appInfo.performanceMode
            if (!performanceMode?.isEmpty()!!) {
                performanceModeView.visibility = View.VISIBLE
                performanceModeView.text = performanceMode
            } else {
                performanceModeView.visibility = View.GONE
            }

            itemView.setOnClickListener { _: View? ->
                if (!activity.isLoading) {
                    val intent = Intent(
                        activity,
                        AppConfigActivity::class.java
                    )
                    intent.putExtra("aName", appInfo.appName)
                    intent.putExtra("pName", boundPackageName)
                    activity.startActivity(intent)
                }
            }
        }
    }
}