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
package com.google.idea.blaze.android.targetmapbuilder;

import static com.google.idea.blaze.android.targetmapbuilder.NbTargetMapUtils.workspacePathForLabel;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.java.JavaBlazeRules;
import org.jetbrains.annotations.Nullable;

/**
 * Builder for a blaze java target's IDE info. Defines common attributes across all java targets.
 * This builder accumulates attributes to a {@link TargetIdeInfo.Builder} which can be used to build
 * {@link TargetMap}.
 *
 * <p>Targets built with {@link NbJavaTarget} always have a {@link JavaIdeInfo} attached, even if
 * it's empty.
 */
public class NbJavaTarget extends NbBaseTargetBuilder {
  private final NbTarget target;
  private final JavaIdeInfo.Builder javaIdeInfoBuilder;
  private final WorkspacePath blazePackage;

  public static NbJavaTarget java_library(String label) {
    return java_library(label, BlazeInfoData.DEFAULT);
  }

  public static NbJavaTarget java_library(String label, BlazeInfoData environment) {
    return new NbJavaTarget(environment, label, JavaBlazeRules.RuleTypes.JAVA_LIBRARY.getKind());
  }

  NbJavaTarget(BlazeInfoData blazeInfoData, String label, Kind kind) {
    super(blazeInfoData);
    target = new NbTarget(blazeInfoData, label, kind);
    javaIdeInfoBuilder = new JavaIdeInfo.Builder();
    this.blazePackage = NbTargetMapUtils.blazePackageForLabel(label);

    // e.g. //java/com/google:app -> java/com/google/app.jdeps
    String jdepsPath = workspacePathForLabel(blazePackage, label.replace(":", "/") + ".jdeps");
    ArtifactLocation jdepsArtifact =
        ArtifactLocation.builder()
            .setRootExecutionPathFragment(blazeInfoData.getRootExecutionPathFragment())
            .setRelativePath(jdepsPath)
            .setIsSource(false)
            .build();
    javaIdeInfoBuilder.setJdepsFile(jdepsArtifact);
  }

  @Override
  public TargetIdeInfo.Builder getIdeInfoBuilder() {
    return target.getIdeInfoBuilder().setJavaInfo(javaIdeInfoBuilder);
  }

  public NbJavaTarget jar(String jarLabel, @Nullable String sourceLabel) {
    String jarPath = workspacePathForLabel(blazePackage, jarLabel);
    ArtifactLocation classJar =
        ArtifactLocation.builder()
            .setRootExecutionPathFragment(blazeInfoData.getRootExecutionPathFragment())
            .setRelativePath(jarPath)
            .setIsSource(false)
            .build();
    LibraryArtifact.Builder builder = LibraryArtifact.builder().setClassJar(classJar);

    if (sourceLabel != null) {
      ArtifactLocation sourceLocation =
          ArtifactLocation.builder()
              .setRootExecutionPathFragment(blazeInfoData.getRootExecutionPathFragment())
              .setRelativePath(sourceLabel)
              .setIsSource(true)
              .build();
      builder.addSourceJar(sourceLocation);
    }

    javaIdeInfoBuilder.addJar(builder);
    return this;
  }

  public NbJavaTarget generated_jar(String jarLabel) {
    String jarPath = workspacePathForLabel(blazePackage, jarLabel);
    ArtifactLocation jar =
        ArtifactLocation.builder()
            .setRootExecutionPathFragment(blazeInfoData.getRootExecutionPathFragment())
            .setRelativePath(jarPath)
            .setIsSource(false)
            .build();
    javaIdeInfoBuilder.addJar(LibraryArtifact.builder().setClassJar(jar));
    return this;
  }

  public NbJavaTarget source_jar(String jarLabel) {
    String jarPath = workspacePathForLabel(blazePackage, jarLabel);
    ArtifactLocation jar =
        ArtifactLocation.builder().setRelativePath(jarPath).setIsSource(true).build();
    javaIdeInfoBuilder.addJar(LibraryArtifact.builder().setClassJar(jar));
    return this;
  }

  public NbJavaTarget src(String... sourceLabels) {
    target.src(sourceLabels);
    return this;
  }

  public NbJavaTarget dep(String... targetLabels) {
    target.dep(targetLabels);
    return this;
  }

  public NbJavaTarget java_toolchain_version(String version) {
    target.java_toolchain_version(version);
    return this;
  }
}
