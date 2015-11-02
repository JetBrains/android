/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification.errors;

import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenUrlHyperlink;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.decapitalize;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class JavaHeapSpaceErrorHandler extends AbstractSyncErrorHandler {
  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    int lineCount = message.size();
    String newMsg = null;

    String firstLine = message.get(0);
    if (firstLine.endsWith("Java heap space")) {
      newMsg = firstLine + ".";
    }
    else if (lineCount > 1 && firstLine.startsWith("Unable to start the daemon process")) {
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
        if (firstLine.endsWith(".")) {
          firstLine = firstLine.substring(0, firstLine.length() - 1);
        }
        if (!cause.endsWith(".")) {
          cause += ".";
        }
        newMsg = firstLine + ": " + decapitalize(cause);
      }
    }

    if (isNotEmpty(newMsg)) {
      List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
      newMsg += "\nPlease assign more memory to Gradle in the project's gradle.properties file.\n" +
                "For example, the following line, in the gradle.properties file, sets the maximum Java heap size to 1,024 MB:\n" +
                "<em>org.gradle.jvmargs=-Xmx1024m</em>";
      hyperlinks.add(new OpenUrlHyperlink("http://www.gradle.org/docs/current/userguide/build_environment.html",
                                          "Read Gradle's configuration guide"));
      hyperlinks.add(new OpenUrlHyperlink("http://docs.oracle.com/javase/7/docs/technotes/guides/vm/gc-ergonomics.html",
                                          "Read about Java's heap size"));
      updateNotification(notification, project, newMsg, hyperlinks);
      return true;
    }

    return false;
  }
}
