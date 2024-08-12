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
package com.google.idea.blaze.kotlin;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.KotlinToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.java.sync.JavaLanguageLevelHelper;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;
import com.google.idea.blaze.kotlin.sync.KotlinLibrarySource;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.module.Module;
import com.intellij.pom.java.LanguageLevel;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder;
import org.jetbrains.kotlin.idea.facet.KotlinFacet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Kotlin-specific sync integration tests. */
@RunWith(JUnit4.class)
public class KotlinSyncTest extends BlazeSyncIntegrationTestCase {

  @Override
  protected final boolean isLightTestCase() {
    return false;
  }

  @Test
  public void testKotlinClassesPresentInClassPath() {
    setProjectView(
        "directories:",
        "  src/main/kotlin/com/google",
        "targets:",
        "  //src/main/kotlin/com/google:lib",
        "workspace_type: java",
        "additional_languages:",
        "  kotlin");

    workspace.createFile(
        new WorkspacePath("src/main/kotlin/com/google/ClassWithUniqueName1.kt"),
        "package com.google;",
        "public class ClassWithUniqueName1 {}");

    workspace.createFile(
        new WorkspacePath("src/main/kotlin/com/google/ClassWithUniqueName2.kt"),
        "package com.google;",
        "public class ClassWithUniqueName2 {}");
    workspace.createDirectory(new WorkspacePath("external/com_github_jetbrains_kotlin"));

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("src/main/kotlin/com/google/BUILD"))
                    .setLabel("//src/main/kotlin/com/google:lib")
                    .setKind("kt_jvm_library_helper")
                    .addSource(sourceRoot("src/main/kotlin/com/google/ClassWithUniqueName1.scala"))
                    .addSource(sourceRoot("src/main/kotlin/com/google/ClassWithUniqueName2.scala"))
                    .setJavaInfo(JavaIdeInfo.builder()))
            .build();

    setTargetMap(targetMap);

    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Full Sync")
            .setSyncMode(SyncMode.FULL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build();
    runBlazeSync(syncParams);

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.getTargetMap()).isEqualTo(targetMap);
    assertThat(blazeProjectData.getWorkspaceLanguageSettings())
        .isEqualTo(
            new WorkspaceLanguageSettings(
                WorkspaceType.JAVA,
                ImmutableSet.of(LanguageClass.GENERIC, LanguageClass.JAVA, LanguageClass.KOTLIN)));

    BlazeJavaSyncData javaSyncData = blazeProjectData.getSyncState().get(BlazeJavaSyncData.class);
    assertThat(javaSyncData).isNotNull();
    List<BlazeContentEntry> contentEntries = javaSyncData.getImportResult().contentEntries;
    assertThat(contentEntries).hasSize(1);

    BlazeContentEntry contentEntry = contentEntries.get(0);
    assertThat(contentEntry.contentRoot.getPath())
        .isEqualTo(
            this.workspaceRoot
                .fileForPath(new WorkspacePath("src/main/kotlin/com/google"))
                .getPath());
    assertThat(contentEntry.sources).hasSize(1);

    BlazeSourceDirectory sourceDir = contentEntry.sources.get(0);
    assertThat(sourceDir.getPackagePrefix()).isEqualTo("com.google");
    assertThat(sourceDir.getDirectory().getPath())
        .isEqualTo(
            this.workspaceRoot
                .fileForPath(new WorkspacePath("src/main/kotlin/com/google"))
                .getPath());
  }

  @Test
  public void testSimpleSync() {
    setProjectView(
        "directories:",
        "  src/main/kotlin/com/google",
        "targets:",
        "  //src/main/kotlin/com/google:lib",
        "workspace_type: java",
        "additional_languages:",
        "  kotlin");

    workspace.createFile(
        new WorkspacePath("src/main/kotlin/com/google/Source.kt"),
        "package com.google;",
        "public class Source {}");

    workspace.createFile(
        new WorkspacePath("src/main/kotlin/com/google/Other.kt"),
        "package com.google;",
        "public class Other {}");
    workspace.createDirectory(new WorkspacePath("external/com_github_jetbrains_kotlin"));

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("src/main/kotlin/com/google/BUILD"))
                    .setLabel("//src/main/kotlin/com/google:lib")
                    .setKind("kt_jvm_library_helper")
                    .addSource(sourceRoot("src/main/kotlin/com/google/Source.kotlin"))
                    .addSource(sourceRoot("src/main/kotlin/com/google/Other.kotlin"))
                    .setJavaInfo(JavaIdeInfo.builder()))
            .build();

    setTargetMap(targetMap);

    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertLanguageLevel(
        ModuleFinder.getInstance(getProject())
            .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME),
        JavaLanguageLevelHelper.getJavaLanguageLevel(getProjectViewSet(), blazeProjectData));
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.getTargetMap()).isEqualTo(targetMap);
    assertThat(blazeProjectData.getWorkspaceLanguageSettings())
        .isEqualTo(
            new WorkspaceLanguageSettings(
                WorkspaceType.JAVA,
                ImmutableSet.of(LanguageClass.GENERIC, LanguageClass.KOTLIN, LanguageClass.JAVA)));
  }

  /** Tests that ijars are omitted in libraries corresponding to Kotlin SDK targets */
  @Test
  public void testCompileJarsAreAttachedForKotlinSdkTargets() {
    MockExperimentService experimentService = new MockExperimentService();
    registerApplicationComponent(ExperimentService.class, experimentService);
    experimentService.setExperiment(KotlinLibrarySource.dontUseSdkIjars, true);

    setProjectView(
        "directories:",
        "  src/main/kotlin/com/google",
        "targets:",
        "  //src/main/kotlin/com/google:lib",
        "workspace_type: java",
        "additional_languages:",
        "  kotlin");

    workspace.createDirectory(new WorkspacePath("src/main/kotlin/com/google"));
    fileSystem.createFile("execroot/root/bin/kotlinsdk/stdlib.jar");
    fileSystem.createFile("execroot/root/bin/kotlinsdk/stdlib-ijar.jar");

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("src/main/kotlin/com/google/BUILD"))
                    .setLabel("//src/main/kotlin/com/google:lib")
                    .setKind("kt_jvm_library_helper")
                    .setJavaInfo(JavaIdeInfo.builder())
                    .setKotlinToolchainIdeInfo(
                        KotlinToolchainIdeInfo.builder()
                            .setSdkTargets(ImmutableList.of(Label.create("//kotlinsdk:stdlib")))
                            .setKotlinCompilerCommonFlags(ImmutableList.of())))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//kotlinsdk:stdlib")
                    .setKind("java_import")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        ArtifactLocation.builder()
                                            .setRelativePath("kotlinsdk/stdlib-ijar.jar")
                                            .setRootExecutionPathFragment("bin")
                                            .build())
                                    .setClassJar(
                                        ArtifactLocation.builder()
                                            .setRelativePath("kotlinsdk/stdlib.jar")
                                            .setRootExecutionPathFragment("bin")
                                            .build()))))
            .build();

    setTargetMap(targetMap);

    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();

    List<BlazeLibrary> libraries =
        BlazeLibraryCollector.getLibraries(getProjectViewSet(), blazeProjectData);
    assertThat(libraries).isNotEmpty();

    // Check that there is one (and only one) BlazeJarLibrary corresponding to Kotlin sdk target
    List<BlazeJarLibrary> sdkLibraries =
        libraries.stream()
            .filter(l -> l instanceof BlazeJarLibrary)
            .map(l -> (BlazeJarLibrary) l)
            .filter(l -> l.targetKey != null)
            .filter(
                l ->
                    l.targetKey.equals(
                        TargetKey.forPlainTarget(Label.create("//kotlinsdk:stdlib"))))
            .collect(Collectors.toList());
    assertThat(sdkLibraries).hasSize(1);

    // Ensure Kotlin SDK library does not have an interface jar. We want to attach the compile jar
    // instead
    BlazeJarLibrary jarLibrary = (BlazeJarLibrary) libraries.get(0);
    assertThat(jarLibrary.libraryArtifact.getInterfaceJar()).isNull();
    assertThat(jarLibrary.libraryArtifact.getClassJar())
        .isEqualTo(
            ArtifactLocation.builder()
                .setRelativePath("kotlinsdk/stdlib.jar")
                .setRootExecutionPathFragment("bin")
                .build());
  }

  private void assertLanguageLevel(Module module, LanguageLevel languageLevel) {
    String javaVersion = languageLevel.toJavaVersion().toString();
    assertThat(
            Kotlin2JvmCompilerArgumentsHolder.Companion.getInstance(getProject())
                .getSettings()
                .getJvmTarget())
        .isEqualTo(javaVersion);
    CommonCompilerArguments commonArguments =
        KotlinFacet.Companion.get(module).getConfiguration().getSettings().getCompilerArguments();
    if (commonArguments instanceof K2JVMCompilerArguments) {
      assertThat(((K2JVMCompilerArguments) commonArguments).getJvmTarget()).isEqualTo(javaVersion);
    }
  }
}
