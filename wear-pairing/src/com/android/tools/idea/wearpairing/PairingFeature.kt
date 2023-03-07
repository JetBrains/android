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

import com.android.ddmlib.IDevice

enum class PairingFeature(val minVersion: Int) {
  // Applicable to phone
  // Until this version becomes available publicly, it can be downloaded from
  // https://drive.google.com/file/d/1NoeiVCzLcDrdnkYmAilcWloR1RRleryt/view?usp=sharing&resourcekey=0-kD6zowQGavs9vipS_HOaHw
  COMPANION_EMULATOR_ACTIVITY(773385010),

  // Applicable to phone/watch
  REFRESH_EMULATOR_CONNECTION(213013000),

  // Applicable to phone
  MULTI_WATCH_SINGLE_PHONE_PAIRING(213204000),

  // Applicable to watch
  REVERSE_PORT_FORWARD(210915000),

  // Applicable to phone/watch
  GET_PAIRING_STATUS(214412000),

  // Application to phone.
  // This is to help the user with post-pairing actions needed on some older companions.
  COMPANION_SKIP_AND_FINISH_FIXED(773393865)
}

private const val GMSCORE_APP_ID = "com.google.android.gms"
private val VERSION_CODE_PATTERN = "versionCode=(\\d+)".toRegex()
private const val OEM_COMPANION_SETTING_KEY = "cw.oem_companion_package"
private const val OEM_COMPANION_PROPERTY_KEY = "ro.oem.companion_package"

const val PIXEL_COMPANION_APP_ID = "com.google.android.apps.wear.companion"
const val OEM_COMPANION_FALLBACK_APP_ID = "com.google.android.wearable.app"

private suspend fun IDevice.getAppVersionCode(appId: String) =
  VERSION_CODE_PATTERN.find(runShellCommand("dumpsys package $appId | grep versionCode | head -n1"))?.groupValues?.get(1)?.toInt()

suspend fun IDevice.getCompanionAppIdForWatch(): String {
  val settingValue = getSecureSetting(OEM_COMPANION_SETTING_KEY)
  if (settingValue.isNotBlank() && settingValue != "null") {
    return settingValue
  }
  val propertyValue = getSystemProperty(OEM_COMPANION_PROPERTY_KEY).await()
  if (!propertyValue.isNullOrBlank() && propertyValue != "null") {
    return propertyValue
  }
  return OEM_COMPANION_FALLBACK_APP_ID
}

suspend fun IDevice.hasPairingFeature(pairingFeature: PairingFeature, companionAppId: String? = null): Boolean {
  return when (pairingFeature) {
    PairingFeature.COMPANION_EMULATOR_ACTIVITY ->
      companionAppId != OEM_COMPANION_FALLBACK_APP_ID ||
        (getAppVersionCode(companionAppId) ?: 0) >= PairingFeature.COMPANION_EMULATOR_ACTIVITY.minVersion
    PairingFeature.REFRESH_EMULATOR_CONNECTION,
    PairingFeature.GET_PAIRING_STATUS,
    PairingFeature.MULTI_WATCH_SINGLE_PHONE_PAIRING,
    PairingFeature.REVERSE_PORT_FORWARD ->
      (getAppVersionCode(GMSCORE_APP_ID) ?: 0) >= pairingFeature.minVersion
    PairingFeature.COMPANION_SKIP_AND_FINISH_FIXED ->
      companionAppId != OEM_COMPANION_FALLBACK_APP_ID ||
      (getAppVersionCode(OEM_COMPANION_FALLBACK_APP_ID) ?: 0) >= PairingFeature.COMPANION_SKIP_AND_FINISH_FIXED.minVersion
  }
}

private suspend fun IDevice.getSecureSetting(settingKey: String) =
  runShellCommand("settings get secure $settingKey")