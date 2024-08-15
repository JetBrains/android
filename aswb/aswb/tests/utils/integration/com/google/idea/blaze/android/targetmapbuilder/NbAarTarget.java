/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.targetmapbuilder;

import com.google.idea.blaze.base.ideinfo.AndroidAarIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.java.AndroidBlazeRules;
import javax.annotation.Nullable;

/**
 * Builder for a blaze aar target's IDE info. Defines common attributes across all aar targets. This
 * builder maintain {@link ArtifactLocation} for path to aar file and {@link NbJavaTarget} for its
 * classes jar file. If any of them is empty, project will treat this target as an invalid aar
 * library.
 *
 * <p>Targets built with {@link NbAarTarget} always have an {@link ArtifactLocation} attached, even
 * if it's empty.
 */
public class NbAarTarget extends NbBaseTargetBuilder {
  private final NbJavaTarget javaTarget;
  private final ArtifactLocation.Builder aarArtifactLocationBuilder;
  private final WorkspacePath blazePackage;
  @Nullable private final String javaPackage;

  public static NbAarTarget aar_import(String label) {
    return aar_import(label, BlazeInfoData.DEFAULT);
  }

  public static NbAarTarget aar_import(String label, BlazeInfoData environment) {
    return new NbAarTarget(
        environment, label, AndroidBlazeRules.RuleTypes.AAR_IMPORT.getKind(), null);
  }

  NbAarTarget(BlazeInfoData blazeInfoData, String label, Kind kind, @Nullable String javaPackage) {
    super(blazeInfoData);
    this.blazePackage = NbTargetMapUtils.blazePackageForLabel(label);
    this.javaTarget = new NbJavaTarget(blazeInfoData, label, kind);
    this.aarArtifactLocationBuilder = ArtifactLocation.builder();
    this.javaPackage = javaPackage;
  }

  @Override
  public TargetIdeInfo.Builder getIdeInfoBuilder() {
    return javaTarget
        .getIdeInfoBuilder()
        .setAndroidAarInfo(new AndroidAarIdeInfo(aarArtifactLocationBuilder.build(), javaPackage));
  }

  public NbAarTarget aar(String aarLabel) {
    return aar(aarLabel, null);
  }

  public NbAarTarget aar(String aarLabel, String srcLabel) {
    aarArtifactLocationBuilder
        .setRelativePath(NbTargetMapUtils.workspacePathForLabel(blazePackage, aarLabel))
        .setIsSource(true);
    return this;
  }

  public NbAarTarget generated_jar(String jarLabel) {
    return generated_jar(jarLabel, null);
  }

  public NbAarTarget dep(String... targetLabels) {
    javaTarget.dep(targetLabels);
    return this;
  }

  /**
   * Sets the jar that is generated from within the aar, and the source jar for the aar.
   *
   * <p>Note that the source jar is specified over here, because in the ide info, the source jar is
   * picked up by the java aspects and goes into the java_ide_info.
   */
  public NbAarTarget generated_jar(String jarLabel, @Nullable String srcJar) {
    javaTarget.jar(jarLabel, srcJar);
    return this;
  }

  public ArtifactLocation getAar() {
    return aarArtifactLocationBuilder.build();
  }
}
