package com.myrbsdk

import androidx.privacysandbox.tools.PrivacySandboxService

@PrivacySandboxService
interface MySdk {
    suspend fun doMath(x: Int, y: Int): Int
}