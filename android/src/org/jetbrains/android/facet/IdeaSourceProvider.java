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
package org.jetbrains.android.facet;

import com.android.builder.model.SourceProvider;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Like {@link SourceProvider}, but for IntelliJ, which means it provides
 * {@link VirtualFile} references rather than {@link File} references.
 */
public abstract class IdeaSourceProvider {
  private IdeaSourceProvider() {
  }

  @NotNull
  public static IdeaSourceProvider create(@NotNull SourceProvider provider) {
    return new IdeaSourceProvider.Gradle(provider);
  }

  @NotNull
  public static IdeaSourceProvider create(@NotNull final AndroidFacet facet) {
    return new IdeaSourceProvider.Legacy(facet);
  }

  @Nullable
  public abstract VirtualFile getManifestFile();

  @NotNull
  public abstract Set<VirtualFile> getJavaDirectories();

  @NotNull
  public abstract Set<VirtualFile> getResourcesDirectories();

  @NotNull
  public abstract Set<VirtualFile> getAidlDirectories();

  @NotNull
  public abstract Set<VirtualFile> getRenderscriptDirectories();

  @NotNull
  public abstract Set<VirtualFile> getJniDirectories();

  @NotNull
  public abstract Set<VirtualFile> getResDirectories();

  @NotNull
  public abstract Set<VirtualFile> getAssetsDirectories();

  /** {@linkplain IdeaSourceProvider} for a Gradle projects */
  private static class Gradle extends IdeaSourceProvider {
    private final SourceProvider myProvider;
    private VirtualFile myManifestFile;
    private File myManifestIoFile;

    private Gradle(@NotNull SourceProvider provider) {
      myProvider = provider;
    }

    @Nullable
    @Override
    public VirtualFile getManifestFile() {
      File manifestFile = myProvider.getManifestFile();
      if (myManifestFile == null || !FileUtil.filesEqual(manifestFile, myManifestIoFile)) {
        myManifestIoFile = manifestFile;
        myManifestFile = LocalFileSystem.getInstance().findFileByIoFile(manifestFile);
      }

      return myManifestFile;
    }

    /** Convert a set of IO files into a set of equivalent virtual files */
    private static Set<VirtualFile> convertFileSet(@NotNull Collection<File> fileSet) {
      Set<VirtualFile> result = Sets.newHashSetWithExpectedSize(fileSet.size());
      LocalFileSystem fileSystem = LocalFileSystem.getInstance();
      for (File file : fileSet) {
        VirtualFile virtualFile = fileSystem.findFileByIoFile(file);
        if (virtualFile != null) {
          result.add(virtualFile);
        }
      }
      return result;
    }

    @NotNull
    @Override
    public Set<VirtualFile> getJavaDirectories() {
      return convertFileSet(myProvider.getJavaDirectories());
    }

    @NotNull
    @Override
    public Set<VirtualFile> getResourcesDirectories() {
      return convertFileSet(myProvider.getResourcesDirectories());
    }

    @NotNull
    @Override
    public Set<VirtualFile> getAidlDirectories() {
      return convertFileSet(myProvider.getAidlDirectories());
    }

    @NotNull
    @Override
    public Set<VirtualFile> getRenderscriptDirectories() {
      return convertFileSet(myProvider.getRenderscriptDirectories());
    }

    @NotNull
    @Override
    public Set<VirtualFile> getJniDirectories() {
      return convertFileSet(myProvider.getJniDirectories());
    }

    @NotNull
    @Override
    public Set<VirtualFile> getResDirectories() {
      // TODO: Perform some caching; this method gets called a lot!
      return convertFileSet(myProvider.getResDirectories());
    }

    @NotNull
    @Override
    public Set<VirtualFile> getAssetsDirectories() {
      return convertFileSet(myProvider.getAssetsDirectories());
    }
  }

  /** {@linkplain IdeaSourceProvider} for a legacy (non-Gradle) Android project */
  private static class Legacy extends IdeaSourceProvider {
    private final AndroidFacet myFacet;

    private Legacy(@NotNull AndroidFacet facet) {
      myFacet = facet;
    }

    @Nullable
    @Override
    public VirtualFile getManifestFile() {
      return AndroidRootUtil.getFileByRelativeModulePath(myFacet.getModule(), myFacet.getProperties().MANIFEST_FILE_RELATIVE_PATH, true);
    }

    @NotNull
    @Override
    public Set<VirtualFile> getJavaDirectories() {
      Module module = myFacet.getModule();
      Set<VirtualFile> dirs = new HashSet<VirtualFile>();
      Collections.addAll(dirs, ModuleRootManager.getInstance(module).getContentRoots());
      return dirs;
    }

    @NotNull
    @Override
    public Set<VirtualFile> getResourcesDirectories() {
      return Collections.emptySet();
    }

    @NotNull
    @Override
    public Set<VirtualFile> getAidlDirectories() {
      final VirtualFile dir = AndroidRootUtil.getAidlGenDir(myFacet);
      assert dir != null;
      return Collections.singleton(dir);
    }

    @NotNull
    @Override
    public Set<VirtualFile> getRenderscriptDirectories() {
      final VirtualFile dir = AndroidRootUtil.getRenderscriptGenDir(myFacet);
      assert dir != null;
      return Collections.singleton(dir);
    }

    @NotNull
    @Override
    public Set<VirtualFile> getJniDirectories() {
      return Collections.emptySet();
    }

    @NotNull
    @Override
    public Set<VirtualFile> getResDirectories() {
      String resRelPath = myFacet.getProperties().RES_FOLDER_RELATIVE_PATH;
      final VirtualFile dir =  AndroidRootUtil.getFileByRelativeModulePath(myFacet.getModule(), resRelPath, true);
      if (dir != null) {
        return Collections.singleton(dir);
      } else {
        return Collections.emptySet();
      }
    }

    @NotNull
    @Override
    public Set<VirtualFile> getAssetsDirectories() {
      final VirtualFile dir = AndroidRootUtil.getAssetsDir(myFacet);
      assert dir != null;
      return Collections.singleton(dir);
    }
  }

  /** Returns true if the given candidate file is a manifest file in the given module */
  public static boolean isManifestFile(@NotNull AndroidFacet facet, @Nullable VirtualFile candidate) {
    if (candidate == null) {
      return false;
    }

    if (candidate == facet.getMainIdeaSourceSet().getManifestFile()) {
      return true;
    }

    if (!facet.isGradleProject()) {
      return false;
    }

    List<IdeaSourceProvider> flavorSourceSets = facet.getIdeaFlavorSourceSets();
    if (flavorSourceSets != null) {
      for (IdeaSourceProvider provider : flavorSourceSets) {
        if (candidate == provider.getManifestFile()) {
          return true;
        }
      }
    }
    IdeaSourceProvider buildTypeSourceSet = facet.getIdeaBuildTypeSourceSet();
    if (buildTypeSourceSet != null) {
      if (candidate == buildTypeSourceSet.getManifestFile()) {
        return true;
      }
    }
    IdeaSourceProvider multiFlavorSourceSet = facet.getIdeaMultiFlavorSourceProvider();
    if (multiFlavorSourceSet != null) {
      if (candidate == multiFlavorSourceSet.getManifestFile()) {
        return true;
      }
    }
    IdeaSourceProvider variantSourceSet = facet.getIdeaVariantSourceProvider();
    if (variantSourceSet != null) {
      if (candidate == variantSourceSet.getManifestFile()) {
        return true;
      }
    }

    return false;
  }

  /** Returns the manifest files in the given module */
  @NotNull
  public static List<VirtualFile> getManifestFiles(@NotNull AndroidFacet facet) {
    VirtualFile main = facet.getMainIdeaSourceSet().getManifestFile();
    if (!facet.isGradleProject()) {
      return main != null ? Collections.singletonList(main) : Collections.<VirtualFile>emptyList();
    }
    List<VirtualFile> files = Lists.newArrayList();
    if (main != null) {
      files.add(main);
    }

    List<IdeaSourceProvider> flavorSourceSets = facet.getIdeaFlavorSourceSets();
    if (flavorSourceSets != null) {
      for (IdeaSourceProvider provider : flavorSourceSets) {
        VirtualFile flavorManifest = provider.getManifestFile();
        if (flavorManifest != null) {
          files.add(flavorManifest);
        }
      }
    }
    IdeaSourceProvider multiFlavorSourceSet = facet.getIdeaMultiFlavorSourceProvider();
    if (multiFlavorSourceSet != null) {
      VirtualFile multiFlavorManifest = multiFlavorSourceSet.getManifestFile();
      if (multiFlavorManifest != null) {
        files.add(multiFlavorManifest);
      }
    }
    IdeaSourceProvider buildTypeSourceSet = facet.getIdeaBuildTypeSourceSet();
    if (buildTypeSourceSet != null) {
      VirtualFile buildTypeManifest = buildTypeSourceSet.getManifestFile();
      if (buildTypeManifest != null) {
        files.add(buildTypeManifest);
      }
    }
    IdeaSourceProvider variantSourceSet = facet.getIdeaVariantSourceProvider();
    if (variantSourceSet != null) {
      VirtualFile variantManifest = variantSourceSet.getManifestFile();
      if (variantManifest != null) {
        files.add(variantManifest);
      }
    }
    return files;
  }
}
