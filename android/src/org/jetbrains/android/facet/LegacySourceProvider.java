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
package org.jetbrains.android.facet;

import com.android.builder.model.SourceProvider;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.android.facet.AndroidRootUtil.*;

/**
 * Compatibility bridge for old (non-AndroidProject-backed) projects. Also used in AndroidProject-backed projects before the module has
 * been synced.
 */
class LegacySourceProvider implements SourceProvider {
  @NotNull private final AndroidFacet myAndroidFacet;

  LegacySourceProvider(@NotNull AndroidFacet androidFacet) {
    myAndroidFacet = androidFacet;
  }

  @Override
  @NotNull
  public String getName() {
    return "main";
  }

  @Override
  @NotNull
  public File getManifestFile() {
    Module module = myAndroidFacet.getModule();
    VirtualFile manifestFile = getFileByRelativeModulePath(module, myAndroidFacet.getProperties().MANIFEST_FILE_RELATIVE_PATH, true);
    if (manifestFile == null) {
      VirtualFile root = !myAndroidFacet.requiresAndroidModel() ? getMainContentRoot(myAndroidFacet) : null;
      if (root != null) {
        return new File(virtualToIoFile(root), ANDROID_MANIFEST_XML);
      }
      else {
        return new File(ANDROID_MANIFEST_XML);
      }
    }
    else {
      return virtualToIoFile(manifestFile);
    }
  }

  @Override
  @NotNull
  public Set<File> getJavaDirectories() {
    Set<File> dirs = Sets.newHashSet();

    Module module = myAndroidFacet.getModule();
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (contentRoots.length != 0) {
      for (VirtualFile root : contentRoots) {
        dirs.add(virtualToIoFile(root));
      }
    }
    return dirs;
  }

  @Override
  @NotNull
  public Set<File> getResourcesDirectories() {
    return Collections.emptySet();
  }

  @Override
  @NotNull
  public Set<File> getAidlDirectories() {
    VirtualFile dir = getAidlGenDir(myAndroidFacet);
    return dir == null ? Collections.emptySet() : Collections.singleton(virtualToIoFile(dir));
  }

  @Override
  @NotNull
  public Set<File> getRenderscriptDirectories() {
    VirtualFile dir = getRenderscriptGenDir(myAndroidFacet);
    return dir == null ? Collections.emptySet() : Collections.singleton(virtualToIoFile(dir));
  }

  @Override
  @NotNull
  public Set<File> getResDirectories() {
    String resRelPath = myAndroidFacet.getProperties().RES_FOLDER_RELATIVE_PATH;
    VirtualFile dir = getFileByRelativeModulePath(myAndroidFacet.getModule(), resRelPath, true);
    return dir == null ? Collections.emptySet() : Collections.singleton(virtualToIoFile(dir));
  }

  @Override
  @NotNull
  public Set<File> getAssetsDirectories() {
    VirtualFile dir = getAssetsDir(myAndroidFacet);
    return dir == null ? Collections.emptySet() : Collections.singleton(virtualToIoFile(dir));
  }

  @Override
  @NotNull
  public Collection<File> getJniLibsDirectories() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Collection<File> getShadersDirectories() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Collection<File> getCDirectories() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Collection<File> getCppDirectories() {
    return Collections.emptyList();
  }
}
