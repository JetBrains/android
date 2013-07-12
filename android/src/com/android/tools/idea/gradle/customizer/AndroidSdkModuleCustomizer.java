/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Sets an Android SDK to a module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class AndroidSdkModuleCustomizer implements ModuleCustomizer {
  private static final Logger LOG = Logger.getInstance(AndroidSdkModuleCustomizer.class);

  /**
   * Sets an Android SDK to the given module only if:
   * <ol>
   * <li>the given module was created by importing an {@code AndroidProject}</li>
   * <li>there is a matching Android SDK already defined in IDEA</li>
   * </ol>
   *
   * @param module             module to customize.
   * @param project            project that owns the module to customize.
   * @param ideaAndroidProject the imported Android-Gradle project.
   */
  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject ideaAndroidProject) {
    if (ideaAndroidProject == null) {
      return;
    }
    LocalProperties properties;
    try {
      properties = new LocalProperties(project);
    }
    catch (IOException e) {
      String msg = String.format("Unable to read local.properties file in project '%1$s'", project.getBasePath());
      LOG.error(msg, e);
      showErrorDialog(msg);
      return;
    }
    String androidSdkPath = properties.getAndroidSdkPath();
    String compileTarget = ideaAndroidProject.getDelegate().getCompileTarget();
    boolean sdkSet = AndroidSdkUtils.findAndSetSdk(module, compileTarget, androidSdkPath, true);
    if (sdkSet) {
      if (!Strings.isNullOrEmpty(androidSdkPath)) {
        // Check that the SDK set is the same as the one in the local.properties.
        String sdkPath = getSdkPath(module);
        assert sdkPath != null;
        sdkPath = FileUtil.toSystemIndependentName(sdkPath);
        androidSdkPath = FileUtil.toSystemIndependentName(sdkPath);
        if (!FileUtil.pathsEqual(sdkPath, androidSdkPath)) {
          // This is *extremely unlikely* to happen. There is no way that project import can happen with an old SDK.
          // Changing the SDK path in local.properties to match the one set, so Studio and command line builds are consistent.
          try {
            properties.setAndroidSdkPath(androidSdkPath);
          }
          catch (IOException e) {
            // An unlikely thing to happen on top of another unlikely thing to happen.
            String msg = String.format("Unable to set SDK path in local.properties file");
            LOG.error(msg, e);
            showErrorDialog(msg);
          }
        }
      }
    }
    else {
      // This should never, ever happen.
      // We already either attempted to create an Android SDK (even prompted the user for its path) or downloaded the matching platform.
      String msg;
      if (androidSdkPath != null) {
        String format = "Unable to set the Android SDK at '%1$s', with compile target '%2$s', to module '%3$s'";
        msg = String.format(format, androidSdkPath, compileTarget, module.getName());
      }
      else {
        String format = "Unable to set an Android SDK, with compile target '%1$s', to module '%2$s'";
        msg = String.format(format, compileTarget, module.getName());
      }
      LOG.error(msg);
      msg += ".\n\nPlease set the Android SDK manually via the \"Project Settings\" dialog.";
      showErrorDialog(msg);
    }
  }

  private static void showErrorDialog(@NotNull String msg) {
    Messages.showErrorDialog(msg, "Android SDK Configuration");
  }

  @Nullable
  private static String getSdkPath(@NotNull Module module) {
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk != null) {
      return sdk.getHomePath();
    }
    return null;
  }
}
