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

import com.android.tools.idea.concurrency.AndroidDispatchers.ioThread
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.wizard.model.WizardModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class WearDevicePairingModel : WizardModel() {
  val phoneList: ObjectValueProperty<List<PairingDevice>> = ObjectValueProperty(emptyList())
  val wearList: ObjectValueProperty<List<PairingDevice>> = ObjectValueProperty(emptyList())

  val selectedPhoneDevice: OptionalProperty<PairingDevice> = OptionalValueProperty()
  val selectedWearDevice: OptionalProperty<PairingDevice> = OptionalValueProperty()

  val removePairingOnCancel: BoolProperty = BoolValueProperty()

  fun getNonSelectedRunningWearEmulators(): List<PairingDevice> {
    val selectedPhoneId = selectedPhoneDevice.valueOrNull?.deviceID ?: return emptyList()
    val selectedWearId = selectedWearDevice.valueOrNull?.deviceID
    return wearList.get()
      .filter { it.isOnline() && it.deviceID != selectedWearId}
      .filter {
        // If this WearOS Emulator is not paired, or is paired with the selected phone, we can kill it.
        val pairedPhoneId = WearPairingManager.getPairedDevices(it.deviceID)?.phone?.deviceID
        pairedPhoneId == null || pairedPhoneId == selectedPhoneId
      }
  }

  override fun handleFinished() {
    removePairingOnCancel.set(false) // User pressed "Finish", don't cancel pairing
  }

  override fun dispose() {
    val wear = selectedWearDevice.valueOrNull
    if (wear != null && removePairingOnCancel.get()) {
      GlobalScope.launch(ioThread) {
        WearPairingManager.removePairedDevices(wear.deviceID)
      }
    }
  }
}