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
package com.android.tools.idea.gradle.editor;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;

/**
 * Makes files in the "build" folder read-only.
 */
public class GeneratedFileWritingAccessProvider extends WritingAccessProvider {
  @NotNull private final GradleProjectInfo myProjectInfo;

  public GeneratedFileWritingAccessProvider(@NotNull GradleProjectInfo projectInfo) {
    myProjectInfo = projectInfo;
  }

  @Override
  @NotNull
  public Collection<VirtualFile> requestWriting(VirtualFile... files) {
    List<VirtualFile> readOnlyFiles = new ArrayList<>();

    for (VirtualFile file : files) {
      if (isInBuildFolder(file)) {
        readOnlyFiles.add(file);
      }
    }

    return readOnlyFiles;
  }

  @Override
  public boolean isPotentiallyWritable(@NotNull VirtualFile file) {
    return !isInBuildFolder(file);
  }

  private boolean isInBuildFolder(@NotNull VirtualFile file) {
    AndroidModuleModel androidModel = myProjectInfo.findAndroidModelInModule(file, false /* include excluded files */);
    if (androidModel == null) {
      return false;
    }
    File buildFolderPath = androidModel.getAndroidProject().getBuildFolder();
    VirtualFile buildFolder = findFileByIoFile(buildFolderPath, false /* do not refresh */);
    if (buildFolder == null || !buildFolder.isDirectory()) {
      return false;
    }
    return isAncestor(buildFolder, file, false /* not strict */);
  }
}
