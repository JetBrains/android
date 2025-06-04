/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.settingssync.onboarding

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.settingsSync.core.UpdateResult
import com.intellij.settingsSync.core.communicator.SettingsSyncCommunicatorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun checkCloudUpdates(userEmail: String, providerCode: String): UpdateResult {
  return withContext(Dispatchers.IO) {
    val parentDisposable = Disposer.newDisposable()
    try {
      val communicator =
        SettingsSyncCommunicatorProvider.PROVIDER_EP.extensionList
          .singleOrNull { it.isAvailable() && it.providerCode == providerCode }
          ?.createCommunicator(userEmail)
          ?.apply { (this as? Disposable)?.let { Disposer.register(parentDisposable, it) } }
          ?: error("Can't create $providerCode communicator for $userEmail.")

      communicator.receiveUpdates()
    } finally {
      Disposer.dispose(parentDisposable)
    }
  }
}
