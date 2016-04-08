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
import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.facet.NativeAndroidGradleFacet;
import com.android.tools.idea.model.AndroidModel;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;

/**
 * Like {@link SourceProvider}, but for IntelliJ, which means it provides
 * {@link VirtualFile} references rather than {@link File} references.
 *
 * @see AndroidSourceType
 */
public abstract class IdeaSourceProvider {
  private IdeaSourceProvider() {
  }

  @NotNull
  public static IdeaSourceProvider create(@NotNull SourceProvider provider) {
    return new Gradle(provider);
  }

  @NotNull
  private static List<IdeaSourceProvider> createAll(@NotNull List<SourceProvider> providers) {
    List<IdeaSourceProvider> ideaProviders = Lists.newArrayList();
    for (SourceProvider provider : providers) {
      ideaProviders.add(create(provider));
    }
    return ideaProviders;
  }

  @NotNull
  public static IdeaSourceProvider create(@NotNull final NativeAndroidGradleFacet facet) {
    return new Native(facet);
  }

  @NotNull
  public static IdeaSourceProvider create(@NotNull final AndroidFacet facet) {
    return new Legacy(facet);
  }

  @NotNull
  public abstract String getName();

  @Nullable
  public abstract VirtualFile getManifestFile();

  @NotNull
  public abstract Collection<VirtualFile> getJavaDirectories();

  @NotNull
  public abstract Collection<VirtualFile> getResourcesDirectories();

  @NotNull
  public abstract Collection<VirtualFile> getAidlDirectories();

  @NotNull
  public abstract Collection<VirtualFile> getRenderscriptDirectories();

  @NotNull
  public abstract Collection<VirtualFile> getJniDirectories();

  @NotNull
  public abstract Collection<VirtualFile> getJniLibsDirectories();

  @NotNull
  public abstract Collection<VirtualFile> getResDirectories();

  @NotNull
  public abstract Collection<VirtualFile> getAssetsDirectories();

  @NotNull
  public abstract Collection<VirtualFile> getShadersDirectories();

  /** {@linkplain IdeaSourceProvider} for a Gradle projects */
  private static class Gradle extends IdeaSourceProvider {
    private final SourceProvider myProvider;
    private VirtualFile myManifestFile;
    private File myManifestIoFile;

    private Gradle(@NotNull SourceProvider provider) {
      myProvider = provider;
    }

    @NotNull
    @Override
    public String getName() {
      return myProvider.getName();
    }

    @Nullable
    @Override
    public VirtualFile getManifestFile() {
      File manifestFile = myProvider.getManifestFile();
      if (myManifestFile == null || !FileUtil.filesEqual(manifestFile, myManifestIoFile)) {
        myManifestIoFile = manifestFile;
        myManifestFile = VfsUtil.findFileByIoFile(manifestFile, false);
      }

      return myManifestFile;
    }

    /** Convert a set of IO files into a set of equivalent virtual files */
    private static Collection<VirtualFile> convertFileSet(@NotNull Collection<File> fileSet) {
      Collection<VirtualFile> result = Lists.newArrayListWithCapacity(fileSet.size());
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
    public Collection<VirtualFile> getJavaDirectories() {
      return convertFileSet(myProvider.getJavaDirectories());
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getResourcesDirectories() {
      return convertFileSet(myProvider.getResourcesDirectories());
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getAidlDirectories() {
      return convertFileSet(myProvider.getAidlDirectories());
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getRenderscriptDirectories() {
      return convertFileSet(myProvider.getRenderscriptDirectories());
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getJniDirectories() {
      // Even though the model has separate methods to get the C and Cpp directories,
      // they both return the same set of folders. So we combine them here.
      Set<VirtualFile> jniDirectories = Sets.newHashSet();
      jniDirectories.addAll(convertFileSet(myProvider.getCDirectories()));
      jniDirectories.addAll(convertFileSet(myProvider.getCppDirectories()));
      return jniDirectories;
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getJniLibsDirectories() {
      return convertFileSet(myProvider.getJniLibsDirectories());
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getResDirectories() {
      // TODO: Perform some caching; this method gets called a lot!
      return convertFileSet(myProvider.getResDirectories());
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getAssetsDirectories() {
      return convertFileSet(myProvider.getAssetsDirectories());
    }

    @Override
    @NotNull
    public Collection<VirtualFile> getShadersDirectories() {
      return convertFileSet(myProvider.getShadersDirectories());
    }

    /**
     * Compares another source provider with this for equality. Returns true if the specified object is also a Gradle source provider,
     * has the same name, and the same set of source locations.
     */
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Gradle that = (Gradle)o;
      if (!myProvider.getName().equals(that.getName())) return false;
      if (!myProvider.getManifestFile().getPath().equals(that.myProvider.getManifestFile().getPath())) return false;

      return true;
    }

    /**
     * Returns the hash code for this source provider. The hash code simply provides the hash of the manifest file's location,
     * but this follows the required contract that if two source providers are equal, their hash codes will be the same.
     */
    @Override
    public int hashCode() {
      return myProvider.getManifestFile().getPath().hashCode();
    }
  }

  /** {@linkplain IdeaSourceProvider} for a Native Android Gradle project */
  private static class Native extends IdeaSourceProvider {
    @NotNull private final NativeAndroidGradleFacet myFacet;

    private Native(@NotNull NativeAndroidGradleFacet facet) {
      myFacet = facet;
    }

    @NotNull
    @Override
    public String getName() {
      return "";
    }

    @Nullable
    @Override
    public VirtualFile getManifestFile() {
      return null;
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getJavaDirectories() {
      return Collections.emptySet();
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getResourcesDirectories() {
      return Collections.emptySet();
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getAidlDirectories() {
      return Collections.emptySet();
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getRenderscriptDirectories() {
      return Collections.emptySet();
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getJniDirectories() {
      NativeAndroidGradleModel nativeAndroidGradleModel = myFacet.getNativeAndroidGradleModel();
      if (nativeAndroidGradleModel == null) {
        return Collections.emptyList();
      }

      Collection<File> sourceFolders = nativeAndroidGradleModel.getSelectedVariant().getSourceFolders();

      Collection<VirtualFile> result = Sets.newLinkedHashSetWithExpectedSize(sourceFolders.size());
      LocalFileSystem fileSystem = LocalFileSystem.getInstance();
      for (File file : sourceFolders) {
        VirtualFile virtualFile = fileSystem.findFileByIoFile(file);
        if (virtualFile != null) {
          result.add(virtualFile);
        }
      }

      return result;
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getJniLibsDirectories() {
      return Collections.emptySet();
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getResDirectories() {
      return Collections.emptySet();
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getAssetsDirectories() {
      return Collections.emptySet();
    }

    @Override
    @NotNull
    public Collection<VirtualFile> getShadersDirectories() {
      return Collections.emptySet();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Native that = (Native)o;
      return myFacet.equals(that.myFacet);
    }

    @Override
    public int hashCode() {
      return myFacet.hashCode();
    }
  }

  /** {@linkplain IdeaSourceProvider} for a legacy (non-Gradle) Android project */
  private static class Legacy extends IdeaSourceProvider {
    @NotNull private final AndroidFacet myFacet;

    private Legacy(@NotNull AndroidFacet facet) {
      myFacet = facet;
    }

    @NotNull
    @Override
    public String getName() {
      return "";
    }

    @Nullable
    @Override
    public VirtualFile getManifestFile() {
      Module module = myFacet.getModule();
      VirtualFile file = AndroidRootUtil.getFileByRelativeModulePath(module, myFacet.getProperties().MANIFEST_FILE_RELATIVE_PATH, true);
      if (file != null) {
        return file;
      }

      // Not calling AndroidRootUtil.getMainContentRoot(myFacet) because that method can
      // recurse into this same method if it can't find a content root. (This scenario
      // applies when we're looking for manifests in for example a temporary file system,
      // as tested by ResourceTypeInspectionTest#testLibraryRevocablePermission)
      VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      if (contentRoots.length == 1) {
        return contentRoots[0].findChild(ANDROID_MANIFEST_XML);
      }

      return null;
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getJavaDirectories() {
      Module module = myFacet.getModule();
      Collection<VirtualFile> dirs = new HashSet<>();
      Collections.addAll(dirs, ModuleRootManager.getInstance(module).getContentRoots());
      return dirs;
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getResourcesDirectories() {
      return Collections.emptySet();
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getAidlDirectories() {
      final VirtualFile dir = AndroidRootUtil.getAidlGenDir(myFacet);
      assert dir != null;
      return Collections.singleton(dir);
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getRenderscriptDirectories() {
      final VirtualFile dir = AndroidRootUtil.getRenderscriptGenDir(myFacet);
      assert dir != null;
      return Collections.singleton(dir);
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getJniDirectories() {
     return Collections.emptySet();
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getJniLibsDirectories() {
      return Collections.emptySet();
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getResDirectories() {
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
    public Collection<VirtualFile> getAssetsDirectories() {
      final VirtualFile dir = AndroidRootUtil.getAssetsDir(myFacet);
      assert dir != null;
      return Collections.singleton(dir);
    }

    @Override
    @NotNull
    public Collection<VirtualFile> getShadersDirectories() {
      return Collections.emptySet();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Legacy that = (Legacy)o;
      return myFacet.equals(that.myFacet);
    }

    @Override
    public int hashCode() {
      return myFacet.hashCode();
    }

  }

  /**
   * Returns a list of source providers, in the overlay order (meaning that later providers
   * override earlier providers when they redefine resources) for the currently selected variant.
   * <p>
   * Note that the list will never be empty; there is always at least one source provider.
   * <p>
   * The overlay source order is defined by the Android Gradle plugin.
   */
  @NotNull
  public static List<IdeaSourceProvider> getCurrentSourceProviders(@NotNull AndroidFacet facet) {
    if (!facet.requiresAndroidModel()) {
      return Collections.singletonList(facet.getMainIdeaSourceProvider());
    }
    AndroidModel androidModel = facet.getAndroidModel();
    if (androidModel != null) {
      return createAll(androidModel.getActiveSourceProviders());
    }
    return Collections.emptyList();
  }

  @NotNull
  public static List<IdeaSourceProvider> getCurrentTestSourceProviders(@NotNull AndroidFacet facet) {
    if (!facet.requiresAndroidModel()) {
      return Collections.emptyList();
    }
    AndroidModel androidModel = facet.getAndroidModel();
    if (androidModel != null) {
      return createAll(androidModel.getTestSourceProviders());
    }
    return Collections.emptyList();
  }

  @NotNull
  private Collection<VirtualFile> getAllSourceFolders() {
    List<VirtualFile> srcDirectories = Lists.newArrayList();
    srcDirectories.addAll(getJavaDirectories());
    srcDirectories.addAll(getResDirectories());
    srcDirectories.addAll(getAidlDirectories());
    srcDirectories.addAll(getRenderscriptDirectories());
    srcDirectories.addAll(getAssetsDirectories());
    srcDirectories.addAll(getJniDirectories());
    srcDirectories.addAll(getJniLibsDirectories());
    return srcDirectories;
  }

  @NotNull
  public static Collection<File> getAllSourceFolders(@NotNull  SourceProvider provider) {
    List<File> srcDirectories = Lists.newArrayList();
    srcDirectories.addAll(provider.getJavaDirectories());
    srcDirectories.addAll(provider.getResDirectories());
    srcDirectories.addAll(provider.getAidlDirectories());
    srcDirectories.addAll(provider.getRenderscriptDirectories());
    srcDirectories.addAll(provider.getAssetsDirectories());
    srcDirectories.addAll(provider.getCDirectories());
    srcDirectories.addAll(provider.getCppDirectories());
    srcDirectories.addAll(provider.getJniLibsDirectories());
    return srcDirectories;
  }

  /**
   * Returns true iff this SourceProvider provides the source folder that contains the given file.
   */
  public boolean containsFile(@NotNull VirtualFile file) {
    Collection<VirtualFile> srcDirectories = getAllSourceFolders();
    if (file.equals(getManifestFile())) {
      return true;
    }

    for (VirtualFile container : srcDirectories) {
      if (!container.exists()) {
        continue;
      }

      if (VfsUtilCore.isAncestor(container, file, false /* allow them to be the same */)) {
        return true;
      }

      // Check the flavor root directories
      if (file.equals(container.getParent())) {
        return true;
      }
    }
    return false;
  }


  /**
   * Returns true if this SourceProvider has one or more source folders contained by (or equal to)
   * the given folder.
   */
  public static boolean isContainedBy(@NotNull SourceProvider provider, @NotNull File targetFolder) {
    Collection<File> srcDirectories = getAllSourceFolders(provider);
    for (File container : srcDirectories) {
      if (FileUtil.isAncestor(targetFolder, container, false)) {
        return true;
      }

      if (!container.exists()) {
        continue;
      }

      if (VfsUtilCore.isAncestor(targetFolder, container, false /* allow them to be the same */)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true iff this SourceProvider provides the source folder that contains the given file.
   */
  public static boolean containsFile(@NotNull SourceProvider provider, @NotNull File file) {
    Collection<File> srcDirectories = getAllSourceFolders(provider);
    if (FileUtil.filesEqual(provider.getManifestFile(), file)) {
      return true;
    }

    for (File container : srcDirectories) {
      // Check the flavor root directories
      File parent = container.getParentFile();
      if (parent != null && parent.isDirectory() && FileUtil.filesEqual(parent, file)) {
        return true;
      }

      // Don't do ancestry checking if this file doesn't exist
      if (!container.exists()) {
        continue;
      }

      if (VfsUtilCore.isAncestor(container, file, false /* allow them to be the same */)) {
        return true;
      }
    }
    return false;
  }


  /**
   * Returns true if this SourceProvider has one or more source folders contained by (or equal to)
   * the given folder.
   */
  public boolean isContainedBy(@NotNull VirtualFile targetFolder) {
    Collection<VirtualFile> srcDirectories = getAllSourceFolders();
    for (VirtualFile container : srcDirectories) {
      if (!container.exists()) {
        continue;
      }

      if (VfsUtilCore.isAncestor(targetFolder, container, false /* allow them to be the same */)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns an iterable of all source providers, for the given facet,
   * in the overlay order (meaning that later providers
   * override earlier providers when they redefine resources.)
   * <p>
   * Note that the list will never be empty; there is always at least one source provider.
   * <p>
   * The overlay source order is defined by the Android Gradle plugin.
   */
  @NotNull
  public static List<SourceProvider> getAllSourceProviders(@NotNull AndroidFacet facet) {
    if (!facet.requiresAndroidModel() || facet.getAndroidModel() == null) {
      return Collections.singletonList(facet.getMainSourceProvider());
    }

    return facet.getAndroidModel().getAllSourceProviders();
  }

  /**
   * Returns a list of all IDEA source providers, for the given facet, in the overlay order
   * (meaning that later providers override earlier providers when they redefine resources.)
   * <p>
   * Note that the list will never be empty; there is always at least one source provider.
   * <p>
   * The overlay source order is defined by the Android Gradle plugin.
   *
   * This method should be used when only on-disk source sets are required. It will return
   * empty source sets for all other source providers (since VirtualFiles MUST exist on disk).
   */
  @NotNull
  public static List<IdeaSourceProvider> getAllIdeaSourceProviders(@NotNull AndroidFacet facet) {
    if (!facet.requiresAndroidModel() || facet.getAndroidModel() == null) {
      return Collections.singletonList(facet.getMainIdeaSourceProvider());
    }
    return createAll(getAllSourceProviders(facet));
  }

  /**
   * Returns a list of all IDEA source providers that contain, or are contained by, the given file.
   * For example, with the file structure:
   * <pre>
   * src
   *   main
   *     aidl
   *       myfile.aidl
   *   free
   *     aidl
   *       myoverlay.aidl
   * </pre>
   *
   * With target file == "myoverlay.aidl" the returned list would be ['free'], but if target file == "src",
   * the returned list would be ['main', 'free'] since both of those source providers have source folders which
   * are descendants of "src."
   */
  @NotNull
  public static List<IdeaSourceProvider> getIdeaSourceProvidersForFile(@NotNull AndroidFacet facet,
                                                                       @Nullable VirtualFile targetFolder,
                                                                       @Nullable IdeaSourceProvider defaultIdeaSourceProvider) {
    List<IdeaSourceProvider> sourceProviderList = Lists.newArrayList();


    if (targetFolder != null) {
      // Add source providers that contain the file (if any) and any that have files under the given folder
      for (IdeaSourceProvider provider : getAllIdeaSourceProviders(facet)) {
        if (provider.containsFile(targetFolder) || provider.isContainedBy(targetFolder)) {
          sourceProviderList.add(provider);
        }
      }
    }

    if (sourceProviderList.isEmpty() && defaultIdeaSourceProvider != null) {
      sourceProviderList.add(defaultIdeaSourceProvider);
    }
    return sourceProviderList;
  }

  /**
   * Returns a list of all source providers that contain, or are contained by, the given file.
   * For example, with the file structure:
   * <pre>
   * src
   *   main
   *     aidl
   *       myfile.aidl
   *   free
   *     aidl
   *       myoverlay.aidl
   * </pre>
   *
   * With target file == "myoverlay.aidl" the returned list would be ['free'], but if target file == "src",
   * the returned list would be ['main', 'free'] since both of those source providers have source folders which
   * are descendants of "src."
   */
  @NotNull
  public static List<SourceProvider> getSourceProvidersForFile(@NotNull AndroidFacet facet, @Nullable VirtualFile targetFolder,
                                                               @Nullable SourceProvider defaultSourceProvider) {
    List<SourceProvider> sourceProviderList = Lists.newArrayList();
    if (targetFolder != null) {
      File targetIoFolder = VfsUtilCore.virtualToIoFile(targetFolder);
      // Add source providers that contain the file (if any) and any that have files under the given folder
      for (SourceProvider provider : getAllSourceProviders(facet)) {
        if (containsFile(provider, targetIoFolder) || isContainedBy(provider, targetIoFolder)) {
          sourceProviderList.add(provider);
        }
      }
    }

    if (sourceProviderList.isEmpty() && defaultSourceProvider != null) {
      sourceProviderList.add(defaultSourceProvider);
    }
    return sourceProviderList;
  }

  /** Returns true if the given candidate file is a manifest file in the given module */
  public static boolean isManifestFile(@NotNull AndroidFacet facet, @Nullable VirtualFile candidate) {
    if (candidate == null) {
      return false;
    }

    if (facet.requiresAndroidModel()) {
      for (IdeaSourceProvider provider : getCurrentSourceProviders(facet)) {
        if (candidate.equals(provider.getManifestFile())) {
          return true;
        }
      }
      return false;
    } else {
      return candidate.equals(facet.getMainIdeaSourceProvider().getManifestFile());
    }
  }

  /** Returns the manifest files in the given module */
  @NotNull
  public static List<VirtualFile> getManifestFiles(@NotNull AndroidFacet facet) {
    VirtualFile main = facet.getMainIdeaSourceProvider().getManifestFile();
    if (!facet.requiresAndroidModel()) {
      return main != null ? Collections.singletonList(main) : Collections.emptyList();
    }

    List<VirtualFile> files = Lists.newArrayList();
    for (IdeaSourceProvider provider : getCurrentSourceProviders(facet)) {
      VirtualFile manifest = provider.getManifestFile();
      if (manifest != null) {
        files.add(manifest);
      }
    }
    return files;
  }

  public static Function<IdeaSourceProvider, List<VirtualFile>> MANIFEST_PROVIDER = provider -> {
    VirtualFile manifestFile = provider.getManifestFile();
    return manifestFile == null ? Collections.emptyList() : Collections.singletonList(manifestFile);
  };

  public static Function<IdeaSourceProvider, List<VirtualFile>> RES_PROVIDER =
    provider -> Lists.newArrayList(provider.getResDirectories());

  public static Function<IdeaSourceProvider, List<VirtualFile>> JAVA_PROVIDER =
    provider -> Lists.newArrayList(provider.getJavaDirectories());

  public static Function<IdeaSourceProvider, List<VirtualFile>> RESOURCES_PROVIDER =
    provider -> Lists.newArrayList(provider.getResourcesDirectories());

  public static Function<IdeaSourceProvider, List<VirtualFile>> AIDL_PROVIDER =
    provider -> Lists.newArrayList(provider.getAidlDirectories());

  public static Function<IdeaSourceProvider, List<VirtualFile>> JNI_PROVIDER =
    provider -> Lists.newArrayList(provider.getJniDirectories());

  public static Function<IdeaSourceProvider, List<VirtualFile>> JNI_LIBS_PROVIDER =
    provider -> Lists.newArrayList(provider.getJniLibsDirectories());

  public static Function<IdeaSourceProvider, List<VirtualFile>> ASSETS_PROVIDER =
    provider -> Lists.newArrayList(provider.getAssetsDirectories());

  public static Function<IdeaSourceProvider, List<VirtualFile>> RENDERSCRIPT_PROVIDER =
    provider -> Lists.newArrayList(provider.getRenderscriptDirectories());

  public static Function<IdeaSourceProvider, List<VirtualFile>> SHADERS_PROVIDER =
    provider -> Lists.newArrayList(provider.getShadersDirectories());
}
