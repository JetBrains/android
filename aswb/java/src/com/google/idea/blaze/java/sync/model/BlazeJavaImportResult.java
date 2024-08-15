/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync.model;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.model.ProjectData;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.java.sync.importer.emptylibrary.EmptyJarTracker;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/** The result of a blaze import operation. */
public final class BlazeJavaImportResult
    implements ProtoWrapper<ProjectData.BlazeJavaImportResult> {
  public final ImmutableList<BlazeContentEntry> contentEntries;
  public final ImmutableMap<LibraryKey, BlazeJarLibrary> libraries;
  public final ImmutableList<ArtifactLocation> buildOutputJars;
  public final ImmutableSet<ArtifactLocation> javaSourceFiles;
  public final ImmutableSet<ArtifactLocation> pluginProcessorJars;
  @Nullable public final String sourceVersion;
  public final EmptyJarTracker emptyJarTracker;

  private BlazeJavaImportResult(
      ImmutableList<BlazeContentEntry> contentEntries,
      ImmutableMap<LibraryKey, BlazeJarLibrary> libraries,
      ImmutableList<ArtifactLocation> buildOutputJars,
      ImmutableSet<ArtifactLocation> javaSourceFiles,
      @Nullable String sourceVersion,
      EmptyJarTracker emptyJarTracker,
      ImmutableSet<ArtifactLocation> pluginProcessorJars) {
    this.contentEntries = contentEntries;
    this.libraries = libraries;
    this.buildOutputJars = buildOutputJars;
    this.javaSourceFiles = javaSourceFiles;
    this.sourceVersion = sourceVersion;
    this.emptyJarTracker = emptyJarTracker;
    this.pluginProcessorJars = pluginProcessorJars;
  }

  public static BlazeJavaImportResult fromProto(ProjectData.BlazeJavaImportResult proto) {
    return builder()
        .setContentEntries(
            ProtoWrapper.map(proto.getContentEntriesList(), BlazeContentEntry::fromProto))
        .setLibraries(
            ProtoWrapper.map(
                proto.getLibrariesMap(), LibraryKey::fromProto, BlazeJarLibrary::fromProto))
        .setBuildOutputJars(
            ProtoWrapper.map(proto.getBuildOutputJarsList(), ArtifactLocation::fromProto))
        .setJavaSourceFiles(
            ProtoWrapper.map(
                proto.getJavaSourceFilesList(),
                ArtifactLocation::fromProto,
                ImmutableSet.toImmutableSet()))
        .setSourceVersion(Strings.emptyToNull(proto.getSourceVersion()))
        .setEmptyJarTracker(EmptyJarTracker.fromProto(proto.getEmptyJarTracker()))
        .setPluginProcessorJars(
            ProtoWrapper.map(
                proto.getPluginProcessorJarArtifactsList(),
                ArtifactLocation::fromProto,
                ImmutableSet.toImmutableSet()))
        .build();
  }

  @Override
  public ProjectData.BlazeJavaImportResult toProto() {
    ProjectData.BlazeJavaImportResult.Builder builder =
        ProjectData.BlazeJavaImportResult.newBuilder()
            .addAllContentEntries(ProtoWrapper.mapToProtos(contentEntries))
            .putAllLibraries(ProtoWrapper.mapToProtos(libraries))
            .addAllBuildOutputJars(ProtoWrapper.mapToProtos(buildOutputJars))
            .addAllJavaSourceFiles(ProtoWrapper.mapToProtos(javaSourceFiles))
            .setEmptyJarTracker(emptyJarTracker.toProto())
            .addAllPluginProcessorJarArtifacts(ProtoWrapper.mapToProtos(pluginProcessorJars));
    ProtoWrapper.setIfNotNull(builder::setSourceVersion, sourceVersion);
    return builder.build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlazeJavaImportResult that = (BlazeJavaImportResult) o;
    return Objects.equals(contentEntries, that.contentEntries)
        && Objects.equals(libraries, that.libraries)
        && Objects.equals(buildOutputJars, that.buildOutputJars)
        && Objects.equals(javaSourceFiles, that.javaSourceFiles)
        && Objects.equals(sourceVersion, that.sourceVersion)
        && Objects.equals(emptyJarTracker, that.emptyJarTracker);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        contentEntries,
        libraries,
        buildOutputJars,
        javaSourceFiles,
        sourceVersion,
        emptyJarTracker);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link BlazeJavaImportResult} */
  public static class Builder {
    private ImmutableList<BlazeContentEntry> contentEntries;
    private ImmutableMap<LibraryKey, BlazeJarLibrary> libraries;
    private ImmutableList<ArtifactLocation> buildOutputJars;
    private ImmutableSet<ArtifactLocation> javaSourceFiles;
    @Nullable private String sourceVersion = null;
    private EmptyJarTracker emptyJarTracker;
    private ImmutableSet<ArtifactLocation> pluginProcessorJars;

    @CanIgnoreReturnValue
    public Builder setContentEntries(List<BlazeContentEntry> contentEntries) {
      this.contentEntries = ImmutableList.copyOf(contentEntries);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setLibraries(Map<LibraryKey, BlazeJarLibrary> libraries) {
      this.libraries = ImmutableMap.copyOf(libraries);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setBuildOutputJars(List<ArtifactLocation> buildOutputJars) {
      this.buildOutputJars = ImmutableList.copyOf(buildOutputJars);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setJavaSourceFiles(Set<ArtifactLocation> javaSourceFiles) {
      this.javaSourceFiles = ImmutableSet.copyOf(javaSourceFiles);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setSourceVersion(@Nullable String sourceVersion) {
      this.sourceVersion = sourceVersion;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setEmptyJarTracker(EmptyJarTracker emptyJarTracker) {
      this.emptyJarTracker = emptyJarTracker;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPluginProcessorJars(Set<ArtifactLocation> pluginProcessorJars) {
      this.pluginProcessorJars = ImmutableSet.copyOf(pluginProcessorJars);
      return this;
    }

    public BlazeJavaImportResult build() {
      return new BlazeJavaImportResult(
          checkNotNull(contentEntries, "contentEntries not set"),
          checkNotNull(libraries, "libraries not set"),
          checkNotNull(buildOutputJars, "buildOutputJars not set"),
          checkNotNull(javaSourceFiles, "javaSourceFiles not set"),
          sourceVersion,
          checkNotNull(emptyJarTracker, "emptyJarTracker not set"),
          checkNotNull(pluginProcessorJars, "lintJars not set"));
    }
  }
}
