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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.annotations.Nullable;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;
import static com.android.SdkConstants.SDK_DIR_PROPERTY;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.google.common.io.Closeables.close;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.SDK_NOT_FOUND;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class MissingAndroidSdkErrorHandler extends BaseSyncErrorHandler {
  private static final String FIX_SDK_DIR_PROPERTY = "Please fix the 'sdk.dir' property in the local.properties file.";
  private static final String SDK_DIR_PROPERTY_MISSING = "No sdk.dir property defined in local.properties file.";
  private static final Pattern SDK_NOT_FOUND_PATTERN = Pattern.compile("The SDK directory '(.*?)' does not exist.");

  @Override
  @Nullable
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull Project project) {
    String text = rootCause.getMessage();

    if (rootCause instanceof RuntimeException &&
        isNotEmpty(text) &&
        (text.equals(SDK_DIR_PROPERTY_MISSING) || SDK_NOT_FOUND_PATTERN.matcher(text).matches())) {
      updateUsageTracker(SDK_NOT_FOUND);
      File buildProperties = new File(getBaseDirPath(project), FN_LOCAL_PROPERTIES);
      if (buildProperties.isFile()) {
        text += EMPTY_LINE + FIX_SDK_DIR_PROPERTY;
        return text;
      }
    }
    return null;
  }

  @Override
  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @NotNull String text) {
    // If we got this far, local.properties exists.
    // Confirmed in findErrorMessage().
    File file = new File(getBaseDirPath(project), FN_LOCAL_PROPERTIES);
    int lineNumber = 0;
    BufferedReader reader = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      reader = new BufferedReader(new FileReader(file));
      int counter = 0;
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith(SDK_DIR_PROPERTY)) {
          lineNumber = counter;
          break;
        }
        counter++;
      }
    }
    catch (IOException e) {
      Logger.getInstance(getClass()).info("Unable to read file: " + file.getPath(), e);
    }
    finally {
      try {
        close(reader, true /* swallowIOException */);
      }
      catch (IOException ignored) {
        // Cannot happen
      }
    }

    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    hyperlinks.add(new OpenFileHyperlink(file.getPath(), lineNumber));
    return hyperlinks;
  }
}