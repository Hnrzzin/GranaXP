package com.hnrzzin.granaxp.repositories

import com.google.firebase.functions.FirebaseFunctions
import com.hnrzzin.granaxp.BuildConfig

internal object FirebaseFunctionsProvider {
    val instance: FirebaseFunctions by lazy {
        FirebaseFunctions.getInstance("southamerica-east1").also { functions ->
            if (BuildConfig.USE_FIREBASE_EMULATORS) {
                functions.useEmulator("10.0.2.2", 5001)
            }
        }
    }
}
