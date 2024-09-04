/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wearwhs.view

import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.wearwhs.communication.ContentProviderDeviceManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class WearHealthServicesPanelManager(private val project: Project) : Disposable {

  private val panels = mutableMapOf<String, WearHealthServicesPanel>()
  private val adbSessionProvider = { AdbLibService.getSession(project) }

  fun getOrCreate(emulatorController: EmulatorController): WearHealthServicesPanel =
    synchronized(panels) {
      val serialNumber = emulatorController.emulatorId.serialNumber
      if (serialNumber in panels) {
        return panels.getValue(serialNumber)
      }

      val workerScope: CoroutineScope = AndroidCoroutineScope(emulatorController, workerThread)
      val uiScope: CoroutineScope = AndroidCoroutineScope(emulatorController, uiThread)
      val deviceManager = ContentProviderDeviceManager(adbSessionProvider)
      val stateManager =
        WearHealthServicesStateManagerImpl(deviceManager, workerScope = workerScope).also {
          Disposer.register(emulatorController, it)
          it.serialNumber = serialNumber
        }

      val panel =
        createWearHealthServicesPanel(stateManager, uiScope = uiScope, workerScope = workerScope)
      panels[serialNumber] = panel

      Disposer.register(emulatorController) { synchronized(panels) { panels.remove(serialNumber) } }

      return panel
    }

  override fun dispose() {}
}
