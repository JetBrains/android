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
package com.google.idea.blaze.android.sync.model;

import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import javax.annotation.concurrent.Immutable;

/** Information about the android platform selected at sync time. */
@Immutable
public class AndroidSdkPlatform implements ProtoWrapper<ProjectData.AndroidSdkPlatform> {
  public final String androidSdk;
  public final int androidMinSdkLevel;

  public AndroidSdkPlatform(String androidSdk, int androidMinSdkLevel) {
    this.androidSdk = androidSdk;
    this.androidMinSdkLevel = androidMinSdkLevel;
  }

  static AndroidSdkPlatform fromProto(ProjectData.AndroidSdkPlatform proto) {
    return new AndroidSdkPlatform(proto.getAndroidSdk(), proto.getAndroidMinSdkLevel());
  }

  @Override
  public ProjectData.AndroidSdkPlatform toProto() {
    return ProjectData.AndroidSdkPlatform.newBuilder()
        .setAndroidSdk(androidSdk)
        .setAndroidMinSdkLevel(androidMinSdkLevel)
        .build();
  }
}
