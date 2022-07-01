/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.configure

import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.gradle.util.ContentEntries
import com.android.tools.idea.io.FilePaths
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.kotlin.android.common.configure.AbstractKotlinAndroidGradleMPPModuleDataService
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import java.io.File
import java.util.stream.Stream

class KotlinAndroidGradleMPPModuleDataService : AbstractKotlinAndroidGradleMPPModuleDataService() {

  override fun getVariantName(node: DataNode<ModuleData>): String? {
    return getAndroidModuleModel(node)?.selectedVariant?.name
  }

  override fun getDependencyModuleNodes(
    moduleNode: DataNode<ModuleData>,
    sourceSetDataNode: DataNode<GradleSourceSetData>,
    indexedModules: IndexedModules,
    modelsProvider: IdeModifiableModelsProvider,
  ): List<DataNode<out ModuleData>> {
    val ideModuleSourceSet = IdeModuleSourceSet.values().find { sourceSet -> sourceSet.sourceSetName == sourceSetDataNode.data.moduleName }
    if (ideModuleSourceSet == null) return emptyList()
    val androidModuleModel = getAndroidModuleModel(moduleNode)
    if (androidModuleModel != null) {
      val selectedVariant = androidModuleModel.selectedVariant
      val moduleDependencies = when (ideModuleSourceSet) {
        IdeModuleSourceSet.MAIN -> selectedVariant.mainArtifact.level2Dependencies.moduleDependencies
        IdeModuleSourceSet.TEST_FIXTURES -> selectedVariant.testFixturesArtifact?.level2Dependencies?.moduleDependencies.orEmpty()
        IdeModuleSourceSet.UNIT_TEST -> selectedVariant.unitTestArtifact?.level2Dependencies?.moduleDependencies.orEmpty()
        IdeModuleSourceSet.ANDROID_TEST -> selectedVariant.androidTestArtifact?.level2Dependencies?.moduleDependencies.orEmpty()
      }

      return moduleDependencies.mapNotNull { moduleDependency ->
        indexedModules.byId[moduleDependency.projectPath]
      }
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
