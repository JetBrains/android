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
package com.google.idea.blaze.qsync.java;

import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.base.Supplier;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.deps.ArtifactDirectoryBuilder;
import com.google.idea.blaze.qsync.deps.DependencyBuildContext;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.TestSourceGlobMatcher;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/**
 * Adds generated java and kotlin source files to the project proto.
 *
 * <p>This class also resolves conflicts between multiple generated source files that resolve to the
 * same output path, i.e. that have the same java class name.
 */
public class AddProjectGenSrcs implements ProjectProtoUpdateOperation {

  private static final ImmutableSet<String> JAVA_SRC_EXTENSIONS = ImmutableSet.of("java", "kt");

  private final Supplier<ImmutableCollection<TargetBuildInfo>> builtTargetsSupplier;
  private final CachedArtifactProvider cachedArtifactProvider;
  private final ProjectDefinition projectDefinition;
  private final PackageStatementParser packageReader;
  private final TestSourceGlobMatcher testSourceMatcher;

  public AddProjectGenSrcs(
    Supplier<ImmutableCollection<TargetBuildInfo>> builtTargetsSupplier,
    ProjectDefinition projectDefinition,
    CachedArtifactProvider cachedArtifactProvider,
    PackageStatementParser packageReader) {
    this.builtTargetsSupplier = builtTargetsSupplier;
    this.projectDefinition = projectDefinition;
    this.cachedArtifactProvider = cachedArtifactProvider;
    this.packageReader = packageReader;
    testSourceMatcher = TestSourceGlobMatcher.create(projectDefinition);
  }

  /**
   * A simple holder class for a build artifact and info about the build that produced it. This is
   * used to resolve conflicts between source files.
   */
  @AutoValue
  abstract static class ArtifactWithOrigin {
    abstract BuildArtifact artifact();

    abstract DependencyBuildContext origin();

    static ArtifactWithOrigin create(BuildArtifact artifact, DependencyBuildContext origin) {
      return new AutoValue_AddProjectGenSrcs_ArtifactWithOrigin(artifact, origin);
    }

    /**
     * When we find conflicting generated sources (same java source path), we resolve the conflict
     * by selecting the per this method.
     *
     * <p>If the files were produced by different build invocations, select the most recent.
     * Otherwise, disambiguate using the target string.
     */
    int compareTo(ArtifactWithOrigin other) {
      // Note: we do a reverse comparison for start time to ensure the newest build "wins".
      int compare = other.origin().startTime().compareTo(origin().startTime());
      if (compare == 0) {
        compare = artifact().target().toString().compareTo(other.artifact().target().toString());
      }
      return compare;
    }
  }

  @Override
  public void update(ProjectProtoUpdate update) throws BuildException {
    ArtifactDirectoryBuilder javaSrc = update.artifactDirectory(ArtifactDirectories.JAVA_GEN_SRC);
    ArtifactDirectoryBuilder javatestsSrc =
      update.artifactDirectory(ArtifactDirectories.JAVA_GEN_TESTSRC);
    ArrayListMultimap<Path, ArtifactWithOrigin> srcsByJavaPath = ArrayListMultimap.create();
    for (TargetBuildInfo target : builtTargetsSupplier.get()) {
      if (target.javaInfo().isEmpty()) {
        continue;
      }
      JavaArtifactInfo javaInfo = target.javaInfo().get();
      if (!projectDefinition.isIncluded(javaInfo.label())) {
        continue;
      }
      for (BuildArtifact genSrc : javaInfo.genSrcs()) {
        if (JAVA_SRC_EXTENSIONS.contains(genSrc.getExtension())) {
          String javaPackage = readJavaPackage(genSrc, testSourceMatcher.matches(genSrc.target().getPackage())
                                                       ? ArtifactDirectories.JAVA_GEN_TESTSRC
                                                       : ArtifactDirectories.JAVA_GEN_SRC);
          Path finalDest =
            Path.of(javaPackage.replace('.', '/')).resolve(genSrc.artifactPath().getFileName());
          srcsByJavaPath.put(finalDest, ArtifactWithOrigin.create(genSrc, target.buildContext()));
        }
      }
    }
    for (var entry : srcsByJavaPath.asMap().entrySet()) {
      Path finalDest = entry.getKey();
      Collection<ArtifactWithOrigin> candidates = entry.getValue();
      // before warning, check that the conflicting sources do actually differ. If they're the
      // same artifact underneath, there's no actual conflict.
      long uniqueDigests =
        candidates.stream()
          .map(ArtifactWithOrigin::artifact)
          .map(BuildArtifact::digest)
          .distinct()
          .count();
      if (uniqueDigests > 1) {
        update
          .context()
          .output(
            PrintOutput.error(
              "WARNING: your project contains conflicting generated java sources for:\n"
              + "  %s\n"
              + "From:\n"
              + "  %s",
              finalDest,
              candidates.stream()
                .map(
                  a ->
                    String.format(
                      "%s (%s built %s ago)",
                      a.artifact().artifactPath(),
                      a.artifact().target(),
                      formatDuration(
                        Duration.between(a.origin().startTime(), Instant.now()))))
                .collect(joining("\n  "))));
        update.context().setHasWarnings();
      }

      ArtifactWithOrigin chosen = candidates.stream().min((a, b) -> a.compareTo(b)).orElseThrow();
      if (testSourceMatcher.matches(chosen.artifact().target().getPackage())) {
        javatestsSrc.addIfNewer(finalDest, chosen.artifact(), chosen.origin());
      }
      else {
        javaSrc.addIfNewer(finalDest, chosen.artifact(), chosen.origin());
      }
    }
    for (ArtifactDirectoryBuilder gensrcDir : ImmutableList.of(javaSrc, javatestsSrc)) {
      if (!gensrcDir.isEmpty()) {
        ProjectProto.ProjectPath pathProto = gensrcDir.root().toProto();
        ProjectProto.ContentEntry.Builder genSourcesContentEntry =
          ProjectProto.ContentEntry.newBuilder().setRoot(pathProto);
        genSourcesContentEntry.addSources(
          ProjectProto.SourceFolder.newBuilder()
            .setProjectPath(pathProto)
            .setIsGenerated(true)
            .setIsTest(gensrcDir == javatestsSrc)
            .setPackagePrefix(""));
        update.workspaceModule().addContentEntries(genSourcesContentEntry.build());
      }
    }
  }

  /**
   * A simple inexact duration format, returning a duration in whichever unit of (days, hours,
   * minutes, seconds) is the first to get a non-zero figure.
   */
  private String formatDuration(Duration p) {
    for (ChronoUnit unit :
      ImmutableList.of(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES)) {
      long durationInUnits = p.getSeconds() / unit.getDuration().getSeconds();
      if (durationInUnits > 0) {
        return String.format("%d %s", durationInUnits, unit);
      }
    }
    return String.format("%d seconds", p.getSeconds());
  }

  /**
   * Parses the java package statement from a build artifact.
   */
  private String readJavaPackage(BuildArtifact genSrc, ProjectPath artifactDirectory) throws BuildException {
    try (InputStream javaSrcStream = cachedArtifactProvider.apply(genSrc, artifactDirectory).byteSource().openStream()) {
      return packageReader.readPackage(javaSrcStream);
    }
    catch (IOException e) {
      throw new BuildException("Failed to read package name for " + genSrc, e);
    }
  }
}
