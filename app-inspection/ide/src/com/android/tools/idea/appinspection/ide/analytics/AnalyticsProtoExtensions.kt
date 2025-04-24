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
package com.android.tools.idea.appinspection.ide.analytics

import com.android.ide.common.util.isMdnsAutoConnectTls
import com.android.ide.common.util.isMdnsAutoConnectUnencrypted
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.wireless.android.sdk.stats.DeviceInfo

fun DeviceDescriptor.toDeviceInfo(): DeviceInfo {
  return DeviceInfo.newBuilder()
    .setAnonymizedSerialNumber(AnonymizerUtil.anonymizeUtf8(this.serial))
    .setBuildVersionRelease(this.version)
    .setBuildApiLevelFull(AndroidVersion(apiLevel, codename, null, true).apiStringWithoutExtension)
    .setManufacturer(this.manufacturer)
    .setDeviceType(
      if (this.isEmulator) DeviceInfo.DeviceType.LOCAL_EMULATOR
      else DeviceInfo.DeviceType.LOCAL_PHYSICAL
    )
    .setMdnsConnectionType(
      when {
        isMdnsAutoConnectUnencrypted(this.serial) ->
          DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_UNENCRYPTED
        isMdnsAutoConnectTls(this.serial) -> DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_TLS
        else -> DeviceInfo.MdnsConnectionType.MDNS_NONE
      }
    )
    .setModel(this.model)
    .build()
}
