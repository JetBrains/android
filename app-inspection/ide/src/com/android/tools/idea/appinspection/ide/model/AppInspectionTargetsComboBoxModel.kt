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
import com.android.tools.idea.appinspection.api.AppInspectionDiscovery
import com.android.tools.idea.appinspection.api.AppInspectionProcessListener
import com.android.tools.idea.appinspection.api.ProcessDescriptor
import com.android.tools.idea.appinspection.ide.AppInspectionClientsService
import com.google.common.annotations.VisibleForTesting
import com.intellij.util.concurrency.AppExecutorUtil

class AppInspectionTargetsComboBoxModel private constructor() : DefaultCommonComboBoxModel<ProcessDescriptor>("") {
  override var editable = false

  companion object {
    fun newInstance() = newInstance(AppInspectionClientsService.discovery)

    @VisibleForTesting
    fun newInstance(discovery: AppInspectionDiscovery): AppInspectionTargetsComboBoxModel {
      val model = AppInspectionTargetsComboBoxModel()
      discovery.addProcessListener(object : AppInspectionProcessListener {
        override fun onProcessConnect(processDescriptor: ProcessDescriptor) {
          model.addElement(processDescriptor)
        }

        override fun onProcessDisconnect(processDescriptor: ProcessDescriptor) {
          model.removeElement(processDescriptor)
        }
      }, AppExecutorUtil.getAppScheduledExecutorService())
      return model
    }
  }

  override fun getSelectedItem() = super.getSelectedItem() ?: "No Inspection Target Available"
}