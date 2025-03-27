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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.NoopContext;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.QuerySyncTestUtils;
import com.google.idea.blaze.qsync.TestDataSyncRunner;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.DependencyBuildContext;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.SrcJarPrefixedJavaPackageRoots;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.ContentEntry;
import com.google.idea.blaze.qsync.project.ProjectProto.Module;
import com.google.idea.blaze.qsync.testdata.TestData;
import com.google.protobuf.TextFormat;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class AddProjectGenSrcJarsTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private final TestDataSyncRunner syncer =
      new TestDataSyncRunner(new NoopContext(), QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER);

  private final SrcJarPrefixedPackageRootsExtractor innerPathsMetadata =
      new SrcJarPrefixedPackageRootsExtractor(null);

  @Test
  public void external_srcjar_ignored() throws Exception {
    QuerySyncProjectSnapshot original = syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);

    ArtifactTracker.State artifactState =
        ArtifactTracker.State.forJavaArtifacts(
            JavaArtifactInfo.empty(Label.of("//java/com/google/common/collect:collect")).toBuilder()
                .setGenSrcs(
                    ImmutableList.of(
                        BuildArtifact.create(
                            "srcjardigest",
                            Path.of("output/path/to/external.srcjar"),
                            Label.of("//java/com/google/common/collect:collect"))))
                .build());

    AddProjectGenSrcJars javaDeps =
        new AddProjectGenSrcJars(original.queryData().projectDefinition(), innerPathsMetadata);

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());
    javaDeps.update(update, artifactState);
    ProjectProto.Project newProject = update.build();
    assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList());
    assertThat(newProject.getModulesList()).isEqualTo(original.project().getModulesList());
    assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keySet()).isEmpty();
  }

  @Test
  public void project_srcjar_added() throws Exception {
    TestData testData = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY;
    QuerySyncProjectSnapshot original = syncer.sync(testData);

    ArtifactTracker.State artifactState =
        ArtifactTracker.State.forTargets(
            TargetBuildInfo.forJavaTarget(
                JavaArtifactInfo.empty(testData.getAssumedOnlyLabel()).toBuilder()
                    .setGenSrcs(
                        ImmutableList.of(
                            BuildArtifact.create(
                                    "srcjardigest",
                                    Path.of("output/path/to/project.srcjar"),
                                    testData.getAssumedOnlyLabel())
                                .withMetadata(
                                    new SrcJarPrefixedJavaPackageRoots(
                                        ImmutableSet.of(JarPath.create("root", ""))))))
                    .build(),
                DependencyBuildContext.NONE));

    AddProjectGenSrcJars javaDeps =
        new AddProjectGenSrcJars(original.queryData().projectDefinition(), innerPathsMetadata);

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());
    javaDeps.update(update, artifactState);
    ProjectProto.Project newProject = update.build();
    assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList());
    Module workspace = newProject.getModules(0);
    // check our assumptions:
    assertThat(workspace.getName()).isEqualTo(".workspace");

    assertThat(workspace.getContentEntriesList())
        .contains(
            TextFormat.parse(
                Joiner.on("\n")
                    .join(
                        "root {",
                        "      path: \".bazel/buildout/output/path/to/project.srcjar\"",
                        "      base: PROJECT",
                        "    }",
                        "    sources {",
                        "      is_generated: true",
                        "      project_path {",
                        "        path: \".bazel/buildout/output/path/to/project.srcjar\"",
                        "        base: PROJECT",
                        "        inner_path: \"root\"",
                        "      }",
                        "    }"),
                ContentEntry.class));
  }

  @Test
  public void missing_metadata_project_srcjar_added() throws Exception {
    TestData testData = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY;
    QuerySyncProjectSnapshot original = syncer.sync(testData);

    ArtifactTracker.State artifactState =
        ArtifactTracker.State.forTargets(
            TargetBuildInfo.forJavaTarget(
                JavaArtifactInfo.empty(testData.getAssumedOnlyLabel()).toBuilder()
                    .setGenSrcs(
                        ImmutableList.of(
                            BuildArtifact.create(
                                "srcjardigest",
                                Path.of("output/path/to/project.srcjar"),
                                testData.getAssumedOnlyLabel())))
                    .build(),
                DependencyBuildContext.NONE));

    AddProjectGenSrcJars javaDeps =
        new AddProjectGenSrcJars(original.queryData().projectDefinition(), innerPathsMetadata);

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());
    javaDeps.update(update, artifactState);
    ProjectProto.Project newProject = update.build();
    assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList());
    Module workspace = newProject.getModules(0);
    // check our assumptions:
    assertThat(workspace.getName()).isEqualTo(".workspace");

    assertThat(workspace.getContentEntriesList())
        .contains(
            TextFormat.parse(
                Joiner.on("\n")
                    .join(
                        "root {",
                        "      path: \".bazel/buildout/output/path/to/project.srcjar\"",
                        "      base: PROJECT",
                        "    }",
                        "    sources {",
                        "      is_generated: true",
                        "      project_path {",
                        "        path: \".bazel/buildout/output/path/to/project.srcjar\"",
                        "        base: PROJECT",
                        "      }",
                        "    }"),
                ContentEntry.class));
  }
}
