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
package com.android.tools.idea.rendering

import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.tools.idea.configurations.AdaptiveIconShape
import com.android.tools.idea.configurations.Configuration

/** [RenderConfiguration] wrapping [Configuration]. */
class StudioRenderConfiguration(private val configuration: Configuration) : RenderConfiguration {
  override val activity: String?
    get() = configuration.activity
  override val resourceResolver: ResourceResolver
    get() = configuration.resourceResolver
  override val target: IAndroidTarget?
    get() = configuration.target
  override val realTarget: IAndroidTarget?
    get() = configuration.realTarget
  override val device: Device?
    get() = configuration.device
  override val fullConfig: FolderConfiguration
    get() = configuration.fullConfig
  override val locale: Locale
    get() = configuration.locale
  override val adaptiveShape: AdaptiveIconShape
    get() = configuration.adaptiveShape
  override val useThemedIcon: Boolean
    get() = configuration.useThemedIcon
  override val wallpaperPath: String?
    get() = configuration.wallpaperPath
  override val fontScale: Float
    get() = configuration.fontScale
  override val uiModeFlagValue: Int
    get() = configuration.uiModeFlagValue
}