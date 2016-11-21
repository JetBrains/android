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
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * A data class which, when initialized with a {@link SourceProvider} and some other relevant
 * parameters, exposes paths which are useful when instantiating an Android project. In cases
 * where the source provider returns multiple paths, we always take the first match.
 */
public final class AndroidProjectPaths {
  @Nullable private File myModuleRoot;
  @Nullable private File mySrcDirectory;
  @Nullable private File myTestDirectory;
  @Nullable private File myResDirectory;
  @Nullable private File myAidlDirectory;
  @Nullable private File myManifestDirectory;

  /**
   * Convenience method to get {@link SourceProvider}s from the current project which can be used
   * to instantiate an instance of this class.
   */
  @NotNull
  static List<SourceProvider> getSourceProviders(@NotNull AndroidFacet androidFacet, @Nullable VirtualFile targetDirectory) {
    if (targetDirectory != null) {
      return IdeaSourceProvider.getSourceProvidersForFile(androidFacet, targetDirectory, androidFacet.getMainSourceProvider());
    }
    else {
      return IdeaSourceProvider.getAllSourceProviders(androidFacet);
    }
  }

  /**
   * Create an instance of this class manually. You may wish to use one of the other
   * convenience constructors instead, however. It assumes the 'main' flavor and default android locations for the source, test, res,
   * aidl and manifest.
   */
  public AndroidProjectPaths(@NotNull File moduleRoot) {
    myModuleRoot = moduleRoot;

    File baseSrcDir = new File(moduleRoot, FD_SOURCES);
    File baseFlavourDir = new File(baseSrcDir, FD_MAIN);

    mySrcDirectory = new File(baseFlavourDir, FD_JAVA);
    myTestDirectory = Paths.get(baseSrcDir.getPath(), FD_TEST, FD_JAVA).toFile();
    myResDirectory = new File(baseFlavourDir, FD_RESOURCES);
    myAidlDirectory = new File(baseFlavourDir, FD_AIDL);
    myManifestDirectory = baseFlavourDir;
  }

  public AndroidProjectPaths(@NotNull AndroidFacet androidFacet) {
    this(androidFacet, getSourceProviders(androidFacet, null).get(0));
  }

  public AndroidProjectPaths(@NotNull AndroidFacet androidFacet, @NotNull SourceProvider sourceProvider) {
    init(sourceProvider);

    Module module = androidFacet.getModule();
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length > 0) {
      myModuleRoot = VfsUtilCore.virtualToIoFile(roots[0]);
    }

    List<VirtualFile> testsRoot = ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.TESTS);
    myTestDirectory = testsRoot.size() == 0 ? null : VfsUtilCore.virtualToIoFile(testsRoot.get(0));
  }

  /**
   * This constructor should be used when there is still no AndroidFacet (eg when creating a new module or Project)
   */
  public AndroidProjectPaths(@NotNull File moduleRoot, @NotNull File testDirectory, @NotNull SourceProvider sourceProvider) {
    init(sourceProvider);

    myModuleRoot = moduleRoot;
    myTestDirectory = testDirectory;
  }

  private void init(@NotNull SourceProvider sourceProvider) {
    mySrcDirectory = Iterables.getFirst(sourceProvider.getJavaDirectories(), null);

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
