package com.google.idea.blaze.qsync.artifacts;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class MockArtifactCache implements BuildArtifactCache {

  private final Path cacheDir;
  private final List<String> requestedDigests = Lists.newArrayList();

  public MockArtifactCache(Path cacheDir) throws IOException {
    this.cacheDir = cacheDir;
    Files.createDirectories(cacheDir);
  }

  @CanIgnoreReturnValue
  public ImmutableList<String> takeRequestedDigests() {
    ImmutableList<String> requested = ImmutableList.copyOf(requestedDigests);
    requestedDigests.clear();
    return requested;
  }

  @Override
  public ListenableFuture<ImmutableMap<String, Path>> addAll(
      ImmutableCollection<OutputArtifact> artifacts, Context<?> context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<ListenableFuture<CachedArtifact>> get(String digest) {
    requestedDigests.add(digest);
    Path artifact = cacheDir.resolve(digest);
    try {
      if (!Files.exists(artifact)) {
        Files.write(artifact, ImmutableList.of(digest));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return Optional.of(Futures.immediateFuture(new CachedArtifact(artifact)));
  }

  @Override
  public void clean(long maxTargetSizeBytes, Duration minKeepDuration) {}

  @Override
  public void purge() {}

  @Override
  public ImmutableMap<String, ByteSource> getBugreportFiles() {
    return ImmutableMap.of();
  }
}
