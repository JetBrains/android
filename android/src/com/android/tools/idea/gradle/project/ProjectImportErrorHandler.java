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
package com.android.tools.idea.gradle.project;

import com.android.build.gradle.BasePlugin;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.util.ExceptionUtil;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Provides better error messages for project import failures.
 */
class ProjectImportErrorHandler {

  @NotNull
  RuntimeException getUserFriendlyError(@NotNull Throwable error) {
    Throwable rootCause = ExceptionUtil.getRootCause(error);
    if (rootCause instanceof ClassNotFoundException) {
      String msg = rootCause.getMessage();
      if (msg != null && msg.contains(ToolingModelBuilderRegistry.class.getName())) {
        // Using an old version of Gradle.
        String newMsg = String.format("You are using an old, unsupported version of Gradle. Please use version %1$s or greater.",
                                      BasePlugin.GRADLE_MIN_VERSION);
        return new ExternalSystemException(newMsg);
      }
    }

    if (rootCause instanceof IllegalStateException) {
      String msg = rootCause.getMessage();
      if (msg != null && msg.startsWith("failed to find target")) {
        // The error message does not help at all. Try to get the path of the build.gradle file where the error is.
        StringBuilder msgBuilder = new StringBuilder();
        String locationMsg = error.getCause().getMessage();
        // If there is a location in the build.gradle file, it is in the immediate cause of the Throwable passed to this method.
        if (locationMsg != null && locationMsg.startsWith("Build file '")) {
          msgBuilder.append(locationMsg).append("\n\n");
        }
        msgBuilder.append("Cause: ").append(msg);
        return new ExternalSystemException(msgBuilder.toString());
      }
    }

    if (rootCause instanceof RuntimeException) {
      String msg = rootCause.getMessage();

      // With this condition we cover 2 similar messages about the same problem.
      if (msg != null && msg.contains("Could not find") && msg.contains("com.android.support:support")) {
        // We keep the original error message and we append a hint about how to fix the missing dependency.
        String newMsg = msg + "\n\nPlease install the Android Support Repository from the Android SDK Manager.";
        return new ExternalSystemException(newMsg);
      }

      return (RuntimeException)rootCause;
    }
    return new ExternalSystemException(rootCause);
  }
}
