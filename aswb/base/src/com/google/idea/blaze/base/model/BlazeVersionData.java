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
package com.google.idea.blaze.base.model;

import com.google.devtools.intellij.model.ProjectData;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.BuildSystemName;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Version data about the user's blaze/bazel and other info needed for switching behaviour
 * dynamically.
 */
public final class BlazeVersionData implements ProtoWrapper<ProjectData.BlazeVersionData> {
  @Nullable private final Long blazeCl;
  @Nullable public final Long clientCl;
  @Nullable private final BazelVersion bazelVersion;

  private BlazeVersionData(
      @Nullable Long blazeCl, @Nullable Long clientCl, @Nullable BazelVersion bazelVersion) {
    this.blazeCl = blazeCl;
    this.clientCl = clientCl;
    this.bazelVersion = bazelVersion;
  }

  static BlazeVersionData fromProto(ProjectData.BlazeVersionData proto) {
    return new BlazeVersionData(
        proto.getBlazeCl() != 0 ? proto.getBlazeCl() : null,
        proto.getClientCl() != 0 ? proto.getClientCl() : null,
        proto.hasBazelVersion() ? BazelVersion.fromProto(proto.getBazelVersion()) : null);
  }

  @Override
  public ProjectData.BlazeVersionData toProto() {
    ProjectData.BlazeVersionData.Builder builder = ProjectData.BlazeVersionData.newBuilder();
    ProtoWrapper.setIfNotNull(builder::setBlazeCl, blazeCl);
    ProtoWrapper.setIfNotNull(builder::setClientCl, clientCl);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setBazelVersion, bazelVersion);
    return builder.build();
  }

  public boolean blazeVersionIsKnown() {
    return blazeCl != null;
  }

  public boolean blazeContainsCl(long cl) {
    return blazeCl != null && blazeCl >= cl;
  }

  public boolean blazeClientIsKnown() {
    return clientCl != null;
  }

  public boolean blazeClientIsAtLeastCl(long cl) {
    return clientCl != null && clientCl >= cl;
  }

  public boolean bazelIsAtLeastVersion(int major, int minor, int bugfix) {
    return bazelVersion != null && bazelVersion.isAtLeast(major, minor, bugfix);
  }

  public boolean bazelIsAtLeastVersion(BazelVersion version) {
    return bazelVersion != null && bazelVersion.isAtLeast(version);
  }

  public BuildSystemName buildSystem() {
    return bazelVersion != null ? BuildSystemName.Bazel : BuildSystemName.Blaze;
  }

  @Override
  public String toString() {
    if (bazelVersion != null) {
      return bazelVersion.toString();
    }
    return String.format("Blaze CL: %s, Client CL: %s", blazeCl, clientCl);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlazeVersionData that = (BlazeVersionData) o;
    return Objects.equals(blazeCl, that.blazeCl)
        && Objects.equals(clientCl, that.clientCl)
        && Objects.equals(bazelVersion, that.bazelVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(blazeCl, clientCl, bazelVersion);
  }

  public static BlazeVersionData build(
      BuildSystem buildSystem, WorkspaceRoot workspaceRoot, BlazeInfo blazeInfo) {
    // TODO(mathewi) This should probably be refatored into a createBlazeVersionData method in
    //    BuildSystem, or perhaps better, remove the need for it by improving encapsulation of
    //    BuildSystem.
    Builder builder = builder();
    buildSystem.populateBlazeVersionData(workspaceRoot, blazeInfo, builder);
    return builder.build();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder class for constructing the blaze version data */
  public static class Builder {
    @Nullable private Long blazeCl;
    @Nullable private Long clientCl;
    @Nullable private BazelVersion bazelVersion;

    @CanIgnoreReturnValue
    public Builder setBlazeCl(@Nullable Long blazeCl) {
      this.blazeCl = blazeCl;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setClientCl(@Nullable Long clientCl) {
      this.clientCl = clientCl;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setBazelVersion(BazelVersion bazelVersion) {
      this.bazelVersion = bazelVersion;
      return this;
    }

    public BlazeVersionData build() {
      return new BlazeVersionData(blazeCl, clientCl, bazelVersion);
    }
  }
}
