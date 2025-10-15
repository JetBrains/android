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
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.Label.Companion.of
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot
import com.google.idea.blaze.qsync.QuerySyncTestUtils
import com.google.idea.blaze.qsync.TestDataSyncRunner
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.DependencyBuildContext
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
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
import com.google.idea.common.experiments.ExperimentService
import com.google.idea.common.experiments.MockExperimentService
import com.google.idea.testing.IntellijRule
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AddDependencySrcJarsTest {
  @get:Rule
  val tempDir: TemporaryFolder = TemporaryFolder()

  companion object {
    @JvmField
    @ClassRule
    val intellij = IntellijRule()
  }

  private lateinit var workspaceRoot: Path
  private var pathResolver: ProjectPath.Resolver? = null
  private val syncer = TestDataSyncRunner(
    NoopContext(), QuerySyncTestUtils.PATH_INFERRING_PREFIX_READER)
  private lateinit var original: QuerySyncProjectSnapshot

  @Before
  @Throws(IOException::class)
  fun setUp() {
    intellij.registerApplicationService(ExperimentService::class.java, MockExperimentService())
    original = syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY)
    workspaceRoot = tempDir.newFolder("workspace").toPath()
    val projectDirPath = tempDir.newFolder("project").toPath()
    pathResolver =
      ProjectPath.Resolver.create(workspaceRoot, projectDirPath, projectDirPath.resolve(".external"))
  }

  @Test
  @Throws(Exception::class)
  fun no_deps_built() {
    val addSrcJars =
      AddDependencySrcJars(
        original.queryData.projectDefinition(),
        pathResolver!!,
        SrcJarInnerPathFinder(PackageStatementParser())
      )
    no_deps_built(addSrcJars)
  }

  @Throws(Exception::class)
  private fun no_deps_built(addSrcJars: AddDependencySrcJars) {
    val update =
      ProjectProtoUpdate(original.project)

    addSrcJars.update(update, original.graph, ArtifactTracker.State.EMPTY, NoopContext())

    val newProject = update.build()

    Truth.assertThat(newProject.libraries).isEqualTo(original.project.libraries)
    Truth.assertThat(newProject.modules).isEqualTo(original.project.modules)
    Truth.assertThat(newProject.artifactDirectories.directoriesMap.keys).isEmpty()
  }

  @Test
  @Throws(Exception::class)
  fun external_srcjar_added() {
    val addSrcJars =
      AddDependencySrcJars(
        original.queryData.projectDefinition(),
        pathResolver!!,
        SrcJarInnerPathFinder(PackageStatementParser())
      )
    external_srcjar_added(
      addSrcJars,
      ProjectProto.Library(
        name = Label.of("//java/com/google/common/collect:collect"),
        classesJarList = emptyList(),
        sourcesList = listOf(
          ProjectPath.workspaceRelativeForTests(Path.of("source/path/external.srcjar")).withInnerJarPath(Path.of("root")))
      )
    )
  }

  @Throws(Exception::class)
  private fun external_srcjar_added(
    addSrcJars: AddDependencySrcJars,
    vararg libraries: ProjectProto.Library?
  ) {
    ZipOutputStream(
      FileOutputStream(
        Files.createDirectories(workspaceRoot.resolve("source/path"))
          .resolve("external.srcjar")
          .toFile()
      )
    ).use { zos ->
      zos.putNextEntry(ZipEntry("root/com/pkg/Class.java"))
      zos.write("package com.pkg;\nclass Class {}".toByteArray(StandardCharsets.UTF_8))
    }
    val artifactState =
      ArtifactTracker.State.forJavaArtifacts(
        DependencyBuildContext.NONE,
        JavaArtifactInfo.empty(of("//java/com/google/common/collect:collect")).toBuilder()
          .setSrcJars(setOf<ProjectPath>(ProjectPath.workspaceRelativeForTests(Path.of("source/path/external.srcjar"))))
          .build()
      )

    val update =
      ProjectProtoUpdate(original.project)

    addSrcJars.update(update, original.graph, artifactState, NoopContext())

    val newProject = update.build()

    Truth.assertThat(newProject.libraries.values).containsExactly(*libraries)
  }
}
