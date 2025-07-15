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
package com.android.tools.idea.adb.wireless.v2.ui

import com.android.adblib.MdnsTlsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class WifiPairableDeviceModel {
  private val _devices = MutableStateFlow<List<MdnsTlsService>>(emptyList())
  val devices: StateFlow<List<MdnsTlsService>> = _devices.asStateFlow()

  fun addMdnsService(newMdnsTlsService: MdnsTlsService) {
    _devices.update { it + newMdnsTlsService }
  }

  fun removeMdnsService(adbServiceName: String) {
    _devices.update {
      it.filterNot { service -> service.service.serviceInstanceName.instance == adbServiceName }
    }
  }
}
