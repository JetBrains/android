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
package com.android.tools.idea.res;

import com.android.annotations.NonNull;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multimap;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

class PsiResourceFile extends ResourceFile implements Iterable<ResourceItem> {
  private static final File DUMMY_FILE = new File("");
  private PsiFile myFile;
  private String myName;
  private ResourceFolderType myFolderType;
  private Multimap<String, ResourceItem> myDuplicates;
  private DataBindingInfo myDataBindingInfo;

  public PsiResourceFile(@NonNull PsiFile file, @NonNull ResourceItem item, @NonNull String qualifiers,
                         @NonNull ResourceFolderType folderType, @NonNull FolderConfiguration folderConfiguration) {
    super(DUMMY_FILE, item, qualifiers, folderConfiguration);
    myFile = file;
    myName = file.getName();
    myFolderType = folderType;
  }

  public PsiResourceFile(@NonNull PsiFile file, @NonNull List<ResourceItem> items, @NonNull String qualifiers,
                         @NonNull ResourceFolderType folderType, @NonNull FolderConfiguration folderConfiguration) {
    super(DUMMY_FILE, items, qualifiers, folderConfiguration);
    myFile = file;
    myName = file.getName();
    myFolderType = folderType;
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

  public void setPsiFile(@NonNull PsiFile psiFile, String qualifiers) {
    mFile = null;
    myFile = psiFile;
    setQualifiers(qualifiers);
    myFolderType = ResourceHelper.getFolderType(psiFile);
  }

  @Override
  public void addItems(@NonNull Iterable<ResourceItem> items) {
    for (ResourceItem item : items) {
      addItem(item);
    }
  }

  @Override
  public void removeItems(@NonNull Iterable<ResourceItem> items) {
    for (ResourceItem item : items) {
      removeItem(item);
    }
  }

  @Override
  public void addItem(@NonNull ResourceItem item) {
    item.setSource(this);
    String key = item.getKey();
    ResourceItem prev = mItems.get(key);
    if (prev != null) {
      // There are duplicates. We need to track these separately since the normal data file
      // only contains a single key position.
      if (myDuplicates == null) {
        myDuplicates = ArrayListMultimap.create();
      }
      myDuplicates.put(key, prev);
    }
    mItems.put(key, item);
  }

  @Override
  public void removeItem(ResourceItem item) {
    String key = item.getKey();
    if (myDuplicates != null) {
      Collection<ResourceItem> prev = myDuplicates.get(key);
      if (prev != null && prev.contains(item)) {
        myDuplicates.remove(key, item);
        if (myDuplicates.isEmpty()) {
          myDuplicates = null;
        }
        item.setSource(null);
        return;
      }
    }

    mItems.remove(key);
    item.setSource(null);

    // If we removed an item and we have duplicates in the wings, shift one of those into the prime position
    if (myDuplicates != null) {
      Collection<ResourceItem> prev = myDuplicates.get(key);
      if (prev != null && !prev.isEmpty()) {
        ResourceItem first = prev.iterator().next();
        myDuplicates.remove(key, first);
        mItems.put(key, first);
        if (myDuplicates.isEmpty()) {
          myDuplicates = null;
        }
      }
    }
  }

  @Override
  public void replace(@NonNull ResourceItem oldItem, @NonNull ResourceItem newItem) {
    removeItem(oldItem);
    addItem(newItem);
  }

  @Override
  public Iterator<ResourceItem> iterator() {
    if (myDuplicates == null) {
      return mItems.values().iterator();
    }

    return Iterators.concat(mItems.values().iterator(), myDuplicates.values().iterator());
  }

  public void setDataBindingInfo(DataBindingInfo dataBindingInfo) {
    myDataBindingInfo = dataBindingInfo;
  }

  @Nullable
  public DataBindingInfo getDataBindingInfo() {
    return myDataBindingInfo;
  }
}
