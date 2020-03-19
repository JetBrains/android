/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.idea.appinspection.api.AppInspectionDiscoveryHost
import com.android.tools.idea.appinspection.api.ProcessDescriptor
import com.intellij.util.concurrency.EdtExecutorService

//TODO(b/148546243): separate view and model code into independent modules.
class AppInspectionProcessesComboBoxModel(appInspectionDiscoveryHost: AppInspectionDiscoveryHost, preferredProcessNames: List<String>) :
  DefaultCommonComboBoxModel<ProcessDescriptor>("") {
  override var editable = false

  init {
    appInspectionDiscoveryHost.addProcessListener(
      EdtExecutorService.getInstance(),
      object : AppInspectionDiscoveryHost.ProcessListener {
        override fun onProcessConnected(descriptor: ProcessDescriptor) {
          if (preferredProcessNames.contains(descriptor.processName)) {
            insertElementAt(descriptor, 0)
          } else {
            insertElementAt(descriptor, size)
          }
          selectedItem = descriptor
        }

        override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
          removeElement(descriptor)
        }
      })
  }

  override fun getSelectedItem() = super.getSelectedItem() ?: "No Inspection Target Available"
}