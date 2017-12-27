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

import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.OUT_OF_MEMORY;
import static com.intellij.openapi.util.text.StringUtil.*;

// See https://code.google.com/p/android/issues/detail?id=75060
public class JavaHeapSpaceErrorHandler extends BaseSyncErrorHandler {
  @Override
  @Nullable
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull Project project) {
    String text = rootCause.getMessage();
    List<String> message = getMessageLines(text);
    int lineCount = message.size();
    if (lineCount == 0) {
      return null;
    }
    String firstLine = message.get(0);
    String newMsg = null;

    if (isNotEmpty(firstLine) && firstLine.endsWith("Java heap space")) {
      newMsg = firstLine + ".";
    }
    else if (isNotEmpty(firstLine) && lineCount > 1 && firstLine.startsWith("Unable to start the daemon process")) {
      String cause = null;
      for (int i = 1; i < lineCount; i++) {
        String line = message.get(i);
        if ("Error occurred during initialization of VM".equals(line)) {
          // The cause of the error is in the next line.
          if (i < lineCount - 1) {
            cause = message.get(i + 1);
            break;
          }
        }
      }
      if (cause != null && cause.startsWith("Could not reserve enough space for object heap")) {
        firstLine = trimEnd(firstLine, ".");
        if (!cause.endsWith(".")) {
          cause += ".";
        }
        newMsg = firstLine + ": " + decapitalize(cause);
      }
    }
    if (isNotEmpty(newMsg)) {
      newMsg += "\nPlease assign more memory to Gradle in the project's gradle.properties file.\n" +
                "For example, the following line, in the gradle.properties file, sets the maximum Java heap size to 1,024 MB:\n" +
                "<em>org.gradle.jvmargs=-Xmx1024m</em>";
      if (rootCause instanceof OutOfMemoryError) {
        newMsg = "Out of memory: " + newMsg;
        updateUsageTracker(OUT_OF_MEMORY);
      }
      else {
        updateUsageTracker();
      }
      return newMsg;
    }
    return null;
  }

  @Override
  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    hyperlinks.add(new OpenUrlHyperlink("http://www.gradle.org/docs/current/userguide/build_environment.html",
                                        "Read Gradle's configuration guide"));
    hyperlinks.add(new OpenUrlHyperlink("http://docs.oracle.com/javase/7/docs/technotes/guides/vm/gc-ergonomics.html",
                                        "Read about Java's heap size"));
    return hyperlinks;
  }
}