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
package com.android.tools.idea.adblib

import com.android.adblib.AdbUsageTracker
import com.android.ide.common.util.isMdnsAutoConnectTls
import com.android.ide.common.util.isMdnsAutoConnectUnencrypted
import com.android.tools.analytics.CommonMetricsData
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.AnonymizerUtil
//import com.android.tools.idea.stats.AnonymizerUtil
import com.google.wireless.android.sdk.stats.AdbUsageEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.intellij.openapi.diagnostic.thisLogger

class AndroidAdbUsageTracker : AdbUsageTracker {

  private val log = thisLogger()

  override fun logUsage(event: AdbUsageTracker.Event) {
    //val studioEvent =
    //  try {
    //    event.toAndroidStudioEvent()
    //  } catch (t: Throwable) {
    //    log.warn("Could not build `AndroidStudioEvent` from `AdbUsageTracker.Event`", t)
    //    return
    //  }
    //
    //UsageTracker.log(studioEvent)
  }

  //private fun AdbUsageTracker.Event.toAndroidStudioEvent(): AndroidStudioEvent.Builder {
  //  val androidStudioEvent =
  //    AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.ADB_USAGE_EVENT)
  //
  //  deviceInfo?.toProto()?.let { androidStudioEvent.setDeviceInfo(it) }
  //
  //  jdwpProcessPropertiesCollector?.let {
  //    androidStudioEvent.adbUsageEventBuilder.processPropertiesEventBuilder
  //      .setSuccess(it.isSuccess)
  //      .setPreviouslyFailedCount(it.previouslyFailedCount)
  //
  //    val failureType = it.failureType?.toProtoEnum()
  //    if (failureType != null) {
  //      androidStudioEvent.adbUsageEventBuilder.processPropertiesEventBuilder.failureType =
  //        failureType
  //    }
  //    val previousFailureType = it.previousFailureType?.toProtoEnum()
  //    if (previousFailureType != null) {
  //      androidStudioEvent.adbUsageEventBuilder.processPropertiesEventBuilder.previousFailureType =
  //        previousFailureType
  //    }
  //  }
  //  return androidStudioEvent
  //}

  private fun AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.toProtoEnum():
    AdbUsageEvent.JdwpProcessPropertiesCollectorEvent.FailureType {
    return when (this) {
      AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.NO_RESPONSE ->
        AdbUsageEvent.JdwpProcessPropertiesCollectorEvent.FailureType.NO_RESPONSE
      AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.CLOSED_CHANNEL_EXCEPTION ->
        AdbUsageEvent.JdwpProcessPropertiesCollectorEvent.FailureType.CLOSED_CHANNEL_EXCEPTION
      AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.CONNECTION_CLOSED_ERROR ->
        AdbUsageEvent.JdwpProcessPropertiesCollectorEvent.FailureType.CONNECTION_CLOSED_ERROR
      AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.IO_EXCEPTION ->
        AdbUsageEvent.JdwpProcessPropertiesCollectorEvent.FailureType.IO_EXCEPTION
      AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.OTHER_ERROR ->
        AdbUsageEvent.JdwpProcessPropertiesCollectorEvent.FailureType.OTHER_ERROR
    }
  }

  private fun AdbUsageTracker.DeviceInfo.toProto(): DeviceInfo {
    val mdnsConnectionType =
      when {
        isMdnsAutoConnectUnencrypted(serialNumber) ->
          DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_UNENCRYPTED
        isMdnsAutoConnectTls(serialNumber) -> DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_TLS
        else -> DeviceInfo.MdnsConnectionType.MDNS_NONE
      }

    // TODO: Fix classification of `CLOUD_EMULATOR` and `CLOUD_PHYSICAL` device types
    val deviceType =
      if (LOCAL_EMULATOR_REGEX.matches(serialNumber)) DeviceInfo.DeviceType.LOCAL_EMULATOR
      else DeviceInfo.DeviceType.LOCAL_PHYSICAL

    return DeviceInfo.newBuilder()
      .setAnonymizedSerialNumber(AnonymizerUtil.anonymizeUtf8(serialNumber))
      .setBuildTags(buildTags)
      .setBuildType(buildType)
      .setBuildVersionRelease(buildVersionRelease)
      .setBuildApiLevelFull(buildApiLevelFull)
      .setCpuAbi(CommonMetricsData.applicationBinaryInterfaceFromString(cpuAbi))
      .setManufacturer(manufacturer)
      .setDeviceType(deviceType)
      .setMdnsConnectionType(mdnsConnectionType)
      .addAllCharacteristics(allCharacteristics)
      .setModel(model)
      .build()
  }

  private val LOCAL_EMULATOR_REGEX = "emulator-(\\d+)".toRegex()
}
