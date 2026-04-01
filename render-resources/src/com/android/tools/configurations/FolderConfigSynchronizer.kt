/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.configurations

import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.configuration.*
import com.android.resources.LayoutDirection
import com.android.resources.NightMode
import com.android.resources.UiMode
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.State
import com.android.tools.idea.layoutlib.LayoutLibrary
import com.android.tools.idea.layoutlib.RenderingException
import com.android.tools.layoutlib.LayoutlibContext
import com.android.tools.sdk.AndroidPlatform
import com.android.tools.sdk.getLayoutLibrary as fetchLayoutLibrary

/** Pure function engine that synchronizes disparate user configuration settings into a compiled Android [FolderConfiguration]. */
object FolderConfigSynchronizer {
  @JvmStatic
  fun sync(
    fullConfig: FolderConfiguration,
    settings: ConfigurationSettings,
    editedConfig: FolderConfiguration,
    device: Device?,
    deviceState: State?,
    locale: Locale,
    target: IAndroidTarget?,
    uiMode: UiMode,
    nightMode: NightMode,
  ) {
    if (device == null) return

    val effectiveState = deviceState ?: device.defaultState

    val config = getFolderConfig(settings.configModule, effectiveState, locale, target)
    if (config != null) {
      fullConfig.set(config)
    }

    fullConfig.localeQualifier = locale.qualifier
    val layoutDirectionQualifier = editedConfig.layoutDirectionQualifier

    if (layoutDirectionQualifier != null && layoutDirectionQualifier !== layoutDirectionQualifier.nullQualifier) {
      fullConfig.layoutDirectionQualifier = layoutDirectionQualifier
    } else if (!locale.hasLanguage()) {
      // Avoid getting the layout library if the locale doesn't have any language.
      fullConfig.layoutDirectionQualifier = LayoutDirectionQualifier(LayoutDirection.LTR)
    } else {
      val configModule = settings.configModule
      val layoutLib = getLayoutLibrary(target, configModule.androidPlatform, configModule.layoutlibContext)

      val isRtl = layoutLib?.isRtl(locale.toLocaleId()) == true
      fullConfig.layoutDirectionQualifier = LayoutDirectionQualifier(if (isRtl) LayoutDirection.RTL else LayoutDirection.LTR)
    }

    fullConfig.uiModeQualifier = UiModeQualifier(uiMode)
    fullConfig.nightModeQualifier = NightModeQualifier(nightMode)

    if (target != null) {
      fullConfig.versionQualifier = VersionQualifier(target.version.featureLevel)
    }
  }

  @JvmStatic
  fun getFolderConfig(module: ConfigurationModelModule, state: State, locale: Locale, target: IAndroidTarget?): FolderConfiguration? {
    val currentConfig = DeviceConfigHelper.getFolderConfig(state) ?: return null

    if (locale.hasLanguage()) {
      currentConfig.localeQualifier = locale.qualifier
      val layoutLib = getLayoutLibrary(target, module.androidPlatform, module.layoutlibContext)
      if (layoutLib?.isRtl(locale.toLocaleId()) == true) {
        currentConfig.layoutDirectionQualifier = LayoutDirectionQualifier(LayoutDirection.RTL)
      }
    }
    return currentConfig
  }

  private fun getLayoutLibrary(target: IAndroidTarget?, platform: AndroidPlatform?, context: LayoutlibContext): LayoutLibrary? {
    if (target == null || platform == null) return null

    return try {
      fetchLayoutLibrary(target, platform, context)
    } catch (ignored: RenderingException) {
      null
    }
  }
}
