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
package com.android.tools.idea.gradle.customizer.android;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.sdk.DefaultSdks;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Sets an Android SDK to a module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class AndroidSdkModuleCustomizer implements ModuleCustomizer<IdeaAndroidProject> {
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
   * @param androidProject the imported Android-Gradle project.
   */
  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject androidProject) {
    if (androidProject == null) {
      return;
    }
    File androidSdkHomePath = DefaultSdks.getDefaultAndroidHome();
    // Android SDK may be not configured in IntelliJ
    if (androidSdkHomePath == null) {
      return;
    }

    String androidHome = androidSdkHomePath.getPath();
    String compileTarget = androidProject.getDelegate().getCompileTarget();

    boolean sdkSet =
      AndroidSdkUtils.findAndSetSdk(module, compileTarget, androidHome, androidProject.getJavaLanguageLevel(), true);
    if (sdkSet) {
      String sdkPath = getSdkPath(module);
      assert sdkPath != null;
    }
    else {
      // This should never, ever happen.
      // We already either attempted to create an Android SDK (even prompted the user for its path) or downloaded the matching platform.
      String format = "Unable to set the Android SDK at '%1$s', with compile target '%2$s', to module '%3$s'";
      String msg = String.format(format, androidHome, compileTarget, module.getName());
      LOG.error(msg);
      msg += ".\n\nPlease set the Android SDK manually via the \"Project Structure\" dialog.";
      showErrorDialog(msg);
    }
  }

  private static void showErrorDialog(@NotNull String msg) {
    Messages.showErrorDialog(msg, "Android SDK Configuration");
  }

  @Nullable
  private static String getSdkPath(@NotNull Module module) {
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    return sdk != null ? sdk.getHomePath() : null;
  }
}
