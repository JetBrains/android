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

import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;

import com.android.ide.common.util.PathString;
import com.android.projectmodel.ExternalAndroidLibrary;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.fonts.StudioDownloadableFontCacheService;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.DependencyScopeType;
import com.android.tools.idea.projectsystem.IdeaSourceProvider;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.projectsystem.SourceProviderManager;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.android.tools.idea.sampledata.datasource.ResourceContent;
import com.android.tools.res.AssetFileOpener;
import com.android.tools.sdk.CompatibilityRenderTarget;
import com.google.common.collect.Streams;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds an asset in all the asset directories and returns the input stream.
 */
public class StudioAssetFileOpener implements AssetFileOpener {
  private static Path myFrameworkResDirOrJar;
  private AndroidFacet myFacet;

  public StudioAssetFileOpener(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  /**
   * @param path path to the asset file.
   */
  @Override
  @Nullable
  public InputStream openAssetFile(@NotNull String path) {
    assert myFacet != null;

    return getDirectories(myFacet, IdeaSourceProvider::getAssetsDirectories, ExternalAndroidLibrary::getAssetsFolder)
      .map(assetDir -> assetDir.findFileByRelativePath(path))
      .map(assetDir -> {
        if (assetDir == null) {
          return null;
        }

        try {
          return assetDir.getInputStream();
        }
        catch (IOException e) {
          return null;
        }
      })
      .filter(Objects::nonNull)
      .findAny()
      .orElse(null);
  }

  /**
   * Returns whether the given file is contained within the downloadable fonts cache
   */
  private static boolean isCachedFontFile(@NotNull VirtualFile file) {
    File fontCachePathFile = StudioDownloadableFontCacheService.getInstance().getFontCachePath();
    if (fontCachePathFile == null) {
      return false;
    }

    VirtualFile fontCachePath = VirtualFileManager.getInstance().findFileByNioPath(fontCachePathFile.toPath());
    if (fontCachePath == null) {
      return false;
    }
    return VfsUtilCore.isAncestor(fontCachePath, file, true);
  }

  /**
   * It takes an absolute path that does not point to an asset and opens the file. Currently the access
   * is restricted to files under the resources directories and the downloadable font cache directory.
   *
   * @param path the path pointing to a file on disk.
   */
  @Override
  @Nullable
  public InputStream openNonAssetFile(@NotNull String path) {
    assert myFacet != null;

    String url;
    if (path.startsWith("file://")) {
      url = path;
    } else {
      if (path.startsWith("file:")) {
        path = path.substring("file:".length());
      }
      url = "file://" + path;
    }

    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    if (file == null) {
      return null;
    }

    return getDirectories(myFacet,
                          IdeaSourceProvider::getResDirectories,
                          it -> it.getResFolder() != null ? it.getResFolder().getRoot() : null)
      .filter(resDir -> VfsUtilCore.isAncestor(resDir, file, true))
      .map(resDir -> {
        try {
          return file.getInputStream();
        }
        catch (IOException e) {
          return null;
        }
      })
      .findAny()
      .orElseGet(() -> {
        if (isCachedFontFile(file)) {
          try {
            return file.getInputStream();
          }
          catch (IOException ignore) {
          }
        }
        return null;
      });
  }

  /**
   * Returns a specific set of directories for source providers and aars. The method receives the facet and two methods.
   * One method to extract the directories from the {@link IdeaSourceProvider} and one to extract them from the
   * {@link ExternalAndroidLibrary}. For example, to extract the assets directories, you would need to pass
   * {@link IdeaSourceProvider::getAssetsDirectories} and {@link ExternalAndroidLibrary::getAssetsFolder}.
   */
  @NotNull
  private static Stream<VirtualFile> getDirectories(@NotNull AndroidFacet facet,
                                                    @NotNull Function<IdeaSourceProvider, Iterable<VirtualFile>> sourceMapper,
                                                    @NotNull Function<ExternalAndroidLibrary, PathString> aarMapper) {
    Stream<VirtualFile> dirsFromSources =
      Stream.concat(Stream.of(facet), AndroidDependenciesCache.getAllAndroidDependencies(facet.getModule(), true).stream())
        .flatMap(f -> {
                   SourceProviders sourceProviders = SourceProviderManager.getInstance(f);
                   List<IdeaSourceProvider> providers = new ArrayList<>();
                   providers.addAll(sourceProviders.getCurrentAndSomeFrequentlyUsedInactiveSourceProviders());
                   providers.add(sourceProviders.getGeneratedSources());
                   return providers.stream();
                 }
        )
        .distinct()
        .map(sourceMapper)
        .flatMap(Streams::stream);

    VirtualFileManager manager = VirtualFileManager.getInstance();

    // TODO(b/178424022) Library dependencies from project system should be sufficient for asset folder dependencies.
    //  This should be removed once the bug is resolved.
    Stream<VirtualFile> dirsFromAars = findAarLibraries(facet).stream()
      .map(aarMapper)
      .filter(Objects::nonNull)
      .map(path -> manager.findFileByUrl("file://" + path.getPortablePath()))
      .filter(Objects::nonNull);

    Stream<VirtualFile> libraryDepAars = Stream.empty();
    if (StudioFlags.NELE_ASSET_REPOSITORY_INCLUDE_AARS_THROUGH_PROJECT_SYSTEM.get()) {
      libraryDepAars = getModuleSystem(facet.getModule()).getAndroidLibraryDependencies(DependencyScopeType.MAIN).stream()
        .map(ExternalAndroidLibrary::getLocation)
        .filter((location) -> location != null && location.getFileName().endsWith(".aar"))
        .map(path -> manager.findFileByUrl("file://" + path.getPortablePath()))
        .filter(Objects::nonNull);
    }

    Stream<VirtualFile> frameworkDirs = Stream.of(getSdkResDirOrJar(facet))
      .filter(Objects::nonNull)
      .map(path -> manager.findFileByUrl("file://" + path))
      .filter(Objects::nonNull);

    AndroidFacet holderFacet = AndroidFacet.getInstance(ModuleSystemUtil.getHolderModule(facet.getModule()));
    Stream<VirtualFile> sampleDataDirs = Stream.of(
      ResourceContent.getSampleDataBaseDir(),
      ResourceContent.getSampleDataUserDir(facet),
      (holderFacet == null) ? null : ResourceContent.getSampleDataUserDir(holderFacet)
    )
      .filter(Objects::nonNull)
      .distinct()
      .map(dir -> manager.findFileByUrl("file://" + dir.toAbsolutePath()))
      .filter(Objects::nonNull);

    return Stream.of(dirsFromSources, dirsFromAars, frameworkDirs, sampleDataDirs, libraryDepAars)
      .flatMap(stream -> stream);
  }

  @Nullable
  private static Path getSdkResDirOrJar(@NotNull AndroidFacet facet) {
    if (myFrameworkResDirOrJar == null) {
      ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(facet.getModule());
      IAndroidTarget target = manager.getHighestApiTarget();
      if (target == null) {
        return null;
      }
      CompatibilityRenderTarget compatibilityTarget = StudioEmbeddedRenderTarget.getCompatibilityTarget(target);
      myFrameworkResDirOrJar = compatibilityTarget.getPath(IAndroidTarget.RESOURCES);
    }
    return myFrameworkResDirOrJar;
  }

  @NotNull
  public static Collection<ExternalAndroidLibrary> findAarLibraries(@NotNull AndroidFacet facet) {
    List<ExternalAndroidLibrary> libraries = new ArrayList<>();
    if (AndroidModel.isRequired(facet)) {
      AndroidModuleSystem androidModuleSystem = getModuleSystem(facet);
      List<AndroidFacet> dependentFacets = AndroidDependenciesCache.getAllAndroidDependencies(facet.getModule(), true);
      addLibraries(libraries, androidModuleSystem);
      for (AndroidFacet dependentFacet : dependentFacets) {
        AndroidModuleSystem dependentModuleSystem = getModuleSystem(dependentFacet);
        addLibraries(libraries, dependentModuleSystem);
      }
    }
    return libraries;
  }

  private static void addLibraries(@NotNull List<ExternalAndroidLibrary> list, @NotNull AndroidModuleSystem androidModuleSystem) {
    list.addAll(androidModuleSystem.getAndroidLibraryDependencies(DependencyScopeType.MAIN));
  }
}
