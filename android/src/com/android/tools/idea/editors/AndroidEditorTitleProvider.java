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

import com.android.resources.ResourceFolderType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;

public class AndroidEditorTitleProvider implements EditorTabTitleProvider {
  @Nullable
  @Override
  public String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    if (DumbService.isDumb(project)) {
      return null;
    }

    if (!FileTypeRegistry.getInstance().isFileOfType(file, XmlFileType.INSTANCE)) {
      return null;
    }

    // Resource file?
    if (file.getName().equals(FN_ANDROID_MANIFEST_XML)) {
      return null;
    }

    VirtualFile parent = file.getParent();
    if (parent == null) {
      return null;
    }

    String parentName = parent.getName();
    int index = parentName.indexOf('-');
    if (index == -1 || index == parentName.length() - 1) {
      return null;
    }

    ResourceFolderType folderType = ResourceFolderType.getFolderType(parentName);
    if (folderType == null) {
      return null;
    }

    return parentName.substring(index + 1) + File.separator + file.getPresentableName();
  }
}
