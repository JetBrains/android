/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import java.util.Objects;

/** android_sdk ide info */
public final class AndroidSdkIdeInfo implements ProtoWrapper<IntellijIdeInfo.AndroidSdkIdeInfo> {
  private final ArtifactLocation androidJar;

  private AndroidSdkIdeInfo(ArtifactLocation androidJar) {
    this.androidJar = androidJar;
  }

  static AndroidSdkIdeInfo fromProto(IntellijIdeInfo.AndroidSdkIdeInfo proto) {
    return new AndroidSdkIdeInfo(ArtifactLocation.fromProto(proto.getAndroidJar()));
  }

  @Override
  public IntellijIdeInfo.AndroidSdkIdeInfo toProto() {
    return IntellijIdeInfo.AndroidSdkIdeInfo.newBuilder()
        .setAndroidJar(androidJar.toProto())
        .build();
  }

  public ArtifactLocation getAndroidJar() {
    return androidJar;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AndroidSdkIdeInfo that = (AndroidSdkIdeInfo) o;
    return Objects.equals(androidJar, that.androidJar);
  }

  @Override
  public int hashCode() {
    return Objects.hash(androidJar);
  }
}
