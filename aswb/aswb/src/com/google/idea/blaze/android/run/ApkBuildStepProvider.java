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
package com.google.idea.blaze.android.run;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.util.BuildSystemExtensionPoint;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

/** A service that provides the build step capable of building APKs suitable for deployment. */
public interface ApkBuildStepProvider extends BuildSystemExtensionPoint {
  ExtensionPointName<ApkBuildStepProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.android.ApkBuildStepProvider");

  static ApkBuildStepProvider getInstance(BuildSystemName buildSystemName) {
    return BuildSystemExtensionPoint.getInstance(EP_NAME, buildSystemName);
  }

  /** Returns a build step that can build artifacts required for an {@code android_binary}. */
  ApkBuildStep getBinaryBuildStep(
      Project project,
      boolean useMobileInstall,
      boolean nativeDebuggingEnabled,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags,
      String launchId);

  /**
   * Returns a build step that can build artifacts required for an {@code
   * android_instrumentation_test}.
   */
  ApkBuildStep getAitBuildStep(
      Project project,
      boolean useMobileInstall,
      boolean nativeDebuggingEnabled,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags,
      String launchId)
      throws ExecutionException;
}
