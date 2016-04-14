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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.UpgradeAppenginePluginVersionHyperlink;
import com.android.tools.idea.gradle.service.repo.ExternalRepository;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.CLASSPATH;
import static com.android.tools.idea.gradle.service.notification.hyperlink.UpgradeAppenginePluginVersionHyperlink.*;
import static com.android.tools.idea.gradle.util.GradleUtil.processBuildModelsRecursively;

/**
 * https://code.google.com/p/android/issues/detail?id=80441
 */
public class OutdatedAppEngineGradlePluginErrorHandler extends AbstractSyncErrorHandler {
  public static final String MARKER_TEXT = "Cause: java.io.File cannot be cast to org.gradle.api.artifacts.Configuration";

  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull final NotificationData notification,
                             @NotNull final Project project) {
    if (!message.contains(MARKER_TEXT)) {
      return false;
    }

    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) {
      return false;
    }

    final Ref<Boolean> handled = new Ref<Boolean>(false);
    processBuildModelsRecursively(project, new Processor<GradleBuildModel>() {
      @Override
      public boolean process(GradleBuildModel buildModel) {
        DependenciesModel dependencies = buildModel.buildscript().dependencies();
        for (ArtifactDependencyModel dependency : dependencies.artifacts(CLASSPATH)) {
          ArtifactDependencySpec spec = dependency.getSpec();
          if (APPENGINE_PLUGIN_GROUP_ID.equals(spec.group) && APPENGINE_PLUGIN_ARTIFACT_ID.equals(spec.name)) {
            String version = spec.version;
            if (version != null) {
              GradleVersion currentVersion = GradleVersion.tryParse(version);
              if (currentVersion != null) {
                if (currentVersion.compareTo(DEFAULT_APPENGINE_PLUGIN_VERSION) < 0) {
                  ServiceManager.getService(ExternalRepository.class).refreshFor(APPENGINE_PLUGIN_GROUP_ID, APPENGINE_PLUGIN_ARTIFACT_ID);

                  NotificationHyperlink quickFix = new UpgradeAppenginePluginVersionHyperlink(dependency, buildModel);
                  updateNotification(notification, project, notification.getMessage(), quickFix);

                  handled.set(true);
                  // Stop processing, problem config is found.
                  return false;
                }

              }
            }
          }
        }
        return true;
      }
    });
    return handled.get();
  }
}
