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

import com.android.ide.common.gradle.model.IdeLibrary;
import com.android.ide.common.rendering.api.AssetRepository;
import com.android.projectmodel.ExternalLibrary;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.fonts.DownloadableFontCacheService;
import com.android.tools.idea.projectsystem.IdeaSourceProvider;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.sampledata.datasource.ResourceContent;
import com.google.common.collect.Streams;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.SourceProviderManager;
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds an asset in all the asset directories and returns the input stream.
 */
public class AssetRepositoryImpl extends AssetRepository {
  private static File myFrameworkResDirOrJar;
  private AndroidFacet myFacet;

  public AssetRepositoryImpl(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @Override
  public boolean isSupported() {
    return true;
  }

  /**
   * @param mode one of ACCESS_UNKNOWN, ACCESS_STREAMING, ACCESS_RANDOM or ACCESS_BUFFER (int values 0-3).
   */
  @Override
  @Nullable
  public InputStream openAsset(@NotNull String path, int mode) throws IOException {
    assert myFacet != null;

    return getDirectories(myFacet, IdeaSourceProvider::getAssetsDirectories, IdeLibrary::getAssetsFolder)
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
    File fontCachePathFile = DownloadableFontCacheService.getInstance().getFontPath();
    if (fontCachePathFile == null) {
      return false;
    }

    VirtualFile fontCachePath = VirtualFileManager.getInstance().findFileByUrl("file://" + fontCachePathFile.getAbsolutePath());
    if (fontCachePath == null) {
      return false;
    }
    return VfsUtilCore.isAncestor(fontCachePath, file, true);
  }

  /**
   * It takes an absolute path that does not point to an asset and opens the file. Currently the access
   * is restricted to files under the resources directories and the downloadable font cache directory.
   *
   * @param cookie ignored
   * @param path the path pointing to a file on disk, or to a ZIP file entry. In the latter case the path
   *     has the following format: "apk:<i>path_to_zip_file</i>!/<i>path_to_zip_entry</i>
   * @param mode ignored
   */
  @Override
  @Nullable
  public InputStream openNonAsset(int cookie, @NotNull String path, int mode) throws IOException {
    assert myFacet != null;

    if (path.startsWith("apk:") || path.startsWith("jar:")) {
      return new ByteArrayInputStream(FileResourceReader.readBytes(path));
    }

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

    return getDirectories(myFacet, IdeaSourceProvider::getResDirectories, IdeLibrary::getResFolder)
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
   * Checks if the given path points to a file resource.
   */
  @Override
  public boolean isFileResource(@NotNull String path) {
    return IdeResourcesUtil.isFileResource(path);
  }

  /**
   * Returns a specific set of directories for source providers and aars. The method receives the facet and two methods.
   * One method to extract the directories from the {@link IdeaSourceProvider} and one to extract them from the {@link Library}.
   * For example, to extract the assets directories, you would need to pass {@link IdeaSourceProvider::getAssetsDirectories} and
   * {@link Library::getAssetsFolder}.
   */
  @NotNull
  private static Stream<VirtualFile> getDirectories(@NotNull AndroidFacet facet,
                                                    @NotNull Function<IdeaSourceProvider, Iterable<VirtualFile>> sourceMapper,
                                                    @NotNull Function<IdeLibrary, String> aarMapper) {
    Stream<VirtualFile> dirsFromSources =
      Stream.concat(Stream.of(facet), AndroidUtils.getAllAndroidDependencies(facet.getModule(), true).stream())
        .flatMap(f -> SourceProviderManager.getInstance(f).getCurrentAndSomeFrequentlyUsedInactiveSourceProviders().stream())
        .distinct()
        .map(sourceMapper)
        .flatMap(Streams::stream);

    VirtualFileManager manager = VirtualFileManager.getInstance();

    // TODO(b/178424022) Library dependencies from project system should be sufficient for asset folder dependencies.
    //  This should be removed once the bug is resolved.
    Stream<VirtualFile> dirsFromAars = ResourceRepositoryManager.findAarLibraries(facet).stream()
      .map(aarMapper)
      .map(path -> manager.findFileByUrl("file://" + path))
      .filter(Objects::nonNull);

    Stream<VirtualFile> libraryDepAars = Stream.empty();
    if (StudioFlags.NELE_ASSET_REPOSITORY_INCLUDE_AARS_THROUGH_PROJECT_SYSTEM.get()) {
      libraryDepAars = ProjectSystemUtil.getModuleSystem(facet.getModule()).getResolvedLibraryDependencies().stream()
        .map(ExternalLibrary::getLocation)
        .filter((location) -> location != null && location.getFileName().endsWith(".aar"))
        .map(path -> manager.findFileByUrl("file://" + path.getPortablePath()))
        .filter(Objects::nonNull);
    }

    Stream<VirtualFile> frameworkDirs = Stream.of(getSdkResDirOrJar(facet))
      .filter(Objects::nonNull)
      .map(path -> manager.findFileByUrl("file://" + path))
      .filter(Objects::nonNull);

    Stream<VirtualFile> sampleDataDirs = Stream.of(
      ResourceContent.getSampleDataBaseDir(),
      ResourceContent.getSampleDataUserDir(facet)
    )
      .filter(Objects::nonNull)
      .map(dir -> manager.findFileByUrl("file://" + dir.getAbsolutePath()))
      .filter(Objects::nonNull);

    return Stream.of(dirsFromSources, dirsFromAars, frameworkDirs, sampleDataDirs, libraryDepAars)
      .flatMap(stream -> stream);
  }

  @Nullable
  private static File getSdkResDirOrJar(@NotNull AndroidFacet facet) {
    if (myFrameworkResDirOrJar == null) {
      ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(facet);
      IAndroidTarget target = manager.getHighestApiTarget();
      if (target == null) {
        return null;
      }
      CompatibilityRenderTarget compatibilityTarget = StudioEmbeddedRenderTarget.getCompatibilityTarget(target);
      myFrameworkResDirOrJar = compatibilityTarget.getFile(IAndroidTarget.RESOURCES);
    }
    return myFrameworkResDirOrJar;
  }
}
