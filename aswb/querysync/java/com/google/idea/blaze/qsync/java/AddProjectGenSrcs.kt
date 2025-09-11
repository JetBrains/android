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
package com.google.idea.blaze.qsync.java

import com.google.auto.value.AutoValue
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSetMultimap
import com.google.common.collect.Lists
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.ArtifactDirectories
import com.google.idea.blaze.qsync.deps.ArtifactDirectoryBuilder
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.DependencyBuildContext
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.TestSourceGlobMatcher
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.function.Consumer
import java.util.stream.Collectors
import kotlin.jvm.optionals.getOrNull

/**
 * Adds generated java and kotlin source files to the project proto.
 *
 *
 * This class also resolves conflicts between multiple generated source files that resolve to
 * the same output path, i.e. that have the same java class name.
 */
class AddProjectGenSrcs(
  private val projectDefinition: ProjectDefinition,
  private val packageReader: ArtifactMetadata.Extractor<JavaArtifactMetadata.JavaSourcePackage>
) : ProjectProtoUpdateOperation {
  private val testSourceMatcher: TestSourceGlobMatcher = TestSourceGlobMatcher.create(projectDefinition)

  /**
   * A simple holder class for a build artifact and info about the build that produced it. This is
   * used to resolve conflicts between source files.
   */
  private data class ArtifactWithOrigin(
    val artifact: BuildArtifact,
    val origin: DependencyBuildContext,
  ) : Comparable<ArtifactWithOrigin> {

    /**
     * When we find conflicting generated sources (same java source path), we resolve the conflict
     * by selecting the per this method.
     *
     *
     * If the files were produced by different build invocations, select the most recent.
     * Otherwise, disambiguate using the target string.
     */
    override fun compareTo(other: ArtifactWithOrigin): Int {
      // Note: we do a reverse comparison for start time to ensure the newest build "wins".
      var compare = other.origin.startTime().compareTo(origin.startTime())
      if (compare == 0) {
        compare = artifact.target().toString().compareTo(other.artifact.target().toString())
      }
      return compare
    }
  }

  private fun getSourceFileArtifacts(target: TargetBuildInfo): List<BuildArtifact> {
    val javaInfo = target.javaInfo().getOrNull() ?: return emptyList()
    if (!projectDefinition.isIncluded(javaInfo.label())) {
      return emptyList()
    }
    return javaInfo.genSrcs().filter { JAVA_SRC_EXTENSIONS.contains(it.getExtension()) || PROTO_EXTENSIONS.contains(it.getExtension()) }
  }

  override fun getRequiredArtifacts(forTarget: TargetBuildInfo): Map<BuildArtifact, Collection<ArtifactMetadata.Extractor<*>>> {
    return getSourceFileArtifacts(forTarget).associateWith { listOf(packageReader) }
  }

  @Throws(BuildException::class)
  override fun update(
    update: ProjectProtoUpdate,
    artifactState: ArtifactTracker.State,
    context: Context<*>
  ) {
    val javaSrc = update.artifactDirectory(ArtifactDirectories.JAVA_GEN_SRC)
    val javatestsSrc = update.artifactDirectory(ArtifactDirectories.JAVA_GEN_TESTSRC)
    val srcsByJavaPath = mutableMapOf<Path, MutableList<ArtifactWithOrigin>>()
    val missingPackageArtifacts = mutableListOf<BuildArtifact>()
    for (target in artifactState.targets()) {
      for (genSrc in getSourceFileArtifacts(target)) {
        val javaPackage =
          genSrc.getMetadata(JavaArtifactMetadata.JavaSourcePackage::class.java)
            .map(JavaArtifactMetadata.JavaSourcePackage::name)
            .orElse(null)
        if (javaPackage == null) {
          missingPackageArtifacts.add(genSrc)
        } else {
          val finalDest =
            Path.of(javaPackage.replace('.', '/')).resolve(genSrc.artifactPath().fileName)
          srcsByJavaPath.getOrPut(finalDest) { mutableListOf() }
            .add(ArtifactWithOrigin(genSrc, target.buildContext()))
        }
      }
    }
    if (!missingPackageArtifacts.isEmpty()) {
      val showSourcesLimit = 10
      update.context().output(
        PrintOutput.error(
          "WARNING: Ignoring %d generated source file(s) due to missing package info:\n  %s",
          missingPackageArtifacts.size,
          missingPackageArtifacts.joinToString(
            limit = showSourcesLimit,
            separator = "\n",
            truncated = "and ${missingPackageArtifacts.size - showSourcesLimit} more"
          ) { it.artifactPath().toString() }
        )
      )
      update.context().setHasWarnings()
    }
    for (entry in srcsByJavaPath.entries) {
      val finalDest = entry.key
      val candidates: MutableCollection<ArtifactWithOrigin> = entry.value
      // before warning, check that the conflicting sources do actually differ. If they're the
      // same artifact underneath, there's no actual conflict.
      val uniqueDigests = candidates.map { it.artifact.digest() }.distinct().count()
      if (uniqueDigests > 1) {
        update
          .context()
          .output(
            PrintOutput.error(
              ("WARNING: your project contains conflicting generated java sources for:\n"
                + "  %s\n"
                + "From:\n"
                + "  %s"),
              finalDest,
              candidates
                .joinToString(separator = "\n  ") {
                  val target = it.artifact.target()
                  val artifactPath = it.artifact.artifactPath()
                  val ago = formatDuration(Duration.between(it.origin.startTime(), Instant.now()))
                  "$artifactPath ($target built $ago ago)"
                }
            )
          )
        update.context().setHasWarnings()
      }

      val chosen = candidates.minOrNull() ?: error("No candidates")
      if (testSourceMatcher.matches(chosen.artifact.target().getBuildPackagePath())) {
        javatestsSrc.addIfNewer(finalDest, chosen.artifact, chosen.origin)
      } else {
        javaSrc.addIfNewer(finalDest, chosen.artifact, chosen.origin)
      }
    }
    for (gensrcDir in listOf(javaSrc, javatestsSrc)) {
      if (!gensrcDir.isEmpty) {
        val pathProto = gensrcDir.root().toProto()
        val genSourcesContentEntry =
          ProjectProto.ContentEntry.newBuilder().setRoot(pathProto)
        genSourcesContentEntry.addSources(
          ProjectProto.SourceFolder.newBuilder()
            .setProjectPath(pathProto)
            .setIsGenerated(true)
            .setIsTest(gensrcDir === javatestsSrc)
            .setPackagePrefix("")
        )
        update.workspaceModule().addContentEntries(genSourcesContentEntry.build())
      }
    }
  }


  /**
   * A simple inexact duration format, returning a duration in whichever unit of (days, hours,
   * minutes, seconds) is the first to get a non-zero figure.
   */
  private fun formatDuration(p: Duration): String {
    for (unit in listOf(
      ChronoUnit.DAYS,
      ChronoUnit.HOURS,
      ChronoUnit.MINUTES
    )) {
      val durationInUnits = p.seconds / unit.duration.seconds
      if (durationInUnits > 0) {
        return "$durationInUnits $unit"
      }
    }
    return "${p.seconds} seconds"
  }

  companion object {
    private val JAVA_SRC_EXTENSIONS = setOf("java", "kt")
    private val PROTO_EXTENSIONS = setOf("proto")
  }
}
