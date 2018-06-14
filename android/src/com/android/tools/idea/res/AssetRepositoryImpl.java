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

import com.android.builder.model.level2.Library;
import com.android.ide.common.rendering.api.AssetRepository;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.fonts.DownloadableFontCacheService;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.sampledata.datasource.ResourceContent;
import com.google.common.io.Files;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Finds an asset in all the asset directories and returns the input stream.
 */
public class AssetRepositoryImpl extends AssetRepository implements Disposable {
  private static File myFrameworkResDir;
  private AndroidFacet myFacet;

  public AssetRepositoryImpl(@NotNull AndroidFacet facet) {
    myFacet = facet;

    // LayoutLib keeps a static reference to the AssetRepository that will be replaced once a new project is opened.
    // In unit tests this will trigger a memory leak error. This makes sure that we do not keep the reference to the facet so
    // the unit test is happy.
    Disposer.register(myFacet, this);
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

    return getDirectories(myFacet, IdeaSourceProvider::getAssetsDirectories, Library::getAssetsFolder)
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

    if (path.startsWith("apk:")) {
      return FileResourceOpener.open(path);
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

    return getDirectories(myFacet, IdeaSourceProvider::getResDirectories, Library::getResFolder)
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
    return ResourceHelper.isFileResource(path);
  }

  /**
   * Returns a specific set of directories for source providers and aars. The method receives the facet and two methods.
   * One method to extract the directories from the {@link IdeaSourceProvider} and one to extract them from the {@link Library}.
   * For example, to extract the assets directories, you would need to pass {@link IdeaSourceProvider::getAssetsDirectories} and
   * {@link Library::getAssetsFolder}.
   */
  @NotNull
  private static Stream<VirtualFile> getDirectories(@NotNull AndroidFacet facet,
                                                    @NotNull Function<IdeaSourceProvider, Collection<VirtualFile>> sourceMapper,
                                                    @NotNull Function<Library, String> aarMapper) {
    Stream<VirtualFile> dirsFromSources =
      Stream.concat(Stream.of(facet), AndroidUtils.getAllAndroidDependencies(facet.getModule(), true).stream())
        .flatMap(f -> IdeaSourceProvider.getAllIdeaSourceProviders(f).stream())
        .distinct()
        .map(sourceMapper)
        .flatMap(Collection::stream);

    VirtualFileManager manager = VirtualFileManager.getInstance();
    Stream<VirtualFile> dirsFromAars = ResourceRepositoryManager.findAarLibraries(facet).stream()
      .map(aarMapper)
      .map(path -> manager.findFileByUrl("file://" + path))
      .filter(Objects::nonNull);

    Stream<VirtualFile> frameworkDirs = Stream.of(getSdkResFolder(facet))
      .filter(Objects::nonNull)
      .map(path -> manager.findFileByUrl("file://" + path))
      .filter(Objects::nonNull);

    Stream<VirtualFile> sampleDataDirs = Stream.of(ResourceContent.getSampleDataBaseDir())
       .filter(Objects::nonNull)
       .map(dir -> manager.findFileByUrl("file://" + dir.getAbsolutePath()))
       .filter(Objects::nonNull);

    return Stream.of(dirsFromSources, dirsFromAars, frameworkDirs, sampleDataDirs)
      .flatMap(stream -> stream);
  }

  @Nullable
  private static File getSdkResFolder(@NotNull AndroidFacet facet) {
    if (myFrameworkResDir == null) {
      ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(facet);
      IAndroidTarget target = manager.getHighestApiTarget();
      if (target == null) {
        return null;
      }
      CompatibilityRenderTarget compatibilityTarget = StudioEmbeddedRenderTarget.getCompatibilityTarget(target);
      String sdkPlatformPath = Files.simplifyPath(compatibilityTarget.getLocation());
      myFrameworkResDir = new File(sdkPlatformPath + "/data/res");
    }
    return myFrameworkResDir;
  }

  @Override
  public void dispose() {
    myFacet = null;
  }
}
