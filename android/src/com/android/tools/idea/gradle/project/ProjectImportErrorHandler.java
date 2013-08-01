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
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.util.Pair;
import org.gradle.api.internal.LocationAwareException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides better error messages for project import failures.
 */
class ProjectImportErrorHandler {
  @NotNull
  RuntimeException getUserFriendlyError(@NotNull Throwable error, @Nullable String buildFilePath) {
    Pair<Throwable, String> rootCauseAndLocation = getRootCauseAndLocation(error);

    Throwable rootCause = rootCauseAndLocation.getFirst();

    String location = rootCauseAndLocation.getSecond();
    if (location == null && !Strings.isNullOrEmpty(buildFilePath)) {
      location = String.format("Build file: '%1$s'", buildFilePath);
    }

    if (isOldGradleVersion(rootCause)) {
      String newMsg = String.format("You are using an old, unsupported version of Gradle. Please use version %1$s or greater.",
                                    BasePlugin.GRADLE_MIN_VERSION);
      return createUserFriendlyError(newMsg, location);
    }

    if (rootCause instanceof RuntimeException) {
      String msg = rootCause.getMessage();

      // With this condition we cover 2 similar messages about the same problem.
      if (msg != null && msg.contains("Could not find") && msg.contains("com.android.support:support")) {
        // We keep the original error message and we append a hint about how to fix the missing dependency.
        String newMsg = msg + "\n\nPlease install the Android Support Repository from the Android SDK Manager.";
        return createUserFriendlyError(newMsg, location);
      }

      if (location != null) {
        return createUserFriendlyError(rootCause.getMessage(), location);
      }
      return (RuntimeException)rootCause;
    }

    return createUserFriendlyError(rootCause.getMessage(), location);
  }

  @NotNull
  private static Pair<Throwable, String> getRootCauseAndLocation(@NotNull Throwable error) {
    Throwable rootCause = error;
    String location = null;
    while (true) {
      if (location == null) {
        location = getLocationFrom(rootCause);
      }
      if (rootCause.getCause() == null) {
        break;
      }
      rootCause = rootCause.getCause();
    }
    //noinspection ConstantConditions
    return Pair.create(rootCause, location);
  }

  @Nullable
  private static String getLocationFrom(@NotNull Throwable error) {
    String errorToString = error.toString();
    if (errorToString != null && errorToString.startsWith(LocationAwareException.class.getName())) {
      // LocationAwareException is never passed, but converted into a PlaceholderException that has the toString value of the original
      // LocationAwareException.
      String location = error.getMessage();
      if (location != null && location.startsWith("Build file '")) {
        // Only the first line contains the location of the error. Discard the rest.
        Iterable<String> lines = Splitter.on('\n').split(location);
        return lines.iterator().next();
      }
    }
    return null;
  }

  private static boolean isOldGradleVersion(@NotNull Throwable error) {
    if (error instanceof ClassNotFoundException) {
      String msg = error.getMessage();
      if (msg != null && msg.contains(ToolingModelBuilderRegistry.class.getName())) {
        return true;
      }
    }
    String errorToString = error.toString();
    return errorToString != null && errorToString.startsWith("org.gradle.api.internal.MissingMethodException");
  }

  @NotNull
  private static RuntimeException createUserFriendlyError(@NotNull String msg, @Nullable String location) {
    String newMsg = msg;
    if (!newMsg.isEmpty() && Character.isLowerCase(newMsg.charAt(0))) {
      // Message starts with lower case letter. Sentences should start with uppercase.
      newMsg = "Cause: " + newMsg;
    }

    if (!Strings.isNullOrEmpty(location)) {
      StringBuilder msgBuilder = new StringBuilder();
      msgBuilder.append(newMsg).append("\n\n").append(location);
      newMsg = msgBuilder.toString();
    }
    return new ExternalSystemException(newMsg);
  }
}
