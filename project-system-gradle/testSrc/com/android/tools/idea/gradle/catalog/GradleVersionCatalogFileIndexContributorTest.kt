// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.catalog

import com.google.common.truth.Truth.assertThat
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesWorkspaceModelNode
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesWorkspaceModelNodesProvider
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.util.PlatformIcons
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import java.io.File
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity
import org.jetbrains.plugins.gradle.model.projectModel.gradleBuilds
import org.jetbrains.plugins.gradle.model.versionCatalogs.GradleVersionCatalogEntity
import org.jetbrains.plugins.gradle.model.versionCatalogs.versionCatalogs
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GradleVersionCatalogFileIndexContributorTest {

  companion object {
    @ClassRule @JvmField val applicationRule = ApplicationRule()
  }

  @get:Rule val projectModel = ProjectModelRule()

  @get:Rule val disposableRule = DisposableRule()

  @get:Rule val tempDir = TemporaryFolder()

  private val project
    get() = projectModel.project

  /**
   * The test classpath of this module does not always load the Android plugin, so the test does not use `AndroidProjectRule`.
   * It registers the two extensions itself when the plugin did not.
   */
  @Before
  fun registerExtensions() {
    val contributors = WorkspaceFileIndexImpl.EP_NAME
    if (contributors.extensionList.none { it is GradleVersionCatalogFileIndexContributor }) {
      contributors.point.registerExtension(GradleVersionCatalogFileIndexContributor(), disposableRule.disposable)
    }
    val nodeProviders = ExternalLibrariesWorkspaceModelNodesProvider.EP
    if (nodeProviders.extensionList.none { it is GradleVersionCatalogNodesProvider }) {
      nodeProviders.point.registerExtension(GradleVersionCatalogNodesProvider(), disposableRule.disposable)
    }
  }

  @Test
  fun `an imported catalog is a library source and has a node`() {
    val catalog = createCatalog("cache/external.versions.toml")

    setLinkedProject(Build("project", "external" to catalog))

    assertThat(isInLibrarySource(catalog)).isTrue()
    val node = importedCatalogNodes().single()
    assertThat(node.roots).containsExactly(catalog)
    runReadActionBlocking { node.update() }
    assertThat(node.presentation.presentableText).isEqualTo("Imported Catalog: external")
    assertThat(node.presentation.getIcon(false)).isEqualTo(PlatformIcons.LIBRARY_ICON)
  }

  @Test
  fun `a catalog in its build is skipped`() {
    val catalog = createCatalog("project/gradle/libs.versions.toml")

    setLinkedProject(Build("project", "libs" to catalog))

    assertThat(isInLibrarySource(catalog)).isFalse()
    assertThat(importedCatalogNodes()).isEmpty()
  }

  @Test
  fun `a catalog in another build of the linked project is skipped`() {
    val catalog = createCatalog("project/gradle/libs.versions.toml")

    // buildSrc reads the catalog of the root build.
    setLinkedProject(Build("project"), Build("project/buildSrc", "libs" to catalog))

    assertThat(isInLibrarySource(catalog)).isFalse()
    assertThat(importedCatalogNodes()).isEmpty()
  }

  @Test
  fun `two builds that import one file give one node`() {
    val catalog = createCatalog("cache/external.versions.toml")

    setLinkedProject(Build("project", "external" to catalog), Build("project/included", "external" to catalog))

    assertThat(isInLibrarySource(catalog)).isTrue()
    assertThat(importedCatalogNodes().map { it.roots.toList() }).containsExactly(listOf(catalog))
  }

  @Test
  fun `the root leaves with the entity`() {
    val catalog = createCatalog("cache/external.versions.toml")
    setLinkedProject(Build("project", "external" to catalog))
    assertThat(isInLibrarySource(catalog)).isTrue()

    setLinkedProject(Build("project"))

    assertThat(isInLibrarySource(catalog)).isFalse()
    assertThat(importedCatalogNodes()).isEmpty()
  }

  @Test
  fun `a build added later hides its catalog`() {
    val catalog = createCatalog("other/gradle/libs.versions.toml")
    setLinkedProject(Build("project", "libs" to catalog))
    assertThat(isInLibrarySource(catalog)).isTrue()

    // The catalog entity does not change. Only the new build makes its file a file of the linked project.
    setLinkedProject(Build("project", "libs" to catalog), Build("other"))

    assertThat(isInLibrarySource(catalog)).isFalse()
    assertThat(importedCatalogNodes()).isEmpty()
  }

  /** A Gradle build in [directory], a path relative to the temporary directory, with its catalogs by name. */
  private class Build(val directory: String, vararg val catalogs: Pair<String, VirtualFile>)

  /** Makes the workspace model hold one linked Gradle project with [builds]. The first build is the root build. */
  private fun setLinkedProject(vararg builds: Build) {
    val urlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    fun directoryUrl(directory: String): VirtualFileUrl =
      urlManager.getOrCreateFromUrl(VfsUtilCore.pathToUrl(File(tempDir.root, directory).path))

    val builder = MutableEntityStorage.create()
    builder.addEntity(
      ExternalProjectEntity(File(tempDir.root, builds.first().directory).path, NonPersistentEntitySource) {
        gradleBuilds =
          builds.map { build ->
            GradleBuildEntity(build.directory, directoryUrl(build.directory), NonPersistentEntitySource) {
              versionCatalogs =
                build.catalogs.map { (name, file) ->
                  GradleVersionCatalogEntity(name, file.toVirtualFileUrl(urlManager), NonPersistentEntitySource)
                }
            }
          }
      }
    )
    runWriteActionAndWait {
      WorkspaceModel.getInstance(project).updateProjectModel("Set the linked Gradle project") { storage ->
        storage.replaceBySource({ it is NonPersistentEntitySource }, builder)
      }
    }
  }

  private fun createCatalog(relativePath: String): VirtualFile {
    val file = File(tempDir.root, relativePath)
    file.parentFile.mkdirs()
    file.writeText("[libraries]")
    return StandardFileSystems.local().refreshAndFindFileByPath(file.path)!!
  }

  private fun isInLibrarySource(file: VirtualFile): Boolean = runReadActionBlocking {
    ProjectFileIndex.getInstance(project).isInLibrarySource(file)
  }

  private fun importedCatalogNodes(): List<ExternalLibrariesWorkspaceModelNode> = runReadActionBlocking {
    ExternalLibrariesNode(project, ViewSettings.DEFAULT).children.filterIsInstance<ExternalLibrariesWorkspaceModelNode>()
  }
}
