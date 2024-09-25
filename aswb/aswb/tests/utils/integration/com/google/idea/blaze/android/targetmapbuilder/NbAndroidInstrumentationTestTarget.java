/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.targetmapbuilder;

import static com.google.idea.blaze.android.targetmapbuilder.NbTargetMapUtils.makeSourceArtifact;

import com.google.idea.blaze.base.ideinfo.AndroidInstrumentationInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes;

/** Builder for a blaze android instrumentation test target's IDE info. */
public class NbAndroidInstrumentationTestTarget extends NbBaseTargetBuilder {
  private final TargetIdeInfo.Builder targetIdeInfoBuilder;
  private final AndroidInstrumentationInfo.Builder androidInstrumentationInfoBuilder;
  private final WorkspacePath blazePackage;

  public static NbAndroidInstrumentationTestTarget android_instrumentation_test(String label) {
    return new NbAndroidInstrumentationTestTarget(BlazeInfoData.DEFAULT, label);
  }

  public NbAndroidInstrumentationTestTarget test_app(String relativeLabel) {
    androidInstrumentationInfoBuilder.setTestApp(
        NbTargetMapUtils.normalizeRelativePathOrLabel(relativeLabel, blazePackage));
    return this;
  }

  public NbAndroidInstrumentationTestTarget target_device(String relativeLabel) {
    androidInstrumentationInfoBuilder.setTargetDevice(
        NbTargetMapUtils.normalizeRelativePathOrLabel(relativeLabel, blazePackage));
    return this;
  }

  NbAndroidInstrumentationTestTarget(BlazeInfoData blazeInfoData, String label) {
    super(blazeInfoData);
    this.blazePackage = NbTargetMapUtils.blazePackageForLabel(label);
    this.androidInstrumentationInfoBuilder = new AndroidInstrumentationInfo.Builder();
    this.targetIdeInfoBuilder = new TargetIdeInfo.Builder();

    targetIdeInfoBuilder
        .setKind(RuleTypes.ANDROID_INSTRUMENTATION_TEST.getKind())
        .setLabel(label)
        .setBuildFile(inferBuildFileLocation(label));
  }

  @Override
  TargetIdeInfo.Builder getIdeInfoBuilder() {
    return targetIdeInfoBuilder.setAndroidInstrumentationInfo(
        androidInstrumentationInfoBuilder.build());
  }

  private static ArtifactLocation inferBuildFileLocation(String label) {
    return makeSourceArtifact(NbTargetMapUtils.blazePackageForLabel(label) + "/BUILD");
  }
}
