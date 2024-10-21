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
package com.google.idea.blaze.base.qsync;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.NoopContext;
import com.google.idea.blaze.common.artifact.ArtifactFetcher;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.BuildArtifactCacheDirectory;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.common.artifact.TestOutputArtifact;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AppInspectorArtifactTrackerImplTest {

  @Rule
  public TemporaryFolder tempDir = new TemporaryFolder();
  private BuildArtifactCacheDirectory buildArtifactCache;

  @Test
  public void update() throws Exception {
    final var buildArtifactCache = createBuildArtifactCache(new TestArtifactFetcher(TestArtifactFetcher.ShouldFail.NO));
    Path storePath = tempDir.newFolder("store").toPath();
    final var tracker = new AppInspectorArtifactTrackerImpl(
      tempDir.newFolder("workspace").toPath(),
      buildArtifactCache,
      storePath
    );
    ImmutableSet<Path> paths = tracker.update(
      Label.of("//test:inspector"),
      AppInspectorInfo.create(
        ImmutableList.of(
          TestOutputArtifact.builder()
            .setArtifactPath(Path.of("out/test.jar"))
            .setDigest("jar_digest")
            .build(),
          TestOutputArtifact.builder()
            .setArtifactPath(Path.of("out/somewhere/else/other_test.jar"))
            .setDigest("jar_digest2")
            .build()
        ), 0), new NoopContext());
    assertThat(paths).containsExactly(storePath.resolve("test/inspector/out/test.jar"),
                                      storePath.resolve("test/inspector/out/somewhere/else/other_test.jar"));
  }

  @Test
  public void update_failed() throws Exception {
    final var buildArtifactCache = createBuildArtifactCache(new TestArtifactFetcher(TestArtifactFetcher.ShouldFail.YES));
    Path storePath = tempDir.newFolder("store").toPath();
    final var tracker = new AppInspectorArtifactTrackerImpl(
      tempDir.newFolder("workspace").toPath(),
      buildArtifactCache,
      storePath
    );
    assertThrows(BuildException.class, () ->
      tracker.update(
        Label.of("//test:inspector"),
        AppInspectorInfo.create(
          ImmutableList.of(
            TestOutputArtifact.builder()
              .setArtifactPath(Path.of("out/test.jar"))
              .setDigest("jar_digest")
              .build(),
            TestOutputArtifact.builder()
              .setArtifactPath(Path.of("out/somewhere/else/other_test.jar"))
              .setDigest("jar_digest2")
              .build()
          ), 0), new NoopContext()));
  }

  private BuildArtifactCacheDirectory createBuildArtifactCache(TestArtifactFetcher artifactFetcher) throws BuildException, IOException {
    return new BuildArtifactCacheDirectory(
      tempDir.newFolder("cache").toPath(),
      artifactFetcher,
      MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
      new BuildArtifactCache.CleanRequest() {
        @Override
        public void request() {
        }

        @Override
        public void cancel() {
        }
      });
  }

  private static class TestArtifactFetcher implements ArtifactFetcher<OutputArtifact> {
    public enum ShouldFail {YES, NO}

    private final ShouldFail shouldFail;

    private TestArtifactFetcher(ShouldFail shouldFail) { this.shouldFail = shouldFail; }

    @Override
    public ListenableFuture<?> copy(
      ImmutableMap<? extends OutputArtifact, ArtifactDestination> artifactToDest,
      Context<?> context) {
      if (shouldFail == ShouldFail.YES) {
        return immediateFailedFuture(new IOException());
      }
      for (Map.Entry<? extends OutputArtifact, ArtifactDestination> entry : artifactToDest.entrySet()) {
        try {
          MoreFiles.createParentDirectories(entry.getValue().path);
          java.nio.file.Files.writeString(entry.getValue().path, entry.getKey().getDigest());
        }
        catch (IOException e) {
          return immediateFailedFuture(e);
        }
      }
      return immediateFuture(null);
    }

    @Override
    public Class<OutputArtifact> supportedArtifactType() {
      return OutputArtifact.class;
    }
  }
}