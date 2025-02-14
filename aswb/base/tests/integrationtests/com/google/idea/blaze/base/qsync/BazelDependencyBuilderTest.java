/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.AswbTestUtils.getRunfilesWorkspaceRoot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.MockProjectViewManager;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.artifacts.MockArtifactCache;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.ServiceContainerUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BazelDependencyBuilderTest extends BlazeIntegrationTestCase {

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final MockExperimentService experimentService = new MockExperimentService();

  @Before
  public void before() {
    // TODO: b/388249589 - reuse com.google.idea.blaze.android.google3.qsync.testrules.QuerySyncEnvironmentRule instead.
    System.setProperty(
      "qsync.aspect.build_dependencies.bzl.file",
      getRunfilesWorkspaceRoot()
        .toPath()
        .resolve("tools/adt/idea/aswb/aspect/build_dependencies.bzl")
        .toString());
    System.setProperty(
      "qsync.aspect.build_dependencies_deps.bzl.file",
      getRunfilesWorkspaceRoot()
        .toPath()
        .resolve("tools/adt/idea/aswb/aspect/build_dependencies_deps.bzl")
        .toString());
    ServiceContainerUtil.registerComponentInstance(ApplicationManager.getApplication(), ExperimentService.class, experimentService,
                                                   getTestRootDisposable());
  }

  @Test
  public void generatesValidAspectConfiguration() throws IOException {
    final var dependencyBuilder =
      new BazelDependencyBuilder(getProject(),
                                 new BazelBuildSystemProvider().getBuildSystem(),
                                 ProjectDefinition.builder()
                                   .setProjectIncludes(ImmutableSet.of())
                                   .setProjectExcludes(ImmutableSet.of())
                                   .setSystemExcludes(ImmutableSet.of())
                                   .setTestSources(ImmutableSet.of())
                                   .setLanguageClasses(ImmutableSet.of())
                                   .build(),
                                 new WorkspaceRoot(temporaryFolder.getRoot()),
                                 Optional.empty(),
                                 new MockArtifactCache(temporaryFolder.newFolder().toPath()),
                                 ImmutableSet.of("always_build_rule1", "always_build_rule2")
      );

    final var generatedAspectName = String.format("qs-%s.bzl", dependencyBuilder.getProjectHash());
    final var invocationFiles = dependencyBuilder.getInvocationFiles(
      ImmutableSet.of(Label.of("//target1:target1"), Label.of("//target2:target2")),
      new BazelDependencyBuilder.BuildDependencyParameters(
        ImmutableList.of("dir1", "dir2"),
        ImmutableList.of("dir1/sub1"),
        ImmutableList.of("always_build_rule1", "always_build_rule2"),
        true,
        false,
        true
      ));
    assertThat(invocationFiles.aspectFileLabel()).isEqualTo(String.format("//.aswb:qs-%s.bzl", dependencyBuilder.getProjectHash()));
    assertThat(
      new String(invocationFiles.files().get(Path.of(".aswb", generatedAspectName)).openStream().readAllBytes(), StandardCharsets.UTF_8))
      .isEqualTo("""
                   load(':build_dependencies.bzl', _collect_dependencies = 'collect_dependencies', _package_dependencies = 'package_dependencies')
                   _config = struct(
                     include = [
                       "dir1",
                       "dir2",
                     ],
                     exclude = [
                       "dir1/sub1",
                     ],
                     always_build_rules = [
                       "always_build_rule1",
                       "always_build_rule2",
                     ],
                     generate_aidl_classes = True,
                     use_generated_srcjars = False,
                     experiment_multi_info_file = True,
                   )
                   
                   collect_dependencies = _collect_dependencies(_config)
                   package_dependencies = _package_dependencies(_config)
                   """);
  }

  @Test
  public void generatesValidTargetPatternFile() throws IOException {
    experimentService.setExperiment(BazelDependencyBuilder.buildUseTargetPatternFile, true);
    new MockProjectViewManager(getProject()).setProjectView(new ProjectViewSet(ImmutableList.of()));
    final var dependencyBuilder =
      new BazelDependencyBuilder(getProject(),
                                 new BazelBuildSystemProvider().getBuildSystem(),
                                 ProjectDefinition.builder()
                                   .setProjectIncludes(ImmutableSet.of())
                                   .setProjectExcludes(ImmutableSet.of())
                                   .setSystemExcludes(ImmutableSet.of())
                                   .setTestSources(ImmutableSet.of())
                                   .setLanguageClasses(ImmutableSet.of())
                                   .build(),
                                 new WorkspaceRoot(temporaryFolder.getRoot()),
                                 Optional.empty(),
                                 new MockArtifactCache(temporaryFolder.newFolder().toPath()),
                                 ImmutableSet.of("always_build_rule1", "always_build_rule2")
      );

    final var generatedTargetPatternName = Label.of(String.format("//.aswb:targets-%s.txt", dependencyBuilder.getProjectHash())).name();
    final var invocationInfo = dependencyBuilder.getInvocationInfo(
      BlazeContext.create(),
      ImmutableSet.of(Label.of("//target1:target1"), Label.of("//target2:target2")),
      ImmutableSet.of(QuerySyncLanguage.JAVA, QuerySyncLanguage.CC)
    );
    ImmutableMap<Path, ByteSource> invocationFiles =
      invocationInfo.invocationWorkspaceFiles();
    assertThat(
      new String(invocationFiles.get(Path.of(".aswb", generatedTargetPatternName)).openStream().readAllBytes(), StandardCharsets.UTF_8))
      .isEqualTo("""
                   //target1:target1
                   //target2:target2""");
    assertThat(invocationInfo.argsAndFlags()).contains("--target_pattern_file=.aswb/" + generatedTargetPatternName);
  }
}
