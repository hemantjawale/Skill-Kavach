package com.example.skilkavach

import android.content.res.Configuration
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LightThemeTest {
    @Test
    fun applicationWindowRemainsLightUnderNightConfiguration() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config =
            Configuration(base.resources.configuration).apply {
                uiMode =
                    (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        Configuration.UI_MODE_NIGHT_YES
            }
        val context =
            ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_SkilKavach)
        val values =
            context.obtainStyledAttributes(
                intArrayOf(
                    android.R.attr.colorBackground,
                    android.R.attr.windowLightStatusBar,
                    android.R.attr.forceDarkAllowed,
                )
            )
        try {
            assertEquals(Color.rgb(247, 248, 250), values.getColor(0, Color.BLACK))
            assertTrue(values.getBoolean(1, false))
            assertFalse(values.getBoolean(2, true))
        } finally {
            values.recycle()
        }
    }
}
