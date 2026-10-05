package com.hnrzzin.granaxp.repositories

import com.google.firebase.functions.FirebaseFunctions

internal object FirebaseFunctionsProvider {
    val instance: FirebaseFunctions by lazy {
        FirebaseFunctions.getInstance("southamerica-east1")
    }
}
