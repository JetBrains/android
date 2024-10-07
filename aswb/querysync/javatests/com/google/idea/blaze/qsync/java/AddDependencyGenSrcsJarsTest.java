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
import com.google.idea.blaze.qsync.project.ProjectProto.Library;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath.Base;
import com.google.idea.blaze.qsync.testdata.TestData;
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

  private final SrcJarPackageRootsExtractor innerRootsMetadata =
      new SrcJarPackageRootsExtractor(null);

  @Test
  public void no_deps_built() throws Exception {

    QuerySyncProjectSnapshot original = syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);

    AddDependencyGenSrcsJars addGenSrcJars =
        new AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata);

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());

    addGenSrcJars.update(update, State.EMPTY);

    ProjectProto.Project newProject = update.build();

    assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList());
    assertThat(newProject.getModulesList()).isEqualTo(original.project().getModulesList());
    assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keySet()).isEmpty();
  }

  @Test
  public void project_gensrcs_ignored() throws Exception {

    TestData testProject = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY;

    QuerySyncProjectSnapshot original = syncer.sync(testProject);

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

    AddDependencyGenSrcsJars addGenSrcJars =
        new AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata);

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());
    addGenSrcJars.update(update, artifactState);
    ProjectProto.Project newProject = update.build();

    verify(cache, never()).get(ArgumentMatchers.any());

    assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList());
    assertThat(newProject.getModulesList()).isEqualTo(original.project().getModulesList());
    assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keySet()).isEmpty();
  }

  @Test
  public void external_gensrcs_added() throws Exception {
    QuerySyncProjectSnapshot original = syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);

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

    AddDependencyGenSrcsJars addGenSrcJars =
        new AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata);

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());
    addGenSrcJars.update(update, artifactState);
    ProjectProto.Project newProject = update.build();

    assertThat(newProject.getLibraryList()).hasSize(1);
    Library depsLib = newProject.getLibrary(0);
    assertThat(depsLib.getName()).isEqualTo(".dependencies");
    assertThat(depsLib.getSourcesList())
        .containsExactly(
            ProjectProto.LibrarySource.newBuilder()
                .setSrcjar(
                    ProjectPath.newBuilder()
                        .setBase(Base.PROJECT)
                        .setPath(".bazel/buildout/output/path/to/external.srcjar")
                        .setInnerPath("root"))
                .build(),
            ProjectProto.LibrarySource.newBuilder()
                .setSrcjar(
                    ProjectPath.newBuilder()
                        .setBase(Base.PROJECT)
                        .setPath(".bazel/buildout/output/path/to/external.srcjar")
                        .setInnerPath("root2"))
                .build());
  }


  @Test
  public void no_metadata_present() throws Exception {
    QuerySyncProjectSnapshot original = syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);

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

    AddDependencyGenSrcsJars addGenSrcJars =
        new AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata);

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());
    addGenSrcJars.update(update, artifactState);
    ProjectProto.Project newProject = update.build();

    assertThat(newProject.getLibraryList()).hasSize(1);
    Library depsLib = newProject.getLibrary(0);
    assertThat(depsLib.getName()).isEqualTo(".dependencies");
    assertThat(depsLib.getSourcesList())
        .containsExactly(
            ProjectProto.LibrarySource.newBuilder()
                .setSrcjar(
                    ProjectPath.newBuilder()
                        .setBase(Base.PROJECT)
                        .setPath(".bazel/buildout/output/path/to/external.srcjar"))
                .build());
  }
}
