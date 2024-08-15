/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.libraries;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.filecache.FileCacheDiffer;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Local copy of unzipped AARs that are part of a project's libraries. Updated whenever the original
 * AAR is changed. Unpacked AARs are directories with many files. {@see
 * https://developer.android.com/studio/projects/android-library.html#aar-contents}, for a subset of
 * the contents (documentation may be outdated).
 *
 * <p>The IDE wants at least the following:
 *
 * <ul>
 *   <li>the res/ folder
 *   <li>the R.txt file adjacent to the res/ folder
 *   <li>See {@link com.android.tools.idea.resources.aar.AarSourceResourceRepository} for the
 *       dependency on R.txt.
 *   <li>jars: we use the merged output jar from Bazel instead of taking jars from the AAR. It
 *       should be placed in a jars/ folder adjacent to the res/ folder. See {@link
 *       org.jetbrains.android.uipreview.ModuleClassLoader}, for that possible assumption.
 *   <li>The IDE may want the AndroidManifest.xml as well.
 * </ul>
 */
public class UnpackedAars {
  private static final Logger logger = Logger.getInstance(UnpackedAars.class);

  private final Project project;
  private final AarCache aarCache;

  public static UnpackedAars getInstance(Project project) {
    return project.getService(UnpackedAars.class);
  }

  public UnpackedAars(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    this.project = project;
    aarCache = new AarCache(getCacheDir(importSettings));
  }

  /* Provide path to aar cache directory. This is for test only. */
  @VisibleForTesting
  @Nullable
  public File getCacheDir() {
    try {
      return aarCache.getOrCreateCacheDir();
    } catch (IOException e) {
      logger.warn("Fail to get cache directory. ", e);
      return null;
    }
  }

  private static File getCacheDir(BlazeImportSettings importSettings) {
    return new File(BlazeDataStorage.getProjectDataDir(importSettings), "aar_libraries");
  }

  void onSync(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData projectData,
      @Nullable BlazeProjectData oldProjectData,
      SyncMode syncMode) {
    boolean fullRefresh = syncMode == SyncMode.FULL;
    if (fullRefresh) {
      aarCache.clearCache();
    }

    // TODO(brendandouglas): add a mechanism for removing missing files for partial syncs
    boolean removeMissingFiles = syncMode == SyncMode.INCREMENTAL;
    refresh(
        context,
        projectViewSet,
        projectData,
        RemoteOutputArtifacts.fromProjectData(oldProjectData),
        removeMissingFiles);
  }

  private void refresh(
      BlazeContext context,
      ProjectViewSet viewSet,
      BlazeProjectData projectData,
      RemoteOutputArtifacts previousOutputs,
      boolean removeMissingFiles) {
    try {
      aarCache.getOrCreateCacheDir();
    } catch (IOException e) {
      logger.warn("Could not create unpacked AAR directory", e);
      return;
    }

    ImmutableMap<String, File> cacheFiles = aarCache.readFileState();
    ImmutableMap<String, AarLibraryContents> projectState =
        getArtifactsToCache(viewSet, projectData);
    ImmutableMap<String, BlazeArtifact> aarOutputs =
        projectState.entrySet().stream()
            .collect(toImmutableMap(Map.Entry::getKey, e -> e.getValue().aar()));
    try {

      Set<String> updatedKeys =
          FileCacheDiffer.findUpdatedOutputs(aarOutputs, cacheFiles, previousOutputs).keySet();
      Set<BlazeArtifact> artifactsToDownload = new HashSet<>();

      for (String key : updatedKeys) {
        artifactsToDownload.add(projectState.get(key).aar());
        BlazeArtifact jar = projectState.get(key).jar();
        // jar file is introduced as a separate artifact (not jar in aar) which asks to download
        // separately. Only update jar when we decide that aar need to be updated.
        if (jar != null) {
          artifactsToDownload.add(jar);
        }
      }

      // Prefetch all libraries to local before reading and copying content
      ListenableFuture<?> downloadArtifactsFuture =
          RemoteArtifactPrefetcher.getInstance()
              .downloadArtifacts(
                  /* projectName= */ project.getName(),
                  /* outputArtifacts= */ RemoteOutputArtifact.getRemoteArtifacts(
                      artifactsToDownload));

      FutureUtil.waitForFuture(context, downloadArtifactsFuture)
          .timed("FetchAars", EventType.Prefetching)
          .withProgressMessage("Fetching aar files...")
          .run();

      // remove files if required. Remove file before updating cache files to avoid removing any
      // manually created directory.
      if (removeMissingFiles) {
        Collection<ListenableFuture<?>> removedFiles =
            aarCache.retainOnly(/* retainedFiles= */ projectState.keySet());
        Futures.allAsList(removedFiles).get();
        if (!removedFiles.isEmpty()) {
          context.output(PrintOutput.log(String.format("Removed %d AARs", removedFiles.size())));
        }
      }

      // update cache files
      Unpacker.unpack(projectState, updatedKeys, aarCache);

      if (!updatedKeys.isEmpty()) {
        context.output(PrintOutput.log(String.format("Copied %d AARs", updatedKeys.size())));
      }

    } catch (InterruptedException e) {
      context.setCancelled();
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      logger.warn("Unpacked AAR synchronization didn't complete", e);
    } finally {
      // update the in-memory record of which files are cached
      aarCache.readFileState();
    }
  }

  private File logAndGetFallbackJar(
      ArtifactLocationDecoder decoder, AarLibrary library, BlazeArtifact jar) {
    if (aarCache.isEmpty()) {
      logger.warn("Cache state is empty");
    }

    // if artifact is RemoteOutputArtifact, we can only find it in aar cache. So it's expected
    // that the aar directory has been cached. It's unexpected when it runs into this case and
    // cannot find any fallback file.
    if (jar instanceof RemoteOutputArtifact) {
      BlazeArtifact aar = decoder.resolveOutput(library.aarArtifact);
      logger.warn(
          String.format(
              "Fail to look up from cache state for library [aarArtifact = %s, jar = %s]",
              aar, jar));
      logger.debug("Cache state contains the following keys: " + aarCache.getCachedKeys());
    }
    return getFallbackFile(jar);
  }

  /** Returns the merged jar derived from an AAR, in the unpacked AAR directory. */
  @Nullable
  public File getClassJar(ArtifactLocationDecoder decoder, AarLibrary library) {
    if (library.libraryArtifact == null) {
      return null;
    }
    File aarDir = getAarDir(decoder, library);
    if (aarDir == null) {
      BlazeArtifact jar = decoder.resolveOutput(library.libraryArtifact.jarForIntellijLibrary());
      return logAndGetFallbackJar(decoder, library, jar);
    }
    return UnpackedAarUtils.getJarFile(aarDir);
  }

  /** Returns the src jars derived from an AAR, in the unpacked AAR directory. */
  public ImmutableList<File> getCachedSrcJars(ArtifactLocationDecoder decoder, AarLibrary library) {
    if (library.libraryArtifact == null) {
      return ImmutableList.of();
    }
    File aarDir = getAarDir(decoder, library);
    return library.libraryArtifact.getSourceJars().stream()
        .map(
            artifactLocation -> {
              BlazeArtifact srcJar = decoder.resolveOutput(artifactLocation);
              if (aarDir == null) {
                return logAndGetFallbackJar(decoder, library, srcJar);
              }
              String srcJarName = UnpackedAarUtils.getSrcJarName(srcJar);
              return new File(aarDir, srcJarName);
            })
        .collect(toImmutableList());
  }

  /** Returns the res/ directory corresponding to an unpacked AAR file. */
  @Nullable
  public File getResourceDirectory(ArtifactLocationDecoder decoder, AarLibrary library) {
    File aarDir = getAarDir(decoder, library);
    return aarDir == null ? aarDir : UnpackedAarUtils.getResDir(aarDir);
  }

  /** Returns the lint.jar file corresponding to an unpacked AAR file. */
  @Nullable
  public File getLintRuleJar(ArtifactLocationDecoder decoder, AarLibrary library) {
    File aarDir = getAarDir(decoder, library);
    return aarDir == null ? null : UnpackedAarUtils.getLintRuleJar(aarDir);
  }

  @Nullable
  public File getAarDir(ArtifactLocationDecoder decoder, AarLibrary library) {
    return getAarDir(decoder, library.aarArtifact);
  }

  @Nullable
  public File getAarDir(ArtifactLocationDecoder decoder, ArtifactLocation aar) {
    BlazeArtifact artifact = decoder.resolveOutput(aar);
    String aarDirName = UnpackedAarUtils.getAarDirName(artifact);
    return aarCache.getCachedAarDir(aarDirName);
  }

  /** The file to return if there's no locally cached version. */
  private static File getFallbackFile(BlazeArtifact output) {
    if (output instanceof RemoteOutputArtifact) {
      // TODO(brendandouglas): copy locally on the fly?
      throw new RuntimeException("The AAR cache must be enabled when syncing remotely");
    }
    return ((LocalFileArtifact) output).getFile();
  }

  /** An implementation of {@link FileCache} delegating to {@link UnpackedAars}. */
  public static class FileCacheAdapter implements FileCache {
    @Override
    public String getName() {
      return "Unpacked AAR libraries";
    }

    @Override
    public void onSync(
        Project project,
        BlazeContext context,
        ProjectViewSet projectViewSet,
        BlazeProjectData projectData,
        @Nullable BlazeProjectData oldProjectData,
        SyncMode syncMode) {
      getInstance(project).onSync(context, projectViewSet, projectData, oldProjectData, syncMode);
    }

    @Override
    public void refreshFiles(
        Project project, BlazeContext context, BlazeBuildOutputs buildOutputs) {
      ProjectViewSet viewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      BlazeProjectData projectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (viewSet == null || projectData == null || !projectData.getRemoteOutputs().isEmpty()) {
        // if we have remote artifacts, only refresh during sync
        return;
      }
      getInstance(project)
          .refresh(
              context,
              viewSet,
              projectData,
              projectData.getRemoteOutputs(),
              /* removeMissingFiles= */ false);
    }

    @Override
    public void initialize(Project project) {
      getInstance(project).aarCache.readFileState();
    }
  }

  /**
   * Returns a map from cache key to {@link AarLibraryContents}, for all the artifacts which should
   * be cached.
   */
  private static ImmutableMap<String, AarLibraryContents> getArtifactsToCache(
      ProjectViewSet projectViewSet, BlazeProjectData projectData) {
    Collection<BlazeLibrary> libraries =
        BlazeLibraryCollector.getLibraries(projectViewSet, projectData);
    List<AarLibrary> aarLibraries =
        libraries.stream()
            .filter(library -> library instanceof AarLibrary)
            .map(library -> (AarLibrary) library)
            .collect(Collectors.toList());

    ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
    Map<String, AarLibraryContents> outputs = new HashMap<>();
    for (AarLibrary library : aarLibraries) {
      BlazeArtifact aar = decoder.resolveOutput(library.aarArtifact);
      LibraryArtifact libraryArtifact = library.libraryArtifact;
      BlazeArtifact jar = null;
      ImmutableList<BlazeArtifact> srcJars = ImmutableList.of();
      if (libraryArtifact != null) {
        jar = decoder.resolveOutput(libraryArtifact.jarForIntellijLibrary());
        srcJars =
            libraryArtifact.getSourceJars().stream()
                .map(decoder::resolveOutput)
                .collect(toImmutableList());
      }
      outputs.put(
          UnpackedAarUtils.getAarDirName(aar), AarLibraryContents.create(aar, jar, srcJars));
    }
    return ImmutableMap.copyOf(outputs);
  }
}
