/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res.aar;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.DuplicateDataException;
import com.android.ide.common.resources.MergingException;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceMerger;
import com.android.ide.common.resources.ResourceRepositories;
import com.android.ide.common.resources.ResourceSet;
import com.android.ide.common.symbols.SymbolIo;
import com.android.ide.common.symbols.SymbolTable;
import com.android.ide.common.util.PathString;
import com.android.ide.common.xml.AndroidManifestParser;
import com.android.ide.common.xml.ManifestData;
import com.android.projectmodel.ResourceFolder;
import com.android.resources.ResourceType;
import com.android.tools.idea.log.LogWrapper;
import com.android.utils.ILogger;
import com.google.common.base.Preconditions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NullableLazyValue;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A resource repository representing unpacked contents of a non-namespaced AAR.
 */
public class AarSourceResourceRepository extends AbstractAarResourceRepository {
  private static final Logger LOG = Logger.getInstance(AarSourceResourceRepository.class);

  @NotNull protected final File myResourceDirectory;
  /** @see #getIdsFromRTxt(). */
  @Nullable private Set<String> myRTxtIds;
  /** The package name read on-demand from the manifest. */
  @NotNull private final NullableLazyValue<String> myManifestPackageName;

  protected AarSourceResourceRepository(@NotNull File resourceDirectory, @NotNull ResourceNamespace namespace,
                                        @Nullable Set<String> rTxtDeclaredIds, @NotNull String libraryName) {
    super(namespace, libraryName);
    myResourceDirectory = resourceDirectory;
    myRTxtIds = rTxtDeclaredIds;

    myManifestPackageName = NullableLazyValue.createValue(() -> {
      File manifest = new File(myResourceDirectory.getParentFile(), FN_ANDROID_MANIFEST_XML);
      if (!manifest.exists()) {
        return null;
      }

      try {
        ManifestData manifestData = AndroidManifestParser.parse(manifest.toPath());
        return manifestData.getPackage();
      }
      catch (IOException e) {
        LOG.error("Failed to read manifest " + manifest.getAbsolutePath() + " for library " + getLibraryName(), e);
        return null;
      }
    });
  }

  /**
   * Creates and loads a resource repository. Consider calling {@link AarResourceRepositoryCache#getSourceRepository} instead of this method.
   *
   * @param resourceDirectory the directory containing resources
   * @param libraryName the name of the library
   * @return the created resource repository
   */
  @NotNull
  public static AarSourceResourceRepository create(@NotNull File resourceDirectory,
                                                   @NotNull String libraryName) {
    return create(resourceDirectory, null, ResourceNamespace.RES_AUTO, libraryName);
  }

  /**
   * Creates and loads a resource repository. Consider calling {@link AarResourceRepositoryCache#getSourceRepository} instead of this method.
   *
   * @param resourceFolder location where the resource files located. It contains a resource directory and a resource list should be loaded.
   *                      A null or empty resource list indicates that all files contained in {@code resourceFolder#root} should be loaded
   * @param libraryName the name of the library
   * @return the created resource repository
   */
  @NotNull
  public static AarSourceResourceRepository create(@NotNull ResourceFolder resourceFolder,
                                                   @NotNull String libraryName) {
    File resDir = resourceFolder.getRoot().toFile();
    Preconditions.checkArgument(resDir != null);
    return create(resDir, resourceFolder.getResources(), ResourceNamespace.RES_AUTO, libraryName);
  }

  @NotNull
  private static AarSourceResourceRepository create(@NotNull File resourceDirectory,
                                                    @Nullable Collection<PathString> resourceFiles,
                                                    @NotNull ResourceNamespace namespace,
                                                    @NotNull String libraryName) {
    Set<String> rTxtIds = loadIdsFromRTxt(resourceDirectory);
    AarSourceResourceRepository repository = new AarSourceResourceRepository(resourceDirectory, namespace, rTxtIds, libraryName);
    try {
      ResourceMerger resourceMerger = createResourceMerger(resourceDirectory, resourceFiles, repository.getNamespace(), libraryName, rTxtIds == null);
      ResourceRepositories.updateTableFromMerger(resourceMerger, repository.getFullTable());
    }
    catch (Exception e) {
      LOG.error("Failed to initialize resources", e);
    }

    return repository;
  }

  @VisibleForTesting
  @NotNull
  public static AarSourceResourceRepository createForTest(@NotNull File resourceDirectory,
                                                          @NotNull ResourceNamespace namespace,
                                                          @NotNull String libraryName) {
    assert ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode();
    return create(resourceDirectory, null, namespace, libraryName);
  }

  /**
   * Loads resource IDs from R.txt file and returns the list of their names, if successful.
   *
   * <p>This method can return null, if the file is missing or invalid. This should not be the case for AARs built using a stable version of
   * Android plugin for Gradle, but could happen for AARs built using other tools.
   *
   * @param resourceDirectory the resource directory of the AAR
   */
  @Nullable
  private static Set<String> loadIdsFromRTxt(@NotNull File resourceDirectory) {
    // Look for a R.txt file which describes the available id's; this is available both
    // in an exploded-aar folder as well as in the build-cache for AAR files.
    File parentDirectory = resourceDirectory.getParentFile();
    if (parentDirectory == null) {
      return null;
    }

    File rDotTxt = new File(parentDirectory, FN_RESOURCE_TEXT);
    if (rDotTxt.exists()) {
      try {
        SymbolTable symbolTable = SymbolIo.readFromAaptNoValues(rDotTxt, null);
        return symbolTable.getSymbols()
                                      .row(ResourceType.ID)
                                      .values()
                                      .stream()
                                      .map(s -> s.getCanonicalName())
                                      .collect(Collectors.toSet());
      }
      catch (Exception e) {
        LOG.warn("Failed to load id resources from " + rDotTxt.getPath(), e);
        return null;
      }
    }
    return null;
  }

  @Override
  @Nullable
  public String getPackageName() {
    String packageName = getNamespace().getPackageName();
    return packageName == null ? myManifestPackageName.getValue() : packageName;
  }

  private static ResourceMerger createResourceMerger(@NotNull File resFolder,
                                                     @Nullable Collection<PathString> resourceFiles,
                                                     @NotNull ResourceNamespace namespace,
                                                     @Nullable String libraryName,
                                                     boolean parseResourceIds) {
    ILogger logger = new LogWrapper(LOG).alwaysLogAsDebug(true).allowVerbose(false);
    ResourceMerger merger = new ResourceMerger(0);

    ResourceSet resourceSet = new ResourceSet(resFolder.getName(), namespace, libraryName, false /* validateEnabled */);
    if (parseResourceIds) {
      resourceSet.setShouldParseResourceIds(true);
    }

    // The resourceFiles collection contains resource files to be parsed.
    // If it's null or empty, all files in the resource folder should be parsed.
    if (resourceFiles == null || resourceFiles.isEmpty()) {
      resourceSet.addSource(resFolder);
    }
    else {
      for (PathString resourceFile : resourceFiles) {
        resourceSet.addSource(resourceFile.toFile());
      }
    }
    resourceSet.setTrackSourcePositions(false);
    try {
      resourceSet.loadFromFiles(logger);
    }
    catch (DuplicateDataException e) {
      // This should not happen; resourceSet validation is disabled.
      assert false;
    }
    catch (MergingException e) {
      LOG.warn(e);
    }
    merger.addDataSet(resourceSet);
    return merger;
  }

  /**
   * Returns names of id resources found in the R.txt file if the directory referenced by this repository contained a valid one.
   *
   * <p>When R.txt is present, the Ids obtained using {@link #getResources(ResourceNamespace, ResourceType)} by passing in {@link
   * ResourceType#ID} only contain a subset of Ids (top level ones like layout file names, and id resources in values xml file). Ids
   * declared inside layouts and menus (using "@+id/") are not included. This is done for efficiency. However, such IDs can be obtained from
   * the R.txt file. And hence, this collection includes all id names from the R.txt file, but doesn't have the associated {@link
   * ResourceItem} with it.
   *
   * <p>When R.txt is missing or cannot be parsed, layout and menu files are scanned for "@+id/" declarations and this method returns null.
   *
   * @see #loadIdsFromRTxt(File)
   */
  @Nullable
  public Set<String> getIdsFromRTxt() {
    return myRTxtIds;
  }

  // For debugging only.
  @Override
  public String toString() {
    return getClass().getSimpleName() + '@' + Integer.toHexString(System.identityHashCode(this)) + " for " + myResourceDirectory;
  }
}
