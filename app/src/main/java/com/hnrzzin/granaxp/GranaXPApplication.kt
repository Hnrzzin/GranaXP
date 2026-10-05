package com.hnrzzin.granaxp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.functions.FirebaseFunctions

class GranaXPApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.USE_FIREBASE_EMULATORS) return

        // The manifest disables FirebaseInitProvider for this debug opt-in.
        // A distinct app ID keeps local Auth persistence separate from the real app.
        val app = FirebaseApp.initializeApp(this, FirebaseOptions.Builder()
            .setProjectId("demo-granaxp")
            .setApplicationId("1:1234567890:android:0123456789abcdef")
            .setApiKey("AIzaSyAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .build())
        check(app.options.projectId == "demo-granaxp")

        // adb reverse forwards the device loopback to the three host emulators.
        FirebaseAuth.getInstance(app).useEmulator("127.0.0.1", 9099)
        FirebaseFirestore.getInstance(app).apply {
            useEmulator("127.0.0.1", 8080)
            firestoreSettings = FirebaseFirestoreSettings.Builder(firestoreSettings)
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
        FirebaseFunctions.getInstance(app, "southamerica-east1").useEmulator("127.0.0.1", 5001)
    }
}
