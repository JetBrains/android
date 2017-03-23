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
package com.android.tools.idea.actions;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.ZipOutputStream;

/**
 * Exports an entire project into a zip file containing everything that is needed to run the project.
 */
public class ExportProjectZip extends AnAction implements DumbAware {

  @Override
  public void update(@NotNull final AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null && !project.isDefault());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    FileSaverDialog saver = FileChooserFactory.getInstance()
        .createSaveFileDialog(new FileSaverDescriptor("Save Project As Zip", "Save to", SdkConstants.EXT_ZIP), project);

    VirtualFileWrapper target = saver.save(null, project.getName() + "." + SdkConstants.EXT_ZIP);
    if (target != null) {
      Task.Backgroundable task = new Task.Backgroundable(project, "Saving Project Zip") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          save(target.getFile(), project, indicator);
        }
      };
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }
  }

  @VisibleForTesting
  static void save(@NotNull File zipFile, @NotNull Project project, @Nullable ProgressIndicator indicator) {
    Set<File> allRoots = new HashSet<>();
    Set<File> excludes = new HashSet<>();
    excludes.add(zipFile);

    assert project.getBasePath() != null;
    File basePath = VfsUtilCore.virtualToIoFile(project.getBaseDir());
    allRoots.add(basePath);

    excludes.add(new File(basePath, SdkConstants.FN_LOCAL_PROPERTIES));

    boolean gradle = GradleProjectInfo.getInstance(project).isBuildWithGradle();
    if (gradle) {
      excludes.add(new File(basePath, SdkConstants.DOT_GRADLE));
      excludes.add(new File(basePath, GradleUtil.BUILD_DIR_DEFAULT_NAME));
      excludes.add(new File(basePath, Project.DIRECTORY_STORE_FOLDER));
      excludes.add(new File(basePath, CaptureService.FD_CAPTURES));
    }

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (gradle) {
        // if this is a gradle project, exclude .iml file
        VirtualFile moduleFile = module.getModuleFile();
        if (moduleFile != null) {
          excludes.add(VfsUtilCore.virtualToIoFile(moduleFile));
        }
      }

      ModuleRootManager roots = ModuleRootManager.getInstance(module);

      VirtualFile[] contentRoots = roots.getContentRoots();
      for (VirtualFile root : contentRoots) {
        allRoots.add(VfsUtilCore.virtualToIoFile(root));
      }

      VirtualFile[] exclude = roots.getExcludeRoots();
      for (VirtualFile root : exclude) {
        excludes.add(VfsUtilCore.virtualToIoFile(root));
      }

      AndroidModuleModel androidModel = AndroidModuleModel.get(module);
      if (androidModel != null) {
        excludes.add(androidModel.getAndroidProject().getBuildFolder());
      }
      JavaFacet facet = JavaFacet.getInstance(module);
      if (facet != null) {
        JavaModuleModel model = facet.getJavaModuleModel();
        if (model != null) {
          excludes.add(model.getBuildFolderPath());
        }
      }
    }

    File commonRoot = null;
    for (File root : allRoots) {
      commonRoot = commonRoot == null ? root : FileUtil.findAncestor(commonRoot, root);
      if (commonRoot == null) {
        throw new IllegalArgumentException("no common root found");
      }
    }
    assert commonRoot != null;

    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileFilter fileFilter = file -> {
      if (fileTypeManager.isFileIgnored(file.getName()) || excludes.stream().anyMatch(root -> FileUtil.isAncestor(root, file, false))) {
        return false;
      }
      if (!file.exists()) {
        Logger.getInstance(ExportProjectZip.class).info("Skipping broken symlink: " + file);
        return false;
      }
      // if it's a folder and an ancestor of any of the roots we must allow it (to allow its content) or if a root is an ancestor.
      return allRoots.stream().anyMatch(
        root -> (file.isDirectory() && FileUtil.isAncestor(file, root, false)) || FileUtil.isAncestor(root, file, false));
    };

    Set<String> writtenItems = indicator == null ? null : new AbstractSet<String>() {
      @Override
      public boolean add(String s) {
        indicator.setText(s);
        return true;
      }

      @Override
      public Iterator<String> iterator() {
        throw new UnsupportedOperationException();
      }

      @Override
      public int size() {
        throw new UnsupportedOperationException();
      }
    };

    try (ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(zipFile))) {
      final File[] children = commonRoot.listFiles();
      if (children != null) {
        for (File child : children) {
          if (fileFilter.accept(child)) { // check child dirs here or it can take a very long time checking each possible descendant file.
            final String childRelativePath = (FileUtil.filesEqual(commonRoot, basePath) ? commonRoot.getName() + "/" : "") + child.getName();
            ZipUtil.addFileOrDirRecursively(outputStream, null, child, childRelativePath, fileFilter, writtenItems);
          }
        }
      }
    }
    catch (Exception ex) {
      Logger.getInstance(ExportProjectZip.class).info("error making zip", ex);
      ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project, "Error: " + ex, "Error!"));
    }
  }
}
