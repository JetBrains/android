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

@file:JvmName("EmulatorAdvFeatures")

package com.android.tools.idea.deviceManager.avdmanager.emulator

import com.android.SdkConstants
import com.android.repository.api.ProgressIndicator
import com.android.sdklib.FileOpFileWrapper
import com.android.sdklib.internal.project.ProjectProperties
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.utils.ILogger
import com.intellij.openapi.updateSettings.impl.ChannelStatus
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import java.io.File


/** A class to query Emulator advanced feature flags.  */
private const val FEATURE_FAST_BOOT = "FastSnapshotV1" // Emulator feature support
private const val FEATURE_SCREEN_RECORDING = "ScreenRecording" // Emulator screen recording feature
private const val FEATURE_VIRTUAL_SCENE = "VirtualScene" // Emulator virtual scene feature

private var emuAdvFeatures: Map<String, String>? = null // Advanced Emulator Features
private var emuAdvFeaturesCanary: Map<String, String>? = null // Advanced Emulator Features (canary)

fun getEmulatorFeaturesMap(
  featuresFile: String,
  sdkHandler: AndroidSdkHandler?,
  progressIndicator: ProgressIndicator,
  log: ILogger
): Map<String, String>? {
  sdkHandler ?: return null
  val emulatorPackage = sdkHandler.getLocalPackage(SdkConstants.FD_EMULATOR, progressIndicator) ?: return null
  val fop = sdkHandler.fileOp
  val emuAdvFeaturesFile = File(emulatorPackage.location, SdkConstants.FD_LIB + File.separator + featuresFile)
  if (fop.exists(emuAdvFeaturesFile)) {
    return ProjectProperties.parsePropertyFile(FileOpFileWrapper(emuAdvFeaturesFile, fop, false), log)
  }
  return null
}

/**
 * Indicates if the Emulator supports the requested advanced feature.
 *
 * @param theFeature The name of the requested feature.
 * @return true if the feature is "on" in the Emulator.
 */
fun emulatorSupportsFeature(
  theFeature: String,
  sdkHandler: AndroidSdkHandler?,
  progressIndicator: ProgressIndicator,
  log: ILogger
): Boolean {
  val channelStatus = UpdateSettings.getInstance().selectedChannelStatus
  var theMap: Map<String, String>? = when (channelStatus) {
    ChannelStatus.EAP -> {
      if (emuAdvFeaturesCanary == null) {
        emuAdvFeaturesCanary = getEmulatorFeaturesMap(SdkConstants.FN_ADVANCED_FEATURES_CANARY, sdkHandler, progressIndicator, log)
      }
      emuAdvFeaturesCanary
    }
    else -> {
      if (emuAdvFeatures == null) {
        emuAdvFeatures = getEmulatorFeaturesMap(SdkConstants.FN_ADVANCED_FEATURES, sdkHandler, progressIndicator, log)
      }
      emuAdvFeatures
    }
  }
  if (channelStatus != ChannelStatus.RELEASE && theMap == null) {
    // Fallback to stable advanced features file
    if (emuAdvFeatures == null) {
      emuAdvFeatures = getEmulatorFeaturesMap(SdkConstants.FN_ADVANCED_FEATURES, sdkHandler, progressIndicator, log)
    }
    theMap = emuAdvFeatures
  }
  return theMap != null && "on" == theMap[theFeature]
}

/**
 * Indicates if the Emulator supports the Fast Boot feature
 *
 * @return true if Fast Boot is supported
 */
fun emulatorSupportsFastBoot(sdkHandler: AndroidSdkHandler?, progressIndicator: ProgressIndicator, log: ILogger): Boolean =
  emulatorSupportsFeature(FEATURE_FAST_BOOT, sdkHandler, progressIndicator, log)

/**
 * Indicates if the Emulator supports screen recording feature
 *
 * @return true if screen recording is supported
 */
fun emulatorSupportsScreenRecording(sdkHandler: AndroidSdkHandler?, progressIndicator: ProgressIndicator, log: ILogger): Boolean =
  emulatorSupportsFeature(FEATURE_SCREEN_RECORDING, sdkHandler, progressIndicator, log)

/**
 * Indicates if the Emulator supports the virtual scene feature
 *
 * @return true if virtual scene is supported
 */
fun emulatorSupportsVirtualScene(sdkHandler: AndroidSdkHandler?, progressIndicator: ProgressIndicator, log: ILogger): Boolean =
  emulatorSupportsFeature(FEATURE_VIRTUAL_SCENE, sdkHandler, progressIndicator, log)
