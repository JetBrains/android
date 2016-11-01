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
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.UpgradeAppenginePluginVersionHyperlink;
import com.android.tools.idea.gradle.service.repo.ExternalRepository;
import com.android.tools.idea.gradle.util.BuildFileProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.CLASSPATH;
import static com.android.tools.idea.gradle.service.notification.hyperlink.UpgradeAppenginePluginVersionHyperlink.*;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class OutdatedAppEngineGradlePluginErrorHandler extends SyncErrorHandler {
  public static final String MARKER_TEXT = "Cause: java.io.File cannot be cast to org.gradle.api.artifacts.Configuration";

  @Override
  public boolean handleError(@NotNull ExternalSystemException error, @NotNull NotificationData notification, @NotNull Project project) {
    String text = findErrorMessage(getRootCause(error), notification, project);
    if (text != null) {
      List<NotificationHyperlink> hyperlinks = getQuickFixHyperlinks(notification, project, text);
      if (!hyperlinks.isEmpty()) {
        updateUsageTracker();
        SyncMessages.getInstance(project).updateNotification(notification, text, hyperlinks);
        return true;
      }
    }
    return false;
  }

  @Nullable
  @Override
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull NotificationData notification, @NotNull Project project) {
    String text = rootCause.getMessage();
    if (isNotEmpty(text) && text.contains(MARKER_TEXT) && project.getBaseDir() != null) {
      updateUsageTracker();
      return text;
    }
    return null;
  }

  @NotNull
  @Override
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull NotificationData notification,
                                                              @NotNull Project project,
                                                              @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    Ref<Boolean> handled = new Ref<>(false);
    BuildFileProcessor.getInstance().processRecursively(project, buildModel -> {
      DependenciesModel dependencies = buildModel.buildscript().dependencies();
      for (ArtifactDependencyModel dependency : dependencies.artifacts(CLASSPATH)) {
        if (APPENGINE_PLUGIN_GROUP_ID.equals(dependency.group().value())
            && APPENGINE_PLUGIN_ARTIFACT_ID.equals(dependency.name().value())) {
          String version = dependency.version().value();
          if (version != null) {
            GradleVersion currentVersion = GradleVersion.tryParse(version);
            if (currentVersion != null) {
              if (currentVersion.compareTo(DEFAULT_APPENGINE_PLUGIN_VERSION) < 0) {
                ServiceManager.getService(ExternalRepository.class).refreshFor(APPENGINE_PLUGIN_GROUP_ID, APPENGINE_PLUGIN_ARTIFACT_ID);

                NotificationHyperlink quickFix = new UpgradeAppenginePluginVersionHyperlink(dependency, buildModel);
                hyperlinks.add(quickFix);
                handled.set(true);
                // Stop processing, problem config is found.
                return false;
              }
            }
          }
        }
      }
      return true;
    });
    return hyperlinks;
  }
}