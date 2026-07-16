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
package com.google.idea.blaze.base.run.testmap;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.qsync.QuerySyncProjectData;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.run.SourceToTargetFinder;
import com.google.idea.blaze.base.settings.BazelImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.qsync.project.ProjectTarget;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.io.File;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the test map */
@RunWith(JUnit4.class)
public class TestMapTest extends BlazeTestCase {

  private MockBlazeProjectDataManager mockBlazeProjectDataManager;
  private ProjectSourceToTargetFinder finder;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(QuerySyncSettings.class, new QuerySyncSettings());

    mockBlazeProjectDataManager = new MockBlazeProjectDataManager();
    projectServices.register(BlazeProjectDataManager.class, mockBlazeProjectDataManager);
    projectServices.register(SyncCache.class, new SyncCache(project));
    BlazeImportSettingsManager importSettingsManager = new BlazeImportSettingsManager(project);
    importSettingsManager.setImportSettingsForTests(
        java.nio.file.Path.of(""), BuildSystemName.Blaze);
    projectServices.register(BazelImportSettingsManager.class, importSettingsManager);

    finder = new ProjectSourceToTargetFinder();
    ExtensionPointImpl<SourceToTargetFinder> ep =
        registerExtensionPoint(SourceToTargetFinder.EP_NAME, SourceToTargetFinder.class);
    ep.registerExtension(finder);

    ExtensionPointImpl<Kind.Provider> kindProvider =
        registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    kindProvider.registerExtension(new GenericBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());
  }

  private QuerySyncProjectData createMockProjectData(ProjectTarget... targets) {
    WorkspacePathResolver resolver =
        new WorkspacePathResolverImpl(new WorkspaceRoot(new File("/")));
    return new QuerySyncProjectData(
        resolver, new WorkspaceLanguageSettings(WorkspaceType.JAVA, ImmutableSet.of())) {
      @Override
      public Collection<ProjectTarget> getReverseDeps(java.nio.file.Path sourcePath) {
        return ImmutableList.copyOf(targets);
      }
    };
  }

  @Test
  public void testTrivialTestMap() throws Exception {
    ProjectTarget target =
        ProjectTarget.builder()
            .label(com.google.idea.blaze.common.Label.of("//test:test"))
            .kind("sh_test")
            .tags(ImmutableList.of())
            .build();
    mockBlazeProjectDataManager.projectData = createMockProjectData(target);

    Collection<TargetInfo> targets =
        finder
            .targetsForSourceFiles(
                project, ImmutableSet.of(new File("/test/Test.java")), Optional.of(RuleType.TEST))
            .get();

    assertThat(targets.stream().map(t -> t.label()).collect(Collectors.toList()))
        .containsExactly(Label.create("//test:test"));
  }

  @Test
  @Ignore("b/466350110")
  public void testOneStepRemovedTestMap() throws Exception {
    // mockBlazeProjectDataManager.targetMap =
    //    TargetMapBuilder.builder()
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:test")
    //                .setKind("sh_test")
    //                .addDependency("//test:lib"))
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:lib")
    //                .setKind("sh_library")
    //                .addSource(sourceRoot("test/Test.java")))
    //        .build();

    Collection<TargetInfo> targets =
        finder
            .targetsForSourceFiles(
                project, ImmutableSet.of(new File("/test/Test.java")), Optional.of(RuleType.TEST))
            .get();

    assertThat(targets.stream().map(t -> t.label()).collect(Collectors.toList()))
        .containsExactly(Label.create("//test:test"));
  }

  @Test
  @Ignore("b/466350110")
  public void testTwoCandidatesTestMap() throws Exception {
    // mockBlazeProjectDataManager.targetMap =
    //    TargetMapBuilder.builder()
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:test")
    //                .setKind("sh_test")
    //                .addDependency("//test:lib"))
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:test2")
    //                .setKind("sh_test")
    //                .addDependency("//test:lib"))
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:lib")
    //                .setKind("sh_library")
    //                .addSource(sourceRoot("test/Test.java")))
    //        .build();

    Collection<TargetInfo> targets =
        finder
            .targetsForSourceFiles(
                project, ImmutableSet.of(new File("/test/Test.java")), Optional.of(RuleType.TEST))
            .get();

    assertThat(targets.stream().map(t -> t.label()).collect(Collectors.toList()))
        .containsExactly(Label.create("//test:test"), Label.create("//test:test2"));
  }

  @Test
  @Ignore("b/466350110")
  public void testBfsPreferred() throws Exception {
    // mockBlazeProjectDataManager.targetMap =
    //    TargetMapBuilder.builder()
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:lib")
    //                .setKind("sh_library")
    //                .addSource(sourceRoot("test/Test.java")))
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:lib2")
    //                .setKind("sh_library")
    //                .addDependency("//test:lib"))
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:test2")
    //                .setKind("sh_test")
    //                .addDependency("//test:lib2"))
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:test")
    //                .setKind("sh_test")
    //                .addDependency("//test:lib"))
    //        .build();

    Collection<TargetInfo> targets =
        finder
            .targetsForSourceFiles(
                project, ImmutableSet.of(new File("/test/Test.java")), Optional.of(RuleType.TEST))
            .get();

    assertThat(targets.stream().map(t -> t.label()).collect(Collectors.toList()))
        .containsExactly(Label.create("//test:test"), Label.create("//test:test2"))
        .inOrder();
  }

  @Test
  @Ignore("b/466350110")
  public void testSourceIncludedMultipleTimesFindsAll() throws Exception {
    // mockBlazeProjectDataManager.targetMap =
    //    TargetMapBuilder.builder()
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:test")
    //                .setKind("sh_test")
    //                .addDependency("//test:lib"))
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:test2")
    //                .setKind("sh_test")
    //                .addDependency("//test:lib2"))
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:lib")
    //                .setKind("sh_library")
    //                .addSource(sourceRoot("test/Test.java")))
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:lib2")
    //                .setKind("sh_library")
    //                .addSource(sourceRoot("test/Test.java")))
    //        .build();

    Collection<TargetInfo> targets =
        finder
            .targetsForSourceFiles(
                project, ImmutableSet.of(new File("/test/Test.java")), Optional.of(RuleType.TEST))
            .get();

    assertThat(targets.stream().map(t -> t.label()).collect(Collectors.toList()))
        .containsExactly(Label.create("//test:test"), Label.create("//test:test2"));
  }

  @Test
  @Ignore("b/466350110")
  public void testSourceIncludedMultipleTimesShouldOnlyGiveOneInstanceOfTest() throws Exception {
    // mockBlazeProjectDataManager.targetMap =
    //    TargetMapBuilder.builder()
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:test")
    //                .setKind("sh_test")
    //                .addDependency("//test:lib")
    //                .addDependency("//test:lib2"))
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:lib")
    //                .setKind("sh_library")
    //                .addSource(sourceRoot("test/Test.java")))
    //        .addTarget(
    //            TargetIdeInfo.builder()
    //                .setBuildFile(sourceRoot("test/BUILD"))
    //                .setLabel("//test:lib2")
    //                .setKind("sh_library")
    //                .addSource(sourceRoot("test/Test.java")))
    //        .build();

    Collection<TargetInfo> targets =
        finder
            .targetsForSourceFiles(
                project, ImmutableSet.of(new File("/test/Test.java")), Optional.of(RuleType.TEST))
            .get();

    assertThat(targets.stream().map(t -> t.label()).collect(Collectors.toList()))
        .containsExactly(Label.create("//test:test"));
  }

  @Test
  public void testTargetWithNoKindDoesNotCauseNpe() throws Exception {
    ProjectTarget target =
        ProjectTarget.builder()
            .label(com.google.idea.blaze.common.Label.of("//test:test"))
            .kind("unrecognized_rule")
            .tags(ImmutableList.of())
            .build();
    mockBlazeProjectDataManager.projectData = createMockProjectData(target);

    Collection<TargetInfo> targets =
        finder
            .targetsForSourceFiles(
                project, ImmutableSet.of(new File("/test/Test.java")), Optional.empty())
            .get();

    // Unknown rule type does not match specific rule types, but when queried with Optional.empty()
    // (no rule type filter), it must not cause an NPE when sorting target priorities.
    assertThat(targets.stream().map(t -> t.label()).collect(Collectors.toList()))
        .containsExactly(Label.create("//test:test"));
  }

  private static class MockBlazeProjectDataManager implements BlazeProjectDataManager {
    @Nullable public BlazeProjectData projectData;

    @Nullable
    @Override
    public BlazeProjectData getBlazeProjectData() {
      return projectData;
    }

    @Nullable
    @Override
    public BlazeProjectData loadProject(BlazeImportSettings importSettings) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void saveProject(BlazeImportSettings importSettings, BlazeProjectData projectData) {
      throw new UnsupportedOperationException();
    }
  }
}
