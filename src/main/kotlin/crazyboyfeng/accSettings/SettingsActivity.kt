package com.acc.settings

import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ContentFrameLayout
import androidx.preference.PreferenceFragmentCompat
import com.topjohnwu.superuser.Shell
import com.acc.settings.fragment.SettingsFragment

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Shell.rootAccess()) {
            val textView = TextView(this)
            textView.setText(R.string.need_root_permission)
            textView.gravity = Gravity.CENTER
            val contentFrameLayout = findViewById<ContentFrameLayout>(android.R.id.content)
            contentFrameLayout.addView(textView)
            return
        }
        supportFragmentManager.addOnBackStackChangedListener {
            val preferenceFragment =
                supportFragmentManager.findFragmentById(android.R.id.content) as PreferenceFragmentCompat
            supportActionBar?.subtitle = preferenceFragment.preferenceScreen.title
        }
        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }
}