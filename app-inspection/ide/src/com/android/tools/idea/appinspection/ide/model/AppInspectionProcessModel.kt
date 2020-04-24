/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.ide.model

import com.android.tools.idea.appinspection.api.AppInspectionDiscoveryHost
import com.android.tools.idea.appinspection.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.api.process.ProcessListener
import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.EdtExecutorService

/**
 * Model class that owns a list of active [ProcessDescriptor] targets with listeners that trigger
 * when one is added or removed.
 */
class AppInspectionProcessModel(private val appInspectionDiscoveryHost: AppInspectionDiscoveryHost,
                                private val getPreferredProcessNames: () -> List<String>) : Disposable {
  private val selectedProcessListeners = mutableListOf<() -> Unit>()
  fun addSelectedProcessListeners(listener: () -> Unit) = selectedProcessListeners.add(listener)

  val processes = mutableSetOf<ProcessDescriptor>()
  var selectedProcess: ProcessDescriptor? = null
    set(value) {
      if (field != value) {
        field = value
        selectedProcessListeners.forEach { listener -> listener() }
      }
    }

  private val processListener = object : ProcessListener {
    override fun onProcessConnected(descriptor: ProcessDescriptor) {
      processes.add(descriptor)
      if (isProcessPreferred(descriptor) && !isProcessPreferred(selectedProcess)) {
        selectedProcess = descriptor
      }
    }

    override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
      processes.remove(descriptor)
      if (descriptor == selectedProcess) {
        selectedProcess = null
      }
    }
  }

  init {
    appInspectionDiscoveryHost.addProcessListener(EdtExecutorService.getInstance(), processListener)
  }

  override fun dispose() {
    appInspectionDiscoveryHost.removeProcessListener(processListener)
  }

  fun isProcessPreferred(processDescriptor: ProcessDescriptor?) =
    processDescriptor != null && getPreferredProcessNames().contains(processDescriptor.processName)
}
