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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Objects;
import javax.annotation.Nullable;

/** Ide info specific to java rules. */
public final class JavaIdeInfo implements ProtoWrapper<IntellijIdeInfo.JavaIdeInfo> {
  private final ImmutableList<LibraryArtifact> jars;
  private final ImmutableList<LibraryArtifact> generatedJars;
  @Nullable private final LibraryArtifact filteredGenJar;
  private final ImmutableList<ArtifactLocation> sources;
  @Nullable private final ArtifactLocation packageManifest;
  @Nullable private final ArtifactLocation jdepsFile;
  @Nullable private final String javaBinaryMainClass;
  @Nullable private final String testClass;
  private final ImmutableList<LibraryArtifact> pluginProcessorJars;

  private JavaIdeInfo(
      ImmutableList<LibraryArtifact> jars,
      ImmutableList<LibraryArtifact> generatedJars,
      @Nullable LibraryArtifact filteredGenJar,
      ImmutableList<ArtifactLocation> sources,
      @Nullable ArtifactLocation packageManifest,
      @Nullable ArtifactLocation jdepsFile,
      @Nullable String javaBinaryMainClass,
      @Nullable String testClass,
      ImmutableList<LibraryArtifact> pluginProcessorJars) {
    this.jars = jars;
    this.generatedJars = generatedJars;
    this.sources = sources;
    this.packageManifest = packageManifest;
    this.jdepsFile = jdepsFile;
    this.filteredGenJar = filteredGenJar;
    this.javaBinaryMainClass = javaBinaryMainClass;
    this.testClass = testClass;
    this.pluginProcessorJars = pluginProcessorJars;
  }

  static JavaIdeInfo fromProto(IntellijIdeInfo.JavaIdeInfo proto) {
    return new JavaIdeInfo(
        ProtoWrapper.map(proto.getJarsList(), LibraryArtifact::fromProto),
        ProtoWrapper.map(proto.getGeneratedJarsList(), LibraryArtifact::fromProto),
        proto.hasFilteredGenJar() ? LibraryArtifact.fromProto(proto.getFilteredGenJar()) : null,
        ProtoWrapper.map(proto.getSourcesList(), ArtifactLocation::fromProto),
        proto.hasPackageManifest() ? ArtifactLocation.fromProto(proto.getPackageManifest()) : null,
        proto.hasJdeps() ? ArtifactLocation.fromProto(proto.getJdeps()) : null,
        Strings.emptyToNull(proto.getMainClass()),
        Strings.emptyToNull(proto.getTestClass()),
        ProtoWrapper.map(proto.getPluginProcessorJarsList(), LibraryArtifact::fromProto));
  }

  @Override
  public IntellijIdeInfo.JavaIdeInfo toProto() {
    IntellijIdeInfo.JavaIdeInfo.Builder builder =
        IntellijIdeInfo.JavaIdeInfo.newBuilder()
            .addAllJars(ProtoWrapper.mapToProtos(jars))
            .addAllGeneratedJars(ProtoWrapper.mapToProtos(generatedJars))
            .addAllSources(ProtoWrapper.mapToProtos(sources))
            .addAllPluginProcessorJars(ProtoWrapper.mapToProtos(pluginProcessorJars));
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setFilteredGenJar, filteredGenJar);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setPackageManifest, packageManifest);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setJdeps, jdepsFile);
    ProtoWrapper.setIfNotNull(builder::setMainClass, javaBinaryMainClass);
    ProtoWrapper.setIfNotNull(builder::setTestClass, testClass);
    return builder.build();
  }

  public ImmutableList<ArtifactLocation> getSources() {
    return sources;
  }

  /**
   * The main jar(s) produced by this java rule.
   *
   * <p>Usually this will be a single jar, but java_imports support importing multiple jars.
   */
  public ImmutableList<LibraryArtifact> getJars() {
    return jars;
  }

  /** A jar containing annotation processing. */
  public ImmutableList<LibraryArtifact> getGeneratedJars() {
    return generatedJars;
  }

  /**
   * A jar containing code from *only* generated sources, iff the rule contains both generated and
   * non-generated sources.
   */
  @Nullable
  public LibraryArtifact getFilteredGenJar() {
    return filteredGenJar;
  }

  /** File containing a map from .java files to their corresponding package. */
  @Nullable
  public ArtifactLocation getPackageManifest() {
    return packageManifest;
  }

  /** File containing dependencies. */
  @Nullable
  public ArtifactLocation getJdepsFile() {
    return jdepsFile;
  }

  /** main_class attribute value for java_binary targets */
  @Nullable
  public String getJavaBinaryMainClass() {
    return javaBinaryMainClass;
  }

  /** Jars needed to apply the encapsulated annotation processors. */
  public ImmutableList<LibraryArtifact> getPluginProcessorJars() {
    return pluginProcessorJars;
  }

  /** test_class attribute value for java_test targets */
  @Nullable
  public String getTestClass() {
    return testClass;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for java info */
  public static class Builder {
    ImmutableList.Builder<LibraryArtifact> jars = ImmutableList.builder();
    ImmutableList.Builder<LibraryArtifact> generatedJars = ImmutableList.builder();
    @Nullable LibraryArtifact filteredGenJar;
    @Nullable String mainClass;
    @Nullable String testClass;
    @Nullable ArtifactLocation jdepsFile;
    ImmutableList.Builder<LibraryArtifact> pluginProcessorJars = ImmutableList.builder();

    @CanIgnoreReturnValue
    public Builder addJar(LibraryArtifact.Builder jar) {
      jars.add(jar.build());
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addGeneratedJar(LibraryArtifact.Builder jar) {
      generatedJars.add(jar.build());
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setFilteredGenJar(LibraryArtifact.Builder jar) {
      this.filteredGenJar = jar.build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setMainClass(@Nullable String mainClass) {
      this.mainClass = mainClass;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTestClass(@Nullable String testClass) {
      this.testClass = testClass;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setJdepsFile(@Nullable ArtifactLocation jdepsFile) {
      this.jdepsFile = jdepsFile;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addPluginProcessorJars(LibraryArtifact.Builder jar) {
      pluginProcessorJars.add(jar.build());
      return this;
    }

    public JavaIdeInfo build() {
      return new JavaIdeInfo(
          jars.build(),
          generatedJars.build(),
          filteredGenJar,
          ImmutableList.of(),
          null,
          jdepsFile,
          mainClass,
          testClass,
          pluginProcessorJars.build());
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    JavaIdeInfo that = (JavaIdeInfo) o;
    return Objects.equals(jars, that.jars)
        && Objects.equals(generatedJars, that.generatedJars)
        && Objects.equals(filteredGenJar, that.filteredGenJar)
        && Objects.equals(sources, that.sources)
        && Objects.equals(packageManifest, that.packageManifest)
        && Objects.equals(jdepsFile, that.jdepsFile)
        && Objects.equals(javaBinaryMainClass, that.javaBinaryMainClass)
        && Objects.equals(testClass, that.testClass)
        && Objects.equals(pluginProcessorJars, that.pluginProcessorJars);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        jars,
        generatedJars,
        filteredGenJar,
        sources,
        packageManifest,
        jdepsFile,
        javaBinaryMainClass,
        testClass,
        pluginProcessorJars);
  }
}
