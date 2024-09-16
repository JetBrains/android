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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.NoopContext;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.common.artifact.MockArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.QuerySyncTestUtils;
import com.google.idea.blaze.qsync.TestDataSyncRunner;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.deps.DependencyBuildContext;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation.CachedArtifactProvider;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.Library;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath.Base;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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

  @Test
  public void no_deps_built() throws Exception {

    QuerySyncProjectSnapshot original = syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);

    AddDependencyGenSrcsJars addGenSrcJars =
        new AddDependencyGenSrcsJars(
            ImmutableList::of,
            original.queryData().projectDefinition(),
            getCachedArtifactProvider(cache, ImmutableMap.of()),
            new SrcJarInnerPathFinder(new PackageStatementParser()));

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());

    addGenSrcJars.update(update);

    ProjectProto.Project newProject = update.build();

    assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList());
    assertThat(newProject.getModulesList()).isEqualTo(original.project().getModulesList());
    assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keySet()).isEmpty();
  }

  @Test
  public void project_gensrcs_ignored() throws Exception {

    TestData testProject = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY;

    QuerySyncProjectSnapshot original = syncer.sync(testProject);

    TargetBuildInfo builtDep =
        TargetBuildInfo.forJavaTarget(
            JavaArtifactInfo.empty(testProject.getAssumedOnlyLabel()).toBuilder()
                .setGenSrcs(
                    ImmutableList.of(
                        BuildArtifact.create(
                            "srcjardigest",
                            Path.of("output/path/to/in_project.srcjar"),
                            testProject.getAssumedOnlyLabel())))
                .build(),
            DependencyBuildContext.create("abc-def", Instant.now(), Optional.empty()));

    AddDependencyGenSrcsJars addGenSrcJars =
        new AddDependencyGenSrcsJars(
            () -> ImmutableList.of(builtDep),
            original.queryData().projectDefinition(),
            getCachedArtifactProvider(cache, ImmutableMap.of()),
            new SrcJarInnerPathFinder(new PackageStatementParser()));

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());
    addGenSrcJars.update(update);
    ProjectProto.Project newProject = update.build();

    verify(cache, never()).get(ArgumentMatchers.any());

    assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList());
    assertThat(newProject.getModulesList()).isEqualTo(original.project().getModulesList());
    assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keySet()).isEmpty();
  }

  @Test
  public void external_gensrcs_added() throws Exception {
    QuerySyncProjectSnapshot original = syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);

    ByteArrayOutputStream zipFile = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(zipFile)) {
      zos.putNextEntry(new ZipEntry("root/com/org/Class.java"));
      zos.write("package com.org;\npublic class Class{}".getBytes(UTF_8));
    }

    when(cache.get("srcjardigest"))
        .thenReturn(
            Optional.of(
                Futures.immediateFuture(new MockArtifact(ByteSource.wrap(zipFile.toByteArray())))));

    TargetBuildInfo builtDep =
        TargetBuildInfo.forJavaTarget(
            JavaArtifactInfo.empty(Label.of("//java/com/google/common/collect:collect")).toBuilder()
                .setGenSrcs(
                    ImmutableList.of(
                        BuildArtifact.create(
                            "srcjardigest",
                            Path.of("output/path/to/external.srcjar"),
                            Label.of("//java/com/google/common/collect:collect"))))
                .build(),
            DependencyBuildContext.create("abc-def", Instant.now(), Optional.empty()));

    AddDependencyGenSrcsJars addGenSrcJars =
        new AddDependencyGenSrcsJars(
            () -> ImmutableList.of(builtDep),
            original.queryData().projectDefinition(),
            getCachedArtifactProvider(cache, ImmutableMap.of(ArtifactDirectories.DEFAULT, ImmutableMap.of())),
            new SrcJarInnerPathFinder(new PackageStatementParser()));

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());
    addGenSrcJars.update(update);
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
                .build());
  }

  @Test
  public void external_gensrcs_buildcache_missing_artifact_added() throws Exception {
    QuerySyncProjectSnapshot original = syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);

    ByteArrayOutputStream zipFile = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(zipFile)) {
      zos.putNextEntry(new ZipEntry("root/com/org/Class.java"));
      zos.write("package com.org;\npublic class Class{}".getBytes(UTF_8));
    }

    when(cache.get("srcjardigest")).thenReturn(Optional.empty());

    TargetBuildInfo builtDep =
      TargetBuildInfo.forJavaTarget(
        JavaArtifactInfo.empty(Label.of("//java/com/google/common/collect:collect")).toBuilder()
          .setGenSrcs(
            ImmutableList.of(
              BuildArtifact.create(
                "srcjardigest",
                Path.of("output/path/to/external.srcjar"),
                Label.of("//java/com/google/common/collect:collect"))))
          .build(),
        DependencyBuildContext.create("abc-def", Instant.now(), Optional.empty()));

    AddDependencyGenSrcsJars addGenSrcJars =
      new AddDependencyGenSrcsJars(
        () -> ImmutableList.of(builtDep),
        original.queryData().projectDefinition(),
        getCachedArtifactProvider(cache, ImmutableMap.of(ArtifactDirectories.DEFAULT,
                                                         ImmutableMap.of("srcjardigest",
                                                                         new MockArtifact(ByteSource.wrap(zipFile.toByteArray()))))),
        new SrcJarInnerPathFinder(new PackageStatementParser()));

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());
    addGenSrcJars.update(update);
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
          .build());
  }

  private CachedArtifactProvider getCachedArtifactProvider(BuildArtifactCache artifactCache,
                                                           Map<com.google.idea.blaze.qsync.project.ProjectPath, Map<String, MockArtifact>> existingArtifactDirectoriesContents) {
    return (buildArtifact, artifactDirectory) -> {
      if (artifactCache.get(buildArtifact.digest()).isPresent()) {
        return buildArtifact.blockingGetFrom(artifactCache);
      }
      if (existingArtifactDirectoriesContents.containsKey(artifactDirectory)) {
        MockArtifact mockArtifact = existingArtifactDirectoriesContents.get(artifactDirectory).get(buildArtifact.digest());
        if (mockArtifact != null) {
          return mockArtifact;
        }
      }
      throw new BuildException(
        "Artifact" + buildArtifact.artifactPath() + " missing from the cache: " + artifactCache + " and " + artifactDirectory);
    };
  }
}
