/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.configure

import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.gradle.util.ContentEntries
import com.android.tools.idea.io.FilePaths
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.DependencyScope
import java.io.File
import java.util.stream.Stream

class KotlinAndroidGradleMPPModuleDataService : AbstractKotlinAndroidGradleMPPModuleDataService() {

    override fun getVariantName(node: DataNode<ModuleData>): String? {
        return getAndroidModuleModel(node)?.selectedVariant?.name
    }

    override fun getDependencyModuleNodes(
        moduleNode: DataNode<ModuleData>,
        indexedModules: IndexedModules,
        modelsProvider: IdeModifiableModelsProvider,
        testScope: Boolean
    ): List<DataNode<out ModuleData>> {
        val androidModel = getAndroidModuleModel(moduleNode)
        if (androidModel != null) {
            val dependencies = if (testScope) {
                androidModel.selectedAndroidTestCompileDependencies
            } else {
                androidModel.selectedMainCompileLevel2Dependencies
            } ?: return emptyList()
            return dependencies
                .moduleDependencies
                .mapNotNull { indexedModules.byId[it.projectPath!!] }
        }

        val javaModel = getJavaModuleModel(moduleNode)
        if (javaModel != null) {
            val scope = if (testScope) DependencyScope.TEST.name else DependencyScope.COMPILE.name
            return javaModel
                .javaModuleDependencies
                .filter { scope == it.scope ?: DependencyScope.COMPILE.name }
                .map { it.moduleName }
                .distinct()
                .mapNotNull { indexedModules.byIdeName[it] }
        }

        return emptyList()
    }

    override fun isAndroidModule(node: DataNode<out ModuleData>) =
        getAndroidModuleModel(node) != null

    private fun getAndroidModuleModel(moduleNode: DataNode<out ModuleData>) =
        ExternalSystemApiUtil.getChildren(moduleNode, AndroidProjectKeys.ANDROID_MODEL).firstOrNull()?.data

    private fun getJavaModuleModel(moduleNode: DataNode<out ModuleData>) =
        ExternalSystemApiUtil.getChildren(moduleNode, AndroidProjectKeys.JAVA_MODULE_MODEL).firstOrNull()?.data

    override fun findParentContentEntry(path: File, contentEntries: Stream<ContentEntry>) =
        ContentEntries.findParentContentEntry(path, contentEntries)

    override fun pathToIdeaUrl(path: File) =
        FilePaths.pathToIdeaUrl(path)

}
