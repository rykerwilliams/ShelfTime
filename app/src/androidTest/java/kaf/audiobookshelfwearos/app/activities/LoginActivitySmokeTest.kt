package kaf.audiobookshelfwearos.app.activities

import android.Manifest
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A minimal end-to-end smoke test: does the app actually launch on a real
 * (emulated) device without crashing? Unit tests can't catch this class of
 * failure (manifest misconfiguration, a crash in Activity/Compose setup,
 * missing resources, etc.) since they never run on an Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class LoginActivitySmokeTest {

    // LoginActivity requests POST_NOTIFICATIONS on API 33+; pre-granting it
    // avoids the runtime permission dialog stealing focus so the Activity
    // can actually reach RESUMED.
    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @Test
    fun loginActivityLaunchesToResumed() {
        ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
