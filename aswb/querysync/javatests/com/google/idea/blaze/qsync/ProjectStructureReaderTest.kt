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
package com.google.idea.blaze.qsync

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.common.Output
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.qsync.project.FileExtensions
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectStructureData
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.google.idea.blaze.qsync.query.PackageSet
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ProjectStructureReaderTest {

  @get:Rule val temporaryFolder = TemporaryFolder()

  private lateinit var workspaceRoot: Path
  private val reader = ProjectStructureReader.create(FileExtensions())
  private val context = NoopContext()

  private class CapturingContext : NoopContext() {
    val outputs = mutableListOf<String>()

    override fun <T : Output> output(output: T) {
      if (output is PrintOutput) {
        outputs.add(output.text())
      }
    }
  }

  @Before
  fun setUp() {
    workspaceRoot = temporaryFolder.root.toPath()
  }

  private fun createProjectDefinition(
    includes: Set<String>,
    excludes: Set<String> = emptySet(),
  ): ProjectDefinition {
    return ProjectDefinition(
      projectIncludes = includes.map { Path.of(it) }.toSet(),
      projectExcludes = excludes.map { Path.of(it) }.toSet(),
      deriveTargetsFromDirectories = false,
      targetPatterns = emptyList(),
      isAndroidWorkspace = true,
      languageClasses = emptySet(),
      testSources = emptySet(),
      systemExcludes = emptySet(),
    )
  }

  private fun createFile(projectRelativePath: String, content: String = "") {
    val absPath = workspaceRoot.resolve(projectRelativePath)
    // Create parent directories, but don't fail if it already exists (e.g., due to symlinks)
    val parent = absPath.parent
    try {
      parent.createDirectories()
    } catch (e: java.nio.file.FileAlreadyExistsException) {
      // Ignore, this can happen in symlink loops
    }
    try {
      absPath.writeText(content)
    } catch (e: java.nio.file.FileSystemException) {
      // Ignore, this can happen in symlink loops
    }
  }

  private fun expectedStructure(
    packages: Set<String> = emptySet(),
    javaSources: List<String> = emptyList(),
    nonJavaSources: List<String> = emptyList(),
    languages: Set<QuerySyncLanguage> = emptySet(),
  ): ProjectStructureData {
    return ProjectStructureData(
      packages = PackageSet(packages.map { Path.of(it) }.toSet()),
      javaSourceFiles = javaSources.map { Path.of(it) }.sorted(),
      nonJavaSourceFiles = nonJavaSources.map { Path.of(it) }.sorted(),
      activeLanguages = languages,
    )
  }

  // Helper to compare ProjectStructureData instances, ignoring list order.
  private fun assertStructureEquals(actual: ProjectStructureData, expected: ProjectStructureData) {
    assertThat(actual.packages.asPathSet()).containsExactlyElementsIn(expected.packages.asPathSet())
    assertThat(actual.javaSourceFiles).containsExactlyElementsIn(expected.javaSourceFiles)
    assertThat(actual.nonJavaSourceFiles).containsExactlyElementsIn(expected.nonJavaSourceFiles)
    assertThat(actual.activeLanguages).containsExactlyElementsIn(expected.activeLanguages)
  }

  @Test
  fun singlePackage() {
    createFile("java/com/example/BUILD")
    createFile("java/com/example/MyClass.java")
    createFile("java/com/example/MyClass.kt")
    createFile("java/com/example/data.txt")

    val projectDefinition = createProjectDefinition(setOf("java"))
    val structure = reader.read(context, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = setOf("java/com/example"),
        javaSources = listOf("java/com/example/MyClass.java", "java/com/example/MyClass.kt"),
        nonJavaSources = listOf(),
        languages = setOf(QuerySyncLanguage.JVM),
      )
    assertStructureEquals(structure, expected)
  }

  @Test
  fun multiplePackages() {
    createFile("java/com/example/one/BUILD")
    createFile("java/com/example/one/One.java")
    createFile("java/com/example/two/BUILD")
    createFile("java/com/example/two/Two.kt")

    val projectDefinition = createProjectDefinition(setOf("java"))
    val structure = reader.read(context, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = setOf("java/com/example/one", "java/com/example/two"),
        javaSources = listOf("java/com/example/one/One.java", "java/com/example/two/Two.kt"),
        languages = setOf(QuerySyncLanguage.JVM),
      )
    assertStructureEquals(structure, expected)
  }

  @Test
  fun nestedPackages() {
    createFile("java/com/example/BUILD")
    createFile("java/com/example/Parent.java")
    createFile("java/com/example/child/BUILD")
    createFile("java/com/example/child/Child.java")

    val projectDefinition = createProjectDefinition(setOf("java"))
    val structure = reader.read(context, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = setOf("java/com/example", "java/com/example/child"),
        javaSources = listOf("java/com/example/Parent.java", "java/com/example/child/Child.java"),
        languages = setOf(QuerySyncLanguage.JVM),
      )
    assertStructureEquals(structure, expected)
  }

  @Test
  fun noBuildFileInInclude() {
    createFile("java/com/example/NoBuild.java")

    val projectDefinition = createProjectDefinition(setOf("java"))
    val structure = reader.read(context, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = emptySet(),
        javaSources = listOf("java/com/example/NoBuild.java"),
        nonJavaSources = emptyList(),
        languages = setOf(QuerySyncLanguage.JVM),
      )
    assertStructureEquals(structure, expected)
  }

  @Test
  fun excludes() {
    createFile("java/com/example/BUILD")
    createFile("java/com/example/Inc.java")
    createFile("java/com/example/excluded/BUILD")
    createFile("java/com/example/excluded/Exc.java")
    createFile("javatests/com/example/BUILD")

    val projectDefinition =
      createProjectDefinition(setOf("java", "javatests"), setOf("java/com/example/excluded"))
    val structure = reader.read(context, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = setOf("java/com/example", "javatests/com/example"),
        javaSources = listOf("java/com/example/Inc.java"),
        languages = setOf(QuerySyncLanguage.JVM),
      )
    assertStructureEquals(structure, expected)
  }

  @Test
  fun excludeSubdirectoryOfInclude() {
    createFile("java/com/example/BUILD")
    createFile("java/com/example/Inc.java")
    createFile("java/com/example/excluded/BUILD")
    createFile("java/com/example/excluded/Exc.java")
    createFile("java/com/example/excluded/deep/BUILD")
    createFile("java/com/example/excluded/deep/Deep.java")

    val projectDefinition =
      createProjectDefinition(setOf("java/com/example"), setOf("java/com/example/excluded/deep"))
    val structure = reader.read(context, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = setOf("java/com/example", "java/com/example/excluded"),
        javaSources = listOf("java/com/example/Inc.java", "java/com/example/excluded/Exc.java"),
        languages = setOf(QuerySyncLanguage.JVM),
      )
    assertStructureEquals(structure, expected)
  }

  @Test
  fun includeSubdirectory() {
    createFile("java/com/example/foo/BUILD")
    createFile("java/com/example/foo/Foo.java")
    createFile("java/com/example/bar/BUILD") // Should not be included

    val projectDefinition = createProjectDefinition(setOf("java/com/example/foo"))
    val structure = reader.read(context, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = setOf("java/com/example/foo"),
        javaSources = listOf("java/com/example/foo/Foo.java"),
        languages = setOf(QuerySyncLanguage.JVM),
      )
    assertStructureEquals(structure, expected)
  }

  @Test
  fun emptyIncludes() {
    createFile("java/com/example/BUILD")
    val projectDefinition = createProjectDefinition(emptySet())
    val structure = reader.read(context, workspaceRoot, projectDefinition)
    assertThat(structure).isEqualTo(ProjectStructureData.EMPTY)
  }

  @Test
  fun nestedWorkspace_moduleBazel() {
    createFile("java/com/example/BUILD")
    createFile("java/com/example/MyClass.java")
    // Nested workspace
    createFile("java/com/example/nested/MODULE.bazel")
    createFile("java/com/example/nested/BUILD")
    createFile("java/com/example/nested/NestedClass.java")

    val projectDefinition = createProjectDefinition(setOf("java"))
    val structure = reader.read(context, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = setOf("java/com/example"),
        javaSources = listOf("java/com/example/MyClass.java"),
        languages = setOf(QuerySyncLanguage.JVM),
      )
    assertStructureEquals(structure, expected)
  }

  @Test
  fun nestedWorkspace_workspaceFile() {
    createFile("java/com/example/BUILD")
    createFile("java/com/example/MyClass.java")
    // Nested workspace
    createFile("java/com/example/nested/WORKSPACE")
    createFile("java/com/example/nested/BUILD")
    createFile("java/com/example/nested/NestedClass.java")

    val projectDefinition = createProjectDefinition(setOf("java"))
    val structure = reader.read(context, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = setOf("java/com/example"),
        javaSources = listOf("java/com/example/MyClass.java"),
        languages = setOf(QuerySyncLanguage.JVM),
      )
    assertStructureEquals(structure, expected)
  }

  @Test
  fun sourcesInSubdirectory() {
    createFile("java/com/example/BUILD")
    createFile("java/com/example/MyClass.java")
    createFile("java/com/example/subdir/AnotherClass.kt")
    createFile("java/com/example/another/BUILD") // Separate package
    createFile("java/com/example/another/Other.java")

    val projectDefinition = createProjectDefinition(setOf("java"))
    val structure = reader.read(context, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = setOf("java/com/example", "java/com/example/another"),
        javaSources =
          listOf(
            "java/com/example/MyClass.java",
            "java/com/example/subdir/AnotherClass.kt",
            "java/com/example/another/Other.java",
          ),
        languages = setOf(QuerySyncLanguage.JVM),
      )
    assertStructureEquals(structure, expected)
  }

  @Test
  fun orphanedSourceFiles() {
    createFile("java/com/example/BUILD")
    createFile("java/com/example/MyClass.java")
    createFile("java/com/orphan/Orphan.kt") // No BUILD in com/orphan
    createFile("another/RootOrphan.java") // No BUILD in another

    val projectDefinition = createProjectDefinition(setOf("java", "another"))

    val capturingContext = CapturingContext()

    val structure = reader.read(capturingContext, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = setOf("java/com/example"),
        javaSources =
          listOf(
            "java/com/example/MyClass.java",
            "java/com/orphan/Orphan.kt",
            "another/RootOrphan.java",
          ),
        languages = setOf(QuerySyncLanguage.JVM),
      )
    assertStructureEquals(structure, expected)

    val logs = capturingContext.outputs.joinToString("\n")
    assertThat(logs).doesNotContain("Warning: Found")
  }

  @Test
  fun deeplyNestedExclude() {
    createFile("java/com/example/BUILD")
    createFile("java/com/example/Inc.java")
    createFile("java/com/example/excluded/BUILD")
    createFile("java/com/example/excluded/Exc.java")
    createFile("java/com/example/excluded/deep/BUILD")
    createFile("java/com/example/excluded/deep/Deep.java")

    val projectDefinition =
      createProjectDefinition(setOf("java"), setOf("java/com/example/excluded"))
    val structure = reader.read(context, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = setOf("java/com/example"),
        javaSources = listOf("java/com/example/Inc.java"),
        languages = setOf(QuerySyncLanguage.JVM),
            )
    assertStructureEquals(structure, expected)
  }

  @Test
  fun unreadableSubdirectory() {
    createFile("java/com/example/BUILD")
    createFile("java/com/example/MyClass.java")
    val unreadableDir = workspaceRoot.resolve("java/com/example/unreadable")
    unreadableDir.createDirectories()
    createFile("java/com/example/unreadable/Secret.java")

    // Make the directory unreadable
    val success = unreadableDir.toFile().setReadable(false)
    assertThat(success).isTrue()

    val projectDefinition = createProjectDefinition(setOf("java"))
    val capturingContext = CapturingContext()
    val structure = reader.read(capturingContext, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = setOf("java/com/example"),
        javaSources = listOf("java/com/example/MyClass.java"),
        languages = setOf(QuerySyncLanguage.JVM),
      )
    assertStructureEquals(structure, expected)

    val logs = capturingContext.outputs.joinToString("\n")
    assertThat(logs).contains("Error reading directory ${unreadableDir.toRealPath()}")

    // Restore readability to allow cleanup
    unreadableDir.toFile().setReadable(true)
  }

  @Test
  fun buildFileInIncludeRoot() {
    createFile("java/BUILD")
    createFile("java/RootClass.java")
    createFile("java/com/example/BUILD")
    createFile("java/com/example/MyClass.java")

    val projectDefinition = createProjectDefinition(setOf("java"))
    val structure = reader.read(context, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = setOf("java", "java/com/example"),
        javaSources = listOf("java/RootClass.java", "java/com/example/MyClass.java"),
        languages = setOf(QuerySyncLanguage.JVM),
      )
    assertStructureEquals(structure, expected)
  }

  @Test
  fun fileTypeDetection() {
    createFile("java/com/example/BUILD")
    createFile("java/com/example/MyClass.java")
    createFile("java/com/example/MyModule.kt")
    createFile("java/com/example/native/stuff.c")
    createFile("java/com/example/native/stuff.h")
    createFile("java/com/example/native/other.cc")
    createFile("java/com/example/native/another.cpp")
    createFile("java/com/example/native/header.hpp")
    createFile("java/com/example/myproto.proto")
    createFile("java/com/example/data.txt")
    createFile("java/com/example/config.xml") // Should be ignored

    val projectDefinition = createProjectDefinition(setOf("java"))
    val structure = reader.read(context, workspaceRoot, projectDefinition)

    val expected =
      expectedStructure(
        packages = setOf("java/com/example"),
        javaSources = listOf("java/com/example/MyClass.java", "java/com/example/MyModule.kt"),
        nonJavaSources =
          listOf(
            "java/com/example/native/stuff.c",
            "java/com/example/native/stuff.h",
            "java/com/example/native/other.cc",
            "java/com/example/native/another.cpp",
            "java/com/example/native/header.hpp",
            "java/com/example/myproto.proto",
          ),
        languages = setOf(QuerySyncLanguage.JVM, QuerySyncLanguage.CC),
      )
    assertStructureEquals(structure, expected)
  }
}
