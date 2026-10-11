package com.example.pdfmaker

import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class UsageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
        }
        box.addView(TextView(this).apply {
            text = "Most used types (all-time)"; textSize = 14f
            setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(12), 0, dp(4))
        })
        UsageStats.topTypes(this, 5).forEachIndexed { i, (typeId, count) ->
            val spec = PdfCatalog.byId[typeId]
            box.addView(TextView(this).apply {
                text = "${i+1}. ${spec?.icon ?: "📄"}  ${spec?.label ?: typeId}  —  $count uses"
                textSize = 13f; setPadding(0, dp(4), 0, dp(4))
            })
        }
        box.addView(TextView(this).apply {
            text = "Total entries logged: ${UsageStats.entries(this).size}"
            textSize = 12f; setTextColor(getColor(R.color.text_secondary))
            setPadding(0, dp(16), 0, 0)
        })
        val scroll = android.widget.ScrollView(this).apply { addView(box) }
        setContentView(scroll)
        supportActionBar?.title = "Usage"
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
