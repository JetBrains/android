/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.cpp;

import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.common.ContentEntriesSetup;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;

class NdkContentEntriesSetup extends ContentEntriesSetup {
  @NotNull private final NdkModuleModel myAndroidModel;

  NdkContentEntriesSetup(@NotNull NdkModuleModel ndkModuleModel, @NotNull ModifiableRootModel rootModel) {
    super(rootModel);
    myAndroidModel = ndkModuleModel;
  }

  @Override
  public void execute(@NotNull List<ContentEntry> contentEntries) {
    Collection<File> sourceFolders = myAndroidModel.getSelectedVariant().getSourceFolders();
    if (!sourceFolders.isEmpty()) {
      for (File path : sourceFolders) {
        addSourceFolder(path, contentEntries, SOURCE, false);
      }
    }
    addOrphans();
  }
}
