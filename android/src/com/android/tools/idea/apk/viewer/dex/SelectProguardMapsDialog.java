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
package com.android.tools.idea.apk.viewer.dex;

import com.android.annotations.NonNull;
import com.android.tools.apk.analyzer.internal.ProguardMappingFiles;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.file.Path;

public class SelectProguardMapsDialog {
  private final Project myProject;
  private final VirtualFile myApkFolder;

  private ProguardMappingFiles myMappingFiles;

  public SelectProguardMapsDialog(@NonNull Project project, @NonNull VirtualFile apkFolder) {
    myProject = project;
    myApkFolder = apkFolder;
  }

  public boolean showAndGet() throws IOException {
    FileChooserDescriptor desc = new FileChooserDescriptor(true, true, false, false, false, true);
    desc.setDescription(
      "Please select the proguard mapping files (mapping, seeds or usage) or a folder containing these files.");
    desc.withFileFilter(file -> "txt".equals(file.getExtension())
                                && (file.getName().contains("mapping")
                                    || file.getName().contains("seeds")
                                    || file.getName().contains("usage")));

    VirtualFile[] files = FileChooser.chooseFiles(desc, myProject, getDefaultFolderToSelect());
    if (files.length == 0) { // user canceled
      return false;
    }

    Path[] paths = new Path[files.length];
    for (int i = 0; i < files.length; i++) {
      paths[i] = VfsUtilCore.virtualToIoFile(files[i]).toPath();
    }

    myMappingFiles = ProguardMappingFiles.from(paths);

    return true;
  }

  private VirtualFile getDefaultFolderToSelect() {
    // typically, gradle projects have the following structure
    //   |--build/outputs
    //   |----apk/release.apk
    //   |----mapping/release/{mapping|seeds|usage}.txt
    // This method returns the mapping/release folder given the apk folder.

    VirtualFile folderToSelect = myApkFolder;
    if (folderToSelect.getParent() != null) {
      folderToSelect = folderToSelect.getParent();
    }

    //try to find the mapping folder in the project file structure
    VirtualFile mappingFolder = folderToSelect.findChild("mapping");
    if (mappingFolder != null) {
      folderToSelect = mappingFolder;
      //if it has just one subfolder (e.g. "mapping/release"), select that
      if (folderToSelect.getChildren().length == 1) {
        folderToSelect = folderToSelect.getChildren()[0];
      }
    }
    return folderToSelect;
  }

  @NonNull
  public ProguardMappingFiles getMappingFiles() {
    return myMappingFiles;
  }
}
