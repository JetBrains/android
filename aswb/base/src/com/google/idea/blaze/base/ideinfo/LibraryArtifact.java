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
package com.google.idea.blaze.base.ideinfo;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.common.experiments.BoolExperiment;
import javax.annotation.Nullable;

/** Represents a jar artifact. */
public final class LibraryArtifact implements ProtoWrapper<IntellijIdeInfo.LibraryArtifact> {

  private static final BoolExperiment indexClassJarIfNoSrcJars =
      new BoolExperiment("blaze.use.class.jar", true);

  @Nullable private final ArtifactLocation interfaceJar;
  @Nullable private final ArtifactLocation classJar;
  private final ImmutableList<ArtifactLocation> sourceJars;

  public LibraryArtifact(
      @Nullable ArtifactLocation interfaceJar,
      @Nullable ArtifactLocation classJar,
      ImmutableList<ArtifactLocation> sourceJars) {
    if (interfaceJar == null && classJar == null) {
      throw new IllegalArgumentException("Interface and class jars cannot both be null.");
    }
    this.interfaceJar = interfaceJar;
    this.classJar = classJar;
    this.sourceJars = checkNotNull(sourceJars);
  }

  public static LibraryArtifact fromProto(IntellijIdeInfo.LibraryArtifact proto) {
    return ProjectDataInterner.intern(
        new LibraryArtifact(
            proto.hasInterfaceJar() ? ArtifactLocation.fromProto(proto.getInterfaceJar()) : null,
            proto.hasJar() ? ArtifactLocation.fromProto(proto.getJar()) : null,
            !proto.getSourceJarsList().isEmpty()
                ? ProtoWrapper.map(proto.getSourceJarsList(), ArtifactLocation::fromProto)
                : proto.hasSourceJar()
                    ? ImmutableList.of(ArtifactLocation.fromProto(proto.getSourceJar()))
                    : ImmutableList.of()));
  }

  @Override
  public IntellijIdeInfo.LibraryArtifact toProto() {
    IntellijIdeInfo.LibraryArtifact.Builder builder =
        IntellijIdeInfo.LibraryArtifact.newBuilder()
            .addAllSourceJars(ProtoWrapper.mapToProtos(sourceJars));
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setInterfaceJar, interfaceJar);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setJar, classJar);
    return builder.build();
  }

  @Nullable
  public ArtifactLocation getInterfaceJar() {
    return interfaceJar;
  }

  @Nullable
  public ArtifactLocation getClassJar() {
    return classJar;
  }

  public ImmutableList<ArtifactLocation> getSourceJars() {
    return sourceJars;
  }

  /**
   * Returns the best jar to add to IntelliJ.
   *
   * <p>We use the class jar if there are no source jars, otherwise the interface jar.
   */
  public ArtifactLocation jarForIntellijLibrary() {
    if (sourceJars.isEmpty() && indexClassJarIfNoSrcJars.getValue()) {
      return classJar != null ? classJar : interfaceJar;
    }
    return interfaceJar != null ? interfaceJar : classJar;
  }

  @Override
  public String toString() {
    return String.format(
        "jar=%s, ijar=%s, srcjars=%s", getClassJar(), getInterfaceJar(), getSourceJars());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LibraryArtifact that = (LibraryArtifact) o;
    return Objects.equal(getInterfaceJar(), that.getInterfaceJar())
        && Objects.equal(getClassJar(), that.getClassJar())
        && Objects.equal(getSourceJars(), that.getSourceJars());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getInterfaceJar(), getClassJar(), getSourceJars());
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for library artifacts */
  public static class Builder {
    private ArtifactLocation interfaceJar;
    private ArtifactLocation classJar;
    private final ImmutableList.Builder<ArtifactLocation> sourceJars = ImmutableList.builder();

    @CanIgnoreReturnValue
    public Builder setInterfaceJar(ArtifactLocation artifactLocation) {
      this.interfaceJar = artifactLocation;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setClassJar(@Nullable ArtifactLocation artifactLocation) {
      this.classJar = artifactLocation;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addSourceJar(ArtifactLocation... artifactLocations) {
      this.sourceJars.add(artifactLocations);
      return this;
    }

    public LibraryArtifact build() {
      return new LibraryArtifact(interfaceJar, classJar, sourceJars.build());
    }
  }
}
