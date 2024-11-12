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
package com.google.idea.blaze.android.sync.model.idea;

import com.android.ide.common.repository.GoogleMavenArtifactIdHelper;
import com.android.ide.common.repository.GradleCoordinate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.projectsystem.MavenArtifactLocator;
import com.google.idea.blaze.android.sync.importer.BlazeAndroidWorkspaceImporter;
import com.google.idea.blaze.android.sync.importer.BlazeImportInput;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.TestFileSystem;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidAarIdeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidResFolder;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.google.idea.blaze.java.JavaBlazeRules;
import com.google.idea.blaze.java.sync.importer.BlazeJavaWorkspaceImporter;
import com.google.idea.blaze.java.sync.jdeps.MockJdepsMap;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.intellij.openapi.project.Project;
import java.io.File;
import org.jetbrains.annotations.NotNull;

/**
 * Fixture that generates a number of data structures related to blaze import for a hardcoded
 * project.
 */
public final class BlazeImportFixture {
  private final TestFileSystem fileSystem;
  private final WorkspaceRoot workspaceRoot;
  private final Project project;
  private ProjectView projectView;
  private TargetMap targetMap;
  private final BlazeContext context;
  private MavenArtifactLocator mavenArtifactLocator;

  public BlazeImportFixture(
      Project project, TestFileSystem fileSystem, WorkspaceRoot root, BlazeContext context) {
    this.fileSystem = fileSystem;
    this.workspaceRoot = root;
    this.project = project;
    this.context = context;
  }

  public TargetMap getTargetMap() {
    if (targetMap == null) {
      targetMap = buildTargetMap().build();
    }
    return targetMap;
  }

  public WorkspaceRoot getWorkspaceRoot() {
    return workspaceRoot;
  }

  public TestFileSystem getFileSystem() {
    return fileSystem;
  }

  public Project getProject() {
    return project;
  }

  public BlazeContext getContext() {
    return context;
  }

  public BlazeJavaImportResult importJavaWorkspace() {
    BlazeJavaWorkspaceImporter blazeWorkspaceImporter =
        new BlazeJavaWorkspaceImporter(
            getProject(),
            getWorkspaceRoot(),
            getProjectViewSet(),
            new WorkspaceLanguageSettings(WorkspaceType.JAVA, ImmutableSet.of(LanguageClass.JAVA)),
            getTargetMap(),
            getBlazeImportInput().createSourceFilter(),
            new MockJdepsMap(),
            null,
            getDecoder(),
            getSyncState());
    return blazeWorkspaceImporter.importWorkspace(getContext());
  }

  public BlazeAndroidImportResult importAndroidWorkspace() {
    BlazeAndroidWorkspaceImporter workspaceImporter =
        new BlazeAndroidWorkspaceImporter(getProject(), getContext(), getBlazeImportInput());
    return workspaceImporter.importWorkspace();
  }

  public SyncState getSyncState() {
    SyncState.Builder syncStateBuilder = new SyncState.Builder();
    BlazeAndroidImportResult importAndroidResult = importAndroidWorkspace();
    BlazeJavaImportResult importJavaResult = importJavaWorkspace();
    syncStateBuilder.put(
        new BlazeAndroidSyncData(importAndroidResult, new AndroidSdkPlatform("stable", 15)));
    syncStateBuilder.put(
        new BlazeJavaSyncData(importJavaResult, new Glob.GlobSet(ImmutableList.of())));
    return syncStateBuilder.build();
  }

  @NotNull
  private BlazeImportInput getBlazeImportInput() {
    return BlazeImportInput.forProject(
        getProject(), getWorkspaceRoot(), getProjectViewSet(), getTargetMap(), getDecoder());
  }

  public ProjectViewSet getProjectViewSet() {
    return ProjectViewSet.builder().add(getProjectView()).build();
  }

  public BlazeProjectData getBlazeProjectData() {
    return MockBlazeProjectDataBuilder.builder(getWorkspaceRoot())
        .setTargetMap(getTargetMap())
        .setSyncState(getSyncState())
        .setWorkspaceLanguageSettings(
            new WorkspaceLanguageSettings(
                WorkspaceType.ANDROID, ImmutableSet.of(LanguageClass.JAVA)))
        .setArtifactLocationDecoder(getDecoder())
        .build();
  }

  public BlazeInfo getBlazeInfo() {
    String outputBase = "/src";
    return BlazeInfo.createMockBlazeInfo(
        outputBase,
        outputBase + "/execroot",
        outputBase + "/execroot/bin",
        outputBase + "/execroot/gen",
        outputBase + "/execroot/testlogs");
  }

  public ArtifactLocationDecoder getDecoder() {
    return new ArtifactLocationDecoderImpl(
        getBlazeInfo(), new WorkspacePathResolverImpl(workspaceRoot), RemoteOutputArtifacts.EMPTY);
  }

  public static File decodePath(ArtifactLocation location) {
    return new File("/src", location.getExecutionRootRelativePath());
  }

  public ProjectView getProjectView() {
    if (projectView == null) {
      projectView =
          ProjectView.builder()
              .add(
                  ListSection.builder(DirectorySection.KEY)
                      .add(DirectoryEntry.include(new WorkspacePath("java/com/google"))))
              .build();
    }
    return projectView;
  }

  public MavenArtifactLocator getMavenArtifactLocator() {
    if (mavenArtifactLocator == null) {
      mavenArtifactLocator =
          new MavenArtifactLocator() {
            @Override
            public Label labelFor(GradleCoordinate coordinate) {
              return GoogleMavenArtifactIdHelper.getLabelForGoogleMavenArtifactId(coordinate);
            }

            @Override
            public BuildSystemName buildSystem() {
              return BuildSystemName.Blaze;
            }
          };
    }
    return mavenArtifactLocator;
  }

  private TargetMapBuilder buildTargetMap() {
    Label recyclerView = Label.create("//third_party/recyclerview:recyclerview");
    Label constraintLayout = Label.create("//third_party/constraint_layout:constraint_layout");
    Label quantum = Label.create("//third_party/quantum:values");
    Label aarFile = Label.create("//third_party/aar:an_aar");
    Label individualLibrary = Label.create("//third_party/individualLibrary:values");
    Label guava = Label.create("//third_party/guava:java");
    Label main = Label.create("//java/com/google:app");
    Label intermediateDependency = Label.create("//java/com/google/intermediate:intermediate");

    return TargetMapBuilder.builder()
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(main)
                .setKind(AndroidBlazeRules.RuleTypes.ANDROID_BINARY.getKind())
                .setBuildFile(source("java/com/google/BUILD"))
                .setJavaInfo(javaInfoWithJars("app.jar"))
                .setAndroidInfo(
                    AndroidIdeInfo.builder()
                        .setManifestFile(source("java/com/google/AndroidManifest.xml"))
                        .addResource(source("java/com/google/res"))
                        .addResource(source("third_party/shared/res"))
                        .setGenerateResourceClass(true)
                        .setResourceJavaPackage("java.com.google.app"))
                .addSource(source("java/com/google/app/MainActivity.java"))
                .addDependency(guava)
                .addDependency(quantum)
                .addDependency(aarFile)
                .addDependency(intermediateDependency))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(individualLibrary)
                .setKind(AndroidBlazeRules.RuleTypes.ANDROID_LIBRARY.getKind())
                .setBuildFile(source("third_party/individualLibrary/BUILD"))
                .setAndroidInfo(
                    AndroidIdeInfo.builder()
                        .setManifestFile(
                            source("third_party/individualLibrary/AndroidManifest.xml"))
                        .addResource(source("third_party/individualLibrary/res"))
                        .setGenerateResourceClass(true)
                        .setResourceJavaPackage("third_party.individualLibrary")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(quantum)
                .setKind(AndroidBlazeRules.RuleTypes.ANDROID_LIBRARY.getKind())
                .setBuildFile(source("third_party/quantum/BUILD"))
                .setAndroidInfo(
                    AndroidIdeInfo.builder()
                        .setManifestFile(source("third_party/quantum/AndroidManifest.xml"))
                        .addResource(
                            AndroidResFolder.builder()
                                .setRoot(source("third_party/quantum/res"))
                                .setAar(source("third_party/quantum/values.aar"))
                                .build())
                        .setGenerateResourceClass(true)
                        .setResourceJavaPackage("third_party.quantum")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(guava)
                .setKind(JavaBlazeRules.RuleTypes.JAVA_LIBRARY.getKind())
                .setJavaInfo(javaInfoWithJars("third_party/guava-21.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(aarFile)
                .setBuildFile(source("third_party/aar/BUILD"))
                .setKind(AndroidBlazeRules.RuleTypes.AAR_IMPORT.getKind())
                .setAndroidAarInfo(
                    new AndroidAarIdeInfo(
                        source("third_party/aar/lib_aar.aar"), /*customJavaPackage=*/ null))
                .setJavaInfo(
                    javaInfoWithJars("third_party/aar/_aar/an_aar/classes_and_libs_merged.jar"))
                .build())
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(recyclerView)
                .setKind(AndroidBlazeRules.RuleTypes.ANDROID_LIBRARY.getKind())
                .setBuildFile(source("third_party/recyclerview/BUILD"))
                .setAndroidInfo(
                    AndroidIdeInfo.builder()
                        .setManifestFile(source("third_party/recyclerview/AndroidManifest.xml"))
                        .addResource(source("third_party/recyclerview/res"))
                        .setGenerateResourceClass(true)
                        .setResourceJavaPackage("third_party.recyclerview")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(intermediateDependency)
                .setKind(AndroidBlazeRules.RuleTypes.ANDROID_LIBRARY.getKind())
                .setBuildFile(source("java/com/google/intermediate/BUILD"))
                .setAndroidInfo(
                    AndroidIdeInfo.builder()
                        .setManifestFile(source("java/com/google/intermediate/AndroidManifest.xml"))
                        .addResource(source("java/com/google/intermediate/res"))
                        .setGenerateResourceClass(true)
                        .setResourceJavaPackage("java.com.google.intermediate"))
                .addDependency(constraintLayout))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(constraintLayout)
                .setKind(AndroidBlazeRules.RuleTypes.ANDROID_LIBRARY.getKind())
                .setBuildFile(source("third_party/constraint_layout/BUILD"))
                .setAndroidInfo(
                    AndroidIdeInfo.builder()
                        .setManifestFile(
                            source("third_party/constraint_layout/AndroidManifest.xml"))
                        .addResource(source("third_party/constraint_layout/res"))
                        .setGenerateResourceClass(true)
                        .setResourceJavaPackage("third_party.constraint_layout")));
  }

  private JavaIdeInfo.Builder javaInfoWithJars(String... relativeJarPaths) {
    JavaIdeInfo.Builder builder = JavaIdeInfo.builder();
    for (String relativeJarPath : relativeJarPaths) {
      ArtifactLocation jar = source(relativeJarPath);
      builder.addJar(LibraryArtifact.builder().setClassJar(jar));
      fileSystem.createFile(jar.getExecutionRootRelativePath());
    }
    return builder;
  }

  public static ArtifactLocation source(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }
}
