package com.hnrzzin.granaxp

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Test-only evidence: listing tests cannot execute this success callback. */
class InstrumentationExecutionProof : TestWatcher() {
    override fun succeeded(description: Description) {
        val arguments = InstrumentationRegistry.getArguments()
        val token = arguments.getString("granaxpGateToken") ?: return
        val mode = if (BuildConfig.USE_FIREBASE_EMULATORS) "local" else "normal"
        check(arguments.getString("granaxpGateMode") == mode) { "Wrong APK environment for the gate." }
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("granaxpGateProof", "${description.className}#${description.methodName}")
            putString("granaxpGateToken", token)
            putString("granaxpGateMode", mode)
        })
    }
}
