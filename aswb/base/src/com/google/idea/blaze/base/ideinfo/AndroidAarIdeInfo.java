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

import com.google.common.base.Strings;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import java.util.Objects;
import javax.annotation.Nullable;

/** aar_import ide info */
public final class AndroidAarIdeInfo implements ProtoWrapper<IntellijIdeInfo.AndroidAarIdeInfo> {
  private final ArtifactLocation aar;

  /** Custom resource package, if defined. */
  @Nullable private final String customJavaPackage;

  public AndroidAarIdeInfo(ArtifactLocation aar, @Nullable String customJavaPackage) {
    this.aar = aar;
    this.customJavaPackage = customJavaPackage;
  }

  static AndroidAarIdeInfo fromProto(IntellijIdeInfo.AndroidAarIdeInfo proto) {
    return new AndroidAarIdeInfo(
        ArtifactLocation.fromProto(proto.getAar()), Strings.emptyToNull(proto.getJavaPackage()));
  }

  @Override
  public IntellijIdeInfo.AndroidAarIdeInfo toProto() {
    IntellijIdeInfo.AndroidAarIdeInfo.Builder builder =
        IntellijIdeInfo.AndroidAarIdeInfo.newBuilder().setAar(aar.toProto());
    ProtoWrapper.setIfNotNull(builder::setJavaPackage, customJavaPackage);
    return builder.build();
  }

  public ArtifactLocation getAar() {
    return aar;
  }

  @Nullable
  public String getCustomJavaPackage() {
    return customJavaPackage;
  }

  @Override
  public String toString() {
    return "AndroidAarIdeInfo{" + "\n" + "  aar=" + getAar() + "\n" + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AndroidAarIdeInfo that = (AndroidAarIdeInfo) o;
    return Objects.equals(aar, that.aar)
        && Objects.equals(customJavaPackage, that.customJavaPackage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(aar);
  }
}
