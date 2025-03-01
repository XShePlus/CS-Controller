package io.github.xsheeee.cs_controller

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // 初始化 Toolbar
        val toolbar = findViewById<Toolbar>(R.id.backButton3)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.outline_arrow_back_24)
        toolbar.setNavigationOnClickListener { v: View? -> finish() }

        setupClickableLinks()
    }

    private fun setupClickableLinks() {
        for (i in TEXT_VIEW_IDS.indices) {
            val textView = findViewById<TextView>(TEXT_VIEW_IDS[i])
            val spannableString = SpannableString(getTextForIndex(i))

            val clickableText = getClickableTextForIndex(i)
            val url = getUrlForIndex(i)

            if (clickableText != null && url != null) {
                addClickablePart(spannableString, clickableText, url)
            }

            textView.text = spannableString
            textView.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun getTextForIndex(index: Int): String {
        return when (index) {
            0 -> getString(R.string.view_source_code)
            1 -> getString(R.string.xshe_github)
            2 -> getString(R.string.cs_source_code)
            3 -> getString(R.string.mowei_github)
            else -> ""
        }
    }

    private fun getClickableTextForIndex(index: Int): String? {
        return when (index) {
            0, 3, 1, 2 -> "GitHub"
            else -> null
        }
    }

    private fun getUrlForIndex(index: Int): String? {
        return when (index) {
            0 -> getString(R.string.url_xshe_source)
            1 -> getString(R.string.url_xshe_github)
            2 -> getString(R.string.url_mowei_source)
            3 -> getString(R.string.url_mowei_github)
            else -> null
        }
    }

    // 添加点击链接
    private fun addClickablePart(
        spannableString: SpannableString,
        clickableText: String,
        url: String
    ) {
        val start = spannableString.toString().indexOf(clickableText)
        val end = start + clickableText.length

        if (start >= 0) {
            val clickableSpan: ClickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setData(Uri.parse(url))
                    startActivity(intent)
                }
            }
            spannableString.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    companion object {
        private val TEXT_VIEW_IDS = intArrayOf(
            R.id.view_source_code,
            R.id.xshe_github,
            R.id.cs_source_code,
            R.id.mowei_github
        )
    }
}