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
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.NoopContext;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.MockArtifact;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.QuerySyncTestUtils;
import com.google.idea.blaze.qsync.TestDataSyncRunner;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.DependencyBuildContext;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents;
import com.google.idea.blaze.qsync.project.ProjectProto.ContentEntry;
import com.google.idea.blaze.qsync.project.ProjectProto.Module;
import com.google.idea.blaze.qsync.testdata.TestData;
import com.google.protobuf.TextFormat;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class AddProjectGenSrcsTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock public BuildArtifactCache cache;

  @Mock Context<?> context;

  private final TestDataSyncRunner syncer =
      new TestDataSyncRunner(new NoopContext(), QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER);

  @Test
  public void generated_source_added() throws Exception {
    TestData testData = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY;
    QuerySyncProjectSnapshot original = syncer.sync(testData);

    when(cache.get("gensrcdigest"))
        .thenReturn(
            Optional.of(
                immediateFuture(
                    new MockArtifact(
                        ByteSource.wrap("package com.org;\nclass Class {}".getBytes(UTF_8))))));

    ArtifactTracker.State artifactState =
        ArtifactTracker.State.forJavaArtifacts(
            JavaArtifactInfo.empty(testData.getAssumedOnlyLabel()).toBuilder()
                .setGenSrcs(
                    ImmutableList.of(
                        BuildArtifact.create(
                            "gensrcdigest",
                            Path.of("output/path/com/org/Class.java"),
                            testData.getAssumedOnlyLabel())))
                .build());

    AddProjectGenSrcs addGensrcs =
        new AddProjectGenSrcs(
            original.queryData().projectDefinition(),
            cache,
            new PackageStatementParser());

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), context);
    addGensrcs.update(update, artifactState);
    ProjectProto.Project newProject = update.build();

    Module workspace = newProject.getModules(0);
    // check our above assumption:
    assertThat(workspace.getName()).isEqualTo(".workspace");
    assertThat(workspace.getContentEntriesList())
        .contains(
            TextFormat.parse(
                Joiner.on("\n")
                    .join(
                        "root {",
                        "      path: \".bazel/gensrc/java\"",
                        "      base: PROJECT",
                        "    }",
                        "    sources {",
                        "      is_generated: true",
                        "      project_path {",
                        "        path: \".bazel/gensrc/java\"",
                        "        base: PROJECT",
                        "      }",
                        "    }"),
                ContentEntry.class));
    assertThat(newProject.getArtifactDirectories().getDirectoriesMap())
        .containsEntry(
            ".bazel/gensrc/java",
            TextFormat.parse(
                Joiner.on("\n")
                    .join(
                        "contents {",
                        "      key: \"com/org/Class.java\"",
                        "      value {",
                        "        transform: COPY",
                        "        build_artifact {",
                        "          digest: \"gensrcdigest\"",
                        "        }",
                        "        target: \"" + testData.getAssumedOnlyLabel() + "\"",
                        "      }",
                        "    }"),
                ArtifactDirectoryContents.class));
    verify(context, never()).setHasWarnings();
  }

  @Test
  public void conflict_last_build_taken() throws Exception {
    TestData testData = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY;
    QuerySyncProjectSnapshot original = syncer.sync(testData);

    when(cache.get("gensrc1"))
        .thenReturn(
            Optional.of(
                immediateFuture(
                    new MockArtifact(
                        ByteSource.wrap("package com.org;\nclass Class {}".getBytes(UTF_8))))));
    when(cache.get("gensrc2"))
        .thenReturn(
            Optional.of(
                immediateFuture(
                    new MockArtifact(
                        ByteSource.wrap("package com.org;\nclass Class {}".getBytes(UTF_8))))));

    Label testLabel = testData.getAssumedOnlyLabel();

    TargetBuildInfo genSrc1 =
        TargetBuildInfo.forJavaTarget(
            JavaArtifactInfo.empty(testLabel.siblingWithName("genSrc1")).toBuilder()
                .setGenSrcs(
                    ImmutableList.of(
                        BuildArtifact.create(
                            "gensrc1",
                            Path.of("output/path/com/org/Class.java"),
                            testLabel.siblingWithName("genSrc1"))))
                .build(),
            DependencyBuildContext.create(
                "abc-def", Instant.now().minusSeconds(60), Optional.empty()));

    Label genSrc2Label = testData.getAssumedOnlyLabel().siblingWithName("genSrc2");
    TargetBuildInfo genSrc2 =
        TargetBuildInfo.forJavaTarget(
            JavaArtifactInfo.empty(testLabel.siblingWithName("genSrc2")).toBuilder()
                .setGenSrcs(
                    ImmutableList.of(
                        BuildArtifact.create(
                            "gensrc2",
                            Path.of("output/otherpath/com/org/Class.java"),
                            genSrc2Label)))
                .build(),
            DependencyBuildContext.create("abc-def", Instant.now(), Optional.empty()));

    ArtifactTracker.State artifactState =
        ArtifactTracker.State.create(
            ImmutableMap.of(genSrc1.label(), genSrc1, genSrc2.label(), genSrc2), ImmutableMap.of());

    AddProjectGenSrcs addGenSrcs =
        new AddProjectGenSrcs(
            original.queryData().projectDefinition(),
            cache,
            new PackageStatementParser());

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), context);
    addGenSrcs.update(update, artifactState);
    ProjectProto.Project newProject = update.build();

    Module workspace = newProject.getModules(0);
    // check our above assumption:
    assertThat(workspace.getName()).isEqualTo(".workspace");

    assertThat(workspace.getContentEntriesList())
        .contains(
            TextFormat.parse(
                Joiner.on("\n")
                    .join(
                        "root {",
                        "      path: \".bazel/gensrc/java\"",
                        "      base: PROJECT",
                        "    }",
                        "    sources {",
                        "      is_generated: true",
                        "      project_path {",
                        "        path: \".bazel/gensrc/java\"",
                        "        base: PROJECT",
                        "      }",
                        "    }"),
                ContentEntry.class));
    assertThat(newProject.getArtifactDirectories().getDirectoriesMap())
        .containsEntry(
            ".bazel/gensrc/java",
            TextFormat.parse(
                Joiner.on("\n")
                    .join(
                        "contents {",
                        "      key: \"com/org/Class.java\"",
                        "      value {",
                        "        transform: COPY",
                        "        build_artifact {",
                        "          digest: \"gensrc2\"",
                        "        }",
                        "        target: \"" + genSrc2Label + "\"",
                        "      }",
                        "    }"),
                ArtifactDirectoryContents.class));
    verify(context).setHasWarnings();
  }

  @Test
  public void conflict_same_digest_ignored() throws Exception {
    TestData testData = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY;
    QuerySyncProjectSnapshot original = syncer.sync(testData);
    Label testLabel = testData.getAssumedOnlyLabel();

    when(cache.get("samedigest"))
        .thenReturn(
            Optional.of(
                immediateFuture(
                    new MockArtifact(
                        ByteSource.wrap("package com.org;\nclass Class {}".getBytes(UTF_8))))));

    TargetBuildInfo genSrc1 =
        TargetBuildInfo.forJavaTarget(
            JavaArtifactInfo.empty(testLabel.siblingWithName("genSrc1")).toBuilder()
                .setGenSrcs(
                    ImmutableList.of(
                        BuildArtifact.create(
                            "samedigest",
                            Path.of("output/path/com/org/Class.java"),
                            testLabel.siblingWithName("genSrc1"))))
                .build(),
            DependencyBuildContext.create(
                "abc-def", Instant.now().minusSeconds(60), Optional.empty()));

    Label genSrc2Label = testData.getAssumedOnlyLabel().siblingWithName("genSrc2");
    TargetBuildInfo genSrc2 =
        TargetBuildInfo.forJavaTarget(
            JavaArtifactInfo.empty(testLabel.siblingWithName("genSrc2")).toBuilder()
                .setGenSrcs(
                    ImmutableList.of(
                        BuildArtifact.create(
                            "samedigest",
                            Path.of("output/otherpath/com/org/Class.java"),
                            genSrc2Label)))
                .build(),
            DependencyBuildContext.create("abc-def", Instant.now(), Optional.empty()));

    ArtifactTracker.State artifactState =
        ArtifactTracker.State.create(
            ImmutableMap.of(genSrc1.label(), genSrc1, genSrc2.label(), genSrc2), ImmutableMap.of());

    AddProjectGenSrcs addGenSrcs =
        new AddProjectGenSrcs(
            original.queryData().projectDefinition(),
            cache,
            new PackageStatementParser());

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), context);
    addGenSrcs.update(update, artifactState);
    verify(context, never()).setHasWarnings();
  }
}
