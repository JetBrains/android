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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.*;
import com.android.ide.common.xml.AndroidManifestParser;
import com.android.ide.common.xml.ManifestData;
import com.android.resources.ResourceType;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.utils.ILogger;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;

/**
 * A resource repository representing unpacked contents of a non-namespaced AAR.
 */
public class AarSourceResourceRepository extends LocalResourceRepository implements SingleNamespaceResourceRepository {
  private static final Logger LOG = Logger.getInstance(AarSourceResourceRepository.class);
  protected final ResourceTable myFullTable = new ResourceTable();
  /** @see #getAllDeclaredIds() */
  @Nullable private Map<String, Integer> myAarDeclaredIds;

  @NotNull private final File myResourceDirectory;
  @NotNull private final ResourceNamespace myNamespace;
  @Nullable private final String myLibraryName;

  /** The package name read on-demand from the manifest. */
  @NotNull private final NullableLazyValue<String> myManifestPackageName;

  protected AarSourceResourceRepository(@NotNull File resourceDirectory, @NotNull ResourceNamespace namespace,
                                        @Nullable String libraryName) {
    super(resourceDirectory.getName());
    myResourceDirectory = resourceDirectory;
    myNamespace = namespace;
    myLibraryName = libraryName;

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
        LOG.error("Failed to read manifest " + manifest.getAbsolutePath() + " for library " + myLibraryName, e);
        return null;
      }
    });
  }

  /**
   * Creates and loads a resource repository. Consider calling {@link AarResourceRepositoryCache#get} instead of this method.
   *
   * @param resourceDirectory the directory containing resources
   * @param libraryName the name of the library
   * @return the created resource repository
   */
  @NotNull
  public static AarSourceResourceRepository create(@NotNull File resourceDirectory, @Nullable String libraryName) {
    return create(resourceDirectory, ResourceNamespace.RES_AUTO, libraryName);
  }

  @NotNull
  private static AarSourceResourceRepository create(@NotNull File resourceDirectory, @NotNull ResourceNamespace namespace,
                                                    @Nullable String libraryName) {
    AarSourceResourceRepository repository = new AarSourceResourceRepository(resourceDirectory, namespace, libraryName);
    try {
      ResourceMerger resourceMerger = createResourceMerger(resourceDirectory, namespace, libraryName);
      ResourceRepositories.updateTableFromMerger(resourceMerger, repository.getItems());
    }
    catch (Exception e) {
      LOG.error("Failed to initialize resources", e);
    }

    repository.loadRTxt(resourceDirectory.getParentFile());

    return repository;
  }

  @VisibleForTesting
  @NotNull
  public static AarSourceResourceRepository createForTest(@NotNull File resourceDirectory, @NotNull ResourceNamespace namespace,
                                                          @Nullable String libraryName) {
    assert ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode();
    return create(resourceDirectory, namespace, libraryName);
  }

  /**
   * Loads resource IDs from R.txt file.
   *
   * @param directory the directory containing R.txt file
   */
  protected void loadRTxt(@NotNull File directory) {
    // Look for a R.txt file which describes the available id's; this is available both
    // in an exploded-aar folder as well as in the build-cache for AAR files.
    File rDotTxt = new File(directory, FN_RESOURCE_TEXT);
    if (rDotTxt.exists()) {
      myAarDeclaredIds = RDotTxtParser.getIds(rDotTxt);
    }
  }

  @NotNull
  public File getResourceDirectory() {
    return myResourceDirectory;
  }

  /**
   * Returns the namespace of all resources in this repository.
   */
  @NotNull
  @Override
  public final ResourceNamespace getNamespace() {
    return myNamespace;
  }

  @Override
  @Nullable
  public final String getLibraryName() {
    return myLibraryName;
  }

  @Nullable
  @Override
  public String getPackageName() {
    if (myNamespace.getPackageName() != null) {
      return myNamespace.getPackageName();
    }
    else {
      return myManifestPackageName.getValue();
    }
  }

  private static ResourceMerger createResourceMerger(File file, ResourceNamespace namespace, String libraryName) {
    ILogger logger = new LogWrapper(LOG).alwaysLogAsDebug(true).allowVerbose(false);
    ResourceMerger merger = new ResourceMerger(0);

    ResourceSet resourceSet = new ResourceSet(file.getName(), namespace, libraryName, false /* validateEnabled */);
    File rDotTxt = new File(file.getParent(), FN_RESOURCE_TEXT);
    if (!rDotTxt.exists()) {
      resourceSet.setShouldParseResourceIds(true);
    }
    resourceSet.addSource(file);
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

  @Override
  @NotNull
  protected ResourceTable getFullTable() {
    return myFullTable;
  }

  @Override
  @Nullable
  @Contract("_, _, true -> !null")
  protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace, @NotNull ResourceType type, boolean create) {
    ListMultimap<String, ResourceItem> multimap = myFullTable.get(namespace, type);
    if (multimap == null && create) {
      multimap = ArrayListMultimap.create();
      myFullTable.put(namespace, type, multimap);
    }
    return multimap;
  }

  /**
   * Returns a collection of resource id names found in the R.txt file if the file referenced by this repository is an AAR.
   * The Ids obtained using {@link #getItemsOfType(ResourceType)} by passing in {@link ResourceType#ID} only contains
   * a subset of IDs (top level ones like layout file names, and id resources in values xml file). Ids declared inside
   * layouts and menus (using "@+id/") are not included. This is done for efficiency. However, such IDs can be obtained
   * from the R.txt file, if present. And hence, this collection includes all id names from the R.txt file, but doesn't
   * have the associated {@link ResourceItem} with it.
   */
  @Nullable
  public Map<String, Integer> getAllDeclaredIds() {
    return myAarDeclaredIds;
  }

  @NotNull
  @Override
  protected Set<VirtualFile> computeResourceDirs() {
    VirtualFile virtualFile = VfsUtil.findFileByIoFile(myResourceDirectory, !ApplicationManager.getApplication().isReadAccessAllowed());
    if (virtualFile == null) {
      return ImmutableSet.of();
    }
    return ImmutableSet.of(virtualFile);
  }

  // For debugging only.
  @Override
  public String toString() {
    return getClass().getSimpleName() + '@' + Integer.toHexString(System.identityHashCode(this)) + " for " + myResourceDirectory;
  }
}
