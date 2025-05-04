package io.github.xsheeee.cs_controller

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import io.github.xsheeee.cs_controller.tools.Logger
import io.github.xsheeee.cs_controller.tools.Tools

class InfoActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // 检查用户是否已接受
        val preferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val hasAccepted = preferences.getBoolean("hasAccepted", false)

        if (hasAccepted) {
            // 用户已经接受，直接跳转到 MainActivity
            val intent = Intent(this@InfoActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_info)

        val refuseButton = findViewById<Button>(R.id.info_refuse)
        val acceptButton = findViewById<Button>(R.id.info_accept)

        Tools(applicationContext)

        // 设置接受按钮的点击事件
        acceptButton.setOnClickListener {
            // 用户接受，保存状态并打开主Activity
            val editor = preferences.edit()
            editor.putBoolean("hasAccepted", true)
            editor.apply()

            val intent = Intent(this@InfoActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        // 设置拒绝按钮的点击事件
        refuseButton.setOnClickListener {
            Logger.showToast(
                this@InfoActivity,
                "不同意将退出应用"
            )
            finish()
        }
    }
}