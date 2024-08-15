/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.python.run;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Extension point for adding blaze flags when debugging python targets. */
public interface BlazePyDebugHelper {

  ExtensionPointName<BlazePyDebugHelper> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BlazePyDebugFlagsProvider");

  static ImmutableList<String> getAllBlazeDebugFlags(Project project, TargetExpression target) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (BlazePyDebugHelper provider : EP_NAME.getExtensions()) {
      builder.addAll(provider.getBlazeDebugFlags(project, target));
    }
    return builder.build();
  }

  static void doBlazeDebugCommandlinePatching(
      Project project, TargetExpression target, GeneralCommandLine commandLine) {
    for (BlazePyDebugHelper provider : EP_NAME.getExtensions()) {
      provider.patchBlazeDebugCommandline(project, target, commandLine);
    }
  }

  @Nullable
  static String validateDebugTarget(Project project, @Nullable TargetExpression target) {
    for (BlazePyDebugHelper provider : EP_NAME.getExtensions()) {
      String error = provider.validatePyDebugTarget(project, target);
      if (error != null) {
        return error;
      }
    }
    return null;
  }

  static void attachProcessListeners(TargetExpression target, ProcessHandler process) {
    for (BlazePyDebugHelper provider : EP_NAME.getExtensions()) {
      provider.attachListeners(target, process);
    }
  }

  default ImmutableList<String> getBlazeDebugFlags(Project project, TargetExpression target) {
    return ImmutableList.of();
  }

  default void patchBlazeDebugCommandline(
      Project project, TargetExpression target, GeneralCommandLine commandLine) {}

  /**
   * Attempts to check whether the given target can be debugged by the Blaze plugin. If there's a
   * known problem, returns an error message with the details.
   */
  @Nullable
  default String validatePyDebugTarget(Project project, @Nullable TargetExpression target) {
    return null;
  }

  default void attachListeners(TargetExpression target, ProcessHandler process) {}
}
