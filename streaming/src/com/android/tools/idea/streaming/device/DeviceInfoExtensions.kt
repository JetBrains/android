/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.device

import com.android.ide.common.util.isMdnsAutoConnectTls
import com.android.ide.common.util.isMdnsAutoConnectUnencrypted
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.tools.analytics.CommonMetricsData
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DeviceInfo.DeviceType
import com.google.wireless.android.sdk.stats.DeviceInfo.DeviceType.LOCAL_EMULATOR
import com.google.wireless.android.sdk.stats.DeviceInfo.DeviceType.LOCAL_PHYSICAL
import com.google.wireless.android.sdk.stats.DeviceInfo.DeviceType.UNKNOWN_DEVICE_TYPE
import com.google.wireless.android.sdk.stats.DeviceInfo.MdnsConnectionType
import com.google.wireless.android.sdk.stats.DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_TLS
import com.google.wireless.android.sdk.stats.DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_UNENCRYPTED
import com.google.wireless.android.sdk.stats.DeviceInfo.MdnsConnectionType.MDNS_NONE

fun DeviceInfo.Builder.fillFrom(deviceProperties: DeviceProperties) {
  deviceProperties.manufacturer?.let { manufacturer = it }
  deviceProperties.manufacturer?.let { model = it }
  deviceProperties.androidRelease?.let { buildVersionRelease = it }
  deviceProperties.androidVersion?.let { buildApiLevelFull = it.apiString }
  deviceProperties.abi?.let { cpuAbi = CommonMetricsData.applicationBinaryInterfaceFromString(it.toString()) }
  deviceType = deviceProperties.getDeviceInfoType()
}

fun DeviceInfo.Builder.fillMdnsConnectionType(deviceSerialNumber: String) {
  mdnsConnectionType = getMdnsConnectionType(deviceSerialNumber)
}

private fun DeviceProperties.getDeviceInfoType(): DeviceType {
  return when (isVirtual) {
    true -> LOCAL_EMULATOR
    false -> LOCAL_PHYSICAL
    null -> UNKNOWN_DEVICE_TYPE
  }
}

private fun getMdnsConnectionType(deviceSerialNumber: String): MdnsConnectionType {
  return when {
    isMdnsAutoConnectUnencrypted(deviceSerialNumber) -> MDNS_AUTO_CONNECT_UNENCRYPTED
    isMdnsAutoConnectTls(deviceSerialNumber) -> MDNS_AUTO_CONNECT_TLS
    else -> MDNS_NONE
  }
}
