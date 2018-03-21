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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.api.GradleBuildModel.parseBuildFile;
import static com.android.tools.idea.gradle.plugin.AndroidPluginInfo.searchInBuildFilesOnly;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

/**
 * Hyperlink to add {@code google()} to the repositories in buildscript block of project build.gradle file.
 */
public class AddGoogleMavenRepositoryHyperlink extends NotificationHyperlink {
  @Nullable private final VirtualFile myBuildFile;
  private final boolean mySyncAfterFix;

  public AddGoogleMavenRepositoryHyperlink(@Nullable VirtualFile buildFile) {
    this(buildFile, true);
  }

  @VisibleForTesting
  public AddGoogleMavenRepositoryHyperlink(@Nullable VirtualFile buildFile, boolean syncAfterFix) {
    super("add.google.maven.repository", getText(buildFile, syncAfterFix));
    myBuildFile = buildFile;
    mySyncAfterFix = syncAfterFix;
  }

  @Override
  protected void execute(@NotNull Project project) {
    GradleBuildModel buildModel;
    if (myBuildFile != null) {
      buildModel = parseBuildFile(myBuildFile, project);
    }
    else {
      buildModel = getBuildModelForPlugin(project);
    }
    if (buildModel != null) {
      addGoogleMavenRepository(buildModel);
    }
    if (mySyncAfterFix) {
      GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED);
    }
  }

  @NotNull
  private static String getText(@Nullable VirtualFile buildFile, boolean syncAfterFix) {
    String text = "Add Google Maven repository";
    if (buildFile == null) {
      text += " (if needed)";
    }
    if (syncAfterFix) {
      text += " and sync project";
    }
    return text;
  }

  @Nullable
  public VirtualFile getBuildFile() {
    return myBuildFile;
  }

  /**
   * Add Google Maven Repository to the given buildModel
   * @param buildModel
   */
  private static void addGoogleMavenRepository(@NotNull GradleBuildModel buildModel) {
    Project project = buildModel.getProject();
    buildModel.repositories().addGoogleMavenRepository(project);
    buildModel.buildscript().repositories().addGoogleMavenRepository(project);
    runWriteCommandAction(project, buildModel::applyChanges);
    GradleBuildModel buildModelProject = GradleBuildModel.get(project);
    // Also add to project's buildscript
    if (buildModelProject != null) {
      buildModelProject.buildscript().repositories().addGoogleMavenRepository(project);
      runWriteCommandAction(project, buildModelProject::applyChanges);
    }
  }

  /**
   * Get GradleBuildModel that contains the Android Gradle Plugin, or project's build model if the plugin cannot be found.
   * @param project
   * @return
   */
  @Nullable
  public static GradleBuildModel getBuildModelForPlugin(Project project) {
    // Get Android Plugin info from the project, if plugin info can not be found, use project build.gradle file instead
    AndroidPluginInfo result = searchInBuildFilesOnly(project);
    GradleBuildModel gradleBuildModel = null;
    if (result != null) {
      VirtualFile buildFile = result.getPluginBuildFile();
      if (buildFile != null) {
        gradleBuildModel = parseBuildFile(buildFile, project);
      }
    }
    if (gradleBuildModel == null) {
      gradleBuildModel = GradleBuildModel.get(project);
    }
    return gradleBuildModel;
  }
}
