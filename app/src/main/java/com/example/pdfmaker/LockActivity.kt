package com.example.pdfmaker
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LockActivity : AppCompatActivity() {
    private var prompting = false
    override fun onCreate(s: Bundle?) {
        super.onCreate(s); setContentView(R.layout.activity_lock)
        findViewById<Button>(R.id.btnUnlock).setOnClickListener { showPrompt() }
        showPrompt()
    }
    private fun showPrompt() {
        if (prompting) return; prompting = true
        AppLock.promptVerify(this, "Unlock PDF Maker",
            onOk = { prompting = false; AppLock.markUnlocked(); finish() },
            onFail = { prompting = false
                Toast.makeText(this, "Not unlocked", Toast.LENGTH_SHORT).show(); moveTaskToBack(true) })
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = moveTaskToBack(true)
}
