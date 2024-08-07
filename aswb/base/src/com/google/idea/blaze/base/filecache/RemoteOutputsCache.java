/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.filecache;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Functions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.Keep;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.logging.LoggedDirectoryProvider;
import com.google.idea.blaze.base.model.OutputsProvider;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * A general-purpose local cache for {@link RemoteOutputArtifact}s. During sync, updated outputs of
 * interest will be copied locally.
 *
 * <p>Cache files have a hash appended to their name to allow matching to the original artifact.
 */
public final class RemoteOutputsCache {

  static final BoolExperiment useSHA256 =
      new BoolExperiment("blaze.base.filecache.remoteOutputsCache.sha256.enable", true);

  public static RemoteOutputsCache getInstance(Project project) {
    return project.getService(RemoteOutputsCache.class);
  }

  private static final Logger logger = Logger.getInstance(RemoteOutputsCache.class);

  private final File cacheDir;
  private final Project project;
  private volatile Map<String, File> cachedFiles = ImmutableMap.of();

  @Keep // Instantiated as an IntelliJ project component.
  private RemoteOutputsCache(Project project) {
    this.project = project;
    this.cacheDir = getCacheDir(project);
  }

  /**
   * Reads the current state of the cache on disk, updating the in-memory view used to resolve
   * cached outputs.
   */
  public void initialize() {
    cachedFiles = readCachedFiles();
  }

  /** Finds the locally-cached version of this file, or null if it isn't in the cache. */
  @Nullable
  public File resolveOutput(RemoteOutputArtifact output) {
    Map<String, File> cachedFiles = this.cachedFiles;
    return cachedFiles != null ? cachedFiles.get(getCacheKey(output)) : null;
  }

  public void updateCache(
      BlazeContext context,
      TargetMap targetMap,
      WorkspaceLanguageSettings languageSettings,
      RemoteOutputArtifacts outputs,
      RemoteOutputArtifacts previousOutputs,
      boolean clearCache) {
    if (clearCache) {
      clearCache();
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    List<OutputsProvider> providers =
        Arrays.stream(OutputsProvider.EP_NAME.getExtensions())
            .filter(p -> p.isActive(languageSettings))
            .collect(toImmutableList());

    Set<RemoteOutputArtifact> toCache =
        targetMap.targets().stream()
            .flatMap(t -> artifactsToCache(providers, t))
            .distinct()
            .map(outputs::findRemoteOutput)
            .filter(Objects::nonNull)
            .collect(toImmutableSet());
    updateCache(context, toCache, previousOutputs);
  }

  private static Stream<ArtifactLocation> artifactsToCache(
      List<OutputsProvider> providers, TargetIdeInfo target) {
    return providers.stream()
        .flatMap(p -> p.selectOutputsToCache(target).stream())
        .filter(ArtifactLocation::isGenerated);
  }

  private void updateCache(
      BlazeContext context,
      Set<RemoteOutputArtifact> toCache,
      RemoteOutputArtifacts previousOutputs) {
    Map<String, RemoteOutputArtifact> newState =
        toCache.stream()
            .collect(toImmutableMap(RemoteOutputsCache::getCacheKey, Functions.identity()));

    Map<String, File> cachedFiles = readCachedFiles();
    try {
      Map<String, RemoteOutputArtifact> updatedOutputs =
          FileCacheDiffer.findUpdatedOutputs(newState, cachedFiles, previousOutputs);

      List<File> removed =
          cachedFiles.entrySet().stream()
              .filter(e -> !newState.containsKey(e.getKey()))
              .map(Map.Entry::getValue)
              .collect(toImmutableList());

      // Ensure the cache dir exists
      if (!cacheDir.exists()) {
        if (!cacheDir.mkdirs()) {
          IssueOutput.error("Could not create remote outputs cache directory").submit(context);
          context.setHasError();
          return;
        }
      }
      ImmutableList<RemoteOutputArtifact> artifactsToDownload =
          RemoteOutputArtifact.getRemoteArtifacts(updatedOutputs.values());
      ListenableFuture<?> downloadArtifactsFuture =
          RemoteArtifactPrefetcher.getInstance()
              .downloadArtifacts(
                  /* projectName= */ project.getName(), /* outputArtifacts= */ artifactsToDownload);
      logger.info(String.format("Prefetching %d output artifacts", artifactsToDownload.size()));
      FutureUtil.waitForFuture(context, downloadArtifactsFuture)
          .timed("PrefetchRemoteOutput", EventType.Prefetching)
          .withProgressMessage("Prefetching output artifacts...")
          .run();

      List<ListenableFuture<?>> futures = new ArrayList<>(copyLocally(updatedOutputs));
      futures.addAll(deleteCacheFiles(removed));

      Futures.allAsList(futures).get();

      this.cachedFiles =
          newState.keySet().stream()
              .collect(toImmutableMap(Functions.identity(), k -> new File(cacheDir, k)));

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      context.setCancelled();

    } catch (ExecutionException e) {
      IssueOutput.warn("Remote outputs synchronization didn't complete: " + e.getMessage())
          .submit(context);
    }
  }

  private Map<String, File> readCachedFiles() {
    File[] files = cacheDir.listFiles();
    if (files == null) {
      return ImmutableMap.of();
    }
    return Arrays.stream(files).collect(toImmutableMap(File::getName, Functions.identity()));
  }

  /**
   * The cache key used to disambiguate output artifacts. This is also the file name in the local
   * cache.
   */
  @VisibleForTesting
  static String getCacheKey(RemoteOutputArtifact output) {
    String key = output.getRelativePath();
    String fileName = PathUtil.getFileName(key);
    List<String> components = Splitter.on('.').limit(2).splitToList(fileName);
    StringBuilder builder =
        new StringBuilder(components.get(0)).append('_').append(Integer.toHexString(hashKey(key)));
    if (components.size() > 1) {
      // file extension(s)
      builder.append('.').append(components.get(1));
    }
    return builder.toString();
  }

  private static int hashKey(String key) {
    if (!useSHA256.getValue()) {
      return key.hashCode();
    }
    Hasher hasher = Hashing.sha256().newHasher();
    hasher.putString(key, Charsets.UTF_8);
    return hasher.hash().asInt();
  }

  private static File getCacheDir(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    return new File(BlazeDataStorage.getProjectDataDir(importSettings), "remoteOutputCache");
  }

  private Collection<ListenableFuture<?>> copyLocally(Map<String, RemoteOutputArtifact> updated) {
    List<ListenableFuture<?>> futures = new ArrayList<>();
    updated.forEach(
        (key, artifact) ->
            futures.add(
                FetchExecutor.EXECUTOR.submit(
                    () -> {
                      Path destination = new File(cacheDir, key).toPath();
                      try (InputStream stream = artifact.getInputStream()) {
                        Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
                      } catch (IOException e) {
                        logger.warn(
                            String.format("Fail to copy artifact %s to %s", artifact, cacheDir), e);
                      }
                    })));
    return futures;
  }

  private Collection<ListenableFuture<?>> deleteCacheFiles(Collection<File> files) {
    return files.stream()
        .map(
            f ->
                FetchExecutor.EXECUTOR.submit(
                    () -> {
                      try {
                        Files.deleteIfExists(Paths.get(f.getPath()));
                      } catch (IOException e) {
                        logger.warn(e);
                      }
                    }))
        .collect(toImmutableList());
  }

  private void clearCache() {
    cachedFiles = ImmutableMap.of();
    if (cacheDir.exists()) {
      File[] cacheFiles = cacheDir.listFiles();
      if (cacheFiles != null) {
        @SuppressWarnings("unused") // go/futurereturn-lsc
        Future<?> possiblyIgnoredError = FileUtil.asyncDelete(Lists.newArrayList(cacheFiles));
      }
    }
    cachedFiles = ImmutableMap.of();
  }

  /** Configuration which includes the remote output cache directory in the logged metrics. */
  static class LoggedRemoteOutputsCacheDirectory implements LoggedDirectoryProvider {

    @Override
    public Optional<LoggedDirectory> getLoggedDirectory(Project project) {
      // RemoteOutputsCache#getCacheDir unfortunately doesn't check for null but wouldn't be easy to
      // change without touching existing callers. To be on the safe side for this specific calling
      // path, perform the null check here.
      BlazeImportSettings importSettings =
          BlazeImportSettingsManager.getInstance(project).getImportSettings();
      if (importSettings == null) {
        return Optional.empty();
      }

      return Optional.of(
          LoggedDirectory.builder()
              .setPath(RemoteOutputsCache.getCacheDir(project).toPath())
              .setOriginatingIdePart(String.format("%s plugin", Blaze.buildSystemName(project)))
              .setPurpose("Remote outputs cache")
              .build());
    }
  }
}
