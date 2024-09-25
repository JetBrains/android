/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common.artifact;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.artifact.ArtifactFetcher.ArtifactDestination;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * A cache of build artifacts.
 *
 * <p>Downloads build artifacts on request, identifying them based on their digest as provided by
 * {@link OutputArtifact#getDigest()}.
 *
 * <p>For artifacts that have previously been requested via {@link #addAll(ImmutableCollection,
 * Context)}, provides access to their contents as a local file via {@link #get(String)}.
 *
 * <p>Access times are updated when artifacts downloads are requested, and when the contents are
 * requested, to enable unused cache entries to be cleaned up later on.
 *
 * <p>An instance of this class is expected to be the sole user of the provided cache directory.
 *
 * <p>When artifacts are added to the cache, a request to clean the cache is made via {@link
 * CleanRequest#request()}. That should result in a call back to {@link #clean(long, Duration)} at
 * an appropriate point (e.g. when the IDE is idle and after a minimum delay of 1 minute.
 *
 * <p>A clean request may be cancelled via a call to {@link CleanRequest#cancel()}, the
 * implementation should ensure that {@link #clean(long, Duration)} is not then called until a new
 * request is made.
 *
 * <p>This class will ensure that a clean request is never active while we are fetching new
 * artifacts into the cache.
 */
class BuildArtifactCacheDirectory implements BuildArtifactCache {

  private static final Logger logger =
      Logger.getLogger(BuildArtifactCacheDirectory.class.getName());

  private final Path cacheDir;
  private final ListeningExecutorService executor;
  private final ArtifactFetcher<OutputArtifact> fetcher;
  private final CleanRequest cleanRequest;
  volatile boolean needClean = false;

  private final Map<String, ListenableFuture<?>> activeFetches;

  /**
   * Read-write lock where the "read" is also used to adding items to the cache. The write is only
   * acquired for cleaning the cache which allows all other functionality to assume that cache items
   * are never deleted.
   *
   * <p>Note, we use a {@link StampedLock} to allow the lock to be released from a different thread
   * from that it wac acquired by. This is necessary as the fetch operation uses a future and we use
   * a future callback to ensure that the lock is released when it is done, see {@link #startFetch}.
   */
  private final StampedLock lock = new StampedLock();

  public BuildArtifactCacheDirectory(
      Path cacheDir,
      ArtifactFetcher<OutputArtifact> fetcher,
      ListeningExecutorService executor,
      CleanRequest cleanRequest)
      throws BuildException {
    this.cacheDir = cacheDir;
    this.fetcher = fetcher;
    this.executor = executor;
    this.cleanRequest = cleanRequest;
    this.activeFetches = Maps.newHashMap();

    if (!Files.exists(cacheDir)) {
      try {
        Files.createDirectories(cacheDir);
      } catch (IOException e) {
        throw new BuildException(e);
      }
    }
    if (!Files.isDirectory(cacheDir)) {
      throw new BuildException("Cache dir is not a directory: " + cacheDir);
    }
  }

  @VisibleForTesting
  int readLockCount() {
    return lock.getReadLockCount();
  }

  @VisibleForTesting
  int writeLockCount() {
    return lock.isWriteLocked() ? 1 : 0;
  }

  @VisibleForTesting
  Path artifactPath(String digest) {
    return cacheDir.resolve(digest);
  }

  private Path artifactPath(OutputArtifact a) {
    return artifactPath(a.getDigest());
  }

  private ArtifactDestination artifactDestination(OutputArtifact a) {
    return new ArtifactDestination(artifactPath(a));
  }

  /**
   * Returns true if the given artifact is present in the cache. May return true even if the
   * artifact is still being fetched (i.e. a true value does not mean the artifact is ready to be
   * used).
   */
  private boolean contains(OutputArtifact artifact) {
    return Files.exists(artifactPath(artifact));
  }

  /**
   * Updates the metadata for a cache entry.
   *
   * <p>For now, we juse use the filesystem last access timestamp for this, in future we may
   * consider adding an explicit metadata file if the timestamp alone proves insufficient.
   *
   * <p>Note we return Void to make this method easier to use with {@link
   * java.util.concurrent.ExecutorService#submit(Callable)}. }
   */
  private Void updateMetadata(String digest, Instant lastAccess) throws IOException {
    long stamp = lock.readLock();
    try {
      // note, when we pass null in here, the existing timestamps are left unchanged:
      Files.getFileAttributeView(artifactPath(digest), BasicFileAttributeView.class)
          .setTimes(
              /* lastModifiedTime */ null,
              /* lastAccessTime */ FileTime.from(lastAccess),
              /* createTime */ null);
      return null;
    } finally {
      lock.unlockRead(stamp);
    }
  }

  /**
   * Updates metadata for a set of artifacts.
   *
   * @param artifacts The affected artifacts.
   * @param lastAccess The last access time to set.
   * @return A future of the update operation.
   */
  private ListenableFuture<?> updateMetadata(
      ImmutableCollection<OutputArtifact> artifacts, Instant lastAccess) {
    return Futures.allAsList(
        artifacts.stream()
            .map(a -> executor.submit(() -> updateMetadata(a.getDigest(), lastAccess)))
            .collect(toImmutableList()));
  }

  /**
   * Kicks off an artifacts fetch, marking the artifacts as active while it's running.
   *
   * @param artifacts Artifacts to fetch.
   * @param accessTime The time that the artifacts were requested.
   * @param context Context
   * @return A future for the fetch operation.
   */
  private ListenableFuture<?> startFetch(
      ImmutableCollection<OutputArtifact> artifacts, Instant accessTime, Context<?> context) {
    // we re-acquire the lock (it is also acquired by our caller) to ensure that we continue holding
    // it until the fetch is done.
    long stamp = lock.readLock();
    ListenableFuture<?> done = null;
    try {
      ListenableFuture<?> newFetch =
          fetcher.copy(
              artifacts.stream()
                  .distinct()
                  .collect(toImmutableMap(Functions.identity(), this::artifactDestination)),
              context);
      // when that's done, update their metadata:
      done =
          Futures.transformAsync(
              newFetch, unused -> updateMetadata(artifacts, accessTime), executor);
      done.addListener(() -> lock.unlockRead(stamp), directExecutor());
    } finally {
      // failsafe to ensure we always release the lock:
      if (done == null) {
        lock.unlockRead(stamp);
      }
    }
    return done;
  }

  /** Helper function to filter a stream based on uniqueness of a property. */
  private static <T, K> Predicate<T> distinctBy(Function<? super T, K> keyExtractor) {
    Set<K> seen = Sets.newConcurrentHashSet();
    return t -> seen.add(keyExtractor.apply(t));
  }

  /**
   * Requests that the given artifacts are added to the cache.
   *
   * @return A future map of (digest)->(absolute path of the artifact) that will complete once all
   *     artifacts have been added to the cache. The future will fail if we fail to add any artifact
   *     to the cache.
   */
  @Override
  public ListenableFuture<?> addAll(
      ImmutableCollection<OutputArtifact> artifacts, Context<?> context) {
    // acquire the read lock to ensure that no clean is ongoing:
    long stamp = lock.readLock();
    try {
      synchronized (activeFetches) {
        Instant accessTime = Instant.now();
        // filter out any duplicate artifacts, and those for which there is already a fetch pending:
        ImmutableList<OutputArtifact> allArtifacts =
            artifacts.stream()
                .filter(distinctBy(OutputArtifact::getDigest))
                .filter(a -> !activeFetches.containsKey(a.getDigest()))
                .collect(toImmutableList());

        // group them based on whether the artifact is already cached
        ImmutableListMultimap<Boolean, OutputArtifact> artifactsByPresence =
            Multimaps.index(allArtifacts, this::contains);

        // Fetch absent artifacts
        ListenableFuture<?> fetch = startFetch(artifactsByPresence.get(false), accessTime, context);
        fetch.addListener(() -> unmarkAsActive(artifactsByPresence.get(false)), directExecutor());
        context.addCancellationHandler(() -> fetch.cancel(false));

        // mark the  artifacts as being actively fetched. If they are requested in the meantime,
        // the future will be used to wait until the fetch is complete.
        // They are unmarked by the future listener above.
        markAsActive(artifactsByPresence.get(false), fetch);

        // Update metadata for present artifacts
        ListenableFuture<?> metadataUpdate =
            Futures.allAsList(
                artifactsByPresence.get(true).stream()
                    .map(OutputArtifact::getDigest)
                    .map(digest -> executor.submit(() -> updateMetadata(digest, accessTime)))
                    .collect(toImmutableList()));
        context.addCancellationHandler(() -> metadataUpdate.cancel(false));

        needClean = true;
        return fetch;
      }
    } finally {
      lock.unlockRead(stamp);
    }
  }

  private void markAsActive(Iterable<OutputArtifact> artifacts, ListenableFuture<?> future) {
    synchronized (activeFetches) {
      cleanRequest.cancel();
      artifacts.forEach(a -> activeFetches.put(a.getDigest(), future));
    }
  }

  private void unmarkAsActive(ImmutableCollection<OutputArtifact> artifacts) {
    boolean empty;
    synchronized (activeFetches) {
      activeFetches
          .keySet()
          .removeAll(artifacts.stream().map(OutputArtifact::getDigest).collect(toImmutableSet()));
      empty = activeFetches.isEmpty();
    }
    if (empty && needClean) {
      cleanRequest.request();
    }
  }

  @Nullable
  private Path getArtifactIfPresent(String digest) throws IOException {
    long stamp = lock.readLock();
    try {
      Path artifactPath = artifactPath(digest);
      if (!Files.exists(artifactPath)) {
        return null;
      }
      updateMetadata(digest, Instant.now());
      return artifactPath;
    } finally {
      lock.unlockRead(stamp);
    }
  }

  /**
   * Returns the path to an artifact that was previously added to the cache.
   *
   * @return A future of the artifact path if the artifact is already present, or is in the process
   *     of being requested. Empty if the artifact has never been added to the cache, or has been
   *     deleted since then.
   */
  @Override
  public Optional<ListenableFuture<CachedArtifact>> get(String digest) {
    ListenableFuture<?> activeFetch;
    synchronized (activeFetches) {
      activeFetch = activeFetches.get(digest);
    }
    if (activeFetch == null) {
      try {
        return Optional.ofNullable(getArtifactIfPresent(digest))
            .map(CachedArtifact::new)
            .map(Futures::immediateFuture);
      } catch (IOException e) {
        return Optional.of(Futures.immediateFailedFuture(e));
      }
    } else {
      Path artifactPath = artifactPath(digest);
      return Optional.of(
          Futures.transform(
              activeFetch, unused2 -> new CachedArtifact(artifactPath), directExecutor()));
    }
  }

  @VisibleForTesting
  void insertForTest(InputStream content, String digest, Instant lastAccessTime)
      throws IOException {
    MoreFiles.asByteSink(artifactPath(digest)).writeFrom(content);
    updateMetadata(digest, lastAccessTime);
  }

  private record Entry(Path path, FileTime lastAccessTime, long size) {}

  private ImmutableList<Path> list() throws IOException {
    return MoreFiles.listFiles(cacheDir);
  }

  @VisibleForTesting
  ImmutableList<String> listDigests() throws IOException {
    return list().stream().map(Path::getFileName).map(Path::toString).collect(toImmutableList());
  }

  @VisibleForTesting
  FileTime readAccessTime(String digest) throws IOException {
    return readAccessTime(artifactPath(digest));
  }

  private FileTime readAccessTime(Path entry) throws IOException {
    return Files.getFileAttributeView(entry, BasicFileAttributeView.class)
        .readAttributes()
        .lastAccessTime();
  }

  @Override
  public void clean(long maxTargetSizeBytes, Duration minKeepDuration) throws BuildException {
    // Ensure that no artifacts are added or read from the cache while we're cleaning:
    long stamp = lock.writeLock();
    try {
      needClean = false;
      clean(maxTargetSizeBytes, Instant.now().minus(minKeepDuration));
    } catch (IOException e) {
      throw new BuildException("Failed to clean the build cache at " + cacheDir, e);
    } finally {
      lock.unlockWrite(stamp);
    }
  }

  @VisibleForTesting
  void clean(long maxTargetSize, Instant minAgeToDelete) throws IOException {
    ImmutableList<Path> entries = list();
    int failed = 0;
    long totalSize = 0;
    PriorityQueue<Entry> queue = new PriorityQueue<>(Comparator.comparing(Entry::lastAccessTime));
    for (Path p : entries) {
      try {
        Entry e = new Entry(p, readAccessTime(p), Files.size(p));
        queue.add(e);
        totalSize += e.size();
      } catch (IOException e) {
        // If we fail read the attributes for a file, we just ignore it and clean up the rest of the
        // cache as best we can.
        failed += 1;
      }
    }
    if (failed > 0) {
      logger.warning("Failed to read attributes from " + failed + " cache files when cleaning");
    }
    logger.info("Cleaning cache " + cacheDir + "; current size = " + totalSize);
    long remainingSize = totalSize;
    while (!queue.isEmpty()) {
      if (remainingSize <= maxTargetSize) {
        // size target reached
        logger.info("Reached target cache size: " + remainingSize + "<=" + maxTargetSize);
        return;
      }
      if (queue.peek().lastAccessTime().toInstant().isAfter(minAgeToDelete)) {
        // the oldest artifact is newer than the minimum age, so we stop deleting artifacts even
        // though the cache is bigger than the max size.
        logger.info(
            "Not deleting entries accessed since "
                + minAgeToDelete
                + "; remaining cache size="
                + remainingSize);
        return;
      }

      Entry toDelete = queue.poll();
      remainingSize -= toDelete.size();
      Files.delete(toDelete.path());
    }
  }

  public void purge() throws BuildException {
    // Ensure that no artifacts are added or read from the cache while we're cleaning:
    long stamp = lock.writeLock();
    try {
      MoreFiles.deleteDirectoryContents(cacheDir);
    } catch (IOException e) {
      throw new BuildException("Failed to purge the build artifact cache", e);
    } finally {
      lock.unlockWrite(stamp);
    }
  }
}
