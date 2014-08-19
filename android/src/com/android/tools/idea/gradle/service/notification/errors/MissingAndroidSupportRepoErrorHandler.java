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
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenAndroidSdkManagerHyperlink;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.project.ProjectImportErrorHandler.INSTALL_ANDROID_SUPPORT_REPO;

public class MissingAndroidSupportRepoErrorHandler extends AbstractSyncErrorHandler {
  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String lastLine = message.get(message.size() - 1);

    if (lastLine.contains(INSTALL_ANDROID_SUPPORT_REPO)) {
      List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
      NotificationHyperlink[] hyperlinks = EMPTY;
      if (!facets.isEmpty()) {
        // We can only open SDK manager if the project has an Android facet. Android facet has a reference to the Android SDK manager.
        hyperlinks = new NotificationHyperlink[] {new OpenAndroidSdkManagerHyperlink()};
      }
      updateNotification(notification, project, error.getMessage(), hyperlinks);
      return true;
    }
    return false;
  }
}
