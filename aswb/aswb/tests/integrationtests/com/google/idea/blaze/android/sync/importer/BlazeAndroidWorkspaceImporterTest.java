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
package com.google.idea.blaze.android.sync.importer;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAarTarget.aar_import;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;
import static com.google.idea.blaze.android.targetmapbuilder.NbTargetBuilder.targetMap;
import static com.intellij.testFramework.UsefulTestCase.assertSameElements;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.projectview.GeneratedAndroidResourcesSection;
import com.google.idea.blaze.android.projectview.GenfilesPath;
import com.google.idea.blaze.android.sync.BlazeAndroidJavaSyncAugmenter;
import com.google.idea.blaze.android.sync.BlazeAndroidLibrarySource;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.java.sync.importer.BlazeJavaWorkspaceImporter;
import com.google.idea.blaze.java.sync.importer.JavaSourceFilter;
import com.google.idea.blaze.java.sync.jdeps.MockJdepsMap;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.workingset.JavaWorkingSet;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for BlazeAndroidWorkspaceImporter */
@RunWith(JUnit4.class)
public class BlazeAndroidWorkspaceImporterTest extends BlazeAndroidIntegrationTestCase {

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

  private BlazeContext context;
  private final MockJdepsMap jdepsMap = new MockJdepsMap();
  private final JavaWorkingSet workingSet =
      new JavaWorkingSet(
          workspaceRoot,
          new WorkingSet(ImmutableList.of(), ImmutableList.of(), ImmutableList.of()),
          Predicate.isEqual("BUILD"));
  private final WorkspaceLanguageSettings workspaceLanguageSettings =
      new WorkspaceLanguageSettings(
          WorkspaceType.ANDROID, ImmutableSet.of(LanguageClass.ANDROID, LanguageClass.JAVA));
  private MockExperimentService experimentService;

  @Before
  public void importerSetUp() {
    experimentService = new MockExperimentService();
    registerApplicationComponent(ExperimentService.class, experimentService);

    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    MockFileOperationProvider mockFileOperationProvider = new MockFileOperationProvider();
    registerApplicationService(FileOperationProvider.class, mockFileOperationProvider);

    context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, errorCollector);
  }

  private BlazeAndroidImportResult importWorkspace(
      WorkspaceRoot workspaceRoot, TargetMap targetMap, ProjectView projectView) {
    ProjectViewSet projectViewSet = ProjectViewSet.builder().add(projectView).build();
    BlazeAndroidWorkspaceImporter workspaceImporter =
        new BlazeAndroidWorkspaceImporter(
            getProject(),
            context,
            BlazeImportInput.forProject(
                getProject(), workspaceRoot, projectViewSet, targetMap, FAKE_ARTIFACT_DECODER));

    return workspaceImporter.importWorkspace();
  }

  private BlazeJavaImportResult importJavaWorkspace(
      WorkspaceRoot workspaceRoot, TargetMap targetMap, ProjectView projectView) {

    BuildSystemName buildSystemName = Blaze.getBuildSystemName(getProject());
    ProjectViewSet projectViewSet = ProjectViewSet.builder().add(projectView).build();
    JavaSourceFilter sourceFilter =
        new JavaSourceFilter(buildSystemName, workspaceRoot, projectViewSet, targetMap);
    BlazeJavaWorkspaceImporter blazeWorkspaceImporter =
        new BlazeJavaWorkspaceImporter(
            getProject(),
            workspaceRoot,
            projectViewSet,
            workspaceLanguageSettings,
            targetMap,
            sourceFilter,
            jdepsMap,
            workingSet,
            FAKE_ARTIFACT_DECODER,
            /* oldSyncState= */ null);

    return blazeWorkspaceImporter.importWorkspace(context);
  }

  @Test
  public void androidResourceImport_manifestOnlyAarEnabled_manifestOnlyAarImported() {
    experimentService.setExperiment(BlazeAndroidWorkspaceImporter.includeManifestOnlyAars, true);
    /**
     * The aspect generates AARs for targets as long as there's an android manifest. These AARs are
     * named with the "-manifest-only.aar" suffix. The experiment {@link
     * BlazeAndroidWorkspaceImporter.INCLUDE_MANIFEST_ONLY_AARS} controls whether or not to use
     * these manifest-only AARs as resource dependencies. Using them will generate a more complete
     * resource dep graph, but it also means more resource modules generated.
     */
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("example")))
                    .add(DirectoryEntry.include(new WorkspacePath("example1"))))
            .build();

    TargetMap targetMap =
        targetMap(
            android_binary("//example1:lib")
                .src("MainActivity.java")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//aarLibrary:lib"),
            android_library("//aarLibrary:lib")
                .src("MainActivity.java")
                .manifest("AndroidManifest.xml"));

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    AndroidResourceModule expectedAndroidResourceModule =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//example1:lib")))
            .addResourceAndTransitiveResource(source("example1/res"))
            .addResourceLibraryKey(
                LibraryKey.libraryNameFromArtifactLocation(
                    source("aarLibrary/resources-manifest-only.aar")))
            .addTransitiveResourceDependency("//aarLibrary:lib")
            .build();

    assertThat(result.aarLibraries.values())
        .containsExactly(
            new AarLibrary(source("aarLibrary/resources-manifest-only.aar"), "aarLibrary"));
    assertThat(result.androidResourceModules).containsExactly(expectedAndroidResourceModule);
  }

  @Test
  public void androidResourceImport_manifestOnlyAarDisabled_manifestOnlyAarNotImported() {
    experimentService.setExperiment(BlazeAndroidWorkspaceImporter.includeManifestOnlyAars, false);
    /**
     * The aspect generates AARs for targets as long as there's an android manifest. These AARs are
     * named with the "-manifest-only.aar" suffix. The experiment {@link
     * BlazeAndroidWorkspaceImporter.INCLUDE_MANIFEST_ONLY_AARS} controls whether or not to use
     * these manifest-only AARs as resource dependencies. Using them will generate a more complete
     * resource dep graph, but it also means more resource modules generated.
     */
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("example")))
                    .add(DirectoryEntry.include(new WorkspacePath("example1"))))
            .build();

    TargetMap targetMap =
        targetMap(
            android_binary("//example1:lib")
                .src("MainActivity.java")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//aarLibrary:lib"),
            android_library("//aarLibrary:lib")
                .src("MainActivity.java")
                .manifest("AndroidManifest.xml"));

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    AndroidResourceModule expectedAndroidResourceModule =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//example1:lib")))
            .addResourceAndTransitiveResource(source("example1/res"))
            .addTransitiveResourceDependency("//aarLibrary:lib")
            .build();

    assertThat(result.aarLibraries.values()).isEmpty();
    assertThat(result.androidResourceModules).containsExactly(expectedAndroidResourceModule);
  }

  /**
   * When multiple targets reference the same AAR generated by another library target, the AAR
   * should be shared between the targets and not duplicated.
   */
  @Test
  public void androidResourceImport_multipleTargetsUsesSameAarLibrary_aarLibraryNotDuplicated() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("example")))
                    .add(DirectoryEntry.include(new WorkspacePath("example1"))))
            .build();

    // //example:lib and and //example1:lib shares the same resources.aar from //aarLibrary:lib
    TargetMap targetMap =
        targetMap(
            android_binary("//example1:lib")
                .src("MainActivity.java")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//aarLibrary:lib"),
            android_binary("//example:lib")
                .src("MainActivity.java")
                .manifest("AndroidManifest.xml")
                .res_folder("//aarLibrary/res", "//aarLibrary/resources.aar"),
            android_library("//aarLibrary:lib")
                .src("MainActivity.java")
                .manifest("AndroidManifest.xml")
                .res_folder("res", "resources.aar"));

    // Assert the AndroidResourceModules created for example:lib and example1:lib point to the same
    // aarLibrary/resources.aar.  A.k.a. it's not duplicated since they refer ot the same one.
    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    AndroidResourceModule expectedAndroidResourceModule1 =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//example:lib")))
            .addResourceLibraryKey(
                LibraryKey.libraryNameFromArtifactLocation(source("aarLibrary/resources.aar")))
            .build();
    AndroidResourceModule expectedAndroidResourceModule2 =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//example1:lib")))
            .addResourceAndTransitiveResource(source("example1/res"))
            .addResourceLibraryKey(
                LibraryKey.libraryNameFromArtifactLocation(source("aarLibrary/resources.aar")))
            .addTransitiveResourceDependency("//aarLibrary:lib")
            .build();

    // There should only be one AAR, referenced by multiple targets.
    assertThat(result.aarLibraries.values())
        .containsExactly(new AarLibrary(source("aarLibrary/resources.aar"), "aarLibrary"));
    assertThat(result.androidResourceModules)
        .containsExactly(expectedAndroidResourceModule1, expectedAndroidResourceModule2);
  }

  /**
   * Test that multiple in-project packages that depend on the the same out-of-project
   * android_library will inherit the resources from the out-of-project android_library.
   */
  @Test
  public void androidResourceImport_resInheritanceFromCommonDep_createAarLibrary() {
    // if experiment variable is set to create AarLibrary
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .build();

    /*
    Deps are lib0 -> lib1 -> shared
              ^- example_debug -^
    */
    TargetMap targetMap =
        targetMap(
            android_library("//java/apps/example/lib0:lib0")
                .generated_jar("lib0.jar")
                .res("res")
                .src("SharedActivity.java")
                .manifest("AndroidManifest.xml")
                .dep("//java/apps/example/lib1:lib1"),
            android_library("//java/apps/example/lib1:lib1")
                .generated_jar("lib1.jar")
                .src("SharedActivity.java")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//java/libraries/shared:shared"),
            android_binary("//java/apps/example:example_debug")
                .generated_jar("example_debug.jar")
                .src("MainActivity.java")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//java/apps/example/lib0:lib0", "//java/libraries/shared:shared"),
            android_library("//java/libraries/shared:shared")
                .generated_jar("shared.jar")
                .src("SharedActivity.java")
                .manifest("AndroidManifest.xml")
                .res_folder("res", "resources.aar"));
    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);

    // Verify that all in-project AndroidResourceModules has java/libraries/shared/resources.aar
    // as a resource library key.
    AndroidResourceModule expectedAndroidResourceModule1 =
        AndroidResourceModule.builder(
                TargetKey.forPlainTarget(Label.create("//java/apps/example:example_debug")))
            .addResourceAndTransitiveResource(source("java/apps/example/res"))
            .addTransitiveResource(source("java/apps/example/lib0/res"))
            .addTransitiveResource(source("java/apps/example/lib1/res"))
            .addResourceLibraryKey(
                LibraryKey.libraryNameFromArtifactLocation(
                    source("java/libraries/shared/resources.aar")))
            .addTransitiveResourceDependency("//java/apps/example/lib0:lib0")
            .addTransitiveResourceDependency("//java/apps/example/lib1:lib1")
            .addTransitiveResourceDependency("//java/libraries/shared:shared")
            .build();
    AndroidResourceModule expectedAndroidResourceModule2 =
        AndroidResourceModule.builder(
                TargetKey.forPlainTarget(Label.create("//java/apps/example/lib0:lib0")))
            .addResourceAndTransitiveResource(source("java/apps/example/lib0/res"))
            .addTransitiveResource(source("java/apps/example/lib1/res"))
            .addResourceLibraryKey(
                LibraryKey.libraryNameFromArtifactLocation(
                    source("java/libraries/shared/resources.aar")))
            .addTransitiveResourceDependency("//java/apps/example/lib1:lib1")
            .addTransitiveResourceDependency("//java/libraries/shared:shared")
            .build();
    AndroidResourceModule expectedAndroidResourceModule3 =
        AndroidResourceModule.builder(
                TargetKey.forPlainTarget(Label.create("//java/apps/example/lib1:lib1")))
            .addResourceAndTransitiveResource(source("java/apps/example/lib1/res"))
            .addResourceLibraryKey(
                LibraryKey.libraryNameFromArtifactLocation(
                    source("java/libraries/shared/resources.aar")))
            .addTransitiveResourceDependency("//java/libraries/shared:shared")
            .build();
    errorCollector.assertNoIssues();

    // Note that result.androidResourceModules only return 3 objects because
    // result.androidResourceModules returns the AndroidResourceModules of in-project targets only.
    // The AndroidResourceModule for the shared library is still present in the resource dep graph.
    assertThat(result.androidResourceModules)
        .containsExactly(
            expectedAndroidResourceModule1,
            expectedAndroidResourceModule2,
            expectedAndroidResourceModule3);
  }

  /** AIDL jars generated by android targets should be added as a library. */
  @Test
  public void androidResourceImport_idlClassJar_isAddedAsLibrary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("example"))))
            .build();

    TargetMap targetMap =
        targetMap(
            android_binary("//example:lib")
                .src("MainActivity.java")
                .idl_jar("libidl.srcjar", "libidl.ijar"));

    // BlazeAndroidJavaSyncAugmenter is an extension.  We need to manually invoke it in this unit
    // test.
    BlazeAndroidJavaSyncAugmenter syncAugmenter = new BlazeAndroidJavaSyncAugmenter();
    List<BlazeJarLibrary> jars = Lists.newArrayList();
    List<BlazeJarLibrary> genJars = Lists.newArrayList();
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystemName.Blaze)
            .add(ProjectViewSet.builder().add(projectView).build())
            .build();
    ProjectViewSet projectViewSet = ProjectViewSet.builder().add(projectView).build();
    for (TargetIdeInfo target : targetMap.targets()) {
      if (importRoots.importAsSource(target.getKey().getLabel())) {
        syncAugmenter.addJarsForSourceTarget(
            workspaceLanguageSettings, projectViewSet, target, jars, genJars);
      }
    }
    assertThat(
            genJars.stream()
                .map(library -> library.libraryArtifact)
                .map(LibraryArtifact::getInterfaceJar)
                .map(artifactLocation -> new File(artifactLocation.getRelativePath()).getName()))
        .containsExactly("libidl.ijar");
  }

  /**
   * Android library targets outside of the project view should have their resources imported as
   * AARs if they have a transitive in-project dependent.
   */
  @Test
  public void resourceImport_resourceOutsideOfProjectView_createAarLibrary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();
    // java/example2 is out of project.  It's resources contained in resources.aar should be
    // imported into aarLibraries of workspace import results.
    TargetMap targetMap =
        targetMap(
            android_library("//java/example:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//java/example2:resources"),
            android_library("//java/example2:resources")
                .manifest("AndroidManifest.xml")
                .res_folder("res", "resources.aar"));
    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    errorCollector.assertNoIssues();
    ImmutableCollection<AarLibrary> library = result.aarLibraries.values();
    assertSameElements(library, new AarLibrary(source("java/example2/resources.aar"), "example2"));
  }

  /**
   * BlazeAndroidWorkspaceImporter needs to make sure there's only one AndroidResourceModule per
   * resource package name: discarding all but one module per name, or merging modules that share
   * the same resources.
   *
   * <p>With resource merging disabled, the target with the shortest label will be chosen among a
   * group of targets with the same resource package name. Others will be discarded.
   */
  @Test
  public void conflictingResourceRClasses_resourceMergingDisabled_picksBestResourceClass() {
    experimentService.setExperiment(MockBlazeAndroidWorkspaceImporter.mergeResourcesEnabled, false);
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    // Generate conflicting R classes by using the same resource package name for both targets.
    TargetMap targetMap =
        targetMap(
            android_library("//java/example:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .setResourceJavaPackage("com.google.android.example")
                .dep("//java/example2:resources"),
            android_library("//java/example:lib2")
                .manifest("AndroidManifest.xml")
                .res("res2")
                .setResourceJavaPackage("com.google.android.example"));

    AndroidResourceModule expectedAndroidResourceModule =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//java/example:lib")))
            .addResourceAndTransitiveResource(source("java/example/res"))
            .build();
    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    errorCollector.assertIssueContaining("Multiple R classes generated");

    assertThat(result.androidResourceModules).containsExactly(expectedAndroidResourceModule);
  }

  /**
   * BlazeAndroidWorkspaceImporter needs to make sure there's only one AndroidResourceModule per
   * resource package name: discarding all but one module per name, or merging modules that share
   * the same resources.
   *
   * <p>With resource merging enabled, the target with the shortest label will be chosen as the
   * canonical target that iwll contain the deps and resources of the other targets that share the
   * same resource package name.
   */
  @Test
  public void conflictingResourceRClasses_resourceMergingEnabled_mergesClassesIntoOne() {
    experimentService.setExperiment(MockBlazeAndroidWorkspaceImporter.mergeResourcesEnabled, true);
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    // Generate conflicting R classes by using the same resource package name for both targets.
    TargetMap targetMap =
        targetMap(
            android_library("//java/example:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .setResourceJavaPackage("com.google.android.example")
                .dep("//java/example2:resources"),
            android_library("//java/example:lib2")
                .manifest("AndroidManifest.xml")
                .res("res2")
                .setResourceJavaPackage("com.google.android.example"));

    AndroidResourceModule expectedAndroidResourceModule =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//java/example:lib")))
            .addSourceTarget(TargetKey.forPlainTarget(Label.create("//java/example:lib2")))
            .addResourceAndTransitiveResource(source("java/example/res"))
            .addResourceAndTransitiveResource(source("java/example/res2"))
            .build();
    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    errorCollector.assertIssueContaining("Multiple R classes generated");
    errorCollector.assertIssueContaining("Merging Resources...");

    assertThat(result.androidResourceModules).containsExactly(expectedAndroidResourceModule);
  }

  /**
   * Rentension filter for generated resources should prevent generated resources of matching paths
   * from being filtered out. (Filtered out means discarded from resources)
   */
  @Test
  public void generatedResourceRetentionFilter_retainsPassingResourceDependency() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    TargetMap targetMap =
        targetMap(
            android_library("//java/example:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//common_deps:lib"),
            android_library("//common_deps:lib")
                .manifest("AndroidManifest.xml")
                .generated_res("res"));

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    errorCollector.assertNoIssues();
    assertThat(result.androidResourceModules).hasSize(1);
    AndroidResourceModule androidResourceModule = result.androidResourceModules.get(0);
    assertThat(androidResourceModule.transitiveResourceDependencies)
        .containsExactly(TargetKey.forPlainTarget(Label.create("//common_deps:lib")));
  }

  /**
   * If there are generated and non-generated resources of the same name, a generated resources
   * warning should still appear.
   */
  @Test
  public void generatedResources_mixingGeneratedAndNonGeneratedSources_raisesError() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    // Both source reosurces and generated resources are called "res"
    TargetMap targetMap =
        targetMap(
            android_library("//java/example:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .generated_res("res"));

    importWorkspace(workspaceRoot, targetMap, projectView);
    errorCollector.assertIssueContaining("Dropping 1 generated resource");
  }

  /**
   * If there are generated and non-generated resource of the same name but the paths are allowed by
   * the GeneratedAndroidResourcesSection filter, then there should be no errors.
   */
  @Test
  public void generatedResources_mixingGeneratedAndNonGeneratedSourcesForAllowedDirs_noIssues() {
    // Note that java/example/res path is an allowed path under GeneratedAndroidResourceSection.
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .add(
                ListSection.builder(GeneratedAndroidResourcesSection.KEY)
                    .add(new GenfilesPath("java/example/res")))
            .build();

    TargetMap targetMap =
        targetMap(
            android_library("//java/example:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .generated_res("res"));

    AndroidResourceModule expectedAndroidResourceModule =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//java/example:lib")))
            .addResourceAndTransitiveResource(source("java/example/res"))
            .addResourceAndTransitiveResource(gen("java/example/res"))
            .build();

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    errorCollector.assertNoIssues();
    assertThat(result.androidResourceModules).containsExactly(expectedAndroidResourceModule);
  }

  /**
   * Test that mixing generated vs non-generated resources with partially allowed paths generate the
   * correct warning message.
   */
  @Test
  public void generatedResources_mixingDirectoryNamesAndAllowedDirs_showsCorrectWarning() {
    // Note that "java/translation-only" only contains translations don't count towards generated
    // resources.
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("java/example2")))
                    .add(DirectoryEntry.include(new WorkspacePath("java/translation-only"))))
            .add(
                ListSection.builder(GeneratedAndroidResourcesSection.KEY)
                    .add(new GenfilesPath("java/example/res"))
                    .add(new GenfilesPath("unused/allowed/path/res")))
            .build();

    TargetMap targetMap =
        targetMap(
            android_library("//java/example:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .generated_res("res"),
            android_library("//java/example2:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .generated_res("res"),
            android_library("//java/translation-only:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .generated_res("res"));
    importWorkspace(workspaceRoot, targetMap, projectView);

    // There should only be a generated resources warning for java/example2/res because
    // java/translation-only only contains translations.
    String expectedString1 =
        "Dropping 1 generated resource directories.\n"
            + "R classes will not contain resources from these directories.\n"
            + "Double-click to add to project view if needed to resolve references.";
    String expectedString2 =
        "Dropping generated resource directory "
            + String.format("'%s/java/example2/res'", FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT)
            + " w/ 2 subdirs";
    String expectedString3 =
        "1 unused entries in project view section \"generated_android_resource_directories\":\n"
            + "unused/allowed/path/res";

    errorCollector.assertIssues(expectedString1, expectedString2, expectedString3);
  }

  /** Generated resource that contain only translation files should not be dropped. */
  @Test
  public void generatedResources_mixingGeneratedAndNonGeneratedSourcesForTranslationDir_noIssues() {
    // Note that "java/translation-only" only contains translations don't count towards generated
    // resources.
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/translation-only"))))
            .build();

    TargetMap targetMap =
        targetMap(
            android_library("//java/translation-only:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .generated_res("res"));

    AndroidResourceModule expectedAndroidResourceModule =
        AndroidResourceModule.builder(
                TargetKey.forPlainTarget(Label.create("//java/translation-only:lib")))
            .addResourceAndTransitiveResource(source("java/translation-only/res"))
            .build();

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    errorCollector.assertNoIssues();
    assertThat(result.androidResourceModules).containsExactly(expectedAndroidResourceModule);
  }

  @Test
  public void aarImport_outsideSources_createsAarLibrary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    TargetMap targetMap =
        targetMap(
            android_library("//java/example:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .src("Source.java")
                .dep("//third_party/lib:an_aar"),
            aar_import("//third_party/lib:an_aar")
                .aar("lib_aar.aar")
                .generated_jar("_aar/an_aar/classes_and_libs_merged.jar"));
    jdepsMap.put(
        TargetKey.forPlainTarget(Label.create("//java/example:lib")),
        ImmutableList.of(jdepsPath("third_party/lib/_aar/an_aar/classes_and_libs_merged.jar")));
    BlazeJavaImportResult javaResult = importJavaWorkspace(workspaceRoot, targetMap, projectView);
    BlazeAndroidImportResult androidResult = importWorkspace(workspaceRoot, targetMap, projectView);

    errorCollector.assertNoIssues();

    // We get 2 libraries representing the AAR. One from java and one from android.
    assertThat(javaResult.libraries).hasSize(1);
    assertThat(androidResult.aarLibraries).hasSize(1);
    assertThat(
            androidResult.aarLibraries.values().stream()
                .map(BlazeAndroidWorkspaceImporterTest::aarJarName)
                .collect(Collectors.toList()))
        .containsExactly("classes_and_libs_merged.jar");
    assertThat(
            androidResult.aarLibraries.values().stream()
                .map(BlazeAndroidWorkspaceImporterTest::aarName)
                .collect(Collectors.toList()))
        .containsExactly("lib_aar.aar");
    // Check that BlazeAndroidLibrarySource can filter out the java one, so that only the
    // android version takes effect.
    BlazeAndroidLibrarySource.AarJarFilter aarFilter =
        new BlazeAndroidLibrarySource.AarJarFilter(androidResult.aarLibraries.values());
    assertThat(aarFilter.test(javaResult.libraries.values().asList().get(0))).isFalse();
  }

  @Test
  public void aarImport_outsideSourcesAndNoJdeps_keepsAarLibrary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    TargetMap targetMap =
        targetMap(
            android_library("//java/example:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .src("Source.java")
                .dep("//third_party/lib:an_aar"),
            aar_import("//third_party/lib:an_aar")
                .aar("lib_aar.aar")
                .generated_jar("_aar/an_aar/classes_and_libs_merged.jar"));
    BlazeJavaImportResult javaResult = importJavaWorkspace(workspaceRoot, targetMap, projectView);
    BlazeAndroidImportResult androidResult = importWorkspace(workspaceRoot, targetMap, projectView);

    errorCollector.assertNoIssues();

    // The java importer performs jdeps optimization, but the android one does not.
    assertThat(javaResult.libraries).isEmpty();
    assertThat(
            androidResult.aarLibraries.values().stream()
                .map(BlazeAndroidWorkspaceImporterTest::aarName)
                .collect(Collectors.toList()))
        .containsExactly("lib_aar.aar");
  }

  @Test
  public void aarImport_inSources_createsAarLibrary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();
    TargetMap targetMap =
        targetMap(
            android_library("//java/example:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .src("Source.java")
                .dep("//java/example:an_aar"),
            aar_import("//java/example:an_aar")
                .aar("an_aar.aar")
                .generated_jar("_aar/an_aar/classes_and_libs_merged.jar"));
    jdepsMap.put(
        TargetKey.forPlainTarget(Label.create("//java/example:lib")),
        ImmutableList.of(jdepsPath("java/example/_aar/an_aar/classes_and_libs_merged.jar")));

    BlazeJavaImportResult javaResult = importJavaWorkspace(workspaceRoot, targetMap, projectView);
    BlazeAndroidImportResult androidResult = importWorkspace(workspaceRoot, targetMap, projectView);

    errorCollector.assertNoIssues();

    assertThat(
            androidResult.aarLibraries.values().stream()
                .map(BlazeAndroidWorkspaceImporterTest::aarJarName)
                .collect(Collectors.toList()))
        .containsExactly("classes_and_libs_merged.jar");
    assertThat(
            androidResult.aarLibraries.values().stream()
                .map(BlazeAndroidWorkspaceImporterTest::aarName)
                .collect(Collectors.toList()))
        .containsExactly("an_aar.aar");
    assertThat(javaResult.libraries).hasSize(1);
    BlazeAndroidLibrarySource.AarJarFilter aarFilter =
        new BlazeAndroidLibrarySource.AarJarFilter(androidResult.aarLibraries.values());
    assertThat(aarFilter.test(javaResult.libraries.values().asList().get(0))).isFalse();

    androidResult = importWorkspace(workspaceRoot, targetMap, projectView);

    errorCollector.assertNoIssues();

    assertThat(
            androidResult.aarLibraries.values().stream()
                .map(BlazeAndroidWorkspaceImporterTest::aarJarName)
                .collect(Collectors.toList()))
        .containsExactly("classes_and_libs_merged.jar");
    assertThat(
            androidResult.aarLibraries.values().stream()
                .map(BlazeAndroidWorkspaceImporterTest::aarName)
                .collect(Collectors.toList()))
        .containsExactly("an_aar.aar");
    assertThat(javaResult.libraries).hasSize(1);
    aarFilter = new BlazeAndroidLibrarySource.AarJarFilter(androidResult.aarLibraries.values());
    assertThat(aarFilter.test(javaResult.libraries.values().asList().get(0))).isFalse();
  }

  @Test
  public void aarImport_inSourcesAndNoJdeps_keepsAarLibrary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    TargetMap targetMap =
        targetMap(
            android_library("//java/example:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .src("Source.java")
                .dep("//java/example:an_aar"),
            aar_import("//java/example:an_aar")
                .aar("an_aar.aar")
                .generated_jar("_aar/an_aar/classes_and_libs_merged.jar"));
    BlazeJavaImportResult javaResult = importJavaWorkspace(workspaceRoot, targetMap, projectView);
    BlazeAndroidImportResult androidResult = importWorkspace(workspaceRoot, targetMap, projectView);

    errorCollector.assertNoIssues();

    assertThat(javaResult.libraries).isEmpty();
    assertThat(
            androidResult.aarLibraries.values().stream()
                .map(BlazeAndroidWorkspaceImporterTest::aarName)
                .collect(Collectors.toList()))
        .containsExactly("an_aar.aar");
    androidResult = importWorkspace(workspaceRoot, targetMap, projectView);

    errorCollector.assertNoIssues();

    assertThat(
            androidResult.aarLibraries.values().stream()
                .map(BlazeAndroidWorkspaceImporterTest::aarName)
                .collect(Collectors.toList()))
        .containsExactly("an_aar.aar");
  }

  @Test
  public void aarImport_multipleJarLibraries_aarLibraryOnlyOverridesAarJar() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    TargetMap targetMap =
        targetMap(
            android_library("//java/example:lib")
                .manifest("AndroidManifest.xml")
                .res("res")
                .src("Source.java")
                .dep("//third_party/lib:consume_export_aar", "//third_party/lib:dep_library"),
            aar_import("//third_party/lib:consume_export_aar")
                .aar("lib1_aar.aar")
                .generated_jar("_aar/consume_export_aar/classes_and_libs_merged.jar")
                .dep("//third_party/lib:dep_aar"),
            aar_import("//third_party/lib:dep_aar")
                .aar("lib2_aar.aar")
                .generated_jar("_aar/dep_aar/classes_and_libs_merged.jar"),
            android_library("//third_party/lib:dep_library")
                .manifest("AndroidManifest.xml")
                .res("res")
                .src("SharedActivity.java")
                .generated_jar("third_party/lib/dep_library.jar"));
    jdepsMap.put(
        TargetKey.forPlainTarget(Label.create("//java/example:lib")),
        ImmutableList.of(
            jdepsPath("third_party/lib/_aar/dep_aar/classes_and_libs_merged.jar"),
            jdepsPath("third_party/lib/_aar/consume_export_aar/classes_and_libs_merged.jar"),
            jdepsPath("third_party/lib/dep_library.jar")));
    BlazeJavaImportResult javaResult = importJavaWorkspace(workspaceRoot, targetMap, projectView);
    BlazeAndroidImportResult androidResult = importWorkspace(workspaceRoot, targetMap, projectView);

    errorCollector.assertNoIssues();

    assertThat(javaResult.libraries).hasSize(3);
    assertThat(androidResult.aarLibraries).hasSize(2);
    assertThat(
            androidResult.aarLibraries.values().stream()
                .map(BlazeAndroidWorkspaceImporterTest::aarJarName)
                .collect(Collectors.toList()))
        .containsExactly("classes_and_libs_merged.jar", "classes_and_libs_merged.jar");
    assertThat(
            androidResult.aarLibraries.values().stream()
                .map(BlazeAndroidWorkspaceImporterTest::aarName)
                .collect(Collectors.toList()))
        .containsExactly("lib1_aar.aar", "lib2_aar.aar");
    BlazeAndroidLibrarySource.AarJarFilter aarFilter =
        new BlazeAndroidLibrarySource.AarJarFilter(androidResult.aarLibraries.values());
    ImmutableList<BlazeJarLibrary> blazeJarLibraries = javaResult.libraries.values().asList();
    for (BlazeJarLibrary jarLibrary : blazeJarLibraries) {
      if (libraryJarName(jarLibrary).equals("dep_library.jar")) {
        assertThat(aarFilter.test(jarLibrary)).isTrue();
      } else {
        assertThat(aarFilter.test(jarLibrary)).isFalse();
      }
    }
  }

  @Test
  public void resJarFilter_resJarFromOutOfProjectTarget_filtersOutJar() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    // resource jar comes from a target inside of project view.
    TargetMap targetMap =
        targetMap(
            android_library("//java/example:lib")
                .src("Source.java")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//third_party/lib:res_lib"),
            android_library("//third_party/lib:res_lib")
                .manifest("AndroidManifest.xml")
                .res_folder("java/example/test_res/res", "test_res_res.aar")
                .resource_jar("third_party/lib/res_lib_resources.jar")
                .generated_jar("third_party/lib/res_lib_resources.jar"));

    jdepsMap.put(
        TargetKey.forPlainTarget(Label.create("//java/example:lib")),
        ImmutableList.of(jdepsPath("third_party/lib/res_lib_resources.jar")));
    BlazeJavaImportResult javaResult = importJavaWorkspace(workspaceRoot, targetMap, projectView);
    BlazeAndroidImportResult androidResult = importWorkspace(workspaceRoot, targetMap, projectView);

    errorCollector.assertNoIssues();

    // BlazeJavaWorkspace should pick up the resource jar from jDeps file, and
    assertThat(javaResult.libraries).hasSize(1);
    assertThat(androidResult.androidResourceModules).hasSize(1);
    // BlazeAndroidWorkspaceImporter should pick up resource JAR from AndroidIdeInfo.
    assertThat(androidResult.resourceJars).hasSize(1);
    // Ensure that the BlazeAndroidWorkspaceImporter picked up the correct JAR
    assertThat(
            androidResult.resourceJars.stream()
                .map(BlazeAndroidWorkspaceImporterTest::libraryJarName)
                .collect(Collectors.toList()))
        .containsExactly("res_lib_resources.jar");

    // Check that BlazeAndroidLibrarySource can filter out the resource jar
    BlazeAndroidLibrarySource.ResourceJarFilter resourceJarFilter =
        new BlazeAndroidLibrarySource.ResourceJarFilter(androidResult.resourceJars);
    assertThat(resourceJarFilter.test(javaResult.libraries.values().asList().get(0))).isFalse();
  }

  @Test
  public void resJarFilter_resJarFromInProjectTarget_filtersOutJar() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    // resource jar comes from a target inside project view.
    TargetMap targetMap =
        targetMap(
            android_library("//java/example:lib")
                .src("Source.java")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//third_party/lib:res_lib"),
            android_library("//java/example/test_res:res_lib")
                .manifest("AndroidManifest.xml")
                .res_folder("java/example/test_res/res", "res.aar")
                .resource_jar("java/example/test_res/res_lib_resources.jar")
                .generated_jar("java/example/test_res/res_lib_resources.jar"));
    jdepsMap.put(
        TargetKey.forPlainTarget(Label.create("//java/example:lib")),
        ImmutableList.of(jdepsPath("java/example/test_res/res_lib_resources.jar")));
    BlazeJavaImportResult javaResult = importJavaWorkspace(workspaceRoot, targetMap, projectView);
    BlazeAndroidImportResult androidResult = importWorkspace(workspaceRoot, targetMap, projectView);

    errorCollector.assertNoIssues();

    // BlazeJavaWorkspace should pick up the resource jar from jDeps file, and
    assertThat(javaResult.libraries).hasSize(1);
    assertThat(androidResult.androidResourceModules).hasSize(2);
    // BlazeAndroidWorkspaceImporter should pick up resource JAR from AndroidIdeInfo.
    assertThat(androidResult.resourceJars).hasSize(1);
    // Ensure that the BlazeAndroidWorkspaceImporter picked up the correct JAR
    assertThat(
            androidResult.resourceJars.stream()
                .map(BlazeAndroidWorkspaceImporterTest::libraryJarName)
                .collect(Collectors.toList()))
        .containsExactly("res_lib_resources.jar");

    // Check that BlazeAndroidLibrarySource can filter out the resource jar
    BlazeAndroidLibrarySource.ResourceJarFilter resourceJarFilter =
        new BlazeAndroidLibrarySource.ResourceJarFilter(androidResult.resourceJars);
    assertThat(resourceJarFilter.test(javaResult.libraries.values().asList().get(0))).isFalse();
  }

  /**
   * Check androidResourceModules are created correct even targetMap contains cyclic dependency
   * b/70781962
   */
  @Test
  public void androidResourceModuleGeneration_cyclicDeps_constructedCorrectly() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("foo")))
                    .add(DirectoryEntry.include(new WorkspacePath("bar"))))
            .build();
    TargetMap targetMap =
        targetMap(
            android_binary("//foo:lib")
                .src("MainActivity.xml")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//bar:lib"),
            android_binary("//bar:lib")
                .src("MainActivity.xml")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//foo:lib"));

    BlazeAndroidImportResult blazeAndroidImportResult =
        importWorkspace(workspaceRoot, targetMap, projectView);
    errorCollector.assertNoIssues();
    AndroidResourceModule expectedAndroidResourceModule1 =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//foo:lib")))
            .addResourceAndTransitiveResource(source("foo/res"))
            .addTransitiveResource(source("bar/res"))
            .addTransitiveResourceDependency("//bar:lib")
            .build();
    AndroidResourceModule expectedAndroidResourceModule2 =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//bar:lib")))
            .addResourceAndTransitiveResource(source("bar/res"))
            .addTransitiveResource(source("foo/res"))
            .addTransitiveResourceDependency("//foo:lib")
            .build();
    assertThat(blazeAndroidImportResult.androidResourceModules)
        .containsExactly(expectedAndroidResourceModule1, expectedAndroidResourceModule2);
  }

  /**
   * Check androidResourceModules are created correct even targetMap does not contain every
   * dependency b/72431530
   */
  @Test
  public void androidResourceModuleGeneration_missingDeps_constructedCorrectly() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("foo")))
                    .add(DirectoryEntry.include(new WorkspacePath("bar"))))
            .build();
    TargetMap targetMap =
        targetMap(
            android_binary("//foo:lib")
                .src("MainActivity.java")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//bar:lib"));
    BlazeAndroidImportResult blazeAndroidImportResult =
        importWorkspace(workspaceRoot, targetMap, projectView);
    errorCollector.assertNoIssues();
    AndroidResourceModule expectedAndroidResourceModule1 =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//foo:lib")))
            .addResourceAndTransitiveResource(source("foo/res"))
            .build();
    assertThat(blazeAndroidImportResult.androidResourceModules)
        .containsExactly(expectedAndroidResourceModule1);
  }

  /**
   * Check resource module are generated correct with chain dependencies. And there's no duplicate/
   * unnecessary create and reduce operations during creation.
   */
  @Test
  public void androidResourceModuleGeneration_longResourceDepChains_constructedCorrectly() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("foo")))
                    .add(DirectoryEntry.include(new WorkspacePath("bar")))
                    .add(DirectoryEntry.include(new WorkspacePath("baz")))
                    .add(DirectoryEntry.include(new WorkspacePath("unrelated"))))
            .build();

    TargetMap targetMap =
        targetMap(
            android_binary("//foo:lib")
                .src("MainActivity.java")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//bar:lib", "//baz:lib", "//qux:lib"),
            android_binary("//bar:lib")
                .src("MainActivity.java")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//baz:lib"),
            android_binary("//baz:lib")
                .src("MainActivity.java")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//qux:lib"),
            android_binary("//qux:lib")
                .src("MainActivity.java")
                .manifest("AndroidManifest.xml")
                .res_folder("res", "resources.aar"),
            android_binary("//unrelated:lib")
                .src("MainActivity.java")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//qux:lib"));

    ProjectViewSet projectViewSet = ProjectViewSet.builder().add(projectView).build();
    MockBlazeAndroidWorkspaceImporter mockBlazeAndroidWorkspaceImporter =
        new MockBlazeAndroidWorkspaceImporter(
            getProject(),
            context,
            BlazeImportInput.forProject(
                getProject(), workspaceRoot, projectViewSet, targetMap, FAKE_ARTIFACT_DECODER));
    AndroidResourceModule expectedAndroidResourceModule1 =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//foo:lib")))
            .addResourceAndTransitiveResource(source("foo/res"))
            .addTransitiveResource(source("bar/res"))
            .addTransitiveResource(source("baz/res"))
            .addResourceLibraryKey(
                LibraryKey.libraryNameFromArtifactLocation(source("qux/resources.aar")))
            .addTransitiveResourceDependency("//bar:lib")
            .addTransitiveResourceDependency("//baz:lib")
            .addTransitiveResourceDependency("//qux:lib")
            .build();
    AndroidResourceModule expectedAndroidResourceModule2 =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//bar:lib")))
            .addResourceAndTransitiveResource(source("bar/res"))
            .addTransitiveResource(source("baz/res"))
            .addResourceLibraryKey(
                LibraryKey.libraryNameFromArtifactLocation(source("qux/resources.aar")))
            .addTransitiveResourceDependency("//baz:lib")
            .addTransitiveResourceDependency("//qux:lib")
            .build();
    AndroidResourceModule expectedAndroidResourceModule3 =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//baz:lib")))
            .addResourceAndTransitiveResource(source("baz/res"))
            .addResourceLibraryKey(
                LibraryKey.libraryNameFromArtifactLocation(source("qux/resources.aar")))
            .addTransitiveResourceDependency("//qux:lib")
            .build();
    AndroidResourceModule expectedAndroidResourceModule4 =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//unrelated:lib")))
            .addResourceAndTransitiveResource(source("unrelated/res"))
            .addResourceLibraryKey(
                LibraryKey.libraryNameFromArtifactLocation(source("qux/resources.aar")))
            .addTransitiveResourceDependency("//qux:lib")
            .build();
    BlazeAndroidImportResult importResult = mockBlazeAndroidWorkspaceImporter.importWorkspace();
    assertThat(importResult.androidResourceModules)
        .containsExactly(
            expectedAndroidResourceModule1,
            expectedAndroidResourceModule2,
            expectedAndroidResourceModule3,
            expectedAndroidResourceModule4);
    assertThat(mockBlazeAndroidWorkspaceImporter.getCreateCount()).isEqualTo(5);
    // One reduce per direct dependency: 3 + 1 + 1 + 0 + 1
    assertThat(mockBlazeAndroidWorkspaceImporter.getReduce()).isEqualTo(6);
  }

  /**
   * If a dependency declares a custom resource package, then that package name should be used in
   * the resource dep graph.
   */
  @Test
  public void androidResourceImport_customPackageNameForAarFromDep_usesExportedPackageName() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    // Set up a target map where the android_library dependency provides a custom java package.
    // //java/com/google/android/assets/quantum:values declares custom java package
    // "dino.google.android.assets.quantum" which should be used by the AarLibrary
    TargetMap targetMap =
        targetMap(
            android_library("//java/com/google/android/assets/quantum:values")
                .setResourceJavaPackage("dino.google.android.assets.quantum")
                .manifest("AndroidManifest.xml")
                .res_folder("res", "resources.aar"),
            android_library("//java/example:resources")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//java/com/google/android/assets/quantum:values"));

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    errorCollector.assertNoIssues();
    assertThat(result.androidResourceModules)
        .containsExactly(
            AndroidResourceModule.builder(
                    TargetKey.forPlainTarget(Label.create("//java/example:resources")))
                .addResourceAndTransitiveResource(source("java/example/res"))
                .addTransitiveResourceDependency("//java/com/google/android/assets/quantum:values")
                .addResourceLibraryKey(
                    LibraryKey.libraryNameFromArtifactLocation(
                        source("java/com/google/android/assets/quantum/resources.aar")))
                .build());
    assertThat(result.aarLibraries.values())
        .containsExactly(
            new AarLibrary(
                source("java/com/google/android/assets/quantum/resources.aar"),
                "dino.google.android.assets.quantum"));

    assertThat(result.aarLibraries.values().stream().map(a -> a.resourcePackage))
        .containsExactly("dino.google.android.assets.quantum");
  }

  /**
   * If a dependency does not declare a custom resource package, then the package name should be
   * inferred via its target label.
   */
  @Test
  public void androidResourceImport_noExplicitPackageNameFromDep_packageNameInferredFromTarget() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    // Set up a target map where the android dependency does not set an explicit java package. In
    // such cases we want to infer the package from the target's path.
    // //java/com/google/android/assets/quantum:values implicitly uses package
    // "com.google.android.assets.quantum" that should be inferred by the AarLibrary
    TargetMap targetMap =
        targetMap(
            android_library("//java/com/google/android/assets/quantum:values")
                .manifest("AndroidManifest.xml")
                .res_folder("res", "resources.aar"),
            android_library("//java/example:resources")
                .manifest("AndroidManifest.xml")
                .res("res")
                .dep("//java/com/google/android/assets/quantum:values"));

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMap, projectView);
    errorCollector.assertNoIssues();
    assertThat(result.androidResourceModules)
        .containsExactly(
            AndroidResourceModule.builder(
                    TargetKey.forPlainTarget(Label.create("//java/example:resources")))
                .addResourceAndTransitiveResource(source("java/example/res"))
                .addTransitiveResourceDependency("//java/com/google/android/assets/quantum:values")
                .addResourceLibraryKey(
                    LibraryKey.libraryNameFromArtifactLocation(
                        source("java/com/google/android/assets/quantum/resources.aar")))
                .build());
    assertThat(result.aarLibraries.values())
        .containsExactly(
            new AarLibrary(
                source("java/com/google/android/assets/quantum/resources.aar"),
                "com.google.android.assets.quantum"));

    assertThat(result.aarLibraries.values().stream().map(a -> a.resourcePackage))
        .containsExactly("com.google.android.assets.quantum");
  }

  /**
   * Mock provider to satisfy directory listing queries from {@link
   * com.google.idea.blaze.android.sync.importer.problems.GeneratedResourceClassifier}.
   */
  private static class MockFileOperationProvider extends FileOperationProvider {
    @Override
    public long getFileSize(File file) {
      // Make JARs appear nonempty so that they aren't filtered out
      return file.getName().endsWith("jar") ? 500L : super.getFileSize(file);
    }

    // Return a few non-translation directories so that directories are considered interesting,
    // or return only-translation directories so that it's considered uninteresting.
    @Override
    public File[] listFiles(File directory) {
      String interestingResDir1 =
          FAKE_ARTIFACT_DECODER.resolveSource(gen("java/example/res")).getPath();
      if (directory.getPath().equals(interestingResDir1)) {
        return new File[] {
          new File("java/example/res/raw"), new File("java/example/res/values-es"),
        };
      }
      String interestingResDir2 =
          FAKE_ARTIFACT_DECODER.resolveSource(gen("java/example2/res")).getPath();
      if (directory.getPath().equals(interestingResDir2)) {
        return new File[] {
          new File("java/example2/res/layout"), new File("java/example2/res/values-ar"),
        };
      }
      String translationResDir =
          FAKE_ARTIFACT_DECODER.resolveSource(gen("java/translation-only/res")).getPath();
      if (directory.getPath().equals(translationResDir)) {
        return new File[] {
          new File("java/translation-only/res/values-ar"),
          new File("java/translation-only/res/values-es"),
        };
      }
      return new File[0];
    }
  }

  private static String aarJarName(AarLibrary library) {
    return new File(library.libraryArtifact.jarForIntellijLibrary().getExecutionRootRelativePath())
        .getName();
  }

  private static String aarName(AarLibrary library) {
    return new File(library.aarArtifact.getExecutionRootRelativePath()).getName();
  }

  private static String libraryJarName(BlazeJarLibrary library) {
    return new File(library.libraryArtifact.jarForIntellijLibrary().getExecutionRootRelativePath())
        .getName();
  }

  private ArtifactLocation source(String relativePath) {
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

  /**
   * Mock BlazeAndroidWorkspaceImporter and count number of create and reduce operations used to
   * generate AndroidResourceModule
   */
  private static class MockBlazeAndroidWorkspaceImporter extends BlazeAndroidWorkspaceImporter {
    private int createCount = 0;
    private int reduce = 0;

    public MockBlazeAndroidWorkspaceImporter(
        Project project, BlazeContext context, BlazeImportInput input) {
      super(project, context, input);
    }

    @Override
    protected AndroidResourceModule.Builder createResourceModuleBuilder(
        TargetIdeInfo target, LibraryFactory libraryFactory) {
      ++createCount;
      return super.createResourceModuleBuilder(target, libraryFactory);
    }

    @Override
    protected void reduce(
        TargetKey targetKey,
        AndroidResourceModule.Builder targetResourceModule,
        TargetKey depKey,
        TargetIdeInfo depIdeInfo,
        LibraryFactory libraryFactory,
        Map<TargetKey, AndroidResourceModule.Builder> resourceModuleBuilderCache) {
      if (depIdeInfo != null) {
        ++reduce;
      }
      super.reduce(
          targetKey,
          targetResourceModule,
          depKey,
          depIdeInfo,
          libraryFactory,
          resourceModuleBuilderCache);
    }

    public int getCreateCount() {
      return createCount;
    }

    public int getReduce() {
      return reduce;
    }
  }
}
