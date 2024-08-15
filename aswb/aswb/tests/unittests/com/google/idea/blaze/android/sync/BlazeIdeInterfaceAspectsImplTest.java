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
package com.google.idea.blaze.android.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.aspect.Common;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.AndroidIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.Dependency;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.JavaIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.LibraryArtifact;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetKey;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.AndroidResFolder;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterfaceAspectsImpl;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeIdeInterfaceAspectsImpl}. */
@RunWith(JUnit4.class)
public class BlazeIdeInterfaceAspectsImplTest extends BlazeTestCase {

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(FileOperationProvider.class, new FileOperationProvider());
    ExtensionPointImpl<Kind.Provider> ep =
        registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    ep.registerExtension(new AndroidBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());
  }

  @Test
  public void testTargetIdeInfoMultipleResourceFiles() {
    Common.ArtifactLocation localResFolder = artifactLocation("res");
    Common.ArtifactLocation quantumResFolder =
        artifactLocation("java/com/google/android/assets/quantum/res");
    Common.ArtifactLocation quantumAAR =
        artifactLocation("java/com/google/android/assets/quantum/resources.aar");
    IntellijIdeInfo.TargetIdeInfo ideProto =
        IntellijIdeInfo.TargetIdeInfo.newBuilder()
            .setKey(TargetKey.newBuilder().setLabel("//test:test").build())
            .setKindString("android_binary")
            .addDeps(
                Dependency.newBuilder()
                    .setTarget(TargetKey.newBuilder().setLabel("//test:dep"))
                    .build())
            .setJavaIdeInfo(
                JavaIdeInfo.newBuilder()
                    .addJars(
                        LibraryArtifact.newBuilder().setJar(artifactLocation("jar.jar")).build())
                    .addGeneratedJars(
                        LibraryArtifact.newBuilder().setJar(artifactLocation("jar.jar")).build())
                    .addSources(artifactLocation("source.java")))
            .setAndroidIdeInfo(
                AndroidIdeInfo.newBuilder()
                    .addResFolders(resFolderLocation(localResFolder))
                    .addResFolders(resFolderLocation(quantumResFolder, quantumAAR))
                    .setApk(artifactLocation("apk"))
                    .addDependencyApk(artifactLocation("apk"))
                    .setJavaPackage("package"))
            .build();
    TargetIdeInfo target = TargetIdeInfo.fromProto(ideProto);
    assertThat(target).isNotNull();
    ImmutableList<AndroidResFolder> resources = target.getAndroidIdeInfo().getResources();
    assertThat(resources)
        .containsExactly(
            resFolderLocation(artifactLocation(localResFolder)),
            resFolderLocation(artifactLocation(quantumResFolder), artifactLocation(quantumAAR)));
  }

  private static ArtifactLocation artifactLocation(Common.ArtifactLocation artifactLocation) {
    return ArtifactLocation.builder().setRelativePath(artifactLocation.getRelativePath()).build();
  }

  private static Common.ArtifactLocation artifactLocation(String relativePath) {
    return Common.ArtifactLocation.newBuilder().setRelativePath(relativePath).build();
  }

  private static AndroidResFolder resFolderLocation(ArtifactLocation root) {
    return AndroidResFolder.builder()
        .setRoot(root)
        .setAar(artifactLocation(Common.ArtifactLocation.getDefaultInstance()))
        .build();
  }

  private static IntellijIdeInfo.ResFolderLocation resFolderLocation(Common.ArtifactLocation root) {
    return IntellijIdeInfo.ResFolderLocation.newBuilder()
        .setAar(Common.ArtifactLocation.getDefaultInstance())
        .setRoot(root)
        .build();
  }

  private static AndroidResFolder resFolderLocation(ArtifactLocation root, ArtifactLocation aar) {
    return AndroidResFolder.builder().setRoot(root).setAar(aar).build();
  }

  private static IntellijIdeInfo.ResFolderLocation resFolderLocation(
      Common.ArtifactLocation root, Common.ArtifactLocation aar) {
    return IntellijIdeInfo.ResFolderLocation.newBuilder().setRoot(root).setAar(aar).build();
  }
}
