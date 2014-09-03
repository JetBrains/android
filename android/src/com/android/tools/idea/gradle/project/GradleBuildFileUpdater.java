/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.Joiner;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * GradleBuildFileUpdater listens for module-level events and updates the settings.gradle and build.gradle files to reflect any changes it
 * sees.
 */
public class GradleBuildFileUpdater extends ModuleAdapter implements BulkFileListener {
  private final Project myProject;

  public GradleBuildFileUpdater(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void moduleAdded(@NotNull final Project project, @NotNull final Module module) {
    // Don't do anything if we are in the middle of a project sync.
    if (GradleSyncState.getInstance(project).isSyncInProgress()) {
      return;
    }
    final GradleSettingsFile settingsFile = GradleSettingsFile.get(project);
    if (settingsFile != null) {
      // if settings.gradle does not have a module, we are in the middle of setting up a project.
      final PsiFile psiFile = settingsFile.getPsiFile();
      Module found = ModuleUtilCore.findModuleForPsiElement(psiFile);
      if (found != null) {
        new WriteCommandAction<Void>(project, "Update settings.gradle", psiFile) {
          @Override
          protected void run(@NotNull Result<Void> result) throws Throwable {
            settingsFile.addModule(module);
          }
        }.execute();
      }
    }
  }

  @Override
  public void moduleRemoved(@NotNull Project project, @NotNull final Module module) {
    // Don't do anything if we are in the middle of a project sync.
    if (GradleSyncState.getInstance(project).isSyncInProgress()) {
      return;
    }
    final GradleSettingsFile settingsFile = GradleSettingsFile.get(project);
    if (settingsFile != null) {
      new WriteCommandAction<Void>(project, "Update settings.gradle", settingsFile.getPsiFile()) {
        @Override
        protected void run(@NotNull Result<Void> result) throws Throwable {
          settingsFile.removeModule(module);
        }
      }.execute();
    }
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
  }

  /**
   * This gets called on all file system changes, but we're interested in changes to module root directories. When we see them, we'll update
   * the settings.gradle file. Note that users can also refactor modules by renaming them, which just changes their display name and not
   * the filesystem directory -- when that happens, this class gets a
   * {@link ModuleAdapter#modulesRenamed(com.intellij.openapi.project.Project, java.util.List)} callback. However, it's not appropriate to
   * update settings.gradle in that case since Gradle doesn't case about IJ's display name of the module.
   */
  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      if (!(event instanceof VFilePropertyChangeEvent)) {
        continue;
      }
      VFilePropertyChangeEvent propChangeEvent = (VFilePropertyChangeEvent)event;
      if (!(VirtualFile.PROP_NAME.equals(propChangeEvent.getPropertyName()))) {
        continue;
      }

      VirtualFile eventFile = propChangeEvent.getFile();
      if (!eventFile.isDirectory()) {
        continue;
      }

      // Dig through our modules and find the one that matches the change event's path (the module will already have its path updated by
      // now).
      Module module = null;
      Module[] modules = ModuleManager.getInstance(myProject).getModules();
      for (Module m : modules) {
        VirtualFile file = GradleUtil.getGradleBuildFile(m);
        if (file != null) {
          VirtualFile moduleDir = file.getParent();
          if (moduleDir != null && FileUtil.pathsEqual(eventFile.getPath(), moduleDir.getPath())) {
            module = m;
            break;
          }
        }
      }

      // If we found the module, then remove the old reference from the settings.gradle file and put in a new one.
      if (module != null) {
        AndroidGradleFacet androidGradleFacet = AndroidGradleFacet.getInstance(module);
        if (androidGradleFacet == null) {
          continue;
        }
        String oldPath = androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
        String newPath = updateProjectNameInGradlePath(androidGradleFacet, eventFile);

        if (oldPath.equals(newPath)) {
          continue;
        }

        GradleSettingsFile settingsFile = GradleSettingsFile.get(myProject);
        if (settingsFile != null) {
          settingsFile.removeModule(oldPath);
          settingsFile.addModule(newPath, VfsUtilCore.virtualToIoFile(eventFile));
        }
      }
    }
  }

  @NotNull
  private static String updateProjectNameInGradlePath(@NotNull AndroidGradleFacet androidGradleFacet, @NotNull VirtualFile moduleDir) {
    String gradlePath = androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
    if (gradlePath.equals(SdkConstants.GRADLE_PATH_SEPARATOR)) {
      // This is root project, renaming folder does not affect it since the path is just ":".
      return gradlePath;
    }
    List<String> pathSegments = GradleUtil.getPathSegments(gradlePath);
    pathSegments.remove(pathSegments.size() - 1);
    pathSegments.add(moduleDir.getName());

    String newPath = Joiner.on(SdkConstants.GRADLE_PATH_SEPARATOR).join(pathSegments);
    androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH = newPath;
    return newPath;
  }
}
