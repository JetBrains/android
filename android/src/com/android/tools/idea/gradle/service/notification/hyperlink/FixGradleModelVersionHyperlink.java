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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.intellij.ide.BrowserUtil.browse;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtil.processFileRecursivelyWithoutIgnored;

public class FixGradleModelVersionHyperlink extends NotificationHyperlink {
  private static final Logger LOG = Logger.getInstance(FixGradleModelVersionHyperlink.class);

  @NotNull private final String myModelVersion;
  @Nullable private final String myGradleVersion;
  private final boolean myOpenMigrationGuide;

  /**
   * Creates a new {@link FixGradleModelVersionHyperlink}. This constructor updates the Gradle model to the version in
   * {@link SdkConstants#GRADLE_PLUGIN_RECOMMENDED_VERSION} and Gradle to the version in {@link SdkConstants#GRADLE_LATEST_VERSION}.
   *
   * @param openMigrationGuide indicates whether the migration guide to the Android Gradle model version 1.0 (from an older version) should
   *                           be opened in the browser.
   */
  public FixGradleModelVersionHyperlink(boolean openMigrationGuide) {
    this(GRADLE_PLUGIN_RECOMMENDED_VERSION, GRADLE_LATEST_VERSION, openMigrationGuide);
  }

  /**
   * Creates a new {@link FixGradleModelVersionHyperlink}.
   *
   * @param modelVersion       the version to update the Android Gradle model to.
   * @param gradleVersion      the version of Gradle to update to. This can be {@code null} if only the model version needs to be updated.
   * @param openMigrationGuide indicates whether the migration guide to the Android Gradle model version 1.0 (from an older version) should
   *                           be opened in the browser.
   */
  public FixGradleModelVersionHyperlink(@NotNull String modelVersion,
                                        @Nullable String gradleVersion,
                                        boolean openMigrationGuide) {
    super("fixGradleElements",
          openMigrationGuide ? "Open migration guide, fix plugin version and sync project" : "Fix plugin version and sync project");
    myModelVersion = modelVersion;
    myGradleVersion = gradleVersion;
    myOpenMigrationGuide = openMigrationGuide;
  }

  @Override
  protected void execute(@NotNull Project project) {
    if (myOpenMigrationGuide) {
      browse("http://tools.android.com/tech-docs/new-build-system/migrating-to-1-0-0");
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

  private boolean updateGradlePluginVersion(@NotNull final Project project) {
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) {
      // Unlikely to happen: this is default project.
      return false;
    }

    final Ref<Boolean> atLeastOneUpdated = new Ref<Boolean>(false);

    processFileRecursivelyWithoutIgnored(baseDir, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile virtualFile) {
        if (FN_BUILD_GRADLE.equals(virtualFile.getName())) {
          final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
          if (document != null) {
            boolean updated = updateGradleDependencyVersion(project, document, GRADLE_PLUGIN_NAME, new Computable<String>() {
              @Override
              public String compute() {
                return myModelVersion;
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
    if (updated && isNotEmpty(myGradleVersion)) {
      String basePath = project.getBasePath();
      if (basePath != null) {
        File wrapperPropertiesFilePath = getGradleWrapperPropertiesFilePath(new File(basePath));
        FullRevision current = getGradleVersionInWrapper(wrapperPropertiesFilePath);
        if (current != null && !isSupportedGradleVersion(current)) {
          try {
            updateGradleDistributionUrl(myGradleVersion, wrapperPropertiesFilePath);
          }
          catch (IOException e) {
            LOG.warn("Failed to update Gradle version in wrapper", e);
          }
        }
      }
    }
    return updated;
  }

  @Nullable
  private static FullRevision getGradleVersionInWrapper(@NotNull File wrapperPropertiesFilePath) {
    String version = null;
    try {
      version = getGradleWrapperVersion(wrapperPropertiesFilePath);
    }
    catch (IOException e) {
      LOG.warn("Failed to obtain Gradle version in wrapper", e);
    }
    if (isNotEmpty(version)) {
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
