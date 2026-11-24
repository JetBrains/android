// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.catalog

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesWorkspaceModelNode
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesWorkspaceModelNodesProvider
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.util.PlatformIcons
import com.intellij.workspaceModel.core.fileIndex.DependencyDescription
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleOrLibrarySourceRootData
import com.intellij.workspaceModel.core.fileIndex.impl.SkipAddingToWatchedRootsData
import com.intellij.workspaceModel.ide.isEqualOrParentOf
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity
import org.jetbrains.plugins.gradle.model.projectModel.gradleBuilds
import org.jetbrains.plugins.gradle.model.versionCatalogs.GradleVersionCatalogEntity
import org.jetbrains.plugins.gradle.model.versionCatalogs.versionCatalogs

/**
 * An imported catalog is a catalog whose file is outside all Gradle builds of its linked project, for example in the Gradle cache.
 * A catalog in another build of the linked project is not imported: `buildSrc` often reads the catalog of the root build.
 */
private fun GradleVersionCatalogEntity.isImported(): Boolean = build.externalProject.gradleBuilds.none { it.url.isEqualOrParentOf(url) }

/** Adds the files of the imported version catalogs to the project as external sources. */
class GradleVersionCatalogFileIndexContributor : WorkspaceFileIndexContributor<GradleVersionCatalogEntity> {
  override val entityClass: Class<GradleVersionCatalogEntity>
    get() = GradleVersionCatalogEntity::class.java

  override fun registerFileSets(entity: GradleVersionCatalogEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    if (entity.isImported()) {
      registrar.registerFileSet(entity.url, WorkspaceFileKind.EXTERNAL_SOURCE, entity, ImportedVersionCatalogData)
    }
  }

  // A new or a removed build of the linked project changes which of its catalogs are imported.
  override val dependenciesOnOtherEntities: List<DependencyDescription<GradleVersionCatalogEntity>> =
    listOf(
      DependencyDescription.OnArbitraryEntity(GradleBuildEntity::class.java) { build ->
        build.externalProject.gradleBuilds.asSequence().flatMap { it.versionCatalogs }
      }
    )
}

/** The file of an imported catalog does not change, so the IDE does not watch it. */
private object ImportedVersionCatalogData : ModuleOrLibrarySourceRootData, SkipAddingToWatchedRootsData

/** Shows each imported version catalog as a node under External Libraries of the Project view. */
class GradleVersionCatalogNodesProvider : ExternalLibrariesWorkspaceModelNodesProvider<GradleVersionCatalogEntity> {
  override fun getWorkspaceClass(): Class<GradleVersionCatalogEntity> = GradleVersionCatalogEntity::class.java

  override fun createNode(entity: GradleVersionCatalogEntity, project: Project, settings: ViewSettings?): AbstractTreeNode<*>? {
    if (!entity.isImported()) return null
    val file = entity.url.virtualFile ?: return null
    // Several builds can import one file. Only the first of their catalogs gets a node.
    val first =
      WorkspaceModel.getInstance(project)
        .currentSnapshot
        .entities(GradleVersionCatalogEntity::class.java)
        .filter { it.url == entity.url && it.isImported() }
        .minWithOrNull(NODE_ORDER)
    if (first != null && NODE_ORDER.compare(first, entity) != 0) return null
    return ExternalLibrariesWorkspaceModelNode(
      project,
      listOf(file),
      emptyList(),
      "Imported Catalog: ${entity.name}",
      PlatformIcons.LIBRARY_ICON,
      settings,
    )
  }
}

/** Compares the data of the entities, so the result does not depend on the snapshot that an entity comes from. */
private val NODE_ORDER: Comparator<GradleVersionCatalogEntity> =
  compareBy({ it.build.externalProject.externalProjectPath }, { it.build.url.url }, { it.name })
