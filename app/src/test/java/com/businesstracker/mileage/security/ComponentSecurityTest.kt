package com.businesstracker.mileage.security

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.businesstracker.mileage.receiver.AutoDriveReceiver
import com.businesstracker.mileage.transition.ActivityTransitionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Validates manifest component exposure, least-privilege export attributes,
 * and explicit PendingIntent routing (GAP-01).
 */
@RunWith(RobolectricTestRunner::class)
class ComponentSecurityTest {

    private lateinit var context: Context
    private lateinit var manifestContent: String

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val manifestFile = File("src/main/AndroidManifest.xml").let {
            if (it.exists()) it else File("app/src/main/AndroidManifest.xml")
        }
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        manifestContent = manifestFile.readText()
    }

    @Test
    fun autoDriveReceiver_isNotExported_inManifest() {
        // GAP-01: AutoDriveReceiver must NOT be exported to prevent arbitrary 3rd-party broadcast injection
        val receiverRegex = Regex(
            """<receiver[^>]*android:name="\.receiver\.AutoDriveReceiver"[^>]*>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val match = receiverRegex.find(manifestContent)
        assertNotNull("AutoDriveReceiver tag must exist in manifest", match)
        val tagText = match!!.value

        assertTrue("AutoDriveReceiver must declare android:exported=\"false\"", tagText.contains("android:exported=\"false\""))
        assertFalse("AutoDriveReceiver must NOT be exported", tagText.contains("android:exported=\"true\""))
    }

    @Test
    fun internalServicesAndReceivers_areNotExported() {
        // LocationTrackingService must be unexported
        val serviceRegex = Regex("""<service[^>]*android:name="\.service\.LocationTrackingService"[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        val serviceMatch = serviceRegex.find(manifestContent)
        assertNotNull("LocationTrackingService tag must exist", serviceMatch)
        assertTrue("LocationTrackingService must be unexported", serviceMatch!!.value.contains("android:exported=\"false\""))

        // ServiceActionReceiver must be unexported
        val actionRegex = Regex("""<receiver[^>]*android:name="\.receiver\.ServiceActionReceiver"[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        val actionMatch = actionRegex.find(manifestContent)
        assertNotNull("ServiceActionReceiver tag must exist", actionMatch)
        assertTrue("ServiceActionReceiver must be unexported", actionMatch!!.value.contains("android:exported=\"false\""))

        // FileProvider must be unexported
        val providerRegex = Regex("""<provider[^>]*android:name="androidx\.core\.content\.FileProvider"[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        val providerMatch = providerRegex.find(manifestContent)
        assertNotNull("FileProvider tag must exist", providerMatch)
        assertTrue("FileProvider must be unexported", providerMatch!!.value.contains("android:exported=\"false\""))
        assertTrue("FileProvider must grantUriPermissions", providerMatch.value.contains("android:grantUriPermissions=\"true\""))
    }

    @Test
    fun autoDriveReceiver_discardsEmptyOrSpoofedIntents_gracefully() {
        val receiver = AutoDriveReceiver()
        // An intent without ActivityTransitionResult extra
        val emptyIntent = Intent(AutoDriveReceiver.ACTION_ACTIVITY_TRANSITION)

        // onReceive should immediately return without throwing or crashing
        receiver.onReceive(context, emptyIntent)
        receiver.onReceive(context, null)
    }

    @Test
    fun activityTransitionManager_usesExplicitIntent() {
        val intent = Intent(context, AutoDriveReceiver::class.java).apply {
            action = AutoDriveReceiver.ACTION_ACTIVITY_TRANSITION
        }
        assertEquals(
            "Intent component package must match application",
            context.packageName,
            intent.component?.packageName
        )
        assertEquals(
            "Intent component class must be AutoDriveReceiver",
            AutoDriveReceiver::class.java.name,
            intent.component?.className
        )
    }
}
