/*
 * Copyright (C) 2014 The Android Open Source Project
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

/**
 * The goal is to keep all defaults in one place so it is easier to update them as needed.
 */
@file:JvmName("FirstRunWizardDefaults")

package com.android.tools.idea.welcome.install

import com.android.sdklib.devices.Storage
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.io.File
import kotlin.math.min

/**
 * Returns initial SDK location. That will be the SDK location from the installer handoff file in the handoff case,
 * SDK location location from the preference if set or platform-dependant default path.
 */
fun getInitialSdkLocation(mode: FirstRunWizardMode): File =
  mode.sdkLocation
  ?: AndroidSdks.getInstance().allAndroidSdks.firstOrNull()?.homeDirectory?.toIoFile()
  ?: AndroidSdkUtils.getAndroidSdkPathOrDefault()
