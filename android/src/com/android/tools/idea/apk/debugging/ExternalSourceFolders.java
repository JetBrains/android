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
package com.android.tools.idea.apk.debugging;

import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.SwingWorker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.tools.idea.gradle.util.ContentEntries.findParentContentEntry;
import static com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil.suggestRoots;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class ExternalSourceFolders {
  @NotNull private final ModifiableRootModel myModuleModel;

  public ExternalSourceFolders(@NotNull ModifiableRootModel moduleModel) {
    myModuleModel = moduleModel;
  }

  @NotNull
  public List<VirtualFile> addSourceFolders(@NotNull VirtualFile[] files, @Nullable Runnable runOnFinish) {
    List<VirtualFile> roots = new ArrayList<>();
    Set<ContentEntry> contentEntries = new HashSet<>();
    for (VirtualFile file : files) {
      ContentEntry contentEntry = findParentContentEntry(virtualToIoFile(file), Arrays.stream(myModuleModel.getContentEntries()));
      if (contentEntry == null) {
        contentEntry = myModuleModel.addContentEntry(file);
      }
      contentEntries.add(contentEntry);
    }
    if (!contentEntries.isEmpty()) {
      addSourceRoots(contentEntries, runOnFinish);
    }

    for (ContentEntry contentEntry : contentEntries) {
      VirtualFile file = contentEntry.getFile();
      if (file != null) {
        roots.add(file);
      }
    }
    return roots;
  }

  private void addSourceRoots(@NotNull Set<ContentEntry> contentEntries, @Nullable Runnable runOnFinish) {
    Map<ContentEntry, Collection<JavaModuleSourceRoot>> entryToRootMap = new HashMap<>();
    Map<File, ContentEntry> fileToEntryMap = new HashMap<>();
    for (ContentEntry contentEntry : contentEntries) {
      VirtualFile file = contentEntry.getFile();
      if (file != null) {
        entryToRootMap.put(contentEntry, null);
        fileToEntryMap.put(virtualToIoFile(file), contentEntry);
      }
    }

    Project project = myModuleModel.getProject();
    ProgressIndicator progressIndicator;
    boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (unitTestMode) {
      progressIndicator = new EmptyProgressIndicator();
    }
    else {
      ProgressWindow progressWindow = new ProgressWindow(true, project);
      progressWindow.setTitle(ProjectBundle.message("module.paths.searching.source.roots.title"));
      progressIndicator = new SmoothProgressAdapter(progressWindow, project);
    }

    Runnable searchTask = () -> {
      Runnable process = () -> {
        for (File file : fileToEntryMap.keySet()) {
          progressIndicator.setText(ProjectBundle.message("module.paths.searching.source.roots.progress", file.getPath()));
          Collection<JavaModuleSourceRoot> roots = suggestRoots(file);
          entryToRootMap.put(fileToEntryMap.get(file), roots);
        }
      };
      ProgressManager.getInstance().runProcess(process, progressIndicator);
    };

    Runnable addSourcesTask = () -> {
      for (ContentEntry contentEntry : contentEntries) {
        Collection<JavaModuleSourceRoot> suggestedRoots = entryToRootMap.get(contentEntry);
        if (suggestedRoots != null) {
          for (JavaModuleSourceRoot suggestedRoot : suggestedRoots) {
            VirtualFile sourceRoot = LocalFileSystem.getInstance().findFileByIoFile(suggestedRoot.getDirectory());
            VirtualFile fileContent = contentEntry.getFile();
            if (sourceRoot != null && fileContent != null && isAncestor(fileContent, sourceRoot, false)) {
              contentEntry.addSourceFolder(sourceRoot, false, suggestedRoot.getPackagePrefix());
            }
          }
        }
      }
      if (runOnFinish != null) {
        runOnFinish.run();
      }
    };

    if (unitTestMode) {
      searchTask.run();
      addSourcesTask.run();
    }
    else {
      new SwingWorker() {
        @Override
        public Object construct() {
          searchTask.run();
          return null;
        }

        @Override
        public void finished() {
          addSourcesTask.run();
        }
      }.start();
    }
  }
}
