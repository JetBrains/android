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
package com.android.tools.idea.gradle.service.notification.hyperlink;

import com.android.SdkConstants;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

import static com.android.SdkConstants.*;
import static com.intellij.notification.NotificationType.ERROR;

public class FixGradleModelVersionHyperlink extends NotificationHyperlink {
  private static final Logger LOG = Logger.getInstance(FixGradleModelVersionHyperlink.class);

  private final boolean myOpenMigrationGuide;

  public FixGradleModelVersionHyperlink() {
    this("Open migration guide, fix plug-in version and sync project", true);
  }

  public FixGradleModelVersionHyperlink(@NotNull String text, boolean openMigrationGuide) {
    super("fixGradleElements", text);
    myOpenMigrationGuide = openMigrationGuide;
  }

  @Override
  protected void execute(@NotNull Project project) {
    if (myOpenMigrationGuide) {
      BrowserUtil.browse("http://tools.android.com/tech-docs/new-build-system/migrating-to-1-0-0");
    }

    if (updateGradlePluginVersion(project)) {
      GradleProjectImporter.getInstance().requestProjectSync(project, null);
      return;
    }

    String msg = "Unable to find any references to the Android Gradle plugin in build.gradle files.\n\n" +
                 "Please click the link to perform a textual search and then update the build files manually.";
    SearchInBuildFilesHyperlink hyperlink = new SearchInBuildFilesHyperlink(GRADLE_PLUGIN_NAME);
    AndroidGradleNotification.getInstance(project).showBalloon(ERROR_MSG_TITLE, msg, ERROR, hyperlink);
  }

  private static boolean updateGradlePluginVersion(@NotNull final Project project) {
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) {
      // Unlikely to happen: this is default project.
      return false;
    }

    final Ref<Boolean> atLeastOneUpdated = new Ref<Boolean>(false);

    VfsUtil.processFileRecursivelyWithoutIgnored(baseDir, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile virtualFile) {
        if (SdkConstants.FN_BUILD_GRADLE.equals(virtualFile.getName())) {
          final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
          if (document != null) {
            boolean updated = GradleUtil.updateGradlePluginVersion(project, document, GRADLE_PLUGIN_NAME, new Computable<String>() {
              @Override
              public String compute() {
                return GRADLE_PLUGIN_RECOMMENDED_VERSION;
              }
            });
            if (updated) {
              atLeastOneUpdated.set(true);
            }
          }
        }
        return true;
      }
    });

    boolean updated = atLeastOneUpdated.get();
    if (updated) {
      File wrapperPropertiesFilePath = GradleUtil.getGradleWrapperPropertiesFilePath(new File(project.getBasePath()));
      FullRevision current = getGradleWrapperVersion(wrapperPropertiesFilePath);
      if (current != null && !GradleUtil.isSupportedGradleVersion(current)) {
        try {
          GradleUtil.updateGradleDistributionUrl(GRADLE_LATEST_VERSION, wrapperPropertiesFilePath);
        }
        catch (IOException e) {
          LOG.warn("Failed to update Gradle version in wrapper", e);
        }
      }
    }
    return updated;
  }

  @Nullable
  private static FullRevision getGradleWrapperVersion(File wrapperPropertiesFilePath) {
    String version = null;
    try {
      version = GradleUtil.getGradleWrapperVersion(wrapperPropertiesFilePath);
    }
    catch (IOException e) {
      LOG.warn("Failed to obtain Gradle version in wrapper", e);
    }
    if (StringUtil.isNotEmpty(version)) {
      try {
        return FullRevision.parseRevision(version);
      }
      catch (NumberFormatException e) {
        LOG.warn("Failed to parse Gradle version " + version, e);
      }
    }
    return null;
  }
}
