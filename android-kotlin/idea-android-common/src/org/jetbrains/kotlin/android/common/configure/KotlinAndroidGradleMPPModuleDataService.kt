// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

@file:Suppress("DuplicatedCode")

package org.jetbrains.kotlin.android.common.configure

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinSourceSetData
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinSourceSetInfo
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.addModuleDependencyIfNeeded
import org.jetbrains.kotlin.idea.gradleJava.configuration.GradleProjectImportHandler
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinSourceSetDataService
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.util.stream.Stream

abstract class KotlinAndroidGradleMPPModuleDataService : AbstractProjectDataService<ModuleData, Void>() {
    override fun getTargetDataKey() = ProjectKeys.MODULE

    protected class IndexedModules(val byId: Map<String, DataNode<ModuleData>>, val byIdeName: Map<String, DataNode<ModuleData>>)

    abstract fun getVariantName(node: DataNode<ModuleData>): String?

    override fun postProcess(
      toImport: MutableCollection<out DataNode<ModuleData>>,
      projectData: ProjectData?,
      project: Project,
      modelsProvider: IdeModifiableModelsProvider) {
        val indexedModulesCache = IndexedModulesCache(modelsProvider)
        toImport.forEach { gradleModuleDataNode ->
            val indexedModules = indexedModulesCache[gradleModuleDataNode] ?: return@forEach
            ExternalSystemApiUtil.findAll(gradleModuleDataNode, GradleSourceSetData.KEY).forEach forEachSourceSet@{ sourceSetDataNode ->
                val sourceSetModule = modelsProvider.findIdeModule(sourceSetDataNode.data) ?: return@forEachSourceSet

                expandMultiplatformDependenciesToDependsOnSourceSets(
                  gradleModuleDataNode, sourceSetDataNode, sourceSetModule, project, modelsProvider, indexedModules
                )

                configureKotlinFacet(
                  gradleModuleDataNode, sourceSetDataNode, sourceSetModule, project, modelsProvider
                )
            }
        }
    }

    /**
     * Android source sets will only declare dependencies between its leaf modules.
     * e.g. an 'app.main' source set will declare a module dependency to 'lib.main'.
     * However, if 'lib.main' happens to be a multiplatform project, then all the 'dependsOn' source sets from 'lib.main'
     * should also be dependencies of 'app.main'.
     */
    protected open fun expandMultiplatformDependenciesToDependsOnSourceSets(
      gradleModuleDataNode: DataNode<ModuleData>,
      sourceSetDataNode: DataNode<GradleSourceSetData>,
      sourceSetModule: Module,
      project: Project,
      modelsProvider: IdeModifiableModelsProvider,
      indexedModules: IndexedModules,
    ) {
        val kotlinSourceSetInfo = ExternalSystemApiUtil.find(sourceSetDataNode, KotlinSourceSetData.KEY)?.data?.sourceSetInfo

        /* Do not expand for Kotlin source sets that are not managed by Android */
        if (kotlinSourceSetInfo != null && kotlinSourceSetInfo.actualPlatforms.platforms.singleOrNull() != KotlinPlatform.ANDROID) {
            return
        }

        /* Fist step: Try to process expansion properly by looking into kotlin source set info's dependsOn */
        val processedModulesWithKotlinSourceSetInfo = if (kotlinSourceSetInfo != null) {
            expandMultiplatformDependenciesToDependsOnSourceSetsWithKotlinSourceSetInfo(
              gradleModuleDataNode, sourceSetDataNode, sourceSetModule, project, modelsProvider, kotlinSourceSetInfo
            )
        } else emptySet()


        /* Second step: expand remaining module dependencies heuristically */
        expandMultiplatformDependenciesToDependsOnSourceSetsByPlatformHeuristic(
          gradleModuleDataNode, sourceSetDataNode, sourceSetModule, modelsProvider, indexedModules, processedModulesWithKotlinSourceSetInfo
        )
    }

    /**
     * Goes through all module dependencies of the given [sourceSetDataNode].
     * If a found module dependency happens to be a Kotlin source set with, then we will
     * find all dependsOn edges, find the corresponding module and add the corresponding module dependency as well.
     *
     * @return All processed gradle modules
     */
    private fun expandMultiplatformDependenciesToDependsOnSourceSetsWithKotlinSourceSetInfo(
      gradleModuleDataNode: DataNode<ModuleData>,
      sourceSetDataNode: DataNode<GradleSourceSetData>,
      sourceSetModule: Module,
      project: Project,
      modelsProvider: IdeModifiableModelsProvider,
      kotlinSourceSetInfo: KotlinSourceSetInfo
    ) : Set<ModuleData> {
        val sourceSetRootModel = modelsProvider.getModifiableRootModel(sourceSetModule)

        return ExternalSystemApiUtil.findAll(sourceSetDataNode, ProjectKeys.MODULE_DEPENDENCY).mapNotNull { dependencyNode ->
            val dependencyModuleNode = ExternalSystemApiUtil.findModuleNode(
              project, GradleConstants.SYSTEM_ID, dependencyNode.data.target.linkedExternalProjectPath
            ) ?: return@mapNotNull null

            /* There is no need for expanding intra gradle project source set dependencies. This is handled by the resolver */
            if (dependencyModuleNode.data == gradleModuleDataNode.data) {
                return@mapNotNull null
            }

            /* Find the 'GradleSourceSetData' node representing the dependencyNode */
            val dependencyModuleSourceSets = ExternalSystemApiUtil.findAll(dependencyModuleNode, GradleSourceSetData.KEY)
            val dependencySourceSetNode = dependencyModuleSourceSets.find { it.data.id == dependencyNode.data.target.id }
            val dependencyKotlinSourceSetInfo = dependencySourceSetNode?.kotlinSourceSetData?.sourceSetInfo
            if (dependencyKotlinSourceSetInfo == null) return@mapNotNull dependencyModuleNode.data

            /* Expand the dependency and also add all its dependsOn (and additionalVisible) source sets as dependencies as well */
            (dependencyKotlinSourceSetInfo.dependsOn + dependencyKotlinSourceSetInfo.additionalVisible).toSet()
              .forEach forEachSourceSet@{ dependsOnPath ->
                  val dependsOnSourceSetNode = dependencyModuleSourceSets.find { it.data.id == dependsOnPath } ?: return@forEachSourceSet
                  val dependsOnModule = modelsProvider.findIdeModule(dependsOnSourceSetNode.data) ?: return@forEachSourceSet
                  addModuleDependencyIfNeeded(sourceSetRootModel, dependsOnModule, kotlinSourceSetInfo.isTestModule, false)
              }

            dependencyModuleNode.data
        }.toSet()
    }

    /**
     * Goes through all module dependencies of the given [sourceSetDataNode], but using [getDependencyModuleNodes] instead
     * of trying to find module dependencies in the data node tree.
     *
     * The [getDependencyModuleNodes] method however, will only return module nodes to the Gradle projects module
     * (not individual source sets). We therefore cannot construct the dependsOn graph, since the leaf (starting point) is unknown.
     *
     * The following heuristic is performed:
     * We assume, that all source sets with the platform 'common' or 'android' will be in the dependsOn graph.
     * We also assume that source sets with 'jvm' platforms are okay, as long as the dependency module in question
     * is not an Android module (then only Android is okay).
     */
    private fun expandMultiplatformDependenciesToDependsOnSourceSetsByPlatformHeuristic(
      gradleModuleDataNode: DataNode<ModuleData>,
      sourceSetDataNode: DataNode<GradleSourceSetData>,
      sourceSetModule: Module,
      modelsProvider: IdeModifiableModelsProvider,
      indexedModules: IndexedModules,
      alreadyProcessedModules: Set<ModuleData>
    ) {
        val sourceSetRootModel = modelsProvider.getModifiableRootModel(sourceSetModule)

        getDependencyModuleNodes(
          gradleModuleDataNode, sourceSetDataNode, indexedModules, modelsProvider
        ).filter { dependencyModuleNode -> dependencyModuleNode.data !in alreadyProcessedModules }
          .flatMap { dependencyModuleNode ->
            /* Find all source sets in this parent 'dependencyModuleNode'
             */
            ExternalSystemApiUtil.findAll(dependencyModuleNode, GradleSourceSetData.KEY)
              .mapNotNull { sourceSetDataNode ->
                  sourceSetDataNode to (sourceSetDataNode.kotlinSourceSetData?.sourceSetInfo ?: return@mapNotNull null)
              }
              /* Test modules are not relevant in this heuristic, since they are not published */
              .filter { (_, sourceSetInfo) -> !sourceSetInfo.isTestModule }
              .filter { (_, sourceSetInfo) ->
                  val platforms = sourceSetInfo.actualPlatforms.platforms.toSet()
                  platforms.contains(KotlinPlatform.COMMON) || platforms.contains(KotlinPlatform.ANDROID) ||
                  (platforms.contains(KotlinPlatform.JVM) && !isAndroidModule(dependencyModuleNode))
              }
              .map { (node, _) -> node }
        }.toSet()

          /* Add dependencies to all source sets that pass the heuristic */
          .forEach { dependencySourceSetNode ->
              val dependencySourceSetModule = modelsProvider.findIdeModule(dependencySourceSetNode.data) ?: return@forEach
              addModuleDependencyIfNeeded(
                sourceSetRootModel, dependencySourceSetModule,
                testScope = sourceSetDataNode.data.moduleName.contains("test", ignoreCase = true), false)
          }
    }

    private fun configureKotlinFacet(
      gradleModuleDataNode: DataNode<ModuleData>,
      sourceSetDataNode: DataNode<GradleSourceSetData>,
      sourceSetModule: Module,
      project: Project,
      modelsProvider: IdeModifiableModelsProvider
    ) {
        val kotlinSourceSetInfo = ExternalSystemApiUtil.find(sourceSetDataNode, KotlinSourceSetData.KEY)?.data?.sourceSetInfo ?: return

        KotlinSourceSetDataService.configureFacet(
          sourceSetDataNode.data, kotlinSourceSetInfo, gradleModuleDataNode, sourceSetModule, modelsProvider
        )

        val kotlinFacet = KotlinFacet.get(sourceSetModule)
        if (kotlinFacet != null) {
            GradleProjectImportHandler.getInstances(project).forEach { it.importByModule(kotlinFacet, gradleModuleDataNode) }
        }
    }

    private class IndexedModulesCache(
      private val modelsProvider: IdeModifiableModelsProvider
    ) {
        private val indexByProject = mutableMapOf<ProjectData, IndexedModules>()

        operator fun get(moduleNode: DataNode<out ModuleData>): IndexedModules? {
            val projectDataNode = ExternalSystemApiUtil.findParent(moduleNode, ProjectKeys.PROJECT) ?: return null

            return indexByProject.getOrPut(projectDataNode.data) {
                val moduleNodes = ExternalSystemApiUtil.findAll(projectDataNode, ProjectKeys.MODULE)
                IndexedModules(
                  byId = moduleNodes.associateBy { it.data.id },
                  byIdeName = moduleNodes.mapNotNull { node -> modelsProvider.findIdeModule(node.data)?.let { it.name to node } }.toMap()
                )
            }
        }
    }

    protected abstract fun getDependencyModuleNodes(
      moduleNode: DataNode<ModuleData>,
      sourceSetDataNode: DataNode<GradleSourceSetData>,
      indexedModules: IndexedModules,
      modelsProvider: IdeModifiableModelsProvider,
    ): List<DataNode<out ModuleData>>

    abstract fun isAndroidModule(node: DataNode<out ModuleData>): Boolean

    abstract fun findParentContentEntry(path: File, contentEntries: Stream<ContentEntry>): ContentEntry?

    abstract fun pathToIdeaUrl(path: File): String

    companion object {
        private val LOG = Logger.getInstance(KotlinAndroidGradleMPPModuleDataService::class.java)
    }
}

