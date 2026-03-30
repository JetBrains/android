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
package com.android.tools.configurations;

import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.configuration.DeviceConfigHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.ide.common.resources.configuration.NightModeQualifier;
import com.android.ide.common.resources.configuration.UiModeQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.LayoutDirection;
import com.android.resources.NightMode;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.layoutlib.RenderingException;
import com.android.tools.layoutlib.LayoutlibContext;
import com.android.tools.sdk.AndroidPlatform;
import com.android.tools.sdk.LayoutlibFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Pure function engine that synchronizes disparate user configuration settings
 * into a compiled Android {@link FolderConfiguration}.
 */
public class FolderConfigSynchronizer {

  public static void sync(
    @NotNull FolderConfiguration fullConfig,
    @NotNull ConfigurationSettings settings,
    @NotNull FolderConfiguration editedConfig,
    @Nullable Device device,
    @Nullable State deviceState,
    @NotNull Locale locale,
    @Nullable IAndroidTarget target,
    @NotNull UiMode uiMode,
    @NotNull NightMode nightMode
  ) {
    if (device == null) {
      return;
    }

    if (deviceState == null) {
      deviceState = device.getDefaultState();
    }

    FolderConfiguration config = getFolderConfig(settings.getConfigModule(), deviceState, locale, target);
    fullConfig.set(config);

    fullConfig.setLocaleQualifier(locale.qualifier);
    LayoutDirectionQualifier layoutDirectionQualifier = editedConfig.getLayoutDirectionQualifier();

    if (layoutDirectionQualifier != null && layoutDirectionQualifier != layoutDirectionQualifier.getNullQualifier()) {
      fullConfig.setLayoutDirectionQualifier(layoutDirectionQualifier);
    } else if (!locale.hasLanguage()) {
      // Avoid getting the layout library if the locale doesn't have any language.
      fullConfig.setLayoutDirectionQualifier(new LayoutDirectionQualifier(LayoutDirection.LTR));
    } else {
      ConfigurationModelModule configModule = settings.getConfigModule();
      LayoutLibrary layoutLib = getLayoutLibrary(target, configModule.getAndroidPlatform(), configModule.getLayoutlibContext());
      if (layoutLib != null) {
        if (layoutLib.isRtl(locale.toLocaleId())) {
          fullConfig.setLayoutDirectionQualifier(new LayoutDirectionQualifier(LayoutDirection.RTL));
        } else {
          fullConfig.setLayoutDirectionQualifier(new LayoutDirectionQualifier(LayoutDirection.LTR));
        }
      }
    }

    fullConfig.setUiModeQualifier(new UiModeQualifier(uiMode));
    fullConfig.setNightModeQualifier(new NightModeQualifier(nightMode));

    if (target != null) {
      int apiLevel = target.getVersion().getFeatureLevel();
      fullConfig.setVersionQualifier(new VersionQualifier(apiLevel));
    }
  }

  @Nullable
  public static FolderConfiguration getFolderConfig(@NotNull ConfigurationModelModule module, @NotNull State state, @NotNull Locale locale,
                                                    @Nullable IAndroidTarget target) {
    FolderConfiguration currentConfig = DeviceConfigHelper.getFolderConfig(state);
    if (currentConfig != null) {
      if (locale.hasLanguage()) {
        currentConfig.setLocaleQualifier(locale.qualifier);
        LayoutLibrary layoutLib = getLayoutLibrary(target, module.getAndroidPlatform(), module.getLayoutlibContext());
        if (layoutLib != null) {
          if (layoutLib.isRtl(locale.toLocaleId())) {
            currentConfig.setLayoutDirectionQualifier(new LayoutDirectionQualifier(LayoutDirection.RTL));
          }
        }
      }
    }
    return currentConfig;
  }

  @Nullable
  private static LayoutLibrary getLayoutLibrary(
    @Nullable IAndroidTarget target, @Nullable AndroidPlatform platform, @NotNull LayoutlibContext context) {
    if (target == null || platform == null) {
      return null;
    }

    try {
      return LayoutlibFactory.getLayoutLibrary(target, platform, context);
    } catch (RenderingException ignored) {
      return null;
    }
  }
}