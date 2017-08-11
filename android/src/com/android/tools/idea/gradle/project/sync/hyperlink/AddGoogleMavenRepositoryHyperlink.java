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
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.model.GradleBuildModel.parseBuildFile;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModel.*;
import static com.android.tools.idea.gradle.util.GradleVersions.isGradle4OrNewer;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

/**
 * Hyperlink to add {@code google()} to the repositories in buildscript block of project build.gradle file.
 */
public class AddGoogleMavenRepositoryHyperlink extends NotificationHyperlink {
  @NotNull private final VirtualFile myBuildFile;
  private final boolean mySyncAfterFix;

  public AddGoogleMavenRepositoryHyperlink(@NotNull VirtualFile buildFile) {
    this(buildFile, true);
  }

  @VisibleForTesting
  public AddGoogleMavenRepositoryHyperlink(@NotNull VirtualFile buildFile, boolean syncAfterFix) {
    super("add.google.maven.repository", getText(syncAfterFix));
    myBuildFile = buildFile;
    mySyncAfterFix = syncAfterFix;
  }

  @Override
  protected void execute(@NotNull Project project) {
    addGoogleRepository(project);
  }

  private void addGoogleRepository(@NotNull Project project) {
    GradleBuildModel buildModel = parseBuildFile(myBuildFile, project);
    if (isGradle4OrNewer(project)) {
      buildModel.buildscript().repositories().addRepositoryByMethodName(GOOGLE_METHOD_NAME);
    }
    else {
      buildModel.buildscript().repositories().addMavenRepositoryByUrl(GOOGLE_DEFAULT_REPO_URL, GOOGLE_DEFAULT_REPO_NAME);
    }
    runWriteCommandAction(project, buildModel::applyChanges);
    if (mySyncAfterFix) {
      GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED, null);
    }
  }

  @NotNull
  private static String getText(boolean syncAfterFix) {
    String text = "Add Google Maven repository to buildscript";
    if (syncAfterFix) {
      text += " and sync project";
    }
    return text;
  }

  @NotNull
  public VirtualFile getBuildFile() {
    return myBuildFile;
  }
}
