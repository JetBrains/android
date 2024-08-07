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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.NoopContext;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.QuerySyncTestUtils;
import com.google.idea.blaze.qsync.TestDataSyncRunner;
import com.google.idea.blaze.qsync.deps.DependencyBuildContext;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.Library;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath.Base;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AddDependencySrcJarsTest {

  @Rule public final TemporaryFolder tempDir = new TemporaryFolder();
  private Path workspaceRoot;
  private ProjectPath.Resolver pathResolver;

  private final TestDataSyncRunner syncer =
      new TestDataSyncRunner(
          new NoopContext(), QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER);

  @Before
  public void createDirs() throws IOException {
    workspaceRoot = tempDir.newFolder("workspace").toPath();
    pathResolver =
        ProjectPath.Resolver.create(workspaceRoot, tempDir.newFolder("project").toPath());
  }

  @Test
  public void no_deps_built() throws Exception {
    QuerySyncProjectSnapshot original = syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);

    AddDependencySrcJars addSrcJars =
        new AddDependencySrcJars(
            ImmutableList::of,
            original.queryData().projectDefinition(),
            pathResolver,
            new SrcJarInnerPathFinder(new PackageStatementParser()));

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());

    addSrcJars.update(update);

    ProjectProto.Project newProject = update.build();

    assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList());
    assertThat(newProject.getModulesList()).isEqualTo(original.project().getModulesList());
    assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keySet()).isEmpty();
  }

  @Test
  public void external_srcjar_added() throws Exception {
    QuerySyncProjectSnapshot original = syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);

    try (ZipOutputStream zos =
        new ZipOutputStream(
            new FileOutputStream(
                Files.createDirectories(workspaceRoot.resolve("source/path"))
                    .resolve("external.srcjar")
                    .toFile()))) {
      zos.putNextEntry(new ZipEntry("root/com/pkg/Class.java"));
      zos.write("package com.pkg;\nclass Class {}".getBytes(UTF_8));
    }

    TargetBuildInfo builtDep =
        TargetBuildInfo.forJavaTarget(
            JavaArtifactInfo.empty(Label.of("//java/com/google/common/collect:collect")).toBuilder()
                .setSrcJars(ImmutableSet.of(Path.of("source/path/external.srcjar")))
                .build(),
            DependencyBuildContext.create("abc-def", Instant.now(), Optional.empty()));

    AddDependencySrcJars addSrcJars =
        new AddDependencySrcJars(
            () -> ImmutableList.of(builtDep),
            original.queryData().projectDefinition(),
            pathResolver,
            new SrcJarInnerPathFinder(new PackageStatementParser()));

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());

    addSrcJars.update(update);

    ProjectProto.Project newProject = update.build();

    assertThat(newProject.getLibraryList()).hasSize(1);
    Library depsLib = newProject.getLibrary(0);
    assertThat(depsLib.getName()).isEqualTo(".dependencies");
    assertThat(depsLib.getSourcesList())
        .containsExactly(
            ProjectProto.LibrarySource.newBuilder()
                .setSrcjar(
                    ProjectProto.ProjectPath.newBuilder()
                        .setBase(Base.WORKSPACE)
                        .setPath("source/path/external.srcjar")
                        .setInnerPath("root"))
                .build());
  }
}
