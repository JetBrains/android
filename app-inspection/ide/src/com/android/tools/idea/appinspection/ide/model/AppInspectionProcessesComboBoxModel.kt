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
import com.android.tools.idea.appinspection.api.TransportProcessDescriptor
import com.android.tools.idea.appinspection.ide.AppInspectionHostService
import com.google.common.annotations.VisibleForTesting
import com.intellij.util.concurrency.AppExecutorUtil

//TODO(b/148546243): separate view and model code into independent modules.
class AppInspectionProcessesComboBoxModel private constructor() : DefaultCommonComboBoxModel<TransportProcessDescriptor>("") {
  override var editable = false

  companion object {
    fun newInstance() = newInstance(AppInspectionHostService.instance.discoveryHost)

    @VisibleForTesting
    fun newInstance(discoveryHost: AppInspectionDiscoveryHost): AppInspectionProcessesComboBoxModel {
      val model = AppInspectionProcessesComboBoxModel()
      discoveryHost.addProcessListener(
        AppExecutorUtil.getAppScheduledExecutorService(),
        object : AppInspectionDiscoveryHost.AppInspectionProcessListener {
          override fun onProcessConnected(descriptor: TransportProcessDescriptor) {
            model.addElement(descriptor)
          }
          override fun onProcessDisconnected(descriptor: TransportProcessDescriptor) {
            model.removeElement(descriptor)
          }
        })
      return model
    }
  }

  override fun getSelectedItem() = super.getSelectedItem() ?: "No Inspection Target Available"
}