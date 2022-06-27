// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.android.common.configure

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.service.project.manage.ContentRootDataService.CREATE_EMPTY_DIRECTORIES
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.containers.stream
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinGradleSourceSetData
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinSourceSetData
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinSourceSetInfo
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.addModuleDependencyIfNeeded
import org.jetbrains.kotlin.idea.gradleJava.configuration.GradleProjectImportHandler
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinSourceSetDataService
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import java.io.File
import java.io.IOException
import java.util.stream.Stream

abstract class AbstractKotlinAndroidGradleMPPModuleDataService : AbstractProjectDataService<ModuleData, Void>() {
    override fun getTargetDataKey() = ProjectKeys.MODULE

    protected class IndexedModules(val byId: Map<String, DataNode<ModuleData>>, val byIdeName: Map<String, DataNode<ModuleData>>)

    private fun shouldCreateEmptySourceRoots(
      moduleDataNode: DataNode<out ModuleData>,
      module: Module
    ): Boolean {
        val projectDataNode = ExternalSystemApiUtil.findParent(moduleDataNode, ProjectKeys.PROJECT) ?: return false
        if (projectDataNode.getUserData(CREATE_EMPTY_DIRECTORIES) == true) return true

        val projectSystemId = projectDataNode.data.owner
        val externalSystemSettings = ExternalSystemApiUtil.getSettings(module.project, projectSystemId)

        val path = ExternalSystemModulePropertyManager.getInstance(module).getRootProjectPath() ?: return false
        return externalSystemSettings.getLinkedProjectSettings(path)?.isCreateEmptyContentRootDirectories ?: false
    }

    abstract fun getVariantName(node: DataNode<ModuleData>): String?

    override fun postProcess(
      toImport: Collection<DataNode<ModuleData>>,
      projectData: ProjectData?,
      project: Project,
      modelsProvider: IdeModifiableModelsProvider
    ) {
        val projectIndexedModules = mutableMapOf<DataNode<ProjectData>, IndexedModules>()
        for (nodeToImport in toImport) {
            val projectNode = ExternalSystemApiUtil.findParent(nodeToImport, ProjectKeys.PROJECT) ?: continue
            val indexedModules = projectIndexedModules.getOrPut(projectNode) {
                val moduleNodes = ExternalSystemApiUtil.findAll(projectNode, ProjectKeys.MODULE)
                IndexedModules(
                  byId = moduleNodes.associateBy { it.data.id },
                  byIdeName = moduleNodes.mapNotNull { node -> modelsProvider.findIdeModule(node.data)?.let { it.name to node } }.toMap()
                )
            }
            val kotlinGradleSourceSetDataNodes = ExternalSystemApiUtil.findAll(nodeToImport, GradleSourceSetData.KEY)
            val nodesWithKotlinSourceSetInfoNodes = kotlinGradleSourceSetDataNodes
              .map { it to ExternalSystemApiUtil.find(it, KotlinSourceSetData.KEY) }

            // Determine MPP modules with only Android target (leaf android source sets
            // or common source set in case of HMPP and single Android target
            val mppNodesWithKotlinSourceSetInfoNodes = nodesWithKotlinSourceSetInfoNodes.mapNotNull { (gradleSourceSet, kotlinSourceSet) ->
                kotlinSourceSet?.data?.sourceSetInfo?.let { info -> gradleSourceSet to info }
            }.filter { it.second.actualPlatforms.singleOrNull() == KotlinPlatform.ANDROID }
              .toMap()

            // Heuristic approach to determine Android GradleSourceSetData nodes,
            // created by AndroidGradleProjectResolver.createAndAttachModelsToDataNode
            val androidNodesWithKotlinSourceSetInfoNodes: Map<DataNode<GradleSourceSetData>, KotlinSourceSetInfo?> = nodesWithKotlinSourceSetInfoNodes
              .mapNotNull { (gradleSourceSet, kotlinSourceSet) ->
                  val isKotlinGradleSourceSet = ExternalSystemApiUtil.findAll(gradleSourceSet, KotlinGradleSourceSetData.KEY).isNotEmpty()
                  if (kotlinSourceSet?.data?.sourceSetInfo == null && !isKotlinGradleSourceSet) gradleSourceSet to kotlinSourceSet?.data?.sourceSetInfo
                  else null
              }.toMap()
            // Logic above could be replaced with more straightforward approach when necessary dependencies are added:
            // ExternalSystemApiUtil.find(nodeToImport, AndroidProjectKeys.ANDROID_MODEL) - to find model with info about variant
            // AndroidGradleProjectResolver.computeModuleIdForArtifact - to calculate moduleId for main, androidTest, unitTest
            // Match modules from `ExternalSystemApiUtil.findAll(nodeToImport, GradleSourceSetData.KEY)` by ids from line above

            for ((dataNode, sourceSetInfo) in mppNodesWithKotlinSourceSetInfoNodes + androidNodesWithKotlinSourceSetInfoNodes) {
                val moduleData = dataNode.data
                val module = modelsProvider.findIdeModule(moduleData) ?: continue
                val shouldCreateEmptySourceRoots = shouldCreateEmptySourceRoots(dataNode, module)
                val rootModel = modelsProvider.getModifiableRootModel(module)

                val compilation = sourceSetInfo?.kotlinComponent as? KotlinCompilation
                for (sourceSet in compilation?.allSourceSets.orEmpty()) {
                    if (sourceSet.actualPlatforms.platforms.singleOrNull() == KotlinPlatform.ANDROID) {
                        val sourceType = if (sourceSet.isTestComponent) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE
                        val resourceType = if (sourceSet.isTestComponent) JavaResourceRootType.TEST_RESOURCE else JavaResourceRootType.RESOURCE
                        sourceSet.sourceDirs.forEach { addSourceRoot(it, sourceType, rootModel, shouldCreateEmptySourceRoots) }
                        sourceSet.resourceDirs.forEach { addSourceRoot(it, resourceType, rootModel, shouldCreateEmptySourceRoots) }
                    }
                }
                addExtraDependencyModules(nodeToImport, indexedModules, modelsProvider, rootModel, false)
                addExtraDependencyModules(nodeToImport, indexedModules, modelsProvider, rootModel, true)

                if (sourceSetInfo == null) {
                    continue
                }

                KotlinSourceSetDataService.configureFacet(moduleData, sourceSetInfo, nodeToImport, module, modelsProvider)
                val kotlinFacet = KotlinFacet.get(module)
                if (kotlinFacet != null) {
                    GradleProjectImportHandler.getInstances(project).forEach { it.importByModule(kotlinFacet, nodeToImport) }
                }
            }
        }
    }

    private fun isRootOrIntermediateSourceSet(sourceSets: Iterable<KotlinSourceSet>, sourceSet: KotlinSourceSet): Boolean {
        return sourceSets.any { anySourceSet -> sourceSet.name in anySourceSet.allDependsOnSourceSets } ||
               /**
                * TODO Sebastian Sellmair
                *  Currently default `dependsOn` edges are not correct for android source sets:
                *  Android source sets are not declaring `dependsOn("commonMain")` by default
                */
               sourceSet.actualPlatforms.platforms.singleOrNull() != KotlinPlatform.ANDROID
    }

    protected abstract fun getDependencyModuleNodes(
      moduleNode: DataNode<ModuleData>,
      indexedModules: IndexedModules,
      modelsProvider: IdeModifiableModelsProvider,
      testScope: Boolean
    ): List<DataNode<out ModuleData>>

    private fun addExtraDependencyModules(
      moduleNode: DataNode<ModuleData>,
      indexedModules: IndexedModules,
      modelsProvider: IdeModifiableModelsProvider,
      rootModel: ModifiableRootModel,
      testScope: Boolean
    ) {
        if (!isAndroidModule(moduleNode)) return
        val dependencyModuleNodes = getDependencyModuleNodes(moduleNode, indexedModules, modelsProvider, testScope)
        for (dependencyModule in dependencyModuleNodes) {
            val dependencySourceSets = ExternalSystemApiUtil.getChildren(dependencyModule, GradleSourceSetData.KEY)
              .filter { sourceSet -> sourceSet.kotlinSourceSetData?.sourceSetInfo?.kotlinComponent?.isTestComponent == false }
              .filter {
                  it.kotlinSourceSetData?.sourceSetInfo?.actualPlatforms?.platforms?.let { platforms ->
                      platforms.contains(KotlinPlatform.COMMON) || platforms.contains(KotlinPlatform.ANDROID) ||
                      platforms.contains(KotlinPlatform.JVM) && !isAndroidModule(dependencyModule)
                  } ?: false
              }

            for (dependencySourceSet in dependencySourceSets) {
                val dependencyIdeModule = modelsProvider.findIdeModule(dependencySourceSet.data) ?: return
                addModuleDependencyIfNeeded(rootModel, dependencyIdeModule, testScope, false)
            }
        }
    }

    abstract fun isAndroidModule(node: DataNode<out ModuleData>): Boolean

    abstract fun findParentContentEntry(path: File, contentEntries: Stream<ContentEntry>): ContentEntry?

    abstract fun pathToIdeaUrl(path: File): String

    private fun addSourceRoot(
      sourceRoot: File,
      type: JpsModuleSourceRootType<*>,
      rootModel: ModifiableRootModel,
      shouldCreateEmptySourceRoots: Boolean
    ) {
        val parent = findParentContentEntry(sourceRoot, rootModel.contentEntries.stream()) ?: return
        val url = pathToIdeaUrl(sourceRoot)
        parent.addSourceFolder(url, type)
        if (shouldCreateEmptySourceRoots) {
            ExternalSystemApiUtil.doWriteAction {
                try {
                    VfsUtil.createDirectoryIfMissing(sourceRoot.path)
                }
                catch (e: IOException) {
                    LOG.warn(String.format("Unable to create directory for the path: %s", sourceRoot.path), e)
                }
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(AbstractKotlinAndroidGradleMPPModuleDataService::class.java)
    }
}
