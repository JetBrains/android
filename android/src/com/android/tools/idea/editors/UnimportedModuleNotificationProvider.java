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
package com.android.tools.idea.editors;

import com.android.SdkConstants;
import com.android.tools.idea.actions.AndroidImportModuleAction;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Informs the user that project is not imported as a module and offers a way to fix this.
 */
public class UnimportedModuleNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  public static final Key<EditorNotificationPanel> KEY = Key.create("android.gradle.module.import");
  @NotNull private final Project myProject;
  private final AtomicBoolean myIsImporting = new AtomicBoolean(false);

  public UnimportedModuleNotificationProvider(@NotNull Project project) {
    myProject = project;
  }

  private static boolean isImportedGradleProjectRoot(VirtualFile file, Project myProject) {
    VirtualFile parent = file.getParent();
    if (parent.equals(myProject.getBaseDir())) {
      return true;
    }
    Module module = ModuleUtilCore.findModuleForFile(file, myProject);
    if (module != null) {
      AndroidGradleFacet facet = AndroidGradleFacet.getInstance(module);
      if (facet != null) {
        IdeaGradleProject gradleProject = facet.getGradleProject();
        if (gradleProject != null) {
          VirtualFile buildFile = gradleProject.getBuildFile();
          if (buildFile != null && file.getParent().equals(parent)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isGradleBuildFile(VirtualFile file) {
    return ImmutableSet.of(SdkConstants.FN_BUILD_GRADLE, SdkConstants.FN_SETTINGS_GRADLE).contains(file.getName());
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (!Projects.requiresAndroidModel(myProject) || myIsImporting.get()) {
      return null;
    }
    GradleSyncState syncState = GradleSyncState.getInstance(myProject);
    if (Projects.lastGradleSyncFailed(myProject) ||
        syncState.isSyncInProgress() ||
        syncState.isSyncNeeded() != ThreeState.NO) {
      return null;
    }
    if (!isGradleBuildFile(file) || isImportedGradleProjectRoot(file, myProject)) {
      return null;
    }
    return new UnimportedModuleNotificationPanel(myProject, file.getParent());
  }

  private class UnimportedModuleNotificationPanel extends EditorNotificationPanel {
    public UnimportedModuleNotificationPanel(@NotNull final Project project, @NotNull final VirtualFile subproject) {
      setText("This folder does not belong to a Gradle project. Make sure it is registered in settings.gradle.");

      createActionLabel("Add Now...", new Runnable() {
        @Override
        public void run() {
          myIsImporting.set(true);
          try {
            AndroidImportModuleAction.importGradleSubprojectAsModule(subproject, project);
          } catch (IOException e) {
            throw Throwables.propagate(e);
          }
          finally {
            myIsImporting.set(false);
          }
        }
      });
    }
  }
}
