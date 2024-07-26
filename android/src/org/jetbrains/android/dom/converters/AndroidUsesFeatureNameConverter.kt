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
package org.jetbrains.android.dom.converters

import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.ResolvingConverter

/** Converter that provides code completion for <uses-feature> 'android:name' attribute. */
class AndroidUsesFeatureNameConverter : ResolvingConverter<String>() {

  // Note: From https://developer.android.com/reference/android/content/pm/PackageManager
  companion object {
    private val usesFeature by lazy {
      mutableListOf(
        "android.hardware.audio.low_latency",
        "android.hardware.audio.output",
        "android.hardware.audio.pro",
        "android.hardware.bluetooth",
        "android.hardware.bluetooth_le",
        "android.hardware.camera",
        "android.hardware.camera.any",
        "android.hardware.camera.ar",
        "android.hardware.camera.autofocus",
        "android.hardware.camera.capability.manual_post_processing",
        "android.hardware.camera.capability.manual_sensor",
        "android.hardware.camera.capability.raw",
        "android.hardware.camera.external",
        "android.hardware.camera.flash",
        "android.hardware.camera.front",
        "android.hardware.camera.level.full",
        "android.hardware.consumerir",
        "android.hardware.ethernet",
        "android.hardware.faketouch",
        "android.hardware.faketouch.multitouch.distinct",
        "android.hardware.faketouch.multitouch.jazzhand",
        "android.hardware.fingerprint",
        "android.hardware.gamepad",
        "android.hardware.location",
        "android.hardware.location.gps",
        "android.hardware.location.network",
        "android.hardware.microphone",
        "android.hardware.nfc",
        "android.hardware.nfc.hce",
        "android.hardware.nfc.hcef",
        "android.hardware.opengles.aep",
        "android.hardware.ram.low",
        "android.hardware.ram.normal",
        "android.hardware.screen.landscape",
        "android.hardware.screen.portrait",
        "android.hardware.sensor.accelerometer",
        "android.hardware.sensor.ambient_temperature",
        "android.hardware.sensor.barometer",
        "android.hardware.sensor.compass",
        "android.hardware.sensor.gyroscope",
        "android.hardware.sensor.heartrate",
        "android.hardware.sensor.heartrate.ecg",
        "android.hardware.sensor.hifi_sensors",
        "android.hardware.sensor.light",
        "android.hardware.sensor.proximity",
        "android.hardware.sensor.relative_humidity",
        "android.hardware.sensor.stepcounter",
        "android.hardware.sensor.stepdetector",
        "android.hardware.strongbox_keystore",
        "android.hardware.telephony",
        "android.hardware.telephony.cdma",
        "android.hardware.telephony.euicc",
        "android.hardware.telephony.gsm",
        "android.hardware.telephony.mbms",
        "android.hardware.touchscreen",
        "android.hardware.touchscreen.multitouch",
        "android.hardware.touchscreen.multitouch.distinct",
        "android.hardware.touchscreen.multitouch.jazzhand",
        "android.hardware.type.automotive",
        "android.hardware.type.embedded",
        "android.hardware.type.pc",
        "android.hardware.type.television",
        "android.hardware.type.watch",
        "android.hardware.usb.accessory",
        "android.hardware.usb.host",
        "android.hardware.vr.headtracking",
        "android.hardware.vr.high_performance",
        "android.hardware.vulkan.compute",
        "android.hardware.vulkan.level",
        "android.hardware.vulkan.version",
        "android.hardware.wifi",
        "android.hardware.wifi.aware",
        "android.hardware.wifi.direct",
        "android.hardware.wifi.passpoint",
        "android.hardware.wifi.rtt",
        "android.software.activities_on_secondary_displays",
        "android.software.app_widgets",
        "android.software.autofill",
        "android.software.backup",
        "android.software.cant_save_state",
        "android.software.companion_device_setup",
        "android.software.connectionservice",
        "android.software.device_admin",
        "android.software.freeform_window_management",
        "android.software.home_screen",
        "android.software.input_methods",
        "android.software.leanback",
        "android.software.leanback_only",
        "android.software.live_tv",
        "android.software.live_wallpaper",
        "android.software.managed_users",
        "android.software.midi",
        "android.software.picture_in_picture",
        "android.software.print",
        "android.software.securely_removes_users",
        "android.software.sip",
        "android.software.sip.voip",
        "android.software.verified_boot",
        "android.software.vr.mode",
        "android.software.webview",
      )
    }
  }

  override fun fromString(s: String?, context: ConvertContext) = s

  override fun toString(t: String?, context: ConvertContext) = t

  override fun getVariants(context: ConvertContext) = usesFeature
}
