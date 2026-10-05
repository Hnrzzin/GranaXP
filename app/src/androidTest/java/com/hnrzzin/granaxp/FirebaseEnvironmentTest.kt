package com.hnrzzin.granaxp

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseEnvironmentTest {
    @get:Rule
    val executionProof = InstrumentationExecutionProof()

    @Test
    fun startupSelectsOneCoherentEnvironmentBeforeRepositoriesRun() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val provider = context.packageManager.getProviderInfo(
            ComponentName(context, "com.google.firebase.provider.FirebaseInitProvider"),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )

        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            assertEquals("demo-granaxp", app.options.projectId)
            assertNotEquals(FirebaseOptions.fromResource(context)?.applicationId, app.options.applicationId)
            assertFalse(provider.enabled)
            assertTrue(BuildConfig.DEBUG)
            assertEquals("127.0.0.1:8080", firestore.firestoreSettings.host)
            assertFalse(firestore.firestoreSettings.isSslEnabled)
            assertTrue(firestore.firestoreSettings.cacheSettings is MemoryCacheSettings)
            assertEquals(1, FirebaseApp.getApps(context).size)
        } else {
            assertEquals(FirebaseOptions.fromResource(context), app.options)
            assertTrue(provider.enabled)
            assertEquals("firestore.googleapis.com", firestore.firestoreSettings.host)
            assertTrue(firestore.firestoreSettings.isSslEnabled)
        }
        val permissions = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse("android.permission.ACCESS_LOCAL_NETWORK" in permissions)
        assertEquals(BuildConfig.USE_FIREBASE_EMULATORS,
            context.applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0)
    }

    @Test
    fun authenticationWorksThroughAdbLoopbackWithoutLocalNetworkPermission(): Unit = runBlocking {
        assumeTrue(BuildConfig.USE_FIREBASE_EMULATORS)
        assertEquals("demo-granaxp", FirebaseApp.getInstance().options.projectId)
        assertEquals("127.0.0.1:8080", FirebaseFirestore.getInstance().firestoreSettings.host)
        withTimeout(30_000) {
            val auth = FirebaseAuth.getInstance()
            val user = auth.createUserWithEmailAndPassword(
                "loopback-${System.nanoTime()}@example.test", "local-test-password",
            ).await().user!!
            try {
                assertTrue(user.uid.isNotBlank())
                assertEquals(user.uid, auth.currentUser?.uid)
            } finally {
                user.delete().await()
                auth.signOut()
            }
        }
    }
}
