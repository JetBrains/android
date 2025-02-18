/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.nio.file.Files.createDirectory;
import static org.junit.Assert.assertThrows;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.artifact.TestOutputArtifact;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.ArtifactFetcher;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.BuildArtifactCacheDirectory;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RuntimeArtifactCacheImplTest {

  @Rule public TemporaryFolder tmpDir = new TemporaryFolder();
  private Path workspaceRoot;
  private Path runfilesDirectory;

  @Before
  public void initDirs() throws Exception {
    workspaceRoot = tmpDir.getRoot().toPath().resolve("workspace");
    createDirectory(workspaceRoot);
    runfilesDirectory = tmpDir.getRoot().toPath().resolve("runfiles");
  }

  @Test
  public void fetchArtifacts() throws Exception {
    final var testArtifactFetcher = new RuntimeArtifactCacheImplTest.TestArtifactFetcher(
      RuntimeArtifactCacheImplTest.TestArtifactFetcher.ShouldFail.NO);
    final var buildArtifactCache = createBuildArtifactCache(testArtifactFetcher);
    RuntimeArtifactCache runtimeArtifactCache =
        new RuntimeArtifactCacheImpl(runfilesDirectory, buildArtifactCache, workspaceRoot);
    TestOutputArtifact artifact1 = TestOutputArtifact.builder()
      .setArtifactPath(Path.of("out/test1.jar"))
      .setDigest("abc")
      .build();
    TestOutputArtifact artifact2 = TestOutputArtifact.builder()
      .setArtifactPath(Path.of("out/test2.jar"))
      .setDigest("def")
      .build();
    Label target = Label.of("//some/label:target");
    List<Path> paths =
        runtimeArtifactCache.fetchArtifacts(
            target, ImmutableList.of(artifact1, artifact2), BlazeContext.create());
    assertThat(paths)
        .containsExactly(
            runfilesDirectory.resolve(
                RuntimeArtifactCacheImpl.getArtifactLocalPath(
                    target, Path.of("out/test1.jar"))),
            runfilesDirectory.resolve(
                RuntimeArtifactCacheImpl.getArtifactLocalPath(
                    target, Path.of("out/test2.jar"))));
    assertThat(Files.readAllBytes(paths.get(0))).isEqualTo("abc".getBytes());
    assertThat(Files.readAllBytes(paths.get(1))).isEqualTo("def".getBytes());
    assertThat(testArtifactFetcher.getCopiedArtifacts()).isEqualTo(List.of(artifact1, artifact2));
  }

  @Test
  public void fetchArtifacts_failed() throws Exception {
    final var buildArtifactCache = createBuildArtifactCache(new RuntimeArtifactCacheImplTest.TestArtifactFetcher(
      RuntimeArtifactCacheImplTest.TestArtifactFetcher.ShouldFail.YES));
    RuntimeArtifactCache runtimeArtifactCache =
      new RuntimeArtifactCacheImpl(runfilesDirectory, buildArtifactCache, workspaceRoot);
    TestOutputArtifact artifact1 = TestOutputArtifact.builder()
      .setArtifactPath(Path.of("out/test1.jar"))
      .setDigest("abc")
      .build();
    TestOutputArtifact artifact2 = TestOutputArtifact.builder()
      .setArtifactPath(Path.of("out/test2.jar"))
      .setDigest("def")
      .build();
    Label target = Label.of("//some/label:target");
    assertThrows(IllegalStateException.class, () -> runtimeArtifactCache.fetchArtifacts(
      target, ImmutableList.of(artifact1, artifact2), BlazeContext.create()));
  }

  private BuildArtifactCacheDirectory createBuildArtifactCache(RuntimeArtifactCacheImplTest.TestArtifactFetcher artifactFetcher) throws
                                                                                                                                        BuildException, IOException {
    return new BuildArtifactCacheDirectory(
      runfilesDirectory,
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

    private final RuntimeArtifactCacheImplTest.TestArtifactFetcher.ShouldFail shouldFail;

    private final List<OutputArtifact> copiedArtifacts = new ArrayList<>();

    private TestArtifactFetcher(RuntimeArtifactCacheImplTest.TestArtifactFetcher.ShouldFail shouldFail) { this.shouldFail = shouldFail; }

    @Override
    public ListenableFuture<?> copy(
      ImmutableMap<? extends OutputArtifact, ArtifactDestination> artifactToDest,
      Context<?> context) {
      if (shouldFail == RuntimeArtifactCacheImplTest.TestArtifactFetcher.ShouldFail.YES) {
        return immediateFailedFuture(new IOException());
      }
      for (Map.Entry<? extends OutputArtifact, ArtifactDestination> entry : artifactToDest.entrySet()) {
        try {
          MoreFiles.createParentDirectories(entry.getValue().path);
          Files.writeString(entry.getValue().path, entry.getKey().getDigest());
          copiedArtifacts.add(entry.getKey());
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

    public List<OutputArtifact> getCopiedArtifacts() {
      return copiedArtifacts;
    }
  }
}
