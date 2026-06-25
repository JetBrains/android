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
package com.google.idea.blaze.qsync.deps

import com.google.common.collect.ImmutableMap
import com.google.common.io.ByteSource
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.idea.blaze.base.BlazeTestCase
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.common.artifact.BuildArtifactCache
import com.google.idea.blaze.common.artifact.CachedArtifact
import com.google.idea.blaze.common.artifact.OutputArtifact
import com.google.idea.blaze.common.artifact.TestOutputArtifact
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata
import com.google.idea.blaze.qsync.artifacts.AspectProtos
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.OutputInfo.Companion.builder
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata
import com.google.idea.blaze.qsync.java.JavaTargetInfo
import com.google.idea.common.experiments.ExperimentService
import com.google.idea.common.experiments.MockExperimentService
import java.nio.file.Path
import java.time.Duration
import java.util.Optional
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class NewArtifactTrackerTest : BlazeTestCase() {
  @get:Rule var cacheDir: TemporaryFolder = TemporaryFolder()

  private val cache =
    object : BuildArtifactCache {
      val artifacts = mutableListOf<OutputArtifact>()

      override fun addAll(artifacts: Collection<OutputArtifact>, context: Context<*>): ListenableFuture<*> {
        this.artifacts.addAll(artifacts)
        return Futures.immediateFuture(null)
      }

      override fun get(digest: String): Optional<ListenableFuture<CachedArtifact>> {
        return Optional.of(
          artifacts.find { it.digest == digest }?.let { Futures.immediateFuture(CachedArtifact(Path.of("/cache/${it.digest}"))) }
            ?: error("""Unexpected get("$digest")""")
        )
      }

      override fun clean(maxTargetSizeBytes: Long, minKeepDuration: Duration) = Unit

      override fun purge() = Unit

      override fun getBugreportFiles(): ImmutableMap<String, ByteSource> = ImmutableMap.of()
    }

  private val artifactMetadataMap: MutableMap<Label, Map<BuildArtifact, List<ArtifactMetadata.Extractor<*>>>> = mutableMapOf()

  private lateinit var artifactTracker: NewArtifactTracker<NoopContext>

  override fun initTest(applicationServices: Container, projectServices: Container) {
    super.initTest(applicationServices, projectServices)
    applicationServices.register(ExperimentService::class.java, MockExperimentService())
    artifactTracker =
      NewArtifactTracker<NoopContext>(
        cacheDir.root.toPath().resolve("w"),
        cacheDir.root.toPath().resolve("p"),
        cache,
        { artifactMetadataMap[it.label].orEmpty() },
        JavaArtifactMetadata.Factory(),
        MoreExecutors.directExecutor(),
      )
  }

  @Test
  @Throws(BuildException::class)
  fun library_jars() {
    artifactTracker.update(
      setOf(Label.of("//test:test"), Label.of("//test:anothertest")),
      builder()
        .setOutputGroups(
          mapOf(
            OutputGroup.JARS to
              listOf(
                TestOutputArtifact.builder().setArtifactPath(Path.of("out/test.jar")).setDigest("jar_digest").build(),
                TestOutputArtifact.builder().setArtifactPath(Path.of("out/anothertest.jar")).setDigest("anotherjar_digest").build(),
              )
          )
        )
        .setArtifactInfo(
          JavaTargetInfo.JavaArtifacts.newBuilder().setTarget("//test:test").addJars(AspectProtos.fileArtifact("out/test.jar")).build(),
          JavaTargetInfo.JavaArtifacts.newBuilder()
            .setTarget("//test:anothertest")
            .addJars(AspectProtos.fileArtifact("out/anothertest.jar"))
            .build(),
        )
        .build(),
      NoopContext(),
    )
    assertThat(cache.artifacts.map { it.digest }).containsExactly("jar_digest", "anotherjar_digest")
    assertThat(artifactTracker.stateSnapshot.depsMap().keys).containsExactly(Label.of("//test:test"), Label.of("//test:anothertest"))
    val builtDeps = artifactTracker.builtDepsForTesting
    assertThat(builtDeps).hasSize(2)
    val depsMap = builtDeps.mapNotNull { (it as? TargetBuildInfo.Java)?.javaInfo }.associateBy { it.label }

    assertThat(depsMap.keys).containsExactly(Label.of("//test:test"), Label.of("//test:anothertest"))
    assertThat(depsMap.get(Label.of("//test:test"))!!.jars)
      .containsExactly(BuildArtifact("jar_digest", Path.of("out/test.jar"), Label.of("//test:test")))
    assertThat(depsMap.get(Label.of("//test:anothertest"))!!.jars)
      .containsExactly(BuildArtifact("anotherjar_digest", Path.of("out/anothertest.jar"), Label.of("//test:anothertest")))
  }

  @Test
  @Throws(BuildException::class)
  fun partial_build_failure_missing_artifacts() {
    artifactTracker.update(
      setOf(Label.of("//test:test"), Label.of("//test:anothertest")),
      builder()
        .setOutputGroups(
          mapOf(
            OutputGroup.JARS to
              listOf(TestOutputArtifact.builder().setArtifactPath(Path.of("out/test.jar")).setDigest("jar_digest").build())
          )
        )
        .setArtifactInfo(
          JavaTargetInfo.JavaArtifacts.newBuilder().setTarget("//test:test").addJars(AspectProtos.fileArtifact("out/test.jar")).build(),
          JavaTargetInfo.JavaArtifacts.newBuilder()
            .setTarget("//test:anothertest")
            .addJars(AspectProtos.fileArtifact("out/anothertest.jar"))
            .build(),
        )
        .setTargetsWithErrors(Label.of("//test:anothertest"))
        .build(),
      NoopContext(),
    )
    assertThat(cache.artifacts.map { it.digest }).containsExactly("jar_digest")
    assertThat(artifactTracker.stateSnapshot.depsMap().keys).containsExactly(Label.of("//test:test"), Label.of("//test:anothertest"))
    val builtDeps = artifactTracker.builtDepsForTesting
    assertThat(builtDeps).hasSize(2)
    val depsMap = builtDeps.mapNotNull { (it as? TargetBuildInfo.Java)?.javaInfo }.associateBy { it.label }
    assertThat(depsMap.keys).containsExactly(Label.of("//test:test"), Label.of("//test:anothertest"))
    assertThat(depsMap.get(Label.of("//test:test"))!!.jars)
      .containsExactly(BuildArtifact("jar_digest", Path.of("out/test.jar"), Label.of("//test:test")))
    assertThat(depsMap.get(Label.of("//test:anothertest"))!!.jars).isEmpty()
  }

  @Test
  @Throws(BuildException::class)
  fun partial_dependency_build_failure_missing_artifacts() {
    artifactTracker.update(
      setOf(Label.of("//test:test"), Label.of("//test:testdep"), Label.of("//test:anothertest")),
      builder()
        .setOutputGroups(
          mapOf(
            OutputGroup.JARS to
              listOf(TestOutputArtifact.builder().setArtifactPath(Path.of("out/anothertest.jar")).setDigest("jar_digest").build())
          )
        )
        .setArtifactInfo(
          JavaTargetInfo.JavaArtifacts.newBuilder().setTarget("//test:test").addJars(AspectProtos.fileArtifact("out/test.jar")).build(),
          JavaTargetInfo.JavaArtifacts.newBuilder()
            .setTarget("//test:testdep")
            .addJars(AspectProtos.fileArtifact("out/testdep.jar"))
            .build(),
          JavaTargetInfo.JavaArtifacts.newBuilder()
            .setTarget("//test:anothertest")
            .addJars(AspectProtos.fileArtifact("out/anothertest.jar"))
            .build(),
        )
        .setTargetsWithErrors(Label.of("//test:testdep"))
        .build(),
      NoopContext(),
    )
    assertThat(cache.artifacts.map { it.digest }).containsExactly("jar_digest")
    assertThat(artifactTracker.stateSnapshot.depsMap().keys)
      .containsExactly(Label.of("//test:test"), Label.of("//test:testdep"), Label.of("//test:anothertest"))
    val builtDeps = artifactTracker.builtDepsForTesting
    assertThat(builtDeps).hasSize(3)
    val depsMap = builtDeps.mapNotNull { (it as? TargetBuildInfo.Java)?.javaInfo }.associateBy { it.label }
    assertThat(depsMap.keys).containsExactly(Label.of("//test:test"), Label.of("//test:testdep"), Label.of("//test:anothertest"))
    assertThat(depsMap.get(Label.of("//test:anothertest"))!!.jars)
      .containsExactly(BuildArtifact("jar_digest", Path.of("out/anothertest.jar"), Label.of("//test:anothertest")))
    assertThat(depsMap.get(Label.of("//test:test"))!!.jars).isEmpty()
  }

  @Test
  @Throws(BuildException::class)
  fun missing_jar_throws() {
    // if we're missing a digest for a target that did *not* fail to build, we should throw as that
    // implies a bug elsewhere (potentially in the aspect).
    Assert.assertThrows(IllegalStateException::class.java) {
      artifactTracker.update(
        setOf(Label.of("//test:test"), Label.of("//test:anothertest")),
        builder()
          .setOutputGroups(
            mapOf(
              OutputGroup.JARS to
                listOf(TestOutputArtifact.builder().setArtifactPath(Path.of("out/test.jar")).setDigest("jar_digest").build())
            )
          )
          .setArtifactInfo(
            JavaTargetInfo.JavaArtifacts.newBuilder().setTarget("//test:test").addJars(AspectProtos.fileArtifact("out/test.jar")).build(),
            JavaTargetInfo.JavaArtifacts.newBuilder()
              .setTarget("//test:anothertest")
              .addJars(AspectProtos.fileArtifact("out/anothertest.jar"))
              .build(),
          )
          .build(),
        NoopContext(),
      )
    }
  }

  @Test
  @Throws(BuildException::class)
  fun duplicate_artifact_mappings() {
    // Add the same artifact as a jar and srcjar:
    artifactTracker.update(
      setOf(Label.of("//test:test")),
      builder()
        .setOutputGroups(
          mapOf(
            OutputGroup.JARS to
              listOf(TestOutputArtifact.builder().setArtifactPath(Path.of("out/test.jar")).setDigest("jar_digest").build()),
            OutputGroup.GENSRCS to
              listOf(TestOutputArtifact.builder().setArtifactPath(Path.of("out/test.jar")).setDigest("jar_digest").build()),
          )
        )
        .setArtifactInfo(
          JavaTargetInfo.JavaArtifacts.newBuilder()
            .setTarget("//test:test")
            .addJars(AspectProtos.fileArtifact("out/test.jar"))
            .addGenSrcs(AspectProtos.fileArtifact("out/test.jar"))
            .build()
        )
        .build(),
      NoopContext(),
    )
  }

  @Test
  @Throws(BuildException::class)
  fun artifact_directory() {
    artifactTracker.update(
      setOf(Label.of("//test:test")),
      builder()
        .setOutputGroups(
          mapOf(
            OutputGroup.JARS to
              listOf(TestOutputArtifact.builder().setArtifactPath(Path.of("out/test.jar")).setDigest("jar_digest").build()),
            OutputGroup.GENSRCS to
              listOf(
                TestOutputArtifact.builder().setArtifactPath(Path.of("out/src/Class1.java")).setDigest("class1_digest").build(),
                TestOutputArtifact.builder().setArtifactPath(Path.of("out/src/Class2.java")).setDigest("class2_digest").build(),
              ),
          )
        )
        .setArtifactInfo(
          JavaTargetInfo.JavaArtifacts.newBuilder()
            .setTarget("//test:test")
            .addJars(AspectProtos.fileArtifact("out/test.jar"))
            .addGenSrcs(AspectProtos.directoryArtifact("out/src"))
            .build()
        )
        .build(),
      NoopContext(),
    )
    assertThat(cache.artifacts.map { it.digest }).containsExactly("jar_digest", "class1_digest", "class2_digest")

    assertThat((artifactTracker.builtDepsForTesting.single() as TargetBuildInfo.Java).javaInfo.genSrcs)
      .containsExactly(
        BuildArtifact("class1_digest", Path.of("out/src/Class1.java"), Label.of("//test:test")),
        BuildArtifact("class2_digest", Path.of("out/src/Class2.java"), Label.of("//test:test")),
      )
  }

  internal class TestArtifactMetadata<T : ArtifactMetadata>(private val metadata: T) : ArtifactMetadata.Extractor<T> {
    override fun extractFrom(buildArtifact: CachedArtifact, nameForLogs: Any): T {
      return metadata
    }

    override fun metadataClass(): Class<T> {
      return metadata.javaClass
    }
  }

  @JvmRecord
  internal data class Metadata1(val content: String?) : ArtifactMetadata {
    override fun toProto(): ArtifactTrackerProto.Metadata? {
      return ArtifactTrackerProto.Metadata.getDefaultInstance()
    }
  }

  @JvmRecord
  internal data class Metadata2(val content: String?) : ArtifactMetadata {
    override fun toProto(): ArtifactTrackerProto.Metadata? {
      return ArtifactTrackerProto.Metadata.getDefaultInstance()
    }
  }

  @Test
  @Throws(BuildException::class)
  fun extract_artifact_metadata() {
    cache.artifacts.add(TestOutputArtifact.builder().setArtifactPath(Path.of("out/test.jar")).setDigest("jar_digest").build())
    val jarArtifact = BuildArtifact("jar_digest", Path.of("out/test.jar"), Label.of("//test:test"))
    artifactMetadataMap[Label.of("//test:test")] =
      mapOf(jarArtifact to listOf(TestArtifactMetadata(Metadata1("md1")), TestArtifactMetadata(Metadata2("md2"))))

    artifactTracker.update(
      setOf(Label.of("//test:test")),
      builder()
        .setOutputGroups(
          mapOf(
            OutputGroup.JARS to
              listOf(TestOutputArtifact.builder().setArtifactPath(Path.of("out/test.jar")).setDigest("jar_digest").build())
          )
        )
        .setArtifactInfo(
          JavaTargetInfo.JavaArtifacts.newBuilder().setTarget("//test:test").addJars(AspectProtos.fileArtifact("out/test.jar")).build()
        )
        .build(),
      NoopContext(),
    )

    val builtDeps = artifactTracker.builtDepsForTesting
    assertThat((builtDeps.single() as TargetBuildInfo.Java).javaInfo.jars.single().getMetadata(Metadata1::class.java))
      .isEqualTo(Metadata1("md1"))
    assertThat((builtDeps.single() as TargetBuildInfo.Java).javaInfo.jars.single().getMetadata(Metadata2::class.java))
      .isEqualTo(Metadata2("md2"))
  }

  /** See [NewArtifactTracker.getUniqueTargetBuildInfos] to understand what this is testing. */
  @Test
  @Throws(BuildException::class)
  fun disjoint_compile_jars() {
    artifactTracker.update(
      setOf(Label.of("//test:test_proto")),
      builder()
        .setOutputGroups(
          mapOf(
            OutputGroup.JARS to
              listOf(
                TestOutputArtifact.builder().setArtifactPath(Path.of("out/test_proto.jar")).setDigest("jar_digest").build(),
                TestOutputArtifact.builder().setArtifactPath(Path.of("out/test_mutable_proto.jar")).setDigest("jar2_digest").build(),
              )
          )
        )
        .setArtifactInfo(
          JavaTargetInfo.JavaArtifacts.newBuilder()
            .setTarget("//test:test_proto")
            .addJars(AspectProtos.fileArtifact("out/test_proto.jar"))
            .build(),
          JavaTargetInfo.JavaArtifacts.newBuilder()
            .setTarget("//test:test_proto")
            .addJars(AspectProtos.fileArtifact("out/test_proto.jar"))
            .addJars(AspectProtos.fileArtifact("out/test_mutable_proto.jar"))
            .build(),
        )
        .build(),
      NoopContext(),
    )
    assertThat(artifactTracker.stateSnapshot.depsMap().keys).containsExactly(Label.of("//test:test_proto"))
    val builtDeps = artifactTracker.builtDepsForTesting
    assertThat((builtDeps.single() as TargetBuildInfo.Java).javaInfo.jars)
      .containsExactly(
        BuildArtifact("jar_digest", Path.of("out/test_proto.jar"), Label.of("//test:test_proto")),
        BuildArtifact("jar2_digest", Path.of("out/test_mutable_proto.jar"), Label.of("//test:test_proto")),
      )
  }

  /**
   * See [NewArtifactTracker.getUniqueTargetBuildInfos] to understand what this is testing.
   *
   * This test covers the case when there are genuine conflicts.
   */
  @Test
  @Throws(BuildException::class)
  fun conflicting_targets_ignored() {
    artifactTracker.update(
      setOf(Label.of("//test:test_proto")),
      builder()
        .setOutputGroups(
          mapOf(
            OutputGroup.JARS to
              listOf(
                TestOutputArtifact.builder().setArtifactPath(Path.of("out/test_proto.jar")).setDigest("jar_digest").build(),
                TestOutputArtifact.builder().setArtifactPath(Path.of("out/test_mutable_proto.jar")).setDigest("jar2_digest").build(),
              )
          )
        )
        .setArtifactInfo(
          JavaTargetInfo.JavaArtifacts.newBuilder()
            .setTarget("//test:test_proto")
            .addJars(AspectProtos.fileArtifact("out/test_proto.jar"))
            .addSrcs("test/test.proto")
            .build(),
          JavaTargetInfo.JavaArtifacts.newBuilder()
            .setTarget("//test:test_proto")
            .addJars(AspectProtos.fileArtifact("out/test_proto.jar"))
            .addJars(AspectProtos.fileArtifact("out/test_mutable_proto.jar"))
            .build(),
        )
        .build(),
      NoopContext(),
    )
    assertThat(artifactTracker.stateSnapshot.depsMap().keys).containsExactly(Label.of("//test:test_proto"))
    val builtDeps = artifactTracker.builtDepsForTesting
    assertThat((builtDeps.single() as TargetBuildInfo.Java).javaInfo.jars)
      .containsExactly(
        BuildArtifact("jar_digest", Path.of("out/test_proto.jar"), Label.of("//test:test_proto")),
        BuildArtifact("jar2_digest", Path.of("out/test_mutable_proto.jar"), Label.of("//test:test_proto")),
      )
  }
}
