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

import static com.google.idea.blaze.android.targetmapbuilder.NbTargetMapUtils.makeSourceArtifact;

import com.google.idea.blaze.android.fixtures.ManifestFixture;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidResFolder;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.java.AndroidBlazeRules;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Builder for a blaze android target's IDE info. Defines common attributes across all android
 * targets. Android targets are like java targets, but with additional attributes like manifests and
 * resource classes. This builder accumulates attributes to a {@link TargetIdeInfo.Builder} which
 * can be used to build {@link TargetMap}.
 *
 * <p>Targets built with {@link NbAndroidTarget} always have an {@link AndroidIdeInfo} attached,
 * even if it's empty.
 */
public class NbAndroidTarget extends NbBaseTargetBuilder {
  private final NbJavaTarget javaTarget;
  private final AndroidIdeInfo.Builder androidIdeInfoBuilder;
  private final WorkspacePath blazePackage;
  private final List<ArtifactLocation> aarList;
  private boolean hasCustomManifest;
  private boolean includesResourceFiles = false;

  public static NbAndroidTarget android_library(String label) {
    return android_library(label, BlazeInfoData.DEFAULT);
  }

  public static NbAndroidTarget android_library(String label, BlazeInfoData environment) {
    return new NbAndroidTarget(
        environment, label, AndroidBlazeRules.RuleTypes.ANDROID_LIBRARY.getKind());
  }

  public static NbAndroidTarget android_binary(String label) {
    return android_binary(label, BlazeInfoData.DEFAULT);
  }

  public static NbAndroidTarget android_binary(String label, BlazeInfoData environment) {
    return new NbAndroidTarget(
        environment, label, AndroidBlazeRules.RuleTypes.ANDROID_BINARY.getKind());
  }

  NbAndroidTarget(BlazeInfoData blazeInfoData, String label, Kind kind) {
    super(blazeInfoData);
    javaTarget = new NbJavaTarget(blazeInfoData, label, kind);
    this.blazePackage = NbTargetMapUtils.blazePackageForLabel(label);
    this.androidIdeInfoBuilder = AndroidIdeInfo.builder();
    aarList = new ArrayList<>();

    if (kind.equals(AndroidBlazeRules.RuleTypes.ANDROID_BINARY.getKind())) {
      // The android_binary rule requires a manifest.
      setDefaultManifest();
      setGenerateResourceClass();
    }

    // blaze java packages all take the form "java/<actual_package_here>".
    String extractedPackagePath = blazePackage.relativePath();
    if (extractedPackagePath.startsWith("java/")) {
      extractedPackagePath = extractedPackagePath.substring(5);
    }

    androidIdeInfoBuilder.setResourceJavaPackage(extractedPackagePath.replaceAll("/", "."));
  }

  @Override
  public TargetIdeInfo.Builder getIdeInfoBuilder() {
    return javaTarget.getIdeInfoBuilder().setAndroidInfo(androidIdeInfoBuilder);
  }

  /**
   * NbAndroidTarget needs to perform some finalization steps before returning the final built
   * ide-info object. E.g. If this target generates resources, then create a res_folder with a dummy
   * res root with a manifest-only AAR if no other resources has been added.
   */
  @Override
  public TargetIdeInfo build() {
    if (androidIdeInfoBuilder.build().generateResourceClass() && aarList.isEmpty()) {
      ArtifactLocation aar =
          ArtifactLocation.builder()
              .setRelativePath(
                  NbTargetMapUtils.workspacePathForLabel(
                      blazePackage, "resources-manifest-only.aar"))
              .setIsSource(true)
              .build();
      aarList.add(aar);
      androidIdeInfoBuilder.addResource(
          AndroidResFolder.builder().setRoot(makeSourceArtifact("")).setAar(aar).build());
    }
    return getIdeInfoBuilder().build();
  }

  private void setGenerateResourceClass() {
    androidIdeInfoBuilder.setGenerateResourceClass(true);
    if (!hasCustomManifest) {
      // An android target with resources must have a manifest.
      setDefaultManifest();
    }
  }

  /**
   * Adds a subset of a resource directory's files to the list of android resources. Note: Also
   * toggles generate resource class to true.
   */
  public NbAndroidTarget res(String... resourceLabels) {
    // An android target that directly declares resources should also generate resource classes.
    setGenerateResourceClass();
    for (String resourceLabel : resourceLabels) {
      String resourcePath = NbTargetMapUtils.workspacePathForLabel(blazePackage, resourceLabel);
      androidIdeInfoBuilder.addResource(makeSourceArtifact(resourcePath));

      // If we didn't generate an AAR for this target it, generate it and add it to the aar list.
      // Resources added here will all go under the same root, which will get packaged into the
      // same AAR.
      if (!includesResourceFiles) {
        includesResourceFiles = true;
        ArtifactLocation aar =
            ArtifactLocation.builder()
                .setRelativePath(NbTargetMapUtils.workspacePathForLabel(blazePackage, "res.aar"))
                .setIsSource(true)
                .build();
        aarList.add(aar);
      }
    }
    return this;
  }

  /**
   * Adds a list of generated android resources. Generated resources are different from regular
   * resources; they are not created by the user.
   *
   * <p>Note: Also toggles generate resource class to true.
   */
  public NbAndroidTarget generated_res(String... resourcePaths) {
    // An android target that directly declares resources should also generate resource classes.
    setGenerateResourceClass();
    for (String resourceLabel : resourcePaths) {
      String resourcePath = NbTargetMapUtils.workspacePathForLabel(blazePackage, resourceLabel);
      androidIdeInfoBuilder.addResource(
          ArtifactLocation.builder()
              .setRootExecutionPathFragment(blazeInfoData.getRootExecutionPathFragment())
              .setRelativePath(resourcePath)
              .setIsSource(false)
              .build());
    }
    return this;
  }

  public List<ArtifactLocation> getAarList() {
    return aarList;
  }

  /**
   * Adds a folder and child files to this target's list of android resources. The folder string is
   * a label where following strings are relative resource files in that folder. Note: Also toggles
   * generate resource class to true.
   */
  public NbAndroidTarget res_folder(String folderLabel, String pathToAar) {
    // An android target that directly declares resources should also generate resource classes.
    setGenerateResourceClass();
    ArtifactLocation aar =
        ArtifactLocation.builder()
            .setRelativePath(NbTargetMapUtils.workspacePathForLabel(blazePackage, pathToAar))
            .setIsSource(true)
            .build();
    aarList.add(aar);
    androidIdeInfoBuilder.addResource(
        AndroidResFolder.builder()
            .setRoot(
                makeSourceArtifact(
                    NbTargetMapUtils.workspacePathForLabel(blazePackage, folderLabel)))
            .setAar(aar)
            .build());
    return this;
  }

  /**
   * Set the android manifest for this android target. Note: Also toggles generate resource class to
   * true.
   *
   * <p>Blaze requires an Android manifest for any android_binary rule and for any android_library
   * which also defines resource files or assets. If the Android manifest isn't explicitly set by
   * calling this method but is required for this target, NbAndroidTarget will assume the manifest
   * is AndroidManifest.xml in the same directory as this target's BUILD file.
   */
  public NbAndroidTarget manifest(String manifestLabel) {
    hasCustomManifest = true;
    setManifest(manifestLabel);
    // An android target that declares its own manifest should also generate resource classes.
    setGenerateResourceClass();
    return this;
  }

  public NbAndroidTarget manifest(ManifestFixture manifestFixture) {
    return manifest(manifestFixture.label);
  }

  public NbAndroidTarget manifest_value(String attributeOrPlaceholder, String value) {
    androidIdeInfoBuilder.putManifestValue(attributeOrPlaceholder, value);
    return this;
  }

  private void setDefaultManifest() {
    setManifest("AndroidManifest.xml");
  }

  private void setManifest(String manifestLabel) {
    String manifestPath = NbTargetMapUtils.workspacePathForLabel(blazePackage, manifestLabel);
    androidIdeInfoBuilder.setManifestFile(makeSourceArtifact(manifestPath));
  }

  /**
   * Explicitly set the resource jar attached to this IDE info to a jar at the provided path. Also
   * sets generate resources to true.
   */
  public NbAndroidTarget resource_jar(String relativeJarPath) {
    // An android target with a resource jar also generates resource classes.
    setGenerateResourceClass();
    androidIdeInfoBuilder.setResourceJar(
        generateJarArtifact(relativeJarPath, relativeJarPath, null));
    return this;
  }

  /** Explicitly set the AIDL jar attached to this IDE info to a jar at the provided path. */
  public NbAndroidTarget idl_jar(String relativeClassJarPath, String relativeIJarPath) {
    androidIdeInfoBuilder
        .setIdlJar(generateJarArtifact(relativeClassJarPath, relativeIJarPath, null).build())
        .setHasIdlSources(true);
    return this;
  }

  private LibraryArtifact.Builder generateJarArtifact(
      @Nullable String relClassJarPath,
      @Nullable String relIJarPath,
      @Nullable String relSourceJarPath) {
    LibraryArtifact.Builder builder = LibraryArtifact.builder();
    if (relIJarPath != null) {
      builder.setInterfaceJar(
          ArtifactLocation.builder()
              .setRootExecutionPathFragment(blazeInfoData.getRootExecutionPathFragment())
              .setRelativePath(relIJarPath)
              .setIsSource(false)
              .build());
    }
    if (relClassJarPath != null) {
      builder.setClassJar(
          ArtifactLocation.builder()
              .setRootExecutionPathFragment(blazeInfoData.getRootExecutionPathFragment())
              .setRelativePath(relClassJarPath)
              .setIsSource(false)
              .build());
    }
    if (relSourceJarPath != null) {
      builder.addSourceJar(
          ArtifactLocation.builder()
              .setRootExecutionPathFragment(blazeInfoData.getRootExecutionPathFragment())
              .setRelativePath(relSourceJarPath)
              .setIsSource(false)
              .build());
    }
    return builder;
  }

  public NbAndroidTarget generated_jar(String relativeJarPath) {
    javaTarget.generated_jar(relativeJarPath);
    return this;
  }

  public NbAndroidTarget source_jar(String relativeJarPath) {
    javaTarget.source_jar(relativeJarPath);
    return this;
  }

  public NbAndroidTarget src(String... sourceLabels) {
    javaTarget.src(sourceLabels);
    return this;
  }

  public NbAndroidTarget dep(String... targetLabels) {
    javaTarget.dep(targetLabels);
    return this;
  }

  public NbAndroidTarget java_toolchain_version(String version) {
    javaTarget.java_toolchain_version(version);
    return this;
  }

  public NbAndroidTarget instruments(String relativeLabel) {
    androidIdeInfoBuilder.setInstruments(
        NbTargetMapUtils.normalizeRelativePathOrLabel(relativeLabel, blazePackage));
    return this;
  }

  public NbAndroidTarget setResourceJavaPackage(String javaPackage) {
    androidIdeInfoBuilder.setResourceJavaPackage(javaPackage);
    return this;
  }
}
