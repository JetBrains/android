/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync.importer.emptylibrary;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.filecache.ArtifactsDiff;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.artifact.ArtifactState;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import com.google.idea.blaze.common.artifact.OutputArtifactWithoutDigest;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Class to remove empty JARs included during workspace import */
public class EmptyLibrary {

  private EmptyLibrary() {}

  private static final Logger logger = Logger.getInstance(EmptyLibrary.class);

  /**
   * Attempts to filter out any libraries from the given map that correspond to effectively empty
   * JARs.
   *
   * <p>Uses {@link EmptyJarTracker} to only prefetch the JARs that have changed since last sync,
   * and puts the updated tracker in `importResultBuilder`
   *
   * <p>If something goes horribly wrong or this feature is disabled, this method just returns a
   * copy of the given map.
   *
   * @see EmptyLibraryFilter
   */
  public static ImmutableMap<LibraryKey, BlazeJarLibrary> removeEmptyLibraries(
      Project project,
      BlazeContext parentContext,
      ArtifactLocationDecoder locationDecoder,
      Map<LibraryKey, BlazeJarLibrary> allLibraries,
      @Nullable SyncState oldSyncState,
      BlazeJavaImportResult.Builder importResultBuilder) {
    if (!EmptyLibraryFilter.isEnabled()) {
      // Preserve previously identified empty jars to save computing time
      // when empty jar filtering is enabled again.
      importResultBuilder.setEmptyJarTracker(EmptyJarTracker.getEmptyJarTracker(oldSyncState));
      return ImmutableMap.copyOf(allLibraries);
    }

    return Scope.push(
        parentContext,
        context -> {
          context.push(new TimingScope("FilterEmptyJars", EventType.Other));
          context.output(new StatusOutput("Filtering empty jars..."));

          return doRemoveEmptyLibraries(
              project,
              context,
              locationDecoder,
              allLibraries,
              EmptyJarTracker.getEmptyJarTracker(oldSyncState),
              importResultBuilder);
        });
  }

  private static ImmutableMap<LibraryKey, BlazeJarLibrary> doRemoveEmptyLibraries(
      Project project,
      BlazeContext context,
      ArtifactLocationDecoder locationDecoder,
      Map<LibraryKey, BlazeJarLibrary> allLibraries,
      EmptyJarTracker oldProjectData,
      BlazeJavaImportResult.Builder importResultBuilder) {

    long startTime = System.currentTimeMillis();
    Set<OutputArtifactWithoutDigest> currentOutputArtifacts = new HashSet<>();
    Map<LibraryKey, ArtifactState> libraryKeyToArtifactState = new HashMap<>();

    // Create a map LibraryKey -> ArtifactState for all entries in `allLibraries` that correspond to
    // an artifact of type `OutputArtifact`. Entries that do not have such artifacts are not
    // evaluated and assumed to be non-empty. `BlazeArtifact` that is not an instance of
    // `OutputArtifact` does not have an associated `ArtifactState`
    for (Map.Entry<LibraryKey, BlazeJarLibrary> entry : allLibraries.entrySet()) {
      ArtifactLocation libraryJar = entry.getValue().libraryArtifact.jarForIntellijLibrary();
      BlazeArtifact libraryArtifact = locationDecoder.resolveOutput(libraryJar);
      if (libraryArtifact instanceof OutputArtifactWithoutDigest) {
        ArtifactState libraryArtifactState =
            ((OutputArtifactWithoutDigest) libraryArtifact).toArtifactState();
        libraryKeyToArtifactState.put(entry.getKey(), libraryArtifactState);
        currentOutputArtifacts.add((OutputArtifactWithoutDigest) libraryArtifact);
      }
    }

    // Update emptyJarTracker by reevaluating any artifact that might have changed since last sync
    EmptyJarTracker emptyJarTracker =
        getUpdatedEmptyJarTracker(project, context, currentOutputArtifacts, oldProjectData);

    // Put non-empty artifacts in `result`
    Map<LibraryKey, BlazeJarLibrary> result = new LinkedHashMap<>();
    for (Map.Entry<LibraryKey, BlazeJarLibrary> entry : allLibraries.entrySet()) {
      ArtifactState libraryArtifactState = libraryKeyToArtifactState.get(entry.getKey());
      if (libraryArtifactState == null || !emptyJarTracker.isKnownEmpty(libraryArtifactState)) {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    context.output(
        PrintOutput.log(
            String.format(
                "Filtered %d JARs, in %dms",
                allLibraries.size() - result.size(), System.currentTimeMillis() - startTime)));

    importResultBuilder.setEmptyJarTracker(emptyJarTracker);
    return ImmutableMap.copyOf(result);
  }

  /**
   * Takes the current set of artifacts and artifacts known from previous sync to calculate which of
   * new artifacts are empty. Stores the status of each artifact in {@link EmptyJarTracker}.
   *
   * <p>Artifacts that have changed are fetched and passed to {@link EmptyLibraryFilter} to check if
   * they are empty or not.
   *
   * <p>NOTE: This method does Network IO and File IO, keep an eye on performance
   */
  private static EmptyJarTracker getUpdatedEmptyJarTracker(
      Project project,
      BlazeContext context,
      Collection<OutputArtifactWithoutDigest> newArtifacts,
      EmptyJarTracker oldTracker) {
    ImmutableMap<String, ArtifactState> oldState = oldTracker.getState();

    try {
      // Calculate artifacts that have changed or been removed since last sync
      ArtifactsDiff diff = ArtifactsDiff.diffArtifacts(oldState, newArtifacts);
      ImmutableList<OutputArtifactWithoutDigest> updated = diff.getUpdatedOutputs();
      ImmutableSet<ArtifactState> removed = diff.getRemovedOutputs();

      // Copy over tracking data from previous sync, and remove entries which are no longer valid.
      EmptyJarTracker.Builder builder = EmptyJarTracker.builder();
      builder.addAllEntries(oldTracker);
      builder.removeEntries(removed);

      // Prefetch updated artifacts
      ListenableFuture<?> future =
          RemoteArtifactPrefetcher.getInstance()
              .downloadArtifacts(
                  project.getName(), RemoteOutputArtifact.getRemoteArtifacts(updated));

      FutureUtil.waitForFuture(context, future)
          .timed("FetchJarsForEmptyStatusTracking", EventType.Prefetching)
          .withProgressMessage("Fetching JARs to track empty status..")
          .run();

      // Evaluate if the updated artifacts are empty or not
      Map<ArtifactState, Boolean> updatedStatuses =
          getEmptyStatusInParallel(updated, new EmptyLibraryFilter(), FetchExecutor.EXECUTOR);
      builder.addAllEntries(updatedStatuses);

      if (!updated.isEmpty()) {
        context.output(
            PrintOutput.log(
                String.format(
                    "[Empty JAR Filter] Calculated empty status of %d JARs", updated.size())));
      }

      if (!removed.isEmpty()) {
        context.output(
            PrintOutput.log(String.format("[Empty JAR Filter] Removed %d JARs", removed.size())));
      }

      return builder.build();
    } catch (InterruptedException e) {
      String message = "Updating EmptyJarTracker failed.";
      logger.warn(message, e);
      IssueOutput.warn(message).submit(context);
    } catch (ExecutionException e) {
      Thread.currentThread().interrupt();
      context.setCancelled();
    }

    // Something went wrong, return old tracking data
    return oldTracker;
  }

  /**
   * Evaluates all passed artifacts in parallel to check whether they are empty or not. Returns a
   * map of ArtifactState -> Empty Status
   */
  private static ImmutableMap<ArtifactState, Boolean> getEmptyStatusInParallel(
      Collection<OutputArtifactWithoutDigest> artifacts,
      Predicate<BlazeArtifact> emptyStatusTester,
      ListeningExecutorService executor)
      throws ExecutionException, InterruptedException {
    return Futures.allAsList(
            artifacts.stream()
                .map(
                    a ->
                        executor.submit(
                            () ->
                                Maps.immutableEntry(
                                    a.toArtifactState(), emptyStatusTester.test(a))))
                .collect(Collectors.toList()))
        .get()
        .stream()
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
