// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.android.configure

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
import org.jetbrains.kotlin.gradle.KotlinCompilation
import org.jetbrains.kotlin.gradle.KotlinPlatform
import org.jetbrains.kotlin.gradle.KotlinSourceSet
import org.jetbrains.kotlin.idea.addModuleDependencyIfNeeded
import org.jetbrains.kotlin.idea.configuration.*
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import java.io.File
import java.io.IOException
import java.util.stream.Stream

abstract class AbstractKotlinAndroidGradleMPPModuleDataService : AbstractProjectDataService<ModuleData, Void>() {
    override fun getTargetDataKey() = ProjectKeys.MODULE

    protected class IndexedModules(val byId: Map<String, DataNode<ModuleData>>, val byIdeName: Map<String, DataNode<ModuleData>>)

    private fun shouldCreateEmptySourceRoots(
        moduleDataNode: DataNode<ModuleData>,
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
            val moduleData = nodeToImport.data
            val module = modelsProvider.findIdeModule(moduleData) ?: continue
            val shouldCreateEmptySourceRoots = shouldCreateEmptySourceRoots(nodeToImport, module)
            val rootModel = modelsProvider.getModifiableRootModel(module)
            val kotlinAndroidSourceSets = nodeToImport.kotlinAndroidSourceSets ?: emptyList()
            for (sourceSetInfo in kotlinAndroidSourceSets) {
                val compilation = sourceSetInfo.kotlinModule as? KotlinCompilation ?: continue
                for (sourceSet in compilation.sourceSets) {
                    if (sourceSet.actualPlatforms.platforms.singleOrNull() == KotlinPlatform.ANDROID) {
                        val sourceType = if (sourceSet.isTestModule) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE
                        val resourceType = if (sourceSet.isTestModule) JavaResourceRootType.TEST_RESOURCE else JavaResourceRootType.RESOURCE
                        sourceSet.sourceDirs.forEach { addSourceRoot(it, sourceType, rootModel, shouldCreateEmptySourceRoots) }
                        sourceSet.resourceDirs.forEach { addSourceRoot(it, resourceType, rootModel, shouldCreateEmptySourceRoots) }
                    }
                }
            }
            val indexedModules = projectIndexedModules.getOrPut(projectNode) {
                val moduleNodes = ExternalSystemApiUtil.findAll(projectNode, ProjectKeys.MODULE)
                IndexedModules(
                    byId = moduleNodes.associateBy { it.data.id },
                    byIdeName = moduleNodes.mapNotNull { node -> modelsProvider.findIdeModule(node.data)?.let { it.name to node } }.toMap()
                )
            }
            addExtraDependencyModules(nodeToImport, indexedModules, modelsProvider, rootModel, false)
            addExtraDependencyModules(nodeToImport, indexedModules, modelsProvider, rootModel, true)

            if (nodeToImport.kotlinAndroidSourceSets == null) {
                continue
            }

            val variantName = getVariantName(nodeToImport) ?: continue
            val activeSourceSetInfos = nodeToImport.kotlinAndroidSourceSets?.filter { it.kotlinModule.name.startsWith(variantName) } ?: emptyList()
            for (activeSourceSetInfo in activeSourceSetInfos) {
                val activeCompilation = activeSourceSetInfo.kotlinModule as? KotlinCompilation ?: continue
                for (sourceSet in activeCompilation.sourceSets) {
                    if (isRootOrIntermediateSourceSet(activeCompilation.sourceSets, sourceSet)) {
                        val sourceSetId = activeSourceSetInfo.sourceSetIdsByName[sourceSet.name] ?: continue
                        val sourceSetNode = ExternalSystemApiUtil.findFirstRecursively(projectNode) {
                            (it.data as? ModuleData)?.id == sourceSetId
                        } as? DataNode<out ModuleData>? ?: continue
                        val sourceSetData = sourceSetNode.data as? ModuleData ?: continue
                        val sourceSetModule = modelsProvider.findIdeModule(sourceSetData) ?: continue
                        addModuleDependencyIfNeeded(
                            rootModel,
                            sourceSetModule,
                            activeSourceSetInfo.isTestModule,
                            sourceSetNode.kotlinSourceSet?.isTestModule ?: false
                        )
                    }
                }
            }

            val mainSourceSetInfo = activeSourceSetInfos.firstOrNull { it.kotlinModule.name == variantName }
            if (mainSourceSetInfo != null) {
                KotlinSourceSetDataService.configureFacet(moduleData, mainSourceSetInfo, nodeToImport, module, modelsProvider)
            }

            val kotlinFacet = KotlinFacet.get(module)
            if (kotlinFacet != null) {
                GradleProjectImportHandler.getInstances(project).forEach { it.importByModule(kotlinFacet, nodeToImport) }
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
                .filter { sourceSet -> sourceSet.kotlinSourceSet?.kotlinModule?.isTestModule == false }
                .filter {
                    it.kotlinSourceSet?.actualPlatforms?.platforms?.contains(KotlinPlatform.COMMON) == true ||
                    (it.kotlinSourceSet?.actualPlatforms?.platforms?.contains(KotlinPlatform.ANDROID) == true) ||
                    (it.kotlinSourceSet?.actualPlatforms?.platforms?.contains(KotlinPlatform.JVM) == true && !isAndroidModule(dependencyModule))
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
                } catch (e: IOException) {
                    LOG.warn(String.format("Unable to create directory for the path: %s", sourceRoot.path), e)
                }
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(AbstractKotlinAndroidGradleMPPModuleDataService::class.java)
    }
}
