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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/** Persistent Wear Pairing Assistant related settings. */
@State(
  name = "WearPairing",
  storages = [Storage(value = "wearPairing.xml", roamingType = RoamingType.DISABLED)],
)
class WearPairingSettings : PersistentStateComponent<WearPairingSettings> {

  /** List of all known paired devices. */
  internal var pairedDevicesState = mutableListOf<PairingDeviceState>()

  /** List of paired device connections. */
  internal var pairedDeviceConnectionsState = mutableListOf<PairingConnectionsState>()

  override fun getState(): WearPairingSettings {
    return this
  }

  override fun loadState(state: WearPairingSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    @JvmStatic
    fun getInstance(): WearPairingSettings {
      return ApplicationManager.getApplication().getService(WearPairingSettings::class.java)
    }
  }
}

/**
 * Pairing connection details. One phone can connect to one or more wear devices (if multiple wear
 * connections are supported).
 */
class PairingConnectionsState {
  var phoneId: String = "?"
  /** The list has at least one element (wear device ID) */
  var wearDeviceIds: MutableList<String> = mutableListOf()
}
