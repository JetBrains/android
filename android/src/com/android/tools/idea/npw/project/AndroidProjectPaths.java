/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.project;

import com.android.builder.model.SourceProvider;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * A data class which, when initialized with a {@link SourceProvider} and some other relevant
 * parameters, exposes paths which are useful when instantiating an Android project. In cases
 * where the source provider returns multiple paths, we always take the first match.
 */
public final class AndroidProjectPaths {
  @NotNull private final AndroidFacet myAndroidFacet;

  @Nullable private File myModuleRoot;
  @Nullable private File mySrcDirectory;
  @Nullable private File myTestDirectory;
  @Nullable private File myResDirectory;
  @Nullable private File myAidlDirectory;
  @Nullable private File myManifestDirectory;

  public AndroidProjectPaths(@NotNull AndroidFacet androidFacet) {
    this(androidFacet, getSourceProviders(androidFacet, null).get(0));
  }

  public AndroidProjectPaths(@NotNull AndroidFacet androidFacet, @NotNull SourceProvider sourceProvider) {
    myAndroidFacet = androidFacet;
    Module module = getModule();
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length > 0) {
      myModuleRoot = VfsUtilCore.virtualToIoFile(roots[0]);
    }

    mySrcDirectory = Iterables.getFirst(sourceProvider.getJavaDirectories(), null);

    List<VirtualFile> testsRoot = ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.TESTS);
    myTestDirectory = testsRoot.size() == 0 ? null : VfsUtilCore.virtualToIoFile(testsRoot.get(0));

    Collection<File> resDirectories = sourceProvider.getResDirectories();
    if (!resDirectories.isEmpty()) {
      myResDirectory = resDirectories.iterator().next();
    }

    Collection<File> aidlDirectories = sourceProvider.getAidlDirectories();
    if (!aidlDirectories.isEmpty()) {
      myAidlDirectory = aidlDirectories.iterator().next();
    }

    myManifestDirectory = sourceProvider.getManifestFile().getParentFile();
  }

  /**
   * Convenience method to get {@link SourceProvider}s from the current project which can be used
   * to instantiate an instance of this class.
   */
  @NotNull
  public static List<SourceProvider> getSourceProviders(@NotNull AndroidFacet androidFacet, @Nullable VirtualFile targetDirectory) {
    if (targetDirectory != null) {
      return IdeaSourceProvider.getSourceProvidersForFile(androidFacet, targetDirectory, androidFacet.getMainSourceProvider());
    }
    else {
      return IdeaSourceProvider.getAllSourceProviders(androidFacet);
    }
  }

  @NotNull
  public AndroidFacet getAndroidFacet() {
    return myAndroidFacet;
  }

  @NotNull
  public Module getModule() {
    return myAndroidFacet.getModule();
  }

  @Nullable
  public File getModuleRoot() {
    return myModuleRoot;
  }

  @Nullable
  public File getSrcDirectory() {
    return mySrcDirectory;
  }

  @Nullable
  public File getTestDirectory() {
    return myTestDirectory;
  }

  @Nullable
  public File getResDirectory() {
    return myResDirectory;
  }

  @Nullable
  public File getAidlDirectory() {
    return myAidlDirectory;
  }

  @Nullable
  public File getManifestDirectory() {
    return myManifestDirectory;
  }
}
