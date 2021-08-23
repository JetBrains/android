/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.wearpairing

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.AndroidStartupActivity
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project

/**
 * Listens to Android Studio startup, loads any previously persisted Wear Pairing data
 * and launches the Wear Pairing Manager. The Pairing Manager will then pair any devices
 * already running, if they need pairing, and will also listen for new devices that come
 * online.
 */
internal class WearPairingAndroidStartupActivity : AndroidStartupActivity {
  @UiThread
  override fun runActivity(project: Project, disposable: Disposable) {
    loadSettingsAndStartWearManager()
  }

  companion object {
    private var initialized = false
    private fun loadSettingsAndStartWearManager() {
      if (initialized) {
        return
      }
      initialized = true
      object : Backgroundable(null, "Loading wear pairing", false, ALWAYS_BACKGROUND) {
        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = true

          WearPairingSettings.getInstance().apply {
            WearPairingManager.loadSettings(pairedDevicesState, pairedDeviceConnectionsState)
          }

          val wizardAction = object : WizardAction {
            override fun restart(project: Project) {
              WearDevicePairingWizard().show(project, null)
            }
          }
          // Launch WearPairingManager
          WearPairingManager.setDeviceListListener(WearDevicePairingModel(), wizardAction)
        }
      }.queue()
    }
  }
}