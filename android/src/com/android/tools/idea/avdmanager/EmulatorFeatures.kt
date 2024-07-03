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

@file:JvmName("EmulatorFeatures")

package com.android.tools.idea.avdmanager

import com.android.sdklib.internal.avd.EmulatorFeaturesChannel
import com.android.sdklib.internal.avd.EmulatorPackage
import com.android.tools.idea.log.LogWrapper
import com.intellij.openapi.updateSettings.impl.ChannelStatus
import com.intellij.openapi.updateSettings.impl.UpdateSettings

/**
 * Extension method for getting the emulator features for the current release channel.
 */
fun EmulatorPackage?.getEmulatorFeatures(): Set<String> =
  this?.getEmulatorFeatures(
    LogWrapper(EmulatorPackage::class.java),
    when (UpdateSettings.getInstance().selectedChannelStatus) {
      ChannelStatus.EAP -> EmulatorFeaturesChannel.CANARY
      else -> EmulatorFeaturesChannel.RELEASE
    },
  ) ?: emptySet()
