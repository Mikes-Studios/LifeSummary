package com.mikestudios.lifesummary

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.mikestudios.lifesummary.databinding.ActivityMainBinding
import com.mikestudios.lifesummary.SettingsFragment
import com.mikestudios.lifesummary.SummaryWindowActivity

class MainActivity : AppCompatActivity() {

    private lateinit var vb: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)

        // --- Toolbar & drawer setup ---
        setSupportActionBar(vb.toolbar)
        drawerToggle = ActionBarDrawerToggle(
            this,
            vb.drawerLayout,
            vb.toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        vb.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        // Initial screen
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        vb.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment())
                R.id.nav_summaries -> loadFragment(SummariesFragment())
                R.id.nav_transcripts -> loadFragment(TranscriptsFragment())
                R.id.nav_summaries30 -> loadFragment(SummaryWindowFragment.create(30))
                R.id.nav_summaries60 -> loadFragment(SummaryWindowFragment.create(60))
                R.id.nav_summaries120 -> loadFragment(SummaryWindowFragment.create(120))
                R.id.nav_summaries240 -> loadFragment(SummaryWindowFragment.create(240))
                R.id.nav_settings -> loadFragment(SettingsFragment())
                else -> {
                    // legacy activities not yet migrated
                    if (item.itemId == R.id.nav_settings) {
                        loadFragment(SettingsFragment())
                        vb.drawerLayout.closeDrawer(GravityCompat.START)
                        return@setNavigationItemSelectedListener true
                    }
                    vb.drawerLayout.closeDrawer(GravityCompat.START)
                    return@setNavigationItemSelectedListener true
                }
            }
            vb.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    fun loadFragment(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, f)
            .commit()
    }

    // No recording control here; handled inside HomeFragment
}
