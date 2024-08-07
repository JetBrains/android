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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/** Ide info specific to android rules. */
public final class AndroidIdeInfo implements ProtoWrapper<IntellijIdeInfo.AndroidIdeInfo> {
  private final ImmutableList<AndroidResFolder> resources;
  @Nullable private final ArtifactLocation manifest;
  /**
   * Maps overridable Android manifest attributes or arbitrary placeholder strings to the values
   * that Bazel substitutes for them during merged manifest computation. {@see <a
   * href="https://docs.bazel.build/versions/master/be/android.html#android_binary.manifest_values">manifest_values</a>}
   */
  private final ImmutableMap<String, String> manifestValues;

  @Nullable private final LibraryArtifact idlJar;
  @Nullable private final LibraryArtifact resourceJar;
  private final boolean hasIdlSources;
  @Nullable private final String resourceJavaPackage;
  private final boolean generateResourceClass;
  @Nullable private final Label legacyResources;
  @Nullable private final Label instruments;
  @Nullable private final ArtifactLocation renderResolveJar;

  private AndroidIdeInfo(
      List<AndroidResFolder> resources,
      @Nullable String resourceJavaPackage,
      boolean generateResourceClass,
      @Nullable ArtifactLocation manifest,
      ImmutableMap<String, String> manifestValues,
      @Nullable LibraryArtifact idlJar,
      @Nullable LibraryArtifact resourceJar,
      boolean hasIdlSources,
      @Nullable Label legacyResources,
      @Nullable Label instruments,
      @Nullable ArtifactLocation renderResolveJar) {
    this.resources = ImmutableList.copyOf(resources);
    this.resourceJavaPackage = resourceJavaPackage;
    this.generateResourceClass = generateResourceClass;
    this.manifest = manifest;
    this.manifestValues = manifestValues;
    this.idlJar = idlJar;
    this.resourceJar = resourceJar;
    this.hasIdlSources = hasIdlSources;
    this.legacyResources = legacyResources;
    this.instruments = instruments;
    this.renderResolveJar = renderResolveJar;
  }

  static AndroidIdeInfo fromProto(IntellijIdeInfo.AndroidIdeInfo proto) {
    return new AndroidIdeInfo(
        !proto.getResFoldersList().isEmpty()
            ? ProtoWrapper.map(proto.getResFoldersList(), AndroidResFolder::fromProto)
            : ProtoWrapper.map(proto.getResourcesList(), AndroidResFolder::fromProto),
        Strings.emptyToNull(proto.getJavaPackage()),
        proto.getGenerateResourceClass(),
        proto.hasManifest() ? ArtifactLocation.fromProto(proto.getManifest()) : null,
        ImmutableMap.copyOf(proto.getManifestValuesMap()),
        proto.hasIdlJar() ? LibraryArtifact.fromProto(proto.getIdlJar()) : null,
        proto.hasResourceJar() ? LibraryArtifact.fromProto(proto.getResourceJar()) : null,
        proto.getHasIdlSources(),
        !Strings.isNullOrEmpty(proto.getLegacyResources())
            ? Label.create(proto.getLegacyResources())
            : null,
        !Strings.isNullOrEmpty(proto.getInstruments())
            ? Label.create(proto.getInstruments())
            : null,
        proto.hasRenderResolveJar()
            ? ArtifactLocation.fromProto(proto.getRenderResolveJar())
            : null);
  }

  @Override
  public IntellijIdeInfo.AndroidIdeInfo toProto() {
    IntellijIdeInfo.AndroidIdeInfo.Builder builder =
        IntellijIdeInfo.AndroidIdeInfo.newBuilder()
            .putAllManifestValues(manifestValues)
            .addAllResFolders(ProtoWrapper.mapToProtos(resources))
            .setGenerateResourceClass(generateResourceClass)
            .setHasIdlSources(hasIdlSources);
    ProtoWrapper.setIfNotNull(builder::setJavaPackage, resourceJavaPackage);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setManifest, manifest);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setIdlJar, idlJar);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setResourceJar, resourceJar);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setLegacyResources, legacyResources);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setInstruments, instruments);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setRenderResolveJar, renderResolveJar);
    return builder.build();
  }

  public ImmutableList<AndroidResFolder> getResources() {
    return resources;
  }

  @Nullable
  public ArtifactLocation getManifest() {
    return manifest;
  }

  public Map<String, String> getManifestValues() {
    return manifestValues;
  }

  @Nullable
  public LibraryArtifact getIdlJar() {
    return idlJar;
  }

  @Nullable
  public LibraryArtifact getResourceJar() {
    return resourceJar;
  }

  public boolean hasIdlSources() {
    return hasIdlSources;
  }

  @Nullable
  public String getResourceJavaPackage() {
    return resourceJavaPackage;
  }

  public boolean generateResourceClass() {
    return generateResourceClass;
  }

  @Nullable
  public Label getLegacyResources() {
    return legacyResources;
  }

  @Nullable
  public Label getInstruments() {
    return instruments;
  }

  @Nullable
  public ArtifactLocation getRenderResolveJar() {
    return renderResolveJar;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for android rule */
  public static class Builder {
    private List<AndroidResFolder> resources = Lists.newArrayList();
    private ArtifactLocation manifest;
    private final ImmutableMap.Builder<String, String> manifestValues = ImmutableMap.builder();
    private LibraryArtifact idlJar;
    private LibraryArtifact resourceJar;
    private boolean hasIdlSources;
    private String resourceJavaPackage;
    private boolean generateResourceClass;
    private Label legacyResources;
    private Label instruments;
    private ArtifactLocation renderResolveJar;

    @CanIgnoreReturnValue
    public Builder setManifestFile(ArtifactLocation artifactLocation) {
      this.manifest = artifactLocation;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder putManifestValue(String attributeOrPlaceholder, String value) {
      manifestValues.put(attributeOrPlaceholder, value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addResource(ArtifactLocation artifactLocation) {
      return addResource(AndroidResFolder.builder().setRoot(artifactLocation).build());
    }

    @CanIgnoreReturnValue
    public Builder addResource(AndroidResFolder androidResFolder) {
      this.resources.add(androidResFolder);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setIdlJar(LibraryArtifact idlJar) {
      this.idlJar = idlJar;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setHasIdlSources(boolean hasIdlSources) {
      this.hasIdlSources = hasIdlSources;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setResourceJar(LibraryArtifact.Builder resourceJar) {
      this.resourceJar = resourceJar.build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setResourceJavaPackage(@Nullable String resourceJavaPackage) {
      this.resourceJavaPackage = resourceJavaPackage;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setGenerateResourceClass(boolean generateResourceClass) {
      this.generateResourceClass = generateResourceClass;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setLegacyResources(@Nullable Label legacyResources) {
      this.legacyResources = legacyResources;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setInstruments(@Nullable Label instruments) {
      this.instruments = instruments;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setRenderResolveJar(@Nullable ArtifactLocation renderResolveJar) {
      this.renderResolveJar = renderResolveJar;
      return this;
    }

    public AndroidIdeInfo build() {
      if (!resources.isEmpty() || manifest != null) {
        if (!generateResourceClass) {
          throw new IllegalStateException(
              "Must set generateResourceClass if manifest or resources set");
        }
      }

      return new AndroidIdeInfo(
          resources,
          resourceJavaPackage,
          generateResourceClass,
          manifest,
          manifestValues.build(),
          idlJar,
          resourceJar,
          hasIdlSources,
          legacyResources,
          instruments,
          renderResolveJar);
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
    AndroidIdeInfo that = (AndroidIdeInfo) o;
    return hasIdlSources == that.hasIdlSources
        && generateResourceClass == that.generateResourceClass
        && Objects.equals(resources, that.resources)
        && Objects.equals(manifest, that.manifest)
        && Objects.equals(manifestValues, that.manifestValues)
        && Objects.equals(idlJar, that.idlJar)
        && Objects.equals(resourceJar, that.resourceJar)
        && Objects.equals(resourceJavaPackage, that.resourceJavaPackage)
        && Objects.equals(legacyResources, that.legacyResources)
        && Objects.equals(instruments, that.instruments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        resources,
        manifest,
        manifestValues,
        idlJar,
        resourceJar,
        hasIdlSources,
        resourceJavaPackage,
        generateResourceClass,
        legacyResources,
        instruments);
  }
}
