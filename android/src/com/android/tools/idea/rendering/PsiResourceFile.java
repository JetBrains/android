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
package com.android.tools.idea.rendering;

import com.android.annotations.NonNull;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.google.common.base.Splitter;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;

import java.io.File;
import java.util.List;

class PsiResourceFile extends ResourceFile {
  private static final File DUMMY_FILE = new File("");
  private PsiFile myFile;
  private String myName;
  private ResourceFolderType myFolderType;
  private FolderConfiguration myFolderConfiguration;

  public PsiResourceFile(@NonNull PsiFile file, @NonNull ResourceItem item, @NonNull String qualifiers,
                         @NonNull ResourceFolderType folderType, @NonNull FolderConfiguration folderConfiguration) {
    super(DUMMY_FILE, item, qualifiers);
    myFile = file;
    myName = file.getName();
    myFolderType = folderType;
    myFolderConfiguration = folderConfiguration;
  }

  public PsiResourceFile(@NonNull PsiFile file, @NonNull List<ResourceItem> items, @NonNull String qualifiers,
                         @NonNull ResourceFolderType folderType, @NonNull FolderConfiguration folderConfiguration) {
    super(DUMMY_FILE, items, qualifiers);
    myFile = file;
    myName = file.getName();
    myFolderType = folderType;
    myFolderConfiguration = folderConfiguration;
  }

  @NonNull
  public PsiFile getPsiFile() {
    return myFile;
  }

  @NonNull
  @Override
  public File getFile() {
    if (mFile == null || mFile == DUMMY_FILE) {
      VirtualFile virtualFile = myFile.getVirtualFile();
      if (virtualFile != null) {
        mFile = VfsUtilCore.virtualToIoFile(virtualFile);
      } else {
        mFile = super.getFile();
      }
    }

    return mFile;
  }

  public String getName() {
    return myName;
  }

  ResourceFolderType getFolderType() {
    return myFolderType;
  }

  FolderConfiguration getFolderConfiguration() {
    return myFolderConfiguration;
  }

  public void setPsiFile(@NonNull PsiFile psiFile, String qualifiers) {
    mFile = null;
    myFile = psiFile;
    setQualifiers(qualifiers);
    myFolderConfiguration = FolderConfiguration.getConfigFromQualifiers(Splitter.on('-').split(qualifiers));
    PsiDirectory parent = psiFile.getParent();
    assert parent != null : psiFile;
    myFolderType = ResourceFolderType.getFolderType(parent.getName());
  }
}
