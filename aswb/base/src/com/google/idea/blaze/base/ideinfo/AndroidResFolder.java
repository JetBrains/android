/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import com.google.common.base.Objects;
import com.google.devtools.intellij.aspect.Common;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.ResFolderLocation;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import javax.annotation.Nullable;

/**
 * Information about Android res folders. Contains the root res folder and optionally the specific
 * resources within that folder.
 */
public final class AndroidResFolder implements ProtoWrapper<IntellijIdeInfo.ResFolderLocation> {
  private final ArtifactLocation root;
  @Nullable private final ArtifactLocation aar;

  private AndroidResFolder(ArtifactLocation root, @Nullable ArtifactLocation aar) {
    this.root = root;
    this.aar = aar;
  }

  static AndroidResFolder fromProto(IntellijIdeInfo.ResFolderLocation proto) {
    return ProjectDataInterner.intern(
        new AndroidResFolder(
            ArtifactLocation.fromProto(proto.getRoot()),
            proto.hasAar() ? ArtifactLocation.fromProto(proto.getAar()) : null));
  }

  static AndroidResFolder fromProto(Common.ArtifactLocation root) {
    return ProjectDataInterner.intern(new AndroidResFolder(ArtifactLocation.fromProto(root), null));
  }

  @Override
  public ResFolderLocation toProto() {
    IntellijIdeInfo.ResFolderLocation.Builder resFolderLocationBuilder =
        IntellijIdeInfo.ResFolderLocation.newBuilder().setRoot(root.toProto());
    if (aar != null) {
      resFolderLocationBuilder.setAar(aar.toProto());
    }
    return resFolderLocationBuilder.build();
  }

  public ArtifactLocation getRoot() {
    return root;
  }

  @Nullable
  public ArtifactLocation getAar() {
    return aar;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for an resource artifact location */
  public static class Builder {
    ArtifactLocation root;
    ArtifactLocation aar;

    @CanIgnoreReturnValue
    public AndroidResFolder.Builder setRoot(ArtifactLocation root) {
      this.root = root;
      return this;
    }

    @CanIgnoreReturnValue
    public AndroidResFolder.Builder setAar(ArtifactLocation aar) {
      this.aar = aar;
      return this;
    }

    public AndroidResFolder build() {
      return new AndroidResFolder(root, aar);
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
    AndroidResFolder that = (AndroidResFolder) o;
    return Objects.equal(getRoot(), that.getRoot()) && Objects.equal(getAar(), that.getAar());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getRoot(), getAar());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("AndroidResFolder {");
    sb.append(LINE_SEPARATOR.value()).append(" root = ").append(getRoot());
    sb.append(LINE_SEPARATOR.value()).append(" aar = ").append(getAar());
    sb.append("}");
    return sb.toString();
  }
}
