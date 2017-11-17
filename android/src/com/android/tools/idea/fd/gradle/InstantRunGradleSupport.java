/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.fd.gradle;

import com.android.builder.model.InstantRun;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;

/** {@link InstantRunGradleSupport} indicates whether the current version of the gradle plugin supports an instant run build. */
public enum InstantRunGradleSupport {
  SUPPORTED,

  NO_GRADLE_MODEL,
  GRADLE_PLUGIN_TOO_OLD(AndroidBundle.message("instant.run.notification.ir.disabled.plugin.too.old",
                                              AndroidPluginGeneration.ORIGINAL.getLatestKnownVersion())),
  VARIANT_DOES_NOT_SUPPORT_INSTANT_RUN(AndroidBundle.message("instant.run.notification.ir.disabled.for.current.variant")),
  LEGACY_MULTIDEX_REQUIRES_ART(AndroidBundle.message("instant.run.notification.ir.disabled.multidex.requires.21")),

  CANNOT_BUILD_FOR_MULTIPLE_DEVICES(AndroidBundle.message("instant.run.notification.ir.disabled.multiple.devices")),
  CANNOT_DEPLOY_FOR_SECONDARY_USER(AndroidBundle.message("instant.run.notification.ir.disabled.secondary.user")),
  TARGET_PLATFORM_NOT_INSTALLED(AndroidBundle.message("instant.run.notification.ir.disabled.target.platform.missing")),
  API_TOO_LOW_FOR_INSTANT_RUN(AndroidBundle.message("instant.run.notification.ir.disabled.api.less.than.21")),
  INSTANT_APP, // for now just disable silently
  HAS_CODE_FALSE(AndroidBundle.message("instant.run.notification.ir.disabled.app.has.code.false")),
  DISABLE_INSTANT_RUN_WHEN_PROFILING(AndroidBundle.message("instant.run.notification.ir.disabled.profiling")),

  // Gradle 2.2.0-alpha6 and above can provide more fine grained info when IR is disabled.
  // The following status messages correspond to the values returned by the gradle plugin.
  NON_DEBUG_VARIANT(AndroidBundle.message("instant.run.notification.ir.disabled.non.debug.variant")),
  VARIANT_USED_FOR_TESTING(AndroidBundle.message("instant.run.notification.ir.disabled.testing.variant")),
  USES_JACK(AndroidBundle.message("instant.run.notification.ir.disabled.jack")),
  USES_EXTERNAL_NATIVE_BUILD(AndroidBundle.message("instant.run.notification.ir.disabled.external.native.build")),
  USES_EXPERIMENTAL_PLUGIN(AndroidBundle.message("instant.run.notification.ir.disabled.experimental.plugin")),
  UNKNOWN_REASON(AndroidBundle.message("instant.run.notification.ir.disabled.unknown.reason"));

  private final String myUserNotification;

  InstantRunGradleSupport() {
    this(null);
  }

  InstantRunGradleSupport(@Nullable String userNotification) {
    myUserNotification = userNotification;
  }

  @Nullable
  public String getUserNotification() {
    return myUserNotification;
  }

  public static InstantRunGradleSupport fromModel(@NotNull AndroidModuleModel model) throws UnsupportedOperationException {
    // Regardless of whether Gradle supports IR or not, we need to make sure it is of a minimum version.
    boolean modelSupportsInstantRun = InstantRunGradleUtils.modelSupportsInstantRun(model);
    if (!modelSupportsInstantRun) {
      return GRADLE_PLUGIN_TOO_OLD;
    }

    if (model.getAndroidProject().getProjectType() == PROJECT_TYPE_INSTANTAPP) {
      return INSTANT_APP;
    }

    int modelStatus;
    try {
      modelStatus = model.getSelectedVariant().getMainArtifact().getInstantRun().getSupportStatus();
    } catch (Throwable e) {
      throw new UnsupportedOperationException(String.format("This gradle model (%1$s) does not support querying for instant run status",
                                                            model.getModelVersion()));
    }

    switch (modelStatus) {
      case InstantRun.STATUS_NOT_SUPPORTED_FOR_NON_DEBUG_VARIANT:
        return NON_DEBUG_VARIANT;
      case InstantRun.STATUS_NOT_SUPPORTED_VARIANT_USED_FOR_TESTING:
        return VARIANT_USED_FOR_TESTING;
      case InstantRun.STATUS_NOT_SUPPORTED_FOR_JACK:
        return USES_JACK;
      case InstantRun.STATUS_NOT_SUPPORTED_FOR_EXTERNAL_NATIVE_BUILD:
        return USES_EXTERNAL_NATIVE_BUILD;
      case InstantRun.STATUS_NOT_SUPPORTED_FOR_EXPERIMENTAL_PLUGIN:
        return USES_EXPERIMENTAL_PLUGIN;
      case InstantRun.STATUS_SUPPORTED:
        return SUPPORTED;
      default:
        // In case Gradle adds a new option, we need to update the IDE..
        Logger.getInstance(InstantRunGradleSupport.class).error("Unknown instant run support status reported: " + modelStatus);
        return UNKNOWN_REASON;
    }
  }
}
