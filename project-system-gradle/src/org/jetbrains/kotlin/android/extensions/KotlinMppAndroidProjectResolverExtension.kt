/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.kotlin.android.extensions

import com.android.kotlin.multiplatform.ide.models.serialization.androidCompilationKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidDependencyKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidSourceSetKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidTargetKey
import com.android.kotlin.multiplatform.models.AndroidCompilation
import com.android.kotlin.multiplatform.models.AndroidCompilation.CompilationType
import com.android.kotlin.multiplatform.models.AndroidTarget
import com.android.tools.idea.gradle.model.LibraryReference
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.android.tools.idea.gradle.project.sync.idea.data.model.KotlinMultiplatformAndroidSourceSetType
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.io.FilePaths
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.AbstractDependencyData
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.android.models.KotlinModelConverter
import org.jetbrains.kotlin.android.models.KotlinModelConverter.Companion.getJavaSourceDirectories
import org.jetbrains.kotlin.config.ExternalSystemTestRunTask
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver.Context
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinMppGradleProjectResolverExtension
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinProjectArtifactDependencyResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinSourceSetModuleId
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.findLibraryDependencyNode
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.kotlinSourceSetModuleId
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinComponent
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.kotlin.idea.projectModel.KotlinTarget
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

class KotlinMppAndroidProjectResolverExtension: KotlinMppGradleProjectResolverExtension {
  private val modelConverter = KotlinModelConverter()

  private val sourceSetResolver = KotlinMppAndroidSourceSetResolver()
  private val sourceSetDependenciesMap = mutableMapOf<String, MutableMap<String, Set<LibraryReference>>>()

  private var sourceSetDataNodeMap: Map<KotlinSourceSetModuleId, DataNode<GradleSourceSetData>>? = null

  private val compilationModelMap = mutableMapOf<String, MutableMap<CompilationType, Pair<KotlinCompilation, AndroidCompilation>>>()

  override fun provideAdditionalProjectArtifactDependencyResolvers(): List<KotlinProjectArtifactDependencyResolver> {
    return listOf(
      KotlinAndroidProjectArtifactDependencyResolver(sourceSetResolver)
    )
  }

  override fun afterMppGradleSourceSetDataNodeCreated(context: Context,
                                                      component: KotlinComponent,
                                                      sourceSetDataNode: DataNode<GradleSourceSetData>) {
    val kotlinCompilation = (component as? KotlinCompilation) ?: return
    val androidCompilation = kotlinCompilation.extras[androidCompilationKey] ?: return

    sourceSetResolver.recordSourceSetForModule(
      gradleProjectPath = context.mppModel.targets.firstNotNullOf { it.extras[androidTargetKey] }.projectPath,
      sourceSetName = androidCompilation.defaultSourceSetName,
      sourceSetType = when(androidCompilation.type) {
        CompilationType.MAIN -> KotlinMultiplatformAndroidSourceSetType.MAIN
        CompilationType.UNIT_TEST -> KotlinMultiplatformAndroidSourceSetType.UNIT_TEST
        CompilationType.INSTRUMENTED_TEST -> KotlinMultiplatformAndroidSourceSetType.ANDROID_TEST
        CompilationType.UNRECOGNIZED, null -> error("Unexpected compilation type.")
      }
    )

    compilationModelMap.getOrPut(context.moduleDataNode.data.id) {
      mutableMapOf()
    }.putIfAbsent(androidCompilation.type, Pair(kotlinCompilation, androidCompilation))
  }

  override fun afterPopulateSourceSetDependencies(context: Context,
                                                  sourceSetDataNode: DataNode<GradleSourceSetData>,
                                                  sourceSet: KotlinSourceSet,
                                                  dependencies: Set<IdeaKotlinDependency>,
                                                  dependencyNodes: List<DataNode<out AbstractDependencyData<*>>>) {
    if (sourceSet.extras[androidSourceSetKey] != null) {
      val sourceSetDependenciesMap = sourceSetDependenciesMap.getOrPut(context.moduleDataNode.data.id) { mutableMapOf() }
      sourceSetDependenciesMap.putIfAbsent(sourceSet.name, dependencies.mapNotNull { modelConverter.recordDependency(it) }.toSet())
    }

    // TODO(KTIJ-28110): This is a workaround for an issue in the kotlin IDE plugin, after we resolve dependencies from a kmp module on an
    //  android module to the appropriate sourceSet by [KotlinAndroidProjectArtifactDependencyResolver], the kotlin IDE plugin is still not
    //  able to map the dependency sourceSet to the gradle sourceSet data node. Here we add these dependencies manually as a workaround
    //  until this issue is solved.
    dependencies.filterIsInstance<IdeaKotlinSourceDependency>().forEach { ideaKotlinDependency ->
      val androidLibInfo = ideaKotlinDependency.extras[androidDependencyKey] ?: return@forEach

      // This is a dependency on an android library module
      if (!androidLibInfo.hasLibrary() || !androidLibInfo.library.hasProjectInfo()) {
        return@forEach
      }

      val dependencyModuleId = KotlinSourceSetModuleId(ideaKotlinDependency.coordinates)

      // Kotlin Ide plugin did add the dependency already.
      if (dependencyNodes.any {
        it.data is ModuleDependencyData && (it.data as ModuleDependencyData).target.id == dependencyModuleId.toString()
      }) {
        return@forEach
      }

      if (sourceSetDataNodeMap == null) {
        sourceSetDataNodeMap = ExternalSystemApiUtil.findAllRecursively(context.projectDataNode, ProjectKeys.MODULE).flatMap { moduleNode ->
          ExternalSystemApiUtil.findAll(moduleNode, GradleSourceSetData.KEY).map {
            it.data.kotlinSourceSetModuleId to it
          }
        }.toMap()
      }

      val dependencyNode = sourceSetDataNodeMap!![dependencyModuleId]

      dependencyNode?.let {
        sourceSetDataNode.createChild(
          ProjectKeys.MODULE_DEPENDENCY,
          ModuleDependencyData(sourceSetDataNode.data, dependencyNode.data)
        )
      }
    }

    dependencies.filterIsInstance<IdeaKotlinBinaryDependency>().forEach { ideaKotlinDependency ->
      val androidLibInfo = ideaKotlinDependency.extras[androidDependencyKey] ?: return@forEach

      // This is a dependency on an android library, add the fine-grained information about the contents of the library to the dependency
      // node.
      if (!androidLibInfo.hasLibrary() && !androidLibInfo.library.hasAndroidLibraryData()) {
        return@forEach
      }
      val node = sourceSetDataNode.findLibraryDependencyNode(ideaKotlinDependency) ?: return@forEach

      fun String.toFile(): File? {
        return File(this).takeIf { it.exists() }
      }

      // Discard what the kotlin IDE plugin has added.
      node.data.target.forgetAllPaths()

      androidLibInfo.library.androidLibraryData.takeIf { it.hasManifest() }?.manifest?.absolutePath?.toFile()?.let {
        node.data.target.addPath(LibraryPathType.BINARY, it.path)
      }

      androidLibInfo.library.androidLibraryData.takeIf { it.hasResFolder() }?.resFolder?.absolutePath?.toFile()?.let {
        node.data.target.addPath(LibraryPathType.BINARY, it.path)
      }

      androidLibInfo.library.androidLibraryData.compileJarFilesList.mapNotNull {it.absolutePath.toFile() }.forEach {
        node.data.target.addPath(LibraryPathType.BINARY, it.path)
      }
    }
  }

  override fun afterPopulateContentRoots(context: Context,
                                         sourceSetDataNode: DataNode<GradleSourceSetData>,
                                         sourceSet: KotlinSourceSet) {
    // We need to do this before populateSourceSetDependencies so that the android project resolver can read this data.
    sourceSetResolver.attachSourceSetDataToProject(
      context.projectDataNode
    )

    val sourceSetInfo = sourceSet.extras[androidSourceSetKey] ?: return
    sourceSetDataNode.createChild(
      ProjectKeys.CONTENT_ROOT,
      ContentRootData(GradleConstants.SYSTEM_ID, sourceSetInfo.sourceProvider.manifestFile.absolutePath)
    )

    val androidTarget = context.mppModel.targets.mapNotNull { it.extras[androidTargetKey] }.singleOrNull() ?: return

    if (androidTarget.withJava) {
      sourceSet.getJavaSourceDirectories().forEach { sourceDir ->
        val contentRootData = ContentRootData(GradleConstants.SYSTEM_ID, sourceDir.absolutePath)
        val sourceType = if (sourceSet.isTestComponent) {
          ExternalSystemSourceType.TEST
        } else {
          ExternalSystemSourceType.SOURCE
        }
        contentRootData.storePath(sourceType, sourceDir.absolutePath, null)
        sourceSetDataNode.createChild(ProjectKeys.CONTENT_ROOT, contentRootData)
      }
    }
  }

  override fun afterResolveFinished(context: Context) {
    try {
      val androidTarget = context.mppModel.targets.find { it.extras[androidTargetKey] != null } ?: return
      val targetInfo = androidTarget.extras[androidTargetKey] ?: return
      val compilationInfo = compilationModelMap[context.moduleDataNode.data.id] ?: return
      val sourceSetDependencies = sourceSetDependenciesMap[context.moduleDataNode.data.id] ?: return

      modelConverter.maybeCreateLibraryTable(
        context.projectDataNode
      )

      compilationInfo[CompilationType.UNIT_TEST]?.let {
        fixUnitTestGradleTasksInKotlinSourceSets(
          moduleNode = context.moduleDataNode,
          gradleModule = context.gradleModule,
          androidTarget = androidTarget,
          unitTestKotlinCompilation = it.first,
          unitTestAndroidCompilation = it.second
        )
      }

      createAndAttachModelsToDataNode(
        moduleNode = context.moduleDataNode,
        targetInfo = targetInfo,
        compilationInfoMap = compilationInfo,
        sourceSetDependenciesMap = sourceSetDependencies
      )
    } finally { // cleanup

      // remove the extras to avoid having them stored in data nodes. Workaround for https://youtrack.jetbrains.com/issue/KTIJ-26387
      context.mppModel.targets.forEach { kotlinTarget ->
        kotlinTarget.extras.remove(androidTargetKey)

        kotlinTarget.compilations.forEach { kotlinCompilation ->
          kotlinCompilation.extras.remove(androidCompilationKey)

          kotlinCompilation.declaredSourceSets.forEach { kotlinSourceSet ->
            kotlinSourceSet.extras.remove(androidSourceSetKey)
          }
        }
      }
      compilationModelMap.remove(context.moduleDataNode.data.id)
      sourceSetDependenciesMap.remove(context.moduleDataNode.data.id)
      sourceSetDataNodeMap = null
    }
  }

  /**
   * KotlinModuleUtils associates test tasks with a compilation based on the compilation name associated with the task data. However, the
   * compilation name is always set to "test" in case of android target. Which then leads to populateExternalSystemRunTasks not populating
   * KotlinSourceSetInfo.externalSystemRunTasks with any gradle test tasks for the android unit test sourceSet.
   *
   * This is a workaround to override `externalSystemRunTasks` with the android unitTest run gradle task.
   */
  private fun fixUnitTestGradleTasksInKotlinSourceSets(
    moduleNode: DataNode<ModuleData>,
    gradleModule: IdeaModule,
    androidTarget: KotlinTarget,
    unitTestKotlinCompilation: KotlinCompilation,
    unitTestAndroidCompilation: AndroidCompilation,
  ) {
    val allKotlinSourceSets = ExternalSystemApiUtil.findAllRecursively(moduleNode, KotlinSourceSetData.KEY).map {
      it.data.sourceSetInfo
    }

    val runUnitTestTask = ExternalSystemTestRunTask(
      unitTestAndroidCompilation.unitTestInfo.unitTestTaskName,
      gradleModule.gradleProject.path,
      androidTarget.name,
      androidTarget.platform.id
    )

    allKotlinSourceSets.forEach { kotlinSourceSetInfo ->
      // Check if this is the android unitTest compilation.
      if (kotlinSourceSetInfo.kotlinComponent is KotlinCompilation &&
          kotlinSourceSetInfo.kotlinComponent.name == unitTestKotlinCompilation.name &&
          (kotlinSourceSetInfo.kotlinComponent as KotlinCompilation).extras[androidCompilationKey] != null) {
        kotlinSourceSetInfo.externalSystemRunTasks = listOf(runUnitTestTask)
      }
      // Check if this is a sourceSet that is included in the android unitTest compilation, meaning that any tests inside will also run.
      // (e.g. commonTest).
      else if (kotlinSourceSetInfo.kotlinComponent is KotlinSourceSet &&
               unitTestKotlinCompilation.allSourceSets.any { it.name == kotlinSourceSetInfo.kotlinComponent.name }) {

        // In that case, add the android unitTest task to the existing list, in case there is another target (e.g. jvm) that also runs this
        // test.
        kotlinSourceSetInfo.externalSystemRunTasks = listOf(
          runUnitTestTask,
          *kotlinSourceSetInfo.externalSystemRunTasks.toTypedArray()
        )
      }
    }
  }

  private fun createAndAttachModelsToDataNode(
    moduleNode: DataNode<ModuleData>,
    targetInfo: AndroidTarget,
    compilationInfoMap: Map<CompilationType, Pair<KotlinCompilation, AndroidCompilation>>,
    sourceSetDependenciesMap: Map<String, Set<LibraryReference>>
  ): GradleAndroidModelData {
    val moduleName = moduleNode.data.internalName
    val rootModulePath = FilePaths.stringToFile(moduleNode.data.linkedExternalProjectPath)

    val kotlinAndroidModel = modelConverter.createGradleAndroidModelData(
      moduleName,
      rootModulePath,
      targetInfo,
      compilationInfoMap,
      sourceSetDependenciesMap
    )
    moduleNode.createChild(AndroidProjectKeys.ANDROID_MODEL, kotlinAndroidModel)

    return kotlinAndroidModel
  }
}
