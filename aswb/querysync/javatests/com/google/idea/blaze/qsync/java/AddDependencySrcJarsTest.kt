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
package com.google.idea.blaze.qsync.java

import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth
import com.google.idea.blaze.common.Label.Companion.of
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot
import com.google.idea.blaze.qsync.QuerySyncTestUtils
import com.google.idea.blaze.qsync.TestDataSyncRunner
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.testdata.TestData
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AddDependencySrcJarsTest {
  @get:Rule
  val tempDir: TemporaryFolder = TemporaryFolder()
  private var workspaceRoot: Path? = null
  private var pathResolver: ProjectPath.Resolver? = null
  private val syncer = TestDataSyncRunner(
    NoopContext(), QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER
  )
  private val original: QuerySyncProjectSnapshot =
    syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY)

  @Before
  @Throws(IOException::class)
  fun createDirs() {
    workspaceRoot = tempDir.newFolder("workspace").toPath()
    pathResolver =
      ProjectPath.Resolver.create(workspaceRoot, tempDir.newFolder("project").toPath())
  }

  @Test
  @Throws(Exception::class)
  fun no_deps_built() {
    val addSrcJars =
      AddDependencySrcJars(
        original.queryData().projectDefinition(),
        pathResolver!!,
        SrcJarInnerPathFinder(PackageStatementParser())
      )
    no_deps_built(addSrcJars)
  }

  @Throws(Exception::class)
  private fun no_deps_built(addSrcJars: AddDependencySrcJars) {
    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())

    addSrcJars.update(update, ArtifactTracker.State.EMPTY, NoopContext())

    val newProject = update.build()

    Truth.assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList())
    Truth.assertThat(newProject.getModulesList()).isEqualTo(original.project().getModulesList())
    Truth.assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keys).isEmpty()
  }

  @Test
  @Throws(Exception::class)
  fun external_srcjar_added() {
    val addSrcJars =
      AddDependencySrcJars(
        original.queryData().projectDefinition(),
        pathResolver!!,
        SrcJarInnerPathFinder(PackageStatementParser())
      )
    external_srcjar_added(
      addSrcJars,
      ProjectProto.Library.newBuilder().setName("//java/com/google/common/collect:collect")
        .addSources(
          ProjectProto.LibrarySource.newBuilder()
            .setSrcjar(
              ProjectProto.ProjectPath.newBuilder()
                .setBase(ProjectProto.ProjectPath.Base.WORKSPACE)
                .setPath("source/path/external.srcjar")
                .setInnerPath("root")
            )
            .build()
        )
        .build()
    )
  }

  @Throws(Exception::class)
  private fun external_srcjar_added(
    addSrcJars: AddDependencySrcJars,
    vararg libraries: ProjectProto.Library?
  ) {
    ZipOutputStream(
      FileOutputStream(
        Files.createDirectories(workspaceRoot!!.resolve("source/path"))
          .resolve("external.srcjar")
          .toFile()
      )
    ).use { zos ->
      zos.putNextEntry(ZipEntry("root/com/pkg/Class.java"))
      zos.write("package com.pkg;\nclass Class {}".toByteArray(StandardCharsets.UTF_8))
    }
    val artifactState =
      ArtifactTracker.State.forJavaArtifacts(
        JavaArtifactInfo.empty(of("//java/com/google/common/collect:collect")).toBuilder()
          .setSrcJars(ImmutableSet.of(Path.of("source/path/external.srcjar")))
          .build()
      )

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())

    addSrcJars.update(update, artifactState, NoopContext())

    val newProject = update.build()

    Truth.assertThat(newProject.getLibraryList()).containsExactly(*libraries)
  }
}
