/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin.run.debug;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ExtensionTestUtil;
import java.util.ArrayList;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BazelKotlinxCoroutinesLibFinder}. */
@RunWith(JUnit4.class)
public class BazelKotlinxCoroutinesLibFinderTest extends BlazeRunConfigurationProducerTestCase {
  private static final String MAIN_CLASS = "com.google.binary.MainKt";
  private static final String MAIN_CLASS_FILE = "com/google/binary/Main.kt";

  private TargetIdeInfo kotlinxCoroutinesLib;
  private TargetIdeInfo kotlinxCoroutinesOldVersion;
  private TargetIdeInfo kotlinxCoroutinesLibNoJavaIdeInfo;
  private TargetIdeInfo kotlinxCoroutinesLibNoJars;
  private TargetIdeInfo kotlinxCoroutinesLibMultipleJars;

  private PsiFile mainFile;

  @Override
  protected BuildSystemName buildSystem() {
    return BuildSystemName.Bazel;
  }

  @Before
  public final void setup() throws Throwable {
    kotlinxCoroutinesLib =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_library")
            .setLabel("//kotlinx_coroutines:jar")
            .setJavaInfo(
                JavaIdeInfo.builder()
                    .addJar(
                        LibraryArtifact.builder()
                            .setClassJar(
                                ArtifactLocation.builder()
                                    .setRelativePath("kotlinx-coroutines-core-1.4.2.jar")
                                    .build())))
            .build();

    kotlinxCoroutinesOldVersion =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_library")
            .setLabel("//kotlinx_coroutines:jar")
            .setJavaInfo(
                JavaIdeInfo.builder()
                    .addJar(
                        LibraryArtifact.builder()
                            .setClassJar(
                                ArtifactLocation.builder()
                                    .setRelativePath("kotlinx-coroutines-core-1.2.jar")
                                    .build())))
            .build();

    kotlinxCoroutinesLibNoJavaIdeInfo =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_library")
            .setLabel("//kotlinx_coroutines:jar")
            .build();

    kotlinxCoroutinesLibNoJars =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_library")
            .setLabel("//kotlinx_coroutines:jar")
            .setJavaInfo(JavaIdeInfo.builder())
            .build();

    kotlinxCoroutinesLibMultipleJars =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_library")
            .setLabel("//kotlinx_coroutines:jar")
            .setJavaInfo(
                JavaIdeInfo.builder()
                    .addJar(
                        LibraryArtifact.builder()
                            .setClassJar(
                                ArtifactLocation.builder()
                                    .setRelativePath("another-lib-jar.jar")
                                    .build()))
                    .addJar(
                        LibraryArtifact.builder()
                            .setClassJar(
                                ArtifactLocation.builder()
                                    .setRelativePath("kotlinx-coroutines-core-1.4.2.jar")
                                    .build())))
            .build();

    mainFile =
        createAndIndexFile(
            WorkspacePath.createIfValid(MAIN_CLASS_FILE),
            "package com.google.binary",
            "import kotlinx.coroutines.runBlocking",
            "fun main(args: Array<String>) {}");
  }

  @Test
  public void kotlinxCoroutinesLibAttached_jarFound() {
    TargetMap targetMap = createTargetMap(kotlinxCoroutinesLib).build();
    registerProjectService(targetMap);
    BlazeCommandRunConfiguration config = createConfiguration();

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinProjectTraversingService.getInstance().findKotlinxCoroutinesLib(config);

    assertThat(coroutinesLibLocation).isPresent();
    assertThat(coroutinesLibLocation.get().getRelativePath())
        .isEqualTo("kotlinx-coroutines-core-1.4.2.jar");
  }

  @Test
  public void kotlinxCoroutinesLibAttachedAsTransitiveDependency_jarFound() {
    TargetMap targetMap =
        createTargetMap(/*isKtLibTarget=*/ true, kotlinxCoroutinesLib)
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setLabel("//com/google/binary:main_kt")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass(MAIN_CLASS))
                    .addSource(sourceRoot(MAIN_CLASS_FILE))
                    .addRuntimeDep("//com/google/binary:lib_kt")
                    .build())
            .build();
    registerProjectService(targetMap);
    BlazeCommandRunConfiguration config = createConfiguration();

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinProjectTraversingService.getInstance().findKotlinxCoroutinesLib(config);

    assertThat(coroutinesLibLocation).isPresent();
    assertThat(coroutinesLibLocation.get().getRelativePath())
        .isEqualTo("kotlinx-coroutines-core-1.4.2.jar");
  }

  @Test
  public void kotlinxCoroutinesLibNotAttached_jarNotFound() {
    TargetMap targetMap = createTargetMap(/*coroutinesLib=*/ null).build();
    registerProjectService(targetMap);
    BlazeCommandRunConfiguration config = createConfiguration();

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinProjectTraversingService.getInstance().findKotlinxCoroutinesLib(config);

    assertThat(coroutinesLibLocation).isEmpty();
  }

  @Test
  public void oldKotlinxCoroutinesLibVersionAttached_jarNotFound() {
    TargetMap targetMap = createTargetMap(kotlinxCoroutinesOldVersion).build();
    registerProjectService(targetMap);
    BlazeCommandRunConfiguration config = createConfiguration();

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinProjectTraversingService.getInstance().findKotlinxCoroutinesLib(config);

    assertThat(coroutinesLibLocation).isEmpty();
  }

  @Test
  public void missingBlazeProjectData_jarNotFound() {
    TargetMap targetMap = createTargetMap(kotlinxCoroutinesLib).build();
    MockBlazeProjectDataManager mockBlazeProjectDataManager = registerProjectService(targetMap);
    BlazeCommandRunConfiguration config = createConfiguration();
    // Set BlazeProjectData to null in the MockBlazeProjectDataManager
    mockBlazeProjectDataManager.setBlazeProjectData(/*blazeProjectData=*/ null);

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinProjectTraversingService.getInstance().findKotlinxCoroutinesLib(config);

    assertThat(coroutinesLibLocation).isEmpty();
  }

  @Test
  public void missingKotlinxCoroutinesLibFinderEP_jarNotFound() {
    TargetMap targetMap = createTargetMap(kotlinxCoroutinesLib).build();
    registerProjectService(targetMap);
    BlazeCommandRunConfiguration config = createConfiguration();
    // Unregister all EPs of KotlinxCoroutinesLibFinder
    ExtensionTestUtil.maskExtensions(
        KotlinxCoroutinesLibFinder.EP_NAME,
        /*newExtensions=*/ new ArrayList<>(),
        getTestRootDisposable());

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinProjectTraversingService.getInstance().findKotlinxCoroutinesLib(config);

    assertThat(coroutinesLibLocation).isEmpty();
  }

  @Test
  public void invalidKotlinxCoroutinesLib_noJavaIdeInfo_jarNotFound() {
    TargetMap targetMap = createTargetMap(kotlinxCoroutinesLibNoJavaIdeInfo).build();
    registerProjectService(targetMap);
    BlazeCommandRunConfiguration config = createConfiguration();

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinProjectTraversingService.getInstance().findKotlinxCoroutinesLib(config);

    assertThat(coroutinesLibLocation).isEmpty();
  }

  @Test
  public void invalidKotlinxCoroutinesLib_noJarsInLib_jarNotFound() {
    TargetMap targetMap = createTargetMap(kotlinxCoroutinesLibNoJars).build();
    registerProjectService(targetMap);
    BlazeCommandRunConfiguration config = createConfiguration();

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinProjectTraversingService.getInstance().findKotlinxCoroutinesLib(config);

    assertThat(coroutinesLibLocation).isEmpty();
  }

  @Test
  public void missingKotlinxCoroutinesLibTarget_jarNotFound() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setLabel("//com/google/binary:main_kt")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass(MAIN_CLASS))
                    .addSource(sourceRoot(MAIN_CLASS_FILE))
                    .addDependency("//kotlinx_coroutines:jar")
                    .build())
            .build();
    registerProjectService(targetMap);
    BlazeCommandRunConfiguration config = createConfiguration();

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinProjectTraversingService.getInstance().findKotlinxCoroutinesLib(config);

    assertThat(coroutinesLibLocation).isEmpty();
  }

  @Test
  public void kotlinxCoroutinesLibWithMultipleJars_rightJarFound() {
    TargetMap targetMap = createTargetMap(kotlinxCoroutinesLibMultipleJars).build();
    registerProjectService(targetMap);
    BlazeCommandRunConfiguration config = createConfiguration();

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinProjectTraversingService.getInstance().findKotlinxCoroutinesLib(config);

    assertThat(coroutinesLibLocation).isPresent();
    assertThat(coroutinesLibLocation.get().getRelativePath())
        .isEqualTo("kotlinx-coroutines-core-1.4.2.jar");
  }

  private static TargetMapBuilder createTargetMap(
      boolean isKtLibTarget, @Nullable TargetIdeInfo coroutinesLib) {
    TargetMapBuilder targetMapbuilder = TargetMapBuilder.builder();
    TargetIdeInfo.Builder targetIdeInfoBuilder =
        TargetIdeInfo.builder()
            .setKind(isKtLibTarget ? "kt_jvm_library" : "kt_jvm_binary")
            .setLabel(isKtLibTarget ? "//com/google/binary:lib_kt" : "//com/google/binary:main_kt")
            .setJavaInfo(JavaIdeInfo.builder().setMainClass(MAIN_CLASS))
            .addSource(sourceRoot(MAIN_CLASS_FILE));

    if (coroutinesLib != null) {
      targetMapbuilder.addTarget(coroutinesLib);
      targetIdeInfoBuilder.addDependency(coroutinesLib.getKey().getLabel());
    }
    targetMapbuilder.addTarget(targetIdeInfoBuilder.build());
    return targetMapbuilder;
  }

  private static TargetMapBuilder createTargetMap(@Nullable TargetIdeInfo coroutinesLib) {
    return createTargetMap(/*isKtLibTarget=*/ false, coroutinesLib);
  }

  private MockBlazeProjectDataManager registerProjectService(TargetMap targetMap) {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(targetMap);
    MockBlazeProjectDataManager mockBlazeProjectDataManager =
        new MockBlazeProjectDataManager(builder.build());
    registerProjectService(BlazeProjectDataManager.class, mockBlazeProjectDataManager);
    return mockBlazeProjectDataManager;
  }

  private BlazeCommandRunConfiguration createConfiguration() {
    RunConfiguration config = createConfigurationFromLocation(mainFile);
    assertThat(config).isNotNull();
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);
    return (BlazeCommandRunConfiguration) config;
  }
}
