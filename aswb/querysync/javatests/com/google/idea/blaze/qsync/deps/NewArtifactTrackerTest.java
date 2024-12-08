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
package com.google.idea.blaze.qsync.deps;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.idea.blaze.qsync.artifacts.AspectProtos.directoryArtifact;
import static com.google.idea.blaze.qsync.artifacts.AspectProtos.fileArtifact;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.NoopContext;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.common.artifact.TestOutputArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto.Metadata;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata;
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaArtifacts;
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaTargetArtifacts;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class NewArtifactTrackerTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder cacheDir = new TemporaryFolder();

  @Mock(strictness = Strictness.STRICT_STUBS) BuildArtifactCache cache;
  @Captor ArgumentCaptor<ImmutableCollection<OutputArtifact>> cachedArtifactsCaptor;

  private Map<Label, ImmutableSetMultimap<BuildArtifact, ArtifactMetadata.Extractor<?>>>
      artifactMetadataMap = Maps.newHashMap();

  private NewArtifactTracker<NoopContext> artifactTracker;

  @Before
  public void createArtifactTracker() {
    artifactTracker =
        new NewArtifactTracker<>(
            cacheDir.getRoot().toPath(),
            cache,
            t -> artifactMetadataMap.getOrDefault(t.label(), ImmutableSetMultimap.of()),
            new JavaArtifactMetadata.Factory(),
            MoreExecutors.directExecutor());
  }

  @After
  public void verifyMocks() {
    Mockito.verifyNoMoreInteractions(cache);
  }

  @Test
  public void library_jars() throws BuildException {
    when(cache.addAll(cachedArtifactsCaptor.capture(), any()))
        .thenReturn(Futures.immediateFuture(null));
    artifactTracker.update(
        ImmutableSet.of(Label.of("//test:test"), Label.of("//test:anothertest")),
        OutputInfo.builder()
            .setOutputGroups(
                ImmutableListMultimap.<OutputGroup, OutputArtifact>builder()
                    .putAll(
                        OutputGroup.JARS,
                        TestOutputArtifact.builder()
                            .setArtifactPath(Path.of("out/test.jar"))
                            .setDigest("jar_digest")
                            .build(),
                        TestOutputArtifact.builder()
                            .setArtifactPath(Path.of("out/anothertest.jar"))
                            .setDigest("anotherjar_digest")
                            .build())
                    .build())
            .setArtifactInfo(
                JavaArtifacts.newBuilder()
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:test")
                            .addJars(fileArtifact("out/test.jar"))
                            .build())
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:anothertest")
                            .addJars(fileArtifact("out/anothertest.jar"))
                            .build())
                    .build())
            .build(),
        new NoopContext());
    assertThat(cachedArtifactsCaptor.getValue().stream().map(OutputArtifact::getDigest))
        .containsExactly("jar_digest", "anotherjar_digest");
    assertThat(artifactTracker.getStateSnapshot().depsMap().keySet())
        .containsExactly(Label.of("//test:test"), Label.of("//test:anothertest"));
    ImmutableCollection<TargetBuildInfo> builtDeps = artifactTracker.getBuiltDeps();
    assertThat(builtDeps).hasSize(2);
    ImmutableMap<Label, JavaArtifactInfo> depsMap =
        builtDeps.stream()
            .map(TargetBuildInfo::javaInfo)
            .flatMap(Optional::stream)
            .collect(ImmutableMap.toImmutableMap(JavaArtifactInfo::label, Functions.identity()));
    assertThat(depsMap.keySet())
        .containsExactly(Label.of("//test:test"), Label.of("//test:anothertest"));
    assertThat(depsMap.get(Label.of("//test:test")).jars())
        .containsExactly(
            BuildArtifact.create("jar_digest", Path.of("out/test.jar"), Label.of("//test:test")));
    assertThat(depsMap.get(Label.of("//test:anothertest")).jars())
        .containsExactly(
            BuildArtifact.create(
                "anotherjar_digest",
                Path.of("out/anothertest.jar"),
                Label.of("//test:anothertest")));
  }

  @Test
  public void partial_build_failure_missing_artifacts() throws BuildException {
    when(cache.addAll(cachedArtifactsCaptor.capture(), any()))
        .thenReturn(Futures.immediateFuture(null));
    artifactTracker.update(
        ImmutableSet.of(Label.of("//test:test"), Label.of("//test:anothertest")),
        OutputInfo.builder()
            .setOutputGroups(
                ImmutableListMultimap.<OutputGroup, OutputArtifact>builder()
                    .putAll(
                        OutputGroup.JARS,
                        TestOutputArtifact.builder()
                            .setArtifactPath(Path.of("out/test.jar"))
                            .setDigest("jar_digest")
                            .build())
                    .build())
            .setArtifactInfo(
                JavaArtifacts.newBuilder()
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:test")
                            .addJars(fileArtifact("out/test.jar"))
                            .build())
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:anothertest")
                            .addJars(fileArtifact("out/anothertest.jar"))
                            .build())
                    .build())
            .setTargetsWithErrors(Label.of("//test:anothertest"))
            .build(),
        new NoopContext());
    assertThat(cachedArtifactsCaptor.getValue().stream().map(OutputArtifact::getDigest))
        .containsExactly("jar_digest");
    assertThat(artifactTracker.getStateSnapshot().depsMap().keySet())
        .containsExactly(Label.of("//test:test"), Label.of("//test:anothertest"));
    ImmutableCollection<TargetBuildInfo> builtDeps = artifactTracker.getBuiltDeps();
    assertThat(builtDeps).hasSize(2);
    ImmutableMap<Label, JavaArtifactInfo> depsMap =
        builtDeps.stream()
            .map(TargetBuildInfo::javaInfo)
            .flatMap(Optional::stream)
            .collect(ImmutableMap.toImmutableMap(JavaArtifactInfo::label, Functions.identity()));
    assertThat(depsMap.keySet())
        .containsExactly(Label.of("//test:test"), Label.of("//test:anothertest"));
    assertThat(depsMap.get(Label.of("//test:test")).jars())
        .containsExactly(
            BuildArtifact.create("jar_digest", Path.of("out/test.jar"), Label.of("//test:test")));
    assertThat(depsMap.get(Label.of("//test:anothertest")).jars()).isEmpty();
  }

  @Test
  public void partial_dependency_build_failure_missing_artifacts() throws BuildException {
    when(cache.addAll(cachedArtifactsCaptor.capture(), any()))
        .thenReturn(Futures.immediateFuture(null));
    artifactTracker.update(
        ImmutableSet.of(
            Label.of("//test:test"), Label.of("//test:testdep"), Label.of("//test:anothertest")),
        OutputInfo.builder()
            .setOutputGroups(
                ImmutableListMultimap.<OutputGroup, OutputArtifact>builder()
                    .putAll(
                        OutputGroup.JARS,
                        TestOutputArtifact.builder()
                            .setArtifactPath(Path.of("out/anothertest.jar"))
                            .setDigest("jar_digest")
                            .build())
                    .build())
            .setArtifactInfo(
                JavaArtifacts.newBuilder()
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:test")
                            .addJars(fileArtifact("out/test.jar"))
                            .build())
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:testdep")
                            .addJars(fileArtifact("out/testdep.jar"))
                            .build())
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:anothertest")
                            .addJars(fileArtifact("out/anothertest.jar"))
                            .build())
                    .build())
            .setTargetsWithErrors(Label.of("//test:testdep"))
            .build(),
        new NoopContext());
    assertThat(cachedArtifactsCaptor.getValue().stream().map(OutputArtifact::getDigest))
        .containsExactly("jar_digest");
    assertThat(artifactTracker.getStateSnapshot().depsMap().keySet())
        .containsExactly(
            Label.of("//test:test"), Label.of("//test:testdep"), Label.of("//test:anothertest"));
    ImmutableCollection<TargetBuildInfo> builtDeps = artifactTracker.getBuiltDeps();
    assertThat(builtDeps).hasSize(3);
    ImmutableMap<Label, JavaArtifactInfo> depsMap =
        builtDeps.stream()
            .map(TargetBuildInfo::javaInfo)
            .flatMap(Optional::stream)
            .collect(ImmutableMap.toImmutableMap(JavaArtifactInfo::label, Functions.identity()));
    assertThat(depsMap.keySet())
        .containsExactly(
            Label.of("//test:test"), Label.of("//test:testdep"), Label.of("//test:anothertest"));
    assertThat(depsMap.get(Label.of("//test:anothertest")).jars())
        .containsExactly(
            BuildArtifact.create(
                "jar_digest", Path.of("out/anothertest.jar"), Label.of("//test:anothertest")));
    assertThat(depsMap.get(Label.of("//test:test")).jars()).isEmpty();
  }

  @Test
  public void missing_jar_throws() throws BuildException {
    when(cache.addAll(cachedArtifactsCaptor.capture(), any()))
        .thenReturn(Futures.immediateFuture(null));
    // if we're missing a digest for a target that did *not* fail to build, we should throw as that
    // implies a bug elsewhere (potentially in the aspect).
    assertThrows(
        IllegalStateException.class,
        () ->
            artifactTracker.update(
                ImmutableSet.of(Label.of("//test:test"), Label.of("//test:anothertest")),
                OutputInfo.builder()
                    .setOutputGroups(
                        ImmutableListMultimap.<OutputGroup, OutputArtifact>builder()
                            .putAll(
                                OutputGroup.JARS,
                                TestOutputArtifact.builder()
                                    .setArtifactPath(Path.of("out/test.jar"))
                                    .setDigest("jar_digest")
                                    .build())
                            .build())
                    .setArtifactInfo(
                        JavaArtifacts.newBuilder()
                            .addArtifacts(
                                JavaTargetArtifacts.newBuilder()
                                    .setTarget("//test:test")
                                    .addJars(fileArtifact("out/test.jar"))
                                    .build())
                            .addArtifacts(
                                JavaTargetArtifacts.newBuilder()
                                    .setTarget("//test:anothertest")
                                    .addJars(fileArtifact("out/anothertest.jar"))
                                    .build())
                            .build())
                    .build(),
                new NoopContext()));
  }

  @Test
  public void duplicate_artifact_mappings() throws BuildException {
    when(cache.addAll(cachedArtifactsCaptor.capture(), any()))
        .thenReturn(Futures.immediateFuture(null));
    // Add the same artifact as a jar and srcjar:
    artifactTracker.update(
        ImmutableSet.of(Label.of("//test:test")),
        OutputInfo.builder()
            .setOutputGroups(
                ImmutableListMultimap.<OutputGroup, OutputArtifact>builder()
                    .putAll(
                        OutputGroup.JARS,
                        TestOutputArtifact.builder()
                            .setArtifactPath(Path.of("out/test.jar"))
                            .setDigest("jar_digest")
                            .build())
                    .putAll(
                        OutputGroup.GENSRCS,
                        TestOutputArtifact.builder()
                            .setArtifactPath(Path.of("out/test.jar"))
                            .setDigest("jar_digest")
                            .build())
                    .build())
            .setArtifactInfo(
                JavaArtifacts.newBuilder()
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:test")
                            .addJars(fileArtifact("out/test.jar"))
                            .addGenSrcs(fileArtifact("out/test.jar"))
                            .build())
                    .build())
            .build(),
        new NoopContext());
  }

  @Test
  public void artifact_directory() throws BuildException {
    when(cache.addAll(cachedArtifactsCaptor.capture(), any()))
        .thenReturn(Futures.immediateFuture(null));
    artifactTracker.update(
        ImmutableSet.of(Label.of("//test:test")),
        OutputInfo.builder()
            .setOutputGroups(
                ImmutableListMultimap.<OutputGroup, OutputArtifact>builder()
                    .putAll(
                        OutputGroup.JARS,
                        TestOutputArtifact.builder()
                            .setArtifactPath(Path.of("out/test.jar"))
                            .setDigest("jar_digest")
                            .build())
                    .putAll(
                        OutputGroup.GENSRCS,
                        TestOutputArtifact.builder()
                            .setArtifactPath(Path.of("out/src/Class1.java"))
                            .setDigest("class1_digest")
                            .build(),
                        TestOutputArtifact.builder()
                            .setArtifactPath(Path.of("out/src/Class2.java"))
                            .setDigest("class2_digest")
                            .build())
                    .build())
            .setArtifactInfo(
                JavaArtifacts.newBuilder()
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:test")
                            .addJars(fileArtifact("out/test.jar"))
                            .addGenSrcs(directoryArtifact("out/src"))
                            .build())
                    .build())
            .build(),
        new NoopContext());
    assertThat(cachedArtifactsCaptor.getValue().stream().map(OutputArtifact::getDigest))
        .containsExactly("jar_digest", "class1_digest", "class2_digest");

    assertThat(getOnlyElement(artifactTracker.getBuiltDeps()).javaInfo().get().genSrcs())
        .containsExactly(
            BuildArtifact.create(
                "class1_digest", Path.of("out/src/Class1.java"), Label.of("//test:test")),
            BuildArtifact.create(
                "class2_digest", Path.of("out/src/Class2.java"), Label.of("//test:test")));
  }

  static class TestArtifactMetadata<T extends ArtifactMetadata>
      implements ArtifactMetadata.Extractor<T> {

    private final T metadata;

    TestArtifactMetadata(T metadata) {
      this.metadata = metadata;
    }

    @Override
    public T extractFrom(CachedArtifact buildArtifact, Object nameForLogs) {
      return metadata;
    }

    @Override
    public Class<T> metadataClass() {
      return (Class<T>) metadata.getClass();
    }
  }

  record Metadata1(String content) implements ArtifactMetadata {

    @Override
    public Metadata toProto() {
      return Metadata.getDefaultInstance();
    }
  }

  record Metadata2(String content) implements ArtifactMetadata {

    @Override
    public Metadata toProto() {
      return Metadata.getDefaultInstance();
    }
  }

  @Test
  public void extract_artifact_metadata() throws BuildException {
    when(cache.addAll(cachedArtifactsCaptor.capture(), any()))
        .thenReturn(Futures.immediateFuture(null));
    when(cache.get("jar_digest"))
        .thenReturn(
            Optional.of(Futures.immediateFuture(new CachedArtifact(Path.of("/cache/jar_digest")))));
    BuildArtifact jarArtifact =
        BuildArtifact.create("jar_digest", Path.of("out/test.jar"), Label.of("//test:test"));
    artifactMetadataMap.put(
        Label.of("//test:test"),
        ImmutableSetMultimap.<BuildArtifact, ArtifactMetadata.Extractor<?>>builder()
            .putAll(
                jarArtifact,
                new TestArtifactMetadata<>(new Metadata1("md1")),
                new TestArtifactMetadata<>(new Metadata2("md2")))
            .build());
    artifactTracker.update(
        ImmutableSet.of(Label.of("//test:test")),
        OutputInfo.builder()
            .setOutputGroups(
                ImmutableListMultimap.<OutputGroup, OutputArtifact>builder()
                    .putAll(
                        OutputGroup.JARS,
                        TestOutputArtifact.builder()
                            .setArtifactPath(Path.of("out/test.jar"))
                            .setDigest("jar_digest")
                            .build())
                    .build())
            .setArtifactInfo(
                JavaArtifacts.newBuilder()
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:test")
                            .addJars(fileArtifact("out/test.jar"))
                            .build())
                    .build())
            .build(),
        new NoopContext());

    ImmutableCollection<TargetBuildInfo> builtDeps = artifactTracker.getBuiltDeps();
    assertThat(
            getOnlyElement(getOnlyElement(builtDeps).javaInfo().orElseThrow().jars())
                .getMetadata(Metadata1.class))
        .hasValue(new Metadata1("md1"));
    assertThat(
            getOnlyElement(getOnlyElement(builtDeps).javaInfo().orElseThrow().jars())
                .getMetadata(Metadata2.class))
        .hasValue(new Metadata2("md2"));
  }

  /**
   * See {@link NewArtifactTracker#getUniqueTargetBuildInfos} to understand what this is testing.
   */
  @Test
  public void disjoint_compile_jars() throws BuildException {
    when(cache.addAll(any(), any())).thenReturn(Futures.immediateFuture(null));
    artifactTracker.update(
        ImmutableSet.of(Label.of("//test:test_proto")),
        OutputInfo.builder()
            .setOutputGroups(
                ImmutableListMultimap.<OutputGroup, OutputArtifact>builder()
                    .putAll(
                        OutputGroup.JARS,
                        TestOutputArtifact.builder()
                            .setArtifactPath(Path.of("out/test_proto.jar"))
                            .setDigest("jar_digest")
                            .build(),
                        TestOutputArtifact.builder()
                            .setArtifactPath(Path.of("out/test_mutable_proto.jar"))
                            .setDigest("jar2_digest")
                            .build())
                    .build())
            .setArtifactInfo(
                JavaArtifacts.newBuilder()
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:test_proto")
                            .addJars(fileArtifact("out/test_proto.jar"))
                            .build())
                    .build(),
                JavaArtifacts.newBuilder()
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:test_proto")
                            .addJars(fileArtifact("out/test_proto.jar"))
                            .addJars(fileArtifact("out/test_mutable_proto.jar"))
                            .build())
                    .build())
            .build(),
        new NoopContext());
    assertThat(artifactTracker.getStateSnapshot().depsMap().keySet())
        .containsExactly(Label.of("//test:test_proto"));
    ImmutableCollection<TargetBuildInfo> builtDeps = artifactTracker.getBuiltDeps();
    assertThat(builtDeps).hasSize(1);
    ImmutableMap<Label, JavaArtifactInfo> depsMap =
        builtDeps.stream()
            .map(TargetBuildInfo::javaInfo)
            .flatMap(Optional::stream)
            .collect(ImmutableMap.toImmutableMap(JavaArtifactInfo::label, Functions.identity()));
    assertThat(depsMap.keySet()).containsExactly(Label.of("//test:test_proto"));
    assertThat(depsMap.get(Label.of("//test:test_proto")).jars())
        .containsExactly(
            BuildArtifact.create(
                "jar_digest", Path.of("out/test_proto.jar"), Label.of("//test:test_proto")),
            BuildArtifact.create(
                "jar2_digest",
                Path.of("out/test_mutable_proto.jar"),
                Label.of("//test:test_proto")));
  }

  /**
   * See {@link NewArtifactTracker#getUniqueTargetBuildInfos} to understand what this is testing.
   *
   * <p>This test covers the case when there are genuine conflicts.
   */
  @Test
  public void conflicting_targets_ignored() throws BuildException {
    when(cache.addAll(any(), any())).thenReturn(Futures.immediateFuture(null));
    artifactTracker.update(
        ImmutableSet.of(Label.of("//test:test_proto")),
        OutputInfo.builder()
            .setOutputGroups(
                ImmutableListMultimap.<OutputGroup, OutputArtifact>builder()
                    .putAll(
                        OutputGroup.JARS,
                        TestOutputArtifact.builder()
                            .setArtifactPath(Path.of("out/test_proto.jar"))
                            .setDigest("jar_digest")
                            .build(),
                        TestOutputArtifact.builder()
                            .setArtifactPath(Path.of("out/test_mutable_proto.jar"))
                            .setDigest("jar2_digest")
                            .build())
                    .build())
            .setArtifactInfo(
                JavaArtifacts.newBuilder()
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:test_proto")
                            .addJars(fileArtifact("out/test_proto.jar"))
                            .addSrcs("test/test.proto")
                            .build())
                    .build(),
                JavaArtifacts.newBuilder()
                    .addArtifacts(
                        JavaTargetArtifacts.newBuilder()
                            .setTarget("//test:test_proto")
                            .addJars(fileArtifact("out/test_proto.jar"))
                            .addJars(fileArtifact("out/test_mutable_proto.jar"))
                            .build())
                    .build())
            .build(),
        new NoopContext());
    assertThat(artifactTracker.getStateSnapshot().depsMap().keySet()).containsExactly(Label.of("//test:test_proto"));
    ImmutableCollection<TargetBuildInfo> builtDeps = artifactTracker.getBuiltDeps();
    assertThat(
      getOnlyElement(builtDeps).javaInfo().orElseThrow().jars()
    ).containsExactly(
      BuildArtifact.create(
        "jar_digest",
        Path.of("out/test_proto.jar"),
        Label.of("//test:test_proto")),
      BuildArtifact.create(
        "jar2_digest",
        Path.of("out/test_mutable_proto.jar"),
        Label.of("//test:test_proto"))
    );
  }
}
