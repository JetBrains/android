/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.filecache;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.filecache.LocalArtifactCache.CACHE_DATA_FILENAME;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifactWithoutDigest;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.DefaultPrefetcher;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.blaze.common.artifact.OutputArtifactWithoutDigest;
import com.google.idea.testing.IntellijRule;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LocalArtifactCache} */
@RunWith(JUnit4.class)
public class LocalArtifactCacheTest {
  @Rule public final IntellijRule intellijRule = new IntellijRule();

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public TemporaryFolder cacheDirectory = new TemporaryFolder();

  private WorkspaceRoot workspaceRoot;
  private BlazeContext blazeContext;

  private LocalArtifactCache artifactCache;

  @Before
  public void initTest() throws IOException {
    intellijRule.registerApplicationService(
        FileOperationProvider.class, new FileOperationProvider());
    intellijRule.registerApplicationService(
        RemoteArtifactPrefetcher.class, new DefaultPrefetcher());

    workspaceRoot = new WorkspaceRoot(temporaryFolder.getRoot());
    ArtifactLocationDecoder artifactLocationDecoder =
        new MockArtifactLocationDecoder() {
          @Override
          public File decode(ArtifactLocation artifactLocation) {
            return new File(workspaceRoot.directory(), artifactLocation.getRelativePath());
          }
        };

    intellijRule.registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder()
                .setArtifactLocationDecoder(artifactLocationDecoder)
                .build()));

    blazeContext = BlazeContext.create();

    artifactCache =
        new LocalArtifactCache(
            intellijRule.getProject(), "TestArtifactCache", cacheDirectory.getRoot().toPath());
  }

  @Test
  public void noStateFile_existingArtifacts_initializesWithAConsistentStateFile()
      throws IOException {
    cacheDirectory.newFile("untracked_artifact.2.jar");
    cacheDirectory.newFile("untracked_artifact.1.jar");

    artifactCache.initialize();

    File expectedCacheStateFile = new File(cacheDirectory.getRoot(), CACHE_DATA_FILENAME);
    assertThat(cacheDirectory.getRoot().listFiles()).asList().contains(expectedCacheStateFile);

    ArtifactCacheData artifactCacheData =
        LocalArtifactCache.readJsonFromDisk(expectedCacheStateFile);
    // check that all files referenced in the serialized cache data exists
    artifactCacheData.getCacheEntries().stream()
        .map(CacheEntry::getFileName)
        .map(f -> new File(cacheDirectory.getRoot(), f))
        .forEach(f -> assertThat(f.exists()).isTrue());
  }

  private LocalFileOutputArtifactWithoutDigest newLocalOutputArtifact(String path) {
    String execRoot = workspaceRoot.directory().getAbsolutePath();
    String mnemonic = "k8-opt";
    return new LocalFileOutputArtifactWithoutDigest(
      new File(execRoot + "/blaze-out" + mnemonic + "/" + path), Path.of("blaze-out", mnemonic, path), mnemonic);
  }

  @Test
  public void put_addsArtifactInDirectory() throws IOException {
    // Create blaze artifacts in FS
    ImmutableList<OutputArtifactWithoutDigest> outputArtifacts =
        ImmutableList.of(
            newLocalOutputArtifact("relative/path_1/artifact_1.jar"),
            newLocalOutputArtifact("relative/path_2/artifact_2.jar"),
            newLocalOutputArtifact("relative/path_3/artifact_3.jar"));

    for (OutputArtifactWithoutDigest a : outputArtifacts) {
      File file = ((LocalFileOutputArtifactWithoutDigest) a).getFile();
      assertThat(Paths.get(file.getParent()).toFile().mkdirs()).isTrue();
      assertThat(file.createNewFile()).isTrue();
    }

    // Put blaze artifacts in cache
    artifactCache.initialize();
    artifactCache.putAll(outputArtifacts, blazeContext, false);

    // Check that the artifacts were added to the cache.
    ImmutableList<File> expectedFiles =
        Stream.concat(
                outputArtifacts.stream()
                    .map(
                        a -> {
                          try {
                            return CacheEntry.forArtifact(a);
                          } catch (ArtifactNotFoundException e) {
                            return null;
                          }
                        })
                    .filter(Objects::nonNull)
                    .map(CacheEntry::getFileName),
                Stream.of(CACHE_DATA_FILENAME))
            .map(f -> new File(cacheDirectory.getRoot(), f))
            .collect(ImmutableList.toImmutableList());
    assertThat(cacheDirectory.getRoot().listFiles())
        .asList()
        .containsExactlyElementsIn(expectedFiles);
  }

  @Test
  public void get_fetchesCorrectFileForArtifact() throws IOException {
    // Create blaze artifacts in FS
    ImmutableList<OutputArtifactWithoutDigest> outputArtifacts =
        ImmutableList.of(
            newLocalOutputArtifact("relative/path_1/artifact_1.jar"),
            newLocalOutputArtifact("relative/path_2/artifact_2.jar"),
            newLocalOutputArtifact("relative/path_3/artifact_3.jar"));
    for (OutputArtifactWithoutDigest a : outputArtifacts) {
      File file = ((LocalFileOutputArtifactWithoutDigest) a).getFile();
      assertThat(Paths.get(file.getParent()).toFile().mkdirs()).isTrue();
      assertThat(file.createNewFile()).isTrue();
    }

    // Add the artifacts to cache
    artifactCache.initialize();
    artifactCache.putAll(outputArtifacts, blazeContext, false);

    // Attempt to get an arbitraty artifact
    OutputArtifactWithoutDigest artifactToFetch = outputArtifacts.get(1);

    // Check that the returned file matches the expected file
    File expectedFile =
        new File(cacheDirectory.getRoot(), CacheEntry.forArtifact(artifactToFetch).getFileName());
    Path returnedPath = artifactCache.get(artifactToFetch);
    assertThat(Collections.singleton(returnedPath)).doesNotContain(null);
    assertThat(returnedPath.toFile()).isEqualTo(expectedFile);
  }
}
