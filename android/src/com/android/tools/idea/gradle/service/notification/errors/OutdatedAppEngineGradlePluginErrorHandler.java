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

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.service.notification.hyperlink.UpgradeAppenginePluginVersionHyperlink;
import com.android.tools.idea.gradle.service.repo.ExternalRepository;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.gradle.service.notification.hyperlink.UpgradeAppenginePluginVersionHyperlink.*;

/**
 * https://code.google.com/p/android/issues/detail?id=80441
 */
public class OutdatedAppEngineGradlePluginErrorHandler extends AbstractSyncErrorHandler {

  public static final String MARKER_TEXT = "Cause: java.io.File cannot be cast to org.gradle.api.artifacts.Configuration";

  private static final Logger LOG = Logger.getInstance(OutdatedAppEngineGradlePluginErrorHandler.class);

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
    VfsUtil.processFileRecursivelyWithoutIgnored(baseDir, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile virtualFile) {
        if (!SdkConstants.FN_BUILD_GRADLE.equals(virtualFile.getName())) {
          return true; // Continue processing.
        }

        File fileToCheck = VfsUtilCore.virtualToIoFile(virtualFile);
        try {
          String contents = FileUtil.loadFile(fileToCheck);
          GradleCoordinate coordinate = GradleUtil.getPluginDefinition(contents, APPENGINE_PLUGIN_NAME);
          if (coordinate == null) {
            return true; // Continue processing.
          }
          // There is a possible case that plugin version is externalized. E.g. there is a multiproject with two
          // modules - android module (client) and server module (appengine). All versions might be defined at the
          // root project's build.gradle and appengine's build.gradle is configured like
          // 'classpath "com.google.appengine:gradle-appengine-plugin:$APPENGINE_PLUGIN_VERSION"'.
          // We want to handle that situation by substituting externalized value by a hard-coded one.
          if (GradleCoordinate.COMPARE_PLUS_HIGHER.compare(coordinate, REFERENCE_APPENGINE_COORDINATE) < 0) {
            ServiceManager.getService(ExternalRepository.class).refreshFor(APPENGINE_PLUGIN_GROUP_ID, APPENGINE_PLUGIN_ARTIFACT_ID);
            updateNotification(notification, project, notification.getMessage(), new UpgradeAppenginePluginVersionHyperlink(virtualFile));
            handled.set(true);
            // Stop processing, problem config is found.
            return true;
          }
        }
        catch (IOException e) {
          LOG.warn("Failed to read contents of " + fileToCheck.getPath() + " on attempt to check if project sync failure is caused "
                   + "by an outdated AppEngine Gradle plugin");
        }
        return false;
      }
    });
    return handled.get();
  }
}
