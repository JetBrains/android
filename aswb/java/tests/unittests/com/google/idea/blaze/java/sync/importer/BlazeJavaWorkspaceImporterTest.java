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
package com.google.idea.blaze.java.sync.importer;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.JavaToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.Tags;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.prefetch.MockPrefetchService;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.ExcludeTargetSection;
import com.google.idea.blaze.base.projectview.section.sections.ImportTargetOutputSection;
import com.google.idea.blaze.base.projectview.section.sections.TestSourceSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.MockRemoteArtifactPrefetcher;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.google.idea.blaze.java.JavaBlazeRules;
import com.google.idea.blaze.java.libraries.JarCache;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.importer.emptylibrary.EmptyLibraryFilterSettings;
import com.google.idea.blaze.java.sync.jdeps.MockJdepsMap;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;
import com.google.idea.blaze.java.sync.source.JavaLikeLanguage;
import com.google.idea.blaze.java.sync.source.JavaSourcePackageReader;
import com.google.idea.blaze.java.sync.source.PackageManifestReader;
import com.google.idea.blaze.java.sync.source.SourceArtifact;
import com.google.idea.blaze.java.sync.workingset.JavaWorkingSet;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for BlazeJavaWorkspaceImporter */
@RunWith(JUnit4.class)
public class BlazeJavaWorkspaceImporterTest extends BlazeTestCase {

  private static class MockFileOperationProvider extends FileOperationProvider {
    private final HashMap<File, Long> fileSizes = new HashMap<>();
    private final HashMap<File, Long> lastModifiedTimes = new HashMap<>();

    void setFileSize(String relativePath, long size) {
      fileSizes.put(new File("/", relativePath), size);
    }

    @Override
    public boolean setFileModifiedTime(File file, long time) {
      lastModifiedTimes.put(file, time);
      return true;
    }

    @Override
    public long getFileSize(File file) {
      if (fileSizes.containsKey(file)) {
        return fileSizes.get(file);
      }
      if (!file.exists()) {
        // this stops use from filtering out library jars
        // because the test setup didn't create them.
        return 500L;
      }
      return super.getFileSize(file);
    }

    @Override
    public long getFileModifiedTime(File file) {
      if (lastModifiedTimes.containsKey(file)) {
        return lastModifiedTimes.get(file);
      }
      return super.getFileModifiedTime(file);
    }
  }

  private static final String FAKE_WORKSPACE_ROOT = "/root";
  private final WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File(FAKE_WORKSPACE_ROOT));

  private static final String FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT =
      "bazel-out/gcc-4.X.Y-crosstool-v17-hybrid-grtev3-k8-fastbuild/bin";

  private static final ArtifactLocationDecoder FAKE_ARTIFACT_DECODER =
      new MockArtifactLocationDecoder() {
        @Override
        public File decode(ArtifactLocation artifactLocation) {
          return new File("/", artifactLocation.getRelativePath());
        }
      };

  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("", "", "", "", BuildSystemName.Bazel, ProjectType.ASPECT_SYNC);
  private ExtensionPointImpl<BlazeJavaSyncAugmenter> augmenters;

  private BlazeContext context;
  private final ErrorCollector errorCollector = new ErrorCollector();
  private final MockJdepsMap jdepsMap = new MockJdepsMap();
  private JavaWorkingSet workingSet = null;
  private final WorkspaceLanguageSettings workspaceLanguageSettings =
      new WorkspaceLanguageSettings(WorkspaceType.JAVA, ImmutableSet.of(LanguageClass.JAVA));
  private MockFileOperationProvider fileOperationProvider;
  private MockExperimentService experimentService;

  @Override
  @SuppressWarnings("FunctionalInterfaceClash") // False positive on getDeclaredPackageOfJavaFile.
  protected void initTest(Container applicationServices, Container projectServices) {
    fileOperationProvider = new MockFileOperationProvider();
    applicationServices.register(FileOperationProvider.class, fileOperationProvider);

    experimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experimentService);

    MockRemoteArtifactPrefetcher remoteArtifactPrefetcher = new MockRemoteArtifactPrefetcher();
    applicationServices.register(RemoteArtifactPrefetcher.class, remoteArtifactPrefetcher);

    ExtensionPointImpl<Kind.Provider> ep =
        registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    ep.registerExtension(new JavaBlazeRules());
    ep.registerExtension(new AndroidBlazeRules());
    ep.registerExtension(new GenericBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());

    BlazeExecutor blazeExecutor = new MockBlazeExecutor();
    applicationServices.register(BlazeExecutor.class, blazeExecutor);
    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    // will silently fall back to FilePathJavaPackageReader
    applicationServices.register(
        JavaSourcePackageReader.class,
        new JavaSourcePackageReader() {
          @Nullable
          @Override
          public String getDeclaredPackageOfJavaFile(
              BlazeContext context,
              ArtifactLocationDecoder artifactLocationDecoder,
              SourceArtifact sourceArtifact) {
            return null;
          }
        });
    applicationServices.register(PackageManifestReader.class, new PackageManifestReader());
    applicationServices.register(PrefetchService.class, new MockPrefetchService());

    context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, errorCollector);

    augmenters =
        registerExtensionPoint(BlazeJavaSyncAugmenter.EP_NAME, BlazeJavaSyncAugmenter.class);
    registerExtensionPoint(EmptyLibraryFilterSettings.EP_NAME, EmptyLibraryFilterSettings.class);

    registerExtensionPoint(JavaLikeLanguage.EP_NAME, JavaLikeLanguage.class)
        .registerExtension(new JavaLikeLanguage.Java());

    projectServices.register(JarCache.class, new MockJarCache(project));
  }

  @Override
  protected BuildSystemProvider createBuildSystemProvider() {
    return new BazelBuildSystemProvider();
  }

  private BlazeJavaImportResult importWorkspace(
      WorkspaceRoot workspaceRoot, TargetMapBuilder targetMapBuilder, ProjectView projectView) {

    ProjectViewSet projectViewSet = ProjectViewSet.builder().add(projectView).build();

    TargetMap targetMap = targetMapBuilder.build();
    com.google.idea.blaze.java.sync.importer.JavaSourceFilter sourceFilter =
        new com.google.idea.blaze.java.sync.importer.JavaSourceFilter(
            Blaze.getBuildSystemName(project), workspaceRoot, projectViewSet, targetMap);
    com.google.idea.blaze.java.sync.importer.BlazeJavaWorkspaceImporter blazeWorkspaceImporter =
        new com.google.idea.blaze.java.sync.importer.BlazeJavaWorkspaceImporter(
            project,
            workspaceRoot,
            projectViewSet,
            workspaceLanguageSettings,
            targetMap,
            sourceFilter,
            jdepsMap,
            workingSet,
            FAKE_ARTIFACT_DECODER,
            null);

    return blazeWorkspaceImporter.importWorkspace(context);
  }

  /** Ensure an empty response results in an empty import result. */
  @Test
  public void testEmptyProject() {
    BlazeJavaImportResult result =
        importWorkspace(workspaceRoot, TargetMapBuilder.builder(), ProjectView.builder().build());
    errorCollector.assertNoIssues();
    assertThat(result.contentEntries).isEmpty();
  }

  @Test
  public void testSingleModule() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .addSource(source("java/apps/example/subdir/SubdirHelper.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("java/apps/example/example_debug-ijar.jar"))
                                    .setClassJar(gen("java/apps/example/example_debug.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.buildOutputJars).hasSize(1);
    ArtifactLocation compilerOutputLib = result.buildOutputJars.iterator().next();
    assertNotNull(compilerOutputLib);
    assertThat(compilerOutputLib.getRelativePath()).endsWith("example_debug.jar");

    assertThat(result.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build());

    assertThat(result.javaSourceFiles)
        .containsExactly(
            source("java/apps/example/MainActivity.java"),
            source("java/apps/example/subdir/SubdirHelper.java"));
  }

  @Test
  public void testGeneratedLibrariesIncluded() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/example:lib")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/example/Test.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/example/lib-ijar.jar"))
                                    .setClassJar(gen("java/example/lib.jar")))
                            .addGeneratedJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/example/lib-gen.jar"))
                                    .setClassJar(gen("java/example/lib-gen.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    assertThat(
            result.libraries.values().stream().map(BlazeJavaWorkspaceImporterTest::libraryFileName))
        .containsExactly("lib-gen.jar");
  }

  /** Imports two binaries and a library. Only one binary should pass the package filter. */
  @Test
  public void testImportFilter() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .addDependency("//java/libraries/example:example")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/example_debug.jar"))
                                    .setClassJar(gen("java/apps/example/example_debug.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/libraries/example:example")
                    .setBuildFile(source("java/libraries/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/libraries/example/SharedActivity.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/libraries/example/AndroidManifest.xml"))
                            .addResource(source("java/libraries/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.libraries.example"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/libraries/example/example.jar"))
                                    .setClassJar(gen("java/libraries/example/example.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/com/dontimport:example_debug")
                    .setBuildFile(source("java/com/dontimport/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/com/dontimport/MainActivity.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/com/dontimport/AndroidManifest.xml"))
                            .addResource(source("java/com/dontimport/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.dontimport"))
                    .addDependency("//java/com/dontimport:sometarget")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/com/dontimport/example_debug.jar"))
                                    .setClassJar(gen("java/com/dontimport/example_debug.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build());
    assertThat(result.javaSourceFiles)
        .containsExactly(source("java/apps/example/MainActivity.java"));
  }

  /** Import a project and its tests */
  @Test
  public void testProjectAndTests() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .add(ListSection.builder(TestSourceSection.KEY).add(new Glob("javatests/*")))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .addSource(source("java/apps/example/subdir/SubdirHelper.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/example_debug.jar"))
                                    .setClassJar(gen("java/apps/example/example_debug.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//javatests/apps/example:example")
                    .setBuildFile(source("javatests/apps/example/BUILD"))
                    .setKind("android_test")
                    .addSource(source("javatests/apps/example/ExampleTests.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .addDependency("//java/apps/example:example_debug")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("javatests/apps/example/example.jar"))
                                    .setClassJar(gen("javatests/apps/example/example.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build(),
            BlazeContentEntry.builder("/root/javatests/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/javatests/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build());
  }

  /** Test library with a source jar */
  @Test
  public void testLibraryWithSourceJar() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .addSource(source("java/apps/example/subdir/SubdirHelper.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(gen("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .addDependency("//thirdparty/some/library:library")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/example_debug.jar"))
                                    .setClassJar(gen("java/apps/example/example_debug.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//thirdparty/some/library:library")
                    .setBuildFile(source("/thirdparty/some/library/BUILD"))
                    .setKind("java_import")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("thirdparty/some/library.jar"))
                                    .setClassJar(gen("thirdparty/some/library.jar"))
                                    .addSourceJar(gen("thirdparty/some/library.srcjar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();

    BlazeJarLibrary library = findLibrary(result.libraries, "library.jar");
    assertNotNull(library);
    assertThat(library.libraryArtifact.getSourceJars()).isNotEmpty();
  }

  /** Test a project with a java test rule */
  @Test
  public void testJavaTestTarget() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .add(ListSection.builder(TestSourceSection.KEY).add(new Glob("javatests/*")))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .addSource(source("java/apps/example/subdir/SubdirHelper.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/example_debug.jar"))
                                    .setClassJar(gen("java/apps/example/example_debug.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//javatests/apps/example:example")
                    .setBuildFile(source("javatests/apps/example/BUILD"))
                    .setKind("java_test")
                    .addSource(source("javatests/apps/example/ExampleTests.java"))
                    .addDependency("//java/apps/example:example_debug")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("javatests/apps/example/example.jar"))
                                    .setClassJar(gen("javatests/apps/example/example.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build(),
            BlazeContentEntry.builder("/root/javatests/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/javatests/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build());
  }

  /*
   * Test that the non-android libraries can be imported.
   */
  @Test
  public void testNormalJavaLibraryPackage() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("java/library/something"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .addSource(source("java/apps/example/subdir/SubdirHelper.java"))
                    .setJavaInfo(JavaIdeInfo.builder())
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .addDependency("//java/library/something:something"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/library/something:something")
                    .setBuildFile(source("java/library/something/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/library/something/SomeJavaFile.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/library/something/something.jar"))
                                    .setClassJar(gen("java/library/something/something.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build(),
            BlazeContentEntry.builder("/root/java/library/something")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/library/something")
                        .setPackagePrefix("library.something")
                        .build())
                .build());
  }

  @Test
  public void testImportTargetOutputTag() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("lib")))
                    .add(DirectoryEntry.include(new WorkspacePath("lib2"))))
            .build();

    TargetMapBuilder response =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//lib:lib")
                    .setBuildFile(source("lib/BUILD"))
                    .setKind("java_library")
                    .addSource(source("lib/Lib.java"))
                    .addDependency("//lib2:lib2")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("lib/lib.jar"))
                                    .setClassJar(gen("lib/lib.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//lib2:lib2")
                    .setBuildFile(source("lib2/BUILD"))
                    .setKind("java_library")
                    .addSource(source("lib2/Lib2.java"))
                    .addTag("intellij-import-target-output")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("lib2/lib2.jar"))
                                    .setClassJar(gen("lib2/lib2.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, response, projectView);
    errorCollector.assertNoIssues();
    assertThat(result.libraries).hasSize(1);
  }

  @Test
  public void testImportAsLibraryTagLegacy() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("lib")))
                    .add(DirectoryEntry.include(new WorkspacePath("lib2"))))
            .build();

    TargetMapBuilder response =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//lib:lib")
                    .setBuildFile(source("lib/BUILD"))
                    .setKind("java_library")
                    .addSource(source("lib/Lib.java"))
                    .addDependency("//lib2:lib2")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("lib/lib.jar"))
                                    .setClassJar(gen("lib/lib.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//lib2:lib2")
                    .setBuildFile(source("lib2/BUILD"))
                    .setKind("java_library")
                    .addSource(source("lib2/Lib2.java"))
                    .addTag("aswb-import-as-library")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("lib2/lib2.jar"))
                                    .setClassJar(gen("lib2/lib2.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, response, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.libraries).hasSize(1);
  }

  @Test
  public void testMultipleImportOfJarsGetMerged() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("lib"))))
            .build();

    TargetMapBuilder response =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//lib:libsource")
                    .setBuildFile(source("lib/BUILD"))
                    .setKind("java_library")
                    .addSource(source("lib/Source.java"))
                    .setJavaInfo(JavaIdeInfo.builder())
                    .addDependency("//lib:lib0")
                    .addDependency("//lib:lib1"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//lib:lib0")
                    .setBuildFile(source("lib/BUILD"))
                    .setKind("java_import")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(source("lib/lib.jar"))
                                    .setClassJar(source("lib/lib.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//lib:lib1")
                    .setBuildFile(source("lib/BUILD"))
                    .setKind("java_import")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(source("lib/lib.jar"))
                                    .setClassJar(source("lib/lib.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, response, projectView);
    errorCollector.assertNoIssues();
    assertThat(result.libraries).hasSize(1); // The libraries were merged
  }

  @Test
  public void testTargetWithOnlyGeneratedSourcesIsAddedAsLibrary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("import"))))
            .build();

    TargetMapBuilder response =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:lib")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("android_library")
                    .addSource(source("import/Lib.java"))
                    .setJavaInfo(JavaIdeInfo.builder())
                    .addDependency("//import:import")
                    .addDependency("//import:import_android"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:import")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("java_library")
                    .addSource(gen("import/GenSource.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("import/import.jar"))
                                    .setClassJar(gen("import/import.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:import_android")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("android_library")
                    .addSource(gen("import/GenSource.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("import/import_android.jar"))
                                    .setClassJar(gen("import/import_android.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, response, projectView);
    errorCollector.assertNoIssues();

    assertThat(findLibrary(result.libraries, "import.jar")).isNotNull();
    assertThat(findLibrary(result.libraries, "import_android.jar")).isNotNull();
  }

  @Test
  public void testTargetWithMixedGeneratedSourcesAddsFilteredGenJar() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("import"))))
            .build();

    TargetMapBuilder response =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:lib")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("java_library")
                    .addSource(source("import/Import.java"))
                    .addSource(gen("import/Import.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .setFilteredGenJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("import/filtered-gen.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, response, projectView);
    errorCollector.assertNoIssues();
    assertThat(findLibrary(result.libraries, "filtered-gen.jar")).isNotNull();
  }

  @Test
  public void testTargetWithOnlySourceJarAsSourceAddedAsLibrary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("import"))))
            .build();

    TargetMapBuilder response =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:lib")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("android_library")
                    .addSource(source("import/Lib.java"))
                    .setJavaInfo(JavaIdeInfo.builder())
                    .addDependency("//import:import")
                    .addDependency("//import:import_android"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:import")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("java_library")
                    .addSource(gen("import/gen-src.jar"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("import/import.jar"))
                                    .setClassJar(gen("import/import.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, response, projectView);
    errorCollector.assertNoIssues();

    assertThat(findLibrary(result.libraries, "import.jar")).isNotNull();
  }

  @Test
  public void testImportTargetOutput() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("import"))))
            .add(
                ListSection.builder(ImportTargetOutputSection.KEY)
                    .add(Label.create("//import:import")))
            .build();

    TargetMapBuilder response =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:lib")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("java_library")
                    .addSource(source("import/Lib.java"))
                    .setJavaInfo(JavaIdeInfo.builder())
                    .addDependency("//import:import"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:import")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("java_library")
                    .addSource(source("import/Import.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("import/import.jar"))
                                    .setClassJar(gen("import/import.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, response, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.libraries).isNotEmpty();
  }

  private TargetMapBuilder targetMapForJdepsSuite() {
    return TargetMapBuilder.builder()
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel("//java/apps/example:example_debug")
                .setBuildFile(source("java/apps/example/BUILD"))
                .setKind("java_library")
                .addSource(source("java/apps/example/Test.java"))
                .setJavaInfo(JavaIdeInfo.builder())
                .addDependency("//thirdparty/a:a"))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel("//thirdparty/a:a")
                .setKind("java_library")
                .addSource(source("thirdparty/a/A.java"))
                .setBuildFile(source("third_party/a/BUILD"))
                .addDependency("//thirdparty/b:b")
                .setJavaInfo(
                    JavaIdeInfo.builder()
                        .addJar(
                            LibraryArtifact.builder()
                                .setInterfaceJar(gen("thirdparty/a.jar"))
                                .setClassJar(gen("thirdparty/a.jar"))
                                .addSourceJar(gen("thirdparty/a.srcjar")))))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel("//thirdparty/b:b")
                .setKind("java_library")
                .addSource(source("thirdparty/b/B.java"))
                .setBuildFile(source("third_party/b/BUILD"))
                .addDependency("//thirdparty/c:c")
                .setJavaInfo(
                    JavaIdeInfo.builder()
                        .addJar(
                            LibraryArtifact.builder()
                                .setInterfaceJar(gen("thirdparty/b.jar"))
                                .setClassJar(gen("thirdparty/b.jar"))
                                .addSourceJar(gen("thirdparty/b.srcjar")))))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel("//thirdparty/c:c")
                .setKind("java_library")
                .addSource(source("thirdparty/c/C.java"))
                .setBuildFile(source("third_party/c/BUILD"))
                .setJavaInfo(
                    JavaIdeInfo.builder()
                        .addJar(
                            LibraryArtifact.builder()
                                .setInterfaceJar(gen("thirdparty/c.jar"))
                                .setClassJar(gen("thirdparty/c.jar"))
                                .addSourceJar(gen("thirdparty/c.srcjar")))));
  }

  @Test
  public void testLibraryDependenciesWithJdepsSet() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .build();
    TargetMapBuilder targetMapBuilder = targetMapForJdepsSuite();
    jdepsMap.put(
        TargetKey.forPlainTarget(Label.create("//java/apps/example:example_debug")),
        Lists.newArrayList(jdepsPath("thirdparty/a.jar"), jdepsPath("thirdparty/c.jar")));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    assertThat(
            result.libraries.values().stream().map(BlazeJavaWorkspaceImporterTest::libraryFileName))
        .containsExactly("a.jar", "c.jar");
  }

  @Test
  public void testLibraryDependenciesWithJdepsButNoTarget() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example"))))
            .build();
    TargetMapBuilder targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/apps/example/Test.java"))
                    .setJavaInfo(JavaIdeInfo.builder())
                    .addDependency("//thirdparty/a:a"));
    jdepsMap.put(
        TargetKey.forPlainTarget(Label.create("//java/apps/example:example_debug")),
        Lists.newArrayList(jdepsPath("thirdparty/a.jar"), jdepsPath("thirdparty/c.jar")));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    assertThat(
            result.libraries.values().stream().map(BlazeJavaWorkspaceImporterTest::libraryFileName))
        .containsExactly("a.jar", "c.jar");
  }

  @Test
  public void testEmptyLibraryExcluded() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example"))))
            .build();
    TargetMapBuilder targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/apps/example/Test.java"))
                    .setJavaInfo(JavaIdeInfo.builder())
                    .addDependency("//thirdparty/a:a"));
    jdepsMap.put(
        TargetKey.forPlainTarget(Label.create("//java/apps/example:example_debug")),
        Lists.newArrayList(jdepsPath("thirdparty/a.jar"), jdepsPath("thirdparty/c.jar")));

    // Set fileModifiedTime so they don't default to being included. The exact time doesn't matter
    // as long as it is not 0
    fileOperationProvider.setFileModifiedTime(new File("/", "thirdparty/a.jar"), 176400L);

    fileOperationProvider.setFileSize("thirdparty/c.jar", 22L);
    fileOperationProvider.setFileModifiedTime(new File("/", "thirdparty/c.jar"), 176400L);

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    assertThat(
            result.libraries.values().stream()
                .map(BlazeJavaWorkspaceImporterTest::libraryFileName)
                .collect(Collectors.toList()))
        .containsExactly("a.jar");
  }

  @Test
  public void testJarsGeneratedFromProjectSourcesExcluded() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("thirdparty"))))
            .build();
    TargetMapBuilder targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/apps/example/Test.java"))
                    .setJavaInfo(JavaIdeInfo.builder())
                    .addDependency("//thirdparty/a:a"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//thirdparty:jars")
                    .setKind("java_library")
                    .addSource(source("thirdparty/Other.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(LibraryArtifact.builder().setClassJar(gen("thirdparty/a.jar")))
                            .addJar(LibraryArtifact.builder().setClassJar(gen("thirdparty/c.jar"))))
                    .build());
    jdepsMap.put(
        TargetKey.forPlainTarget(Label.create("//java/apps/example:example_debug")),
        Lists.newArrayList(jdepsPath("thirdparty/a.jar"), jdepsPath("thirdparty/c.jar")));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    assertThat(result.libraries).isEmpty();
  }

  @Test
  public void testJarsGeneratedFromProjectProtoLibrariesIncluded() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example"))))
            .build();
    TargetMapBuilder targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:foo_java_proto")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("java_proto_library")
                    .addDependency("//java/apps/example:foo_proto"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:foo_proto")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("proto_library")
                    .addSource(source("java/apps/example/foo.proto"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setClassJar(gen("java/apps/example/foo.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    assertThat(
            result.libraries.values().stream()
                .map(BlazeJavaWorkspaceImporterTest::libraryFileName)
                .collect(Collectors.toList()))
        .containsExactly("foo.jar");
  }

  @Test
  public void testJarsGeneratedFromExcludedTargetsNotAddedToProjectLibraries() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example"))))
            .build();
    TargetMapBuilder targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/apps/example/Test.java"))
                    .setJavaInfo(JavaIdeInfo.builder())
                    .addDependency("//thirdparty:jars"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//thirdparty:jars")
                    .setKind("java_library")
                    .addTag("intellij-provided-by-sdk")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(LibraryArtifact.builder().setClassJar(gen("thirdparty/a.jar"))))
                    .build());
    jdepsMap.put(
        TargetKey.forPlainTarget(Label.create("//java/apps/example:example_debug")),
        Lists.newArrayList(jdepsPath("thirdparty/a.jar"), jdepsPath("thirdparty/c.jar")));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    assertThat(
            result.libraries.values().stream().map(BlazeJavaWorkspaceImporterTest::libraryFileName))
        .containsExactly("c.jar");
  }

  @Test
  public void
      testLibraryDependenciesWithJdepsReportingNothingShouldStillIncludeDirectDepsIfInWorkingSet() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .build();
    TargetMapBuilder targetMapBuilder = targetMapForJdepsSuite();
    workingSet =
        new JavaWorkingSet(
            workspaceRoot,
            new WorkingSet(
                ImmutableList.of(new WorkspacePath("java/apps/example/Test.java")),
                ImmutableList.of(),
                ImmutableList.of()),
            Predicate.isEqual("BUILD"));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    assertThat(
            result.libraries.values().stream().map(BlazeJavaWorkspaceImporterTest::libraryFileName))
        .containsExactly("a.jar");
  }

  @Test
  public void testLibraryDepsWithJdepsReportingZeroShouldNotIncludeDirectDepsIfNotInWorkingSet() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .build();
    TargetMapBuilder targetMapBuilder = targetMapForJdepsSuite();
    workingSet =
        new JavaWorkingSet(
            workspaceRoot,
            new WorkingSet(ImmutableList.of(), ImmutableList.of(), ImmutableList.of()),
            Predicate.isEqual("BUILD"));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    assertThat(
            result.libraries.values().stream()
                .map(BlazeJavaWorkspaceImporterTest::libraryFileName)
                .collect(Collectors.toList()))
        .isEmpty();
  }

  @Test
  public void testLibrariesAreOrderedCorrectly() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .build();
    TargetMapBuilder targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/apps/example/Test.java"))
                    .setJavaInfo(JavaIdeInfo.builder())
                    .addDependency("//thirdparty/a:a")
                    .addDependency("//thirdparty/b:b")
                    .addDependency("//thirdparty/c:c")
                    .addDependency("//thirdparty/d:d"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//thirdparty/d:d")
                    .setKind("java_library")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder().setClassJar(gen("thirdparty/d.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//javatests/apps/example:example_debug_test")
                    .setBuildFile(source("javatests/apps/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("javatests/apps/example/Test.java"))
                    .setJavaInfo(JavaIdeInfo.builder())
                    .addDependency("//thirdparty/f:f")
                    .addDependency("//thirdparty/g:g")
                    .addDependency("//thirdparty/h:h")
                    .addDependency("//thirdparty/i:i"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//thirdparty/i:i")
                    .setKind("java_library")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder().setClassJar(gen("thirdparty/i.jar")))));

    jdepsMap.put(
        TargetKey.forPlainTarget(Label.create("//java/apps/example:example_debug")),
        Lists.newArrayList(
            jdepsPath("thirdparty/a.jar"),
            jdepsPath("thirdparty/b.jar"),
            jdepsPath("thirdparty/c.jar"),
            jdepsPath("thirdparty/d.jar")));

    jdepsMap.put(
        TargetKey.forPlainTarget(Label.create("//javatests/apps/example:example_debug_test")),
        Lists.newArrayList(
            jdepsPath("thirdparty/f.jar"),
            jdepsPath("thirdparty/g.jar"),
            jdepsPath("thirdparty/h.jar"),
            jdepsPath("thirdparty/i.jar")));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    errorCollector.assertNoIssues();
    assertThat(result.libraries).hasSize(8);

    List<String> importedLibs =
        result.libraries.values().stream()
            .map(BlazeJavaWorkspaceImporterTest::libraryFileName)
            .collect(Collectors.toList());
    List<String> jdepLibs = importedLibs.subList(0, 6);
    List<String> directDepsLibs = importedLibs.subList(6, importedLibs.size());

    // Need not be ordered within sublist.
    assertThat(jdepLibs).containsExactly("a.jar", "b.jar", "c.jar", "f.jar", "g.jar", "h.jar");
    assertThat(directDepsLibs).containsExactly("d.jar", "i.jar");
  }

  /*
   * Test the exclude_target section
   */
  @Test
  public void testExcludeTarget() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example"))))
            .add(
                ListSection.builder(ExcludeTargetSection.KEY)
                    .add(Label.create("//java/apps/example:example")))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/apps/example/Example.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder().setInterfaceJar(gen("example.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.javaSourceFiles).isEmpty();
  }

  /*
   * Test the intellij-exclude-target tag
   */
  @Test
  public void testExcludeTargetTag() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example")
                    .addTag(Tags.TARGET_TAG_EXCLUDE_TARGET)
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/apps/example/Example.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder().setInterfaceJar(gen("example.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.javaSourceFiles).isEmpty();
  }

  /** Test that the non-android libraries can be imported. */
  @Test
  public void testImporterWorksWithWorkspaceRootDirectoryIncluded() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath(""))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .addSource(source("java/apps/example/subdir/SubdirHelper.java"))
                    .setJavaInfo(JavaIdeInfo.builder())
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .addDependency("//java/library/something:something"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/library/something:something")
                    .setBuildFile(source("java/library/something/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/library/something/SomeJavaFile.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/library/something/something.jar"))
                                    .setClassJar(gen("java/library/something/something.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root")
                .addSource(BlazeSourceDirectory.builder("/root/java").build())
                .build());
  }

  @Test
  public void testLanguageLevelIsReadFromToolchain() {
    ProjectView projectView = ProjectView.builder().build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java:toolchain")
                    .setBuildFile(source("java/BUILD"))
                    .setKind("java_toolchain")
                    .setJavaToolchainIdeInfo(
                        JavaToolchainIdeInfo.builder()
                            .setSourceVersion("8")
                            .setTargetVersion("8")));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    assertThat(result.sourceVersion).isEqualTo("8");
  }

  @Test
  public void testSyncAugmenter_notAttachGenerateJar() {
    augmenters.registerExtension(
        new BlazeJavaSyncAugmenter() {

          @Override
          public void addJarsForSourceTarget(
              WorkspaceLanguageSettings workspaceLanguageSettings,
              ProjectViewSet projectViewSet,
              TargetIdeInfo target,
              Collection<BlazeJarLibrary> jars,
              Collection<BlazeJarLibrary> genJars) {
            if (target.getKey().getLabel().equals(Label.create("//java/example:kotlinlib"))) {
              jars.add(
                  new BlazeJarLibrary(
                      LibraryArtifact.builder().setInterfaceJar(gen("source.jar")).build(),
                      target.getKey()));
            }
          }

          @Override
          public boolean shouldAttachGenJar(TargetIdeInfo target) {
            return !target.getKey().getLabel().equals(Label.create("//java/example:kotlinlib"));
          }
        });

    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/example:kotlinlib")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("Source.java"))
                    .addDependency("//java/lib:lib")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addGeneratedJar(
                                LibraryArtifact.builder().setInterfaceJar(gen("generated.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/lib:lib")
                    .setBuildFile(source("java/lib/BUILD"))
                    .setKind("java_library")
                    .addSource(source("Lib.java"))
                    .setJavaInfo(JavaIdeInfo.builder()));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    assertThat(
            result.libraries.values().stream().map(BlazeJavaWorkspaceImporterTest::libraryFileName))
        .containsExactly("source.jar");
  }

  @Test
  public void testSyncAugmenter_attachGeneratedJar() {
    augmenters.registerExtension(
        (workspaceLanguageSettings, projectViewSet, target, jars, genJars) -> {
          if (target.getKey().getLabel().equals(Label.create("//java/example:source"))) {
            jars.add(
                new BlazeJarLibrary(
                    LibraryArtifact.builder().setInterfaceJar(gen("source.jar")).build(),
                    target.getKey()));
          }
        });

    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/example:source")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("Source.java"))
                    .addDependency("//java/lib:lib")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addGeneratedJar(
                                LibraryArtifact.builder().setInterfaceJar(gen("generated.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/lib:lib")
                    .setBuildFile(source("java/lib/BUILD"))
                    .setKind("java_library")
                    .addSource(source("Lib.java"))
                    .setJavaInfo(JavaIdeInfo.builder()));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    assertThat(
            result.libraries.values().stream()
                .map(BlazeJavaWorkspaceImporterTest::libraryFileName)
                .collect(Collectors.toList()))
        .containsExactly("source.jar", "generated.jar");
  }

  /** Test that the plugin processor jars are included. */
  @Test
  public void testPluginProcessorJarsAreIncluded() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath(""))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .addSource(source("java/apps/example/subdir/SubdirHelper.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addPluginProcessorJars(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/example/lint.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(
            result.pluginProcessorJars.stream()
                .map(
                    artifactLocation ->
                        new File(artifactLocation.getExecutionRootRelativePath()).getName()))
        .containsExactly("lint.jar");
  }

  /** Test that only top level targets' the plugin processor jars are included. */
  @Test
  public void testOnlyTopLevelPluginProcessorJarsAreIncluded() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .addSource(source("java/apps/example/subdir/SubdirHelper.java"))
                    .addDependency("//third_party:deps")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addPluginProcessorJars(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/example/lint.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//third_party:deps")
                    .setKind("android_library")
                    .addSource(source("third_party/deps.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addPluginProcessorJars(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("third_party/lint.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(
            result.pluginProcessorJars.stream()
                .map(
                    artifactLocation ->
                        new File(artifactLocation.getExecutionRootRelativePath()).getName()))
        .containsExactly("lint.jar");
  }

  /* Utility methods */

  private static String libraryFileName(BlazeJarLibrary library) {
    return new File(library.libraryArtifact.jarForIntellijLibrary().getExecutionRootRelativePath())
        .getName();
  }

  @Nullable
  private static BlazeJarLibrary findLibrary(
      Map<LibraryKey, BlazeJarLibrary> libraries, String libraryName) {
    for (BlazeJarLibrary library : libraries.values()) {
      if (library
          .libraryArtifact
          .jarForIntellijLibrary()
          .getExecutionRootRelativePath()
          .endsWith(libraryName)) {
        return library;
      }
    }
    return null;
  }

  private static ArtifactLocation source(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }

  private static ArtifactLocation gen(String relativePath) {
    return ArtifactLocation.builder()
        .setRootExecutionPathFragment(FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT)
        .setRelativePath(relativePath)
        .setIsSource(false)
        .build();
  }

  private static String jdepsPath(String relativePath) {
    return FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT + "/" + relativePath;
  }

  private static class MockJarCache extends JarCache {

    public MockJarCache(Project project) {
      super(project);
    }

    @Override
    @Nullable
    public File getCachedJar(ArtifactLocationDecoder decoder, BlazeJarLibrary library) {
      return null;
    }
  }
}
