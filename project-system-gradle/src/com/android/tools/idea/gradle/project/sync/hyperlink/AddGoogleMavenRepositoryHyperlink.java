/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.plugin.AndroidPluginInfo.findFromBuildFiles;
import static com.android.tools.idea.gradle.project.sync.issues.processor.AddRepoProcessor.Repository.GOOGLE;
import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.getGradleBuildFile;

import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.issues.processor.AddRepoProcessor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Hyperlink to add {@code google()} to the repositories in buildscript block of project build.gradle file.
 */
public class AddGoogleMavenRepositoryHyperlink extends SyncIssueNotificationHyperlink {
  @NotNull private final List<VirtualFile> myBuildFiles;
  private final boolean mySyncAfterFix;

  public AddGoogleMavenRepositoryHyperlink(@NotNull Project project) {
    this(getBuildFileForPlugin(project), true);
  }

  public AddGoogleMavenRepositoryHyperlink(@NotNull List<VirtualFile> buildFiles) {
    this(buildFiles, true);
  }

  @VisibleForTesting
  public AddGoogleMavenRepositoryHyperlink(@NotNull List<VirtualFile> buildFiles, boolean syncAfterFix) {
    super("add.google.maven.repository",
          getText(buildFiles.isEmpty(), syncAfterFix),
          AndroidStudioEvent.GradleSyncQuickFix.ADD_GOOGLE_MAVEN_REPOSITORY_HYPERLINK);
    myBuildFiles = buildFiles;
    mySyncAfterFix = syncAfterFix;
  }

  @Override
  protected void execute(@NotNull Project project) {
    List<VirtualFile> files = myBuildFiles;
    if (files.isEmpty()) {
      files.addAll(getBuildFileForPlugin(project));
    }
    if (!myBuildFiles.isEmpty()) {
      AddRepoProcessor processor = new AddRepoProcessor(project, myBuildFiles, GOOGLE, mySyncAfterFix);
      processor.setPreviewUsages(true);
      processor.run();
    }
    else {
      // TODO: Add message dialog
    }
  }

  @NotNull
  private static String getText(boolean buildFileFound, boolean syncAfterFix) {
    String text = "Add Google Maven repository";
    if (buildFileFound) {
      text += " (if needed)";
    }
    if (syncAfterFix) {
      text += " and sync project";
    }
    return text;
  }

  @NotNull
  public List<VirtualFile> getBuildFiles() {
    return myBuildFiles;
  }

  /**
   * Get GradleBuildModel that contains the Android Gradle Plugin, or project's build model if the plugin cannot be found.
   * @param project
   * @return
   */
  @NotNull
  public static List<VirtualFile> getBuildFileForPlugin(@NotNull Project project) {
    // Get Android Plugin info from the project, if plugin info can not be found, use project build.gradle file instead
    AndroidPluginInfo result = findFromBuildFiles(project);
    if (result != null) {
      VirtualFile buildFile = result.getPluginBuildFile();
      if (buildFile != null) {
        return ImmutableList.of(buildFile);
      }
    }
    VirtualFile buildFile = getGradleBuildFile(getBaseDirPath(project));
    return (buildFile == null) ? ImmutableList.of() : ImmutableList.of(buildFile);
  }
}
