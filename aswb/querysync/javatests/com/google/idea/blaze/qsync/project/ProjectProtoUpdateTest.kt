package com.google.idea.blaze.qsync.project

import com.google.common.truth.Expect
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import java.nio.file.Path
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ProjectProtoUpdateTest {

  @get:Rule
  val expect: Expect = Expect.create()

  @Test
  fun testAddLibrary() {
    val update = ProjectProtoUpdate(ProjectProto.Project.getDefaultInstance())
    update.library(Label.of("//foo:bar")) {
      addClassJars(listOf(workspaceRelative("foo/foo.jar")))
      addSourceJars(listOf(workspaceRelative("foo/foo-src.jar")))
    }
    val project = update.build()

    val expectedProject = ProjectProto.Project(
      modules = listOf(
        ProjectProto.Module(
          name = ".workspace",
          isAndroidModule = false,
          contentEntries = emptyMap(),
          androidResourceDirectories = emptyList(),
          androidSourcePackages = emptyList(),
          androidCustomPackages = emptyList(),
          androidExternalLibraries = emptyList(),
          kotlinCompilerFlags = emptyList(),
        )
      ),
      libraries = mapOf(
        Label.of("//foo:bar") to ProjectProto.Library(
          name = Label.of("//foo:bar"),
          classesJarList = listOf(workspaceRelative("foo/foo.jar")),
          sourcesList = listOf(workspaceRelative("foo/foo-src.jar"))
        )
      ),
      artifactDirectories = ProjectProto.ArtifactDirectories.getDefaultInstance(),
      ccWorkspace = ProjectProto.CcWorkspace.getDefaultInstance(),
      activeLanguages = emptySet()
    )
    expect.that(project).isEqualTo(expectedProject)
  }

  @Test
  fun testAddModule() {
    val update = ProjectProtoUpdate(ProjectProto.Project.getDefaultInstance())
    update.module(Label.of("//foo:bar")) {
      contentEntry(workspaceRelative("foo/bar")) {
        addSourceRoot(workspaceRelative("foo/bar/java"), "com.google.foo", isTest = false, isGenerated = false)
        addExcludes(listOf(workspaceRelative("foo/bar/baz")))
      }
    }
    val project = update.build()

    val expectedProject = ProjectProto.Project(
      modules = listOf(
        ProjectProto.Module(
          name = ".workspace",
          isAndroidModule = false,
          contentEntries = mapOf(
            workspaceRelative("foo/bar") to ProjectProto.ContentEntry(
              root = workspaceRelative("foo/bar"),
              sourceFolders = listOf(
                ProjectProto.SourceFolder(
                  projectPath = workspaceRelative("foo/bar/java"),
                  isGenerated = false,
                  isTest = false,
                  packagePrefix = "com.google.foo"
                )
              ),
              excludes = listOf(workspaceRelative("foo/bar/baz"))
            )
          ),
          androidResourceDirectories = emptyList(),
          androidSourcePackages = emptyList(),
          androidCustomPackages = emptyList(),
          androidExternalLibraries = emptyList(),
          kotlinCompilerFlags = emptyList(),
        )
      ),
      libraries = emptyMap(),
      artifactDirectories = ProjectProto.ArtifactDirectories.getDefaultInstance(),
      ccWorkspace = ProjectProto.CcWorkspace.getDefaultInstance(),
      activeLanguages = emptySet()
    )
    expect.that(project).isEqualTo(expectedProject)
  }

  @Test
  fun testAddAndroidModuleWithResourcesAndCustomPackage() {
    val update = ProjectProtoUpdate(ProjectProto.Project.getDefaultInstance())
    update.module(Label.of("//foo:android_app")) {
      markAsAndroidModule()
      addAndroidResourceDirectories(listOf(workspaceRelative("foo/res")))
      addAndroidCustomPackage("com.example.custom")
    }
    val project = update.build()

    val expectedProject = ProjectProto.Project(
      modules = listOf(
        ProjectProto.Module(
          name = ".workspace",
          isAndroidModule = true,
          contentEntries = emptyMap(),
          androidResourceDirectories = listOf(workspaceRelative("foo/res")),
          androidSourcePackages = emptyList(),
          androidCustomPackages = listOf("com.example.custom"),
          androidExternalLibraries = emptyList(),
          kotlinCompilerFlags = emptyList(),
        )
      ),
      libraries = emptyMap(),
      artifactDirectories = ProjectProto.ArtifactDirectories.getDefaultInstance(),
      ccWorkspace = ProjectProto.CcWorkspace.getDefaultInstance(),
      activeLanguages = emptySet()
    )
    expect.that(project).isEqualTo(expectedProject)
  }
}

private fun workspaceRelative(path: String): ProjectPath.SourceCodeRepositoryRelativeProjectPath {
  return ProjectPath.workspaceRelative(Path.of(path), ProjectPath.ExternalRepositoryFinder.createEmptyForTests())
}
