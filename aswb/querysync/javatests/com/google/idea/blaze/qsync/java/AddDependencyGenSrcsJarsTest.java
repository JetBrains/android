/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.java;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.NoopContext;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.QuerySyncTestUtils;
import com.google.idea.blaze.qsync.TestDataSyncRunner;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.ArtifactTracker.State;
import com.google.idea.blaze.qsync.deps.DependencyBuildContext;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.SrcJarJavaPackageRoots;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath.Base;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class AddDependencyGenSrcsJarsTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock public BuildArtifactCache cache;

  private final TestDataSyncRunner syncer =
    new TestDataSyncRunner(new NoopContext(), QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER);

  private final QuerySyncProjectSnapshot original = syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);

  private final SrcJarPackageRootsExtractor innerRootsMetadata =
    new SrcJarPackageRootsExtractor(null);

  public AddDependencyGenSrcsJarsTest() throws IOException, BuildException { }

  @Test
  public void enable_library_provider_no_deps_built() throws Exception {
    AddDependencyGenSrcsJars addGenSrcJars =
      new AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata, true);
    no_deps_built(addGenSrcJars);
  }

  @Test
  public void disable_library_provider_no_deps_built() throws Exception {
    AddDependencyGenSrcsJars addGenSrcJars =
      new AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata, false);
    no_deps_built(addGenSrcJars);
  }

  private void no_deps_built(AddDependencyGenSrcsJars addGenSrcJars) throws Exception {
    ProjectProtoUpdate update =
      new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());

    addGenSrcJars.update(update, State.EMPTY, new NoopContext());

    ProjectProto.Project newProject = update.build();

    assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList());
    assertThat(newProject.getModulesList()).isEqualTo(original.project().getModulesList());
    assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keySet()).isEmpty();
  }

  @Test
  public void enable_library_provider_project_gensrcs_ignored() throws Exception {
    AddDependencyGenSrcsJars addGenSrcJars =
      new AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata, true);
    project_gensrcs_ignored(addGenSrcJars);
  }

  @Test
  public void disable_library_provider_project_gensrcs_ignored() throws Exception {
    AddDependencyGenSrcsJars addGenSrcJars =
      new AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata, false);
    project_gensrcs_ignored(addGenSrcJars);
  }

  private void project_gensrcs_ignored(AddDependencyGenSrcsJars addGenSrcJars) throws Exception {
    TestData testProject = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY;

    ArtifactTracker.State artifactState =
      ArtifactTracker.State.forJavaArtifacts(
        JavaArtifactInfo.empty(testProject.getAssumedOnlyLabel()).toBuilder()
          .setGenSrcs(
            ImmutableList.of(
              BuildArtifact.create(
                "srcjardigest",
                Path.of("output/path/to/in_project.srcjar"),
                testProject.getAssumedOnlyLabel())))
          .build());

    ProjectProtoUpdate update =
      new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());
    addGenSrcJars.update(update, artifactState, new NoopContext());
    ProjectProto.Project newProject = update.build();

    verify(cache, never()).get(ArgumentMatchers.any());

    assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList());
    assertThat(newProject.getModulesList()).isEqualTo(original.project().getModulesList());
    assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keySet()).isEmpty();
  }

  @Test
  public void enable_library_provider_external_gensrcs_added() throws Exception {
    AddDependencyGenSrcsJars addGenSrcJars =
      new AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata, true);
    external_gensrcs_added(addGenSrcJars, ProjectProto.Library.newBuilder().setName("//java/com/google/common/collect:collect")
      .addSources(ProjectProto.LibrarySource.newBuilder()
                    .setSrcjar(
                      ProjectPath.newBuilder()
                        .setBase(Base.PROJECT)
                        .setPath(".bazel/buildout/output/path/to/external.srcjar")
                        .setInnerPath("root"))
                    .build())
      .addSources(
        ProjectProto.LibrarySource.newBuilder()
          .setSrcjar(
            ProjectPath.newBuilder()
              .setBase(Base.PROJECT)
              .setPath(".bazel/buildout/output/path/to/external.srcjar")
              .setInnerPath("root2"))
          .build())
      .build());
  }

  @Test
  public void disable_library_provider_external_gensrcs_added() throws Exception {
    AddDependencyGenSrcsJars addGenSrcJars =
      new AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata, false);
    external_gensrcs_added(addGenSrcJars, ProjectProto.Library.newBuilder().setName(".dependencies")
      .addSources(ProjectProto.LibrarySource.newBuilder()
                    .setSrcjar(
                      ProjectPath.newBuilder()
                        .setBase(Base.PROJECT)
                        .setPath(".bazel/buildout/output/path/to/external.srcjar")
                        .setInnerPath("root"))
                    .build())
      .addSources(
        ProjectProto.LibrarySource.newBuilder()
          .setSrcjar(
            ProjectPath.newBuilder()
              .setBase(Base.PROJECT)
              .setPath(".bazel/buildout/output/path/to/external.srcjar")
              .setInnerPath("root2"))
          .build())
      .build());
  }

  private void external_gensrcs_added(AddDependencyGenSrcsJars addGenSrcJars, ProjectProto.Library... expectedLibraries) throws Exception {
    ArtifactTracker.State artifactState =
      ArtifactTracker.State.forTargets(
        TargetBuildInfo.forJavaTarget(
          JavaArtifactInfo.empty(Label.of("//java/com/google/common/collect:collect"))
            .toBuilder()
            .setGenSrcs(
              ImmutableList.of(
                BuildArtifact.create(
                    "srcjardigest",
                    Path.of("output/path/to/external.srcjar"),
                    Label.of("//java/com/google/common/collect:collect"))
                  .withMetadata(
                    new SrcJarJavaPackageRoots(
                      ImmutableSet.of(Path.of("root"), Path.of("root2"))))))
            .build(),
          DependencyBuildContext.NONE));

    ProjectProtoUpdate update =
      new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());
    addGenSrcJars.update(update, artifactState, new NoopContext());
    ProjectProto.Project newProject = update.build();

    assertThat(newProject.getLibraryList()).containsExactly(expectedLibraries);
  }


  @Test
  public void enable_library_provider_no_metadata_present() throws Exception {
    AddDependencyGenSrcsJars addGenSrcJars =
      new AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata, true);
    no_metadata_present(addGenSrcJars, ProjectProto.Library.newBuilder().setName("//java/com/google/common/collect:collect")
      .addSources(ProjectProto.LibrarySource.newBuilder()
                    .setSrcjar(
                      ProjectPath.newBuilder()
                        .setBase(Base.PROJECT)
                        .setPath(".bazel/buildout/output/path/to/external.srcjar"))
                    .build())
      .build());
  }

  @Test
  public void disable_library_provider_no_metadata_present() throws Exception {
    AddDependencyGenSrcsJars addGenSrcJars =
      new AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata, false);
    no_metadata_present(addGenSrcJars, ProjectProto.Library.newBuilder().setName(".dependencies")
      .addSources(ProjectProto.LibrarySource.newBuilder()
                    .setSrcjar(
                      ProjectPath.newBuilder()
                        .setBase(Base.PROJECT)
                        .setPath(".bazel/buildout/output/path/to/external.srcjar"))
                    .build())
      .build());
  }

  private void no_metadata_present(AddDependencyGenSrcsJars addGenSrcJars, ProjectProto.Library... expectedLibraries) throws Exception {
    ArtifactTracker.State artifactState =
      ArtifactTracker.State.forTargets(
        TargetBuildInfo.forJavaTarget(
          JavaArtifactInfo.empty(Label.of("//java/com/google/common/collect:collect"))
            .toBuilder()
            .setGenSrcs(
              ImmutableList.of(
                BuildArtifact.create(
                  "srcjardigest",
                  Path.of("output/path/to/external.srcjar"),
                  Label.of("//java/com/google/common/collect:collect"))))
            .build(),
          DependencyBuildContext.NONE));

    ProjectProtoUpdate update =
      new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());
    addGenSrcJars.update(update, artifactState, new NoopContext());
    ProjectProto.Project newProject = update.build();

    assertThat(newProject.getLibraryList()).containsExactly(expectedLibraries);
  }
}
