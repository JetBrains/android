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

import com.android.tools.idea.gradle.service.notification.hyperlink.OpenFileHyperlink;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;
import static com.android.SdkConstants.SDK_DIR_PROPERTY;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.google.common.io.Closeables.close;

public class MissingAndroidSdkErrorHandler extends AbstractSyncErrorHandler {
  @NonNls public static final String FIX_SDK_DIR_PROPERTY = "Please fix the 'sdk.dir' property in the local.properties file.";

  private static final Logger LOG = Logger.getInstance(MissingAndroidSdkErrorHandler.class);

  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String lastLine = message.get(message.size() - 1);
    if (FIX_SDK_DIR_PROPERTY.equals(lastLine)) {
      File file = new File(getBaseDirPath(project), FN_LOCAL_PROPERTIES);
      if (file.isFile()) {
        int lineNumber = 0;
        // If we got this far, local.properties exists.
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
          LOG.info("Unable to read file: " + file.getPath(), e);
        }
        finally {
          try {
            close(reader, true /* swallowIOException */);
          } catch (IOException ignored) {
            // Cannot happen
          }
        }
        updateNotification(notification, project, error.getMessage(), new OpenFileHyperlink(file.getPath(), lineNumber));
        return true;
      }
    }

    return false;
  }
}
