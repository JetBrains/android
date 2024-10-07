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
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Lists;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata.Extractor;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.deps.ArtifactDirectoryBuilder;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.DependencyBuildContext;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.JavaSourcePackage;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.TestSourceGlobMatcher;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adds generated java and kotlin source files to the project proto.
 *
 * <p>This class also resolves conflicts between multiple generated source files that resolve to
 * the same output path, i.e. that have the same java class name.
 */
public class AddProjectGenSrcs implements ProjectProtoUpdateOperation {

  private static final ImmutableSet<String> JAVA_SRC_EXTENSIONS = ImmutableSet.of("java", "kt");

  private final ProjectDefinition projectDefinition;
  private final Extractor<JavaSourcePackage> packageReader;
  private final TestSourceGlobMatcher testSourceMatcher;

  public AddProjectGenSrcs(
      ProjectDefinition projectDefinition, Extractor<JavaSourcePackage> packageReader) {
    this.projectDefinition = projectDefinition;
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

  private ImmutableList<BuildArtifact> getSourceFileArtifacts(TargetBuildInfo target) {
    if (target.javaInfo().isEmpty()) {
      return ImmutableList.of();
    }
    JavaArtifactInfo javaInfo = target.javaInfo().get();
    if (!projectDefinition.isIncluded(javaInfo.label())) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<BuildArtifact> srcs = ImmutableList.builder();
    for (BuildArtifact genSrc : javaInfo.genSrcs()) {
      if (JAVA_SRC_EXTENSIONS.contains(genSrc.getExtension())) {
        srcs.add(genSrc);
      }
    }
    return srcs.build();
  }

  @Override
  public ImmutableSetMultimap<BuildArtifact, Extractor<?>> getRequiredArtifacts(
      TargetBuildInfo forTarget) {
    ImmutableSetMultimap.Builder<BuildArtifact, Extractor<?>> required =
        ImmutableSetMultimap.builder();
    getSourceFileArtifacts(forTarget).forEach(p -> required.put(p, packageReader));
    return required.build();
  }

  @Override
  public void update(ProjectProtoUpdate update, ArtifactTracker.State artifactState)
      throws BuildException {
    ArtifactDirectoryBuilder javaSrc = update.artifactDirectory(ArtifactDirectories.JAVA_GEN_SRC);
    ArtifactDirectoryBuilder javatestsSrc =
        update.artifactDirectory(ArtifactDirectories.JAVA_GEN_TESTSRC);
    ArrayListMultimap<Path, ArtifactWithOrigin> srcsByJavaPath = ArrayListMultimap.create();
    List<BuildArtifact> missingPackageArtifacts = Lists.newArrayList();
    for (TargetBuildInfo target : artifactState.depsMap().values()) {
      for (BuildArtifact genSrc : getSourceFileArtifacts(target)) {

        String javaPackage =
            genSrc.getMetadata(JavaSourcePackage.class).map(JavaSourcePackage::name).orElse(null);
        if (javaPackage == null) {
          missingPackageArtifacts.add(genSrc);
        } else {
          Path finalDest =
              Path.of(javaPackage.replace('.', '/')).resolve(genSrc.artifactPath().getFileName());
          srcsByJavaPath.put(finalDest, ArtifactWithOrigin.create(genSrc, target.buildContext()));
        }
      }
    }
    if (!missingPackageArtifacts.isEmpty()) {
      final int showSourcesLimit = 10;
      update.context().output(PrintOutput.error(
          "WARNING: Ignoring %d generated source file(s) due to missing package info:\n  %s",
          missingPackageArtifacts.size(),
          missingPackageArtifacts.stream().limit(showSourcesLimit).map(BuildArtifact::artifactPath)
              .map(Path::toString)
              .collect(Collectors.joining("\n  "))));
      if (missingPackageArtifacts.size() > showSourcesLimit) {
        update.context().output(
            PrintOutput.error("  (and %d more)",
                missingPackageArtifacts.size() - showSourcesLimit));
      }
      update.context().setHasWarnings();
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
      } else {
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
}
