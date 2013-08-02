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
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * GradleBuildFileUpdater listens for module-level events and updates the settings.gradle and build.gradle files to reflect any changes it
 * sees.
 */
public class GradleBuildFileUpdater extends ModuleAdapter implements BulkFileListener {
  private static final Logger LOG = Logger.getInstance(GradleBuildFileUpdater.class);

  // Module doesn't implement hashCode(); we can't use a map
  private final Collection<Pair<Module, GradleBuildFile>> myBuildFiles = Lists.newArrayList();
  private final GradleSettingsFile mySettingsFile;
  private final Project myProject;

  public GradleBuildFileUpdater(@NotNull Project project) {
    myProject = project;
    VirtualFile settingsFile = project.getBaseDir().findFileByRelativePath(SdkConstants.FN_SETTINGS_GRADLE);
    if (settingsFile != null) {
      mySettingsFile = new GradleSettingsFile(settingsFile, project);
    } else {
      mySettingsFile = null;
      LOG.warn("Unable to find settings.gradle file for project " + project.getName());
    }

    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      VirtualFile vf;
      if ((vf = module.getModuleFile()) != null &&
          (vf = vf.getParent()) != null &&
          (vf = vf.findChild(SdkConstants.FN_BUILD_GRADLE)) != null) {
        put(module, new GradleBuildFile(vf, project));
      } else {
        LOG.warn("Unable to find build.gradle file for module " + module.getName());
      }
    }
  }

  @Override
  public void moduleAdded(@NotNull final Project project, @NotNull final Module module) {
    // At the time we're called, module.getModuleFile() is null, but getModuleFilePath returns the path where it will be created.
    File moduleFile = new File(module.getModuleFilePath());
    File buildFile = new File(moduleFile.getParentFile(), SdkConstants.FN_BUILD_GRADLE);
    VirtualFile vBuildFile = LocalFileSystem.getInstance().findFileByIoFile(buildFile);
    if (vBuildFile != null) {
      put(module, new GradleBuildFile(vBuildFile, project));
    }

    // The module has probably already been added to the settings file but let's call this to be safe.
    mySettingsFile.addModule(module);
  }

  @Override
  public void moduleRemoved(@NotNull Project project, @NotNull final Module module) {
    remove(module);
    mySettingsFile.removeModule(module);
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
  }

  @Override
  /**
   * This gets called on all filesystem changes, but we're interested in changes to module root directories. When we see them, we'll update
   * the setitngs.gradle file. Note that users can also refactor modules by renaming them, which just changes their display name and not
   * the filesystem directory -- when that happens, this class gets a
   * {@link ModuleAdapter#modulesRenamed(com.intellij.openapi.project.Project, java.util.List)} callback. However, it's not appropriate to
   * update settings.gradle in that case since Gradle doesn't case about IJ's display name of the module.
   */
  public void after(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      if (!(event instanceof VFilePropertyChangeEvent)) {
        continue;
      }
      VFilePropertyChangeEvent propChangeEvent = (VFilePropertyChangeEvent) event;
      if (!("name".equals(propChangeEvent.getPropertyName())) || propChangeEvent.getFile() == null) {
        continue;
      }
      // Get the old and new paths. The new path is in the event; derive the old path from the old value + new path.
      final String newPath = propChangeEvent.getFile().getPath();
      final String oldPath =
          newPath.substring(0, newPath.length() - ((String)propChangeEvent.getNewValue()).length()) + propChangeEvent.getOldValue();

      // Dig through our modules and find the one that matches the new path (the module will already have its path updated by now).
      Module module = null;
      for (Pair<Module, GradleBuildFile> pair : myBuildFiles) {
        if (pair.first.getModuleFile() == null || pair.first.getModuleFile().getParent() == null) {
          continue;
        }
        String path = pair.first.getModuleFile().getParent().getPath();
        if (newPath.equals(path)) {
          module = pair.first;
          break;
        }
      }

      // If we found the module, then remove the old reference from the build.settings file and from our data structures, and put
      // in new references.
      if (module != null) {
        remove(module);
        mySettingsFile.removeModule(mySettingsFile.convertToGradlePath(oldPath));

        File modulePath = new File(newPath);
        File buildFile = new File(modulePath, SdkConstants.FN_BUILD_GRADLE);
        VirtualFile vBuildFile = LocalFileSystem.getInstance().findFileByIoFile(buildFile);
        if (vBuildFile != null) {
          put(module, new GradleBuildFile(vBuildFile, myProject));
        }

        mySettingsFile.addModule(mySettingsFile.convertToGradlePath(newPath));
      }
    }
  }

  private void put(@NotNull Module module, @NotNull GradleBuildFile file) {
    remove(module);
    myBuildFiles.add(new Pair<Module, GradleBuildFile>(module, file));
  }

  private void remove(@NotNull Module module) {
    for (Pair<Module, GradleBuildFile> pair : myBuildFiles) {
      if (pair.first == module) {
        myBuildFiles.remove(pair);
        return;
      }
    }
  }
}
