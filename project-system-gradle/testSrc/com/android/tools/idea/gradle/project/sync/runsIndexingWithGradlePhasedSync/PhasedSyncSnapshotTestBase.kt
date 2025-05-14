/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.runsIndexingWithGradlePhasedSync

import com.android.builder.model.v2.models.Versions
import com.android.testutils.TestUtils
import com.android.testutils.TestUtils.getSdk
import com.android.tools.idea.gradle.project.sync.internal.ProjectDumper
import com.android.tools.idea.gradle.project.sync.internal.dump
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.testing.nameProperties
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncProjectConfigurator.project
import java.io.File

abstract class PhasedSyncSnapshotTestBase {

  lateinit var intermediateDump: ModuleDumpWithType
  lateinit var isAndroidByPath:  Map<File, Boolean>


  @Suppress("UnstableApiUsage")
  fun setupPhasedSyncIntermediateStateCollector(disposable: Disposable) {
    val testSyncContributor = object : GradleSyncContributor {

      override suspend fun onModelFetchCompleted(context: ProjectResolverContext,
                                                 storage: MutableEntityStorage) {
        isAndroidByPath = context.allBuilds.flatMap { buildModel ->
          buildModel.projects.map { projectModel ->
            val isAndroidProject = context.getProjectModel(projectModel, Versions::class.java) != null
            projectModel.projectDirectory to isAndroidProject
          }
        }.toMap()

        intermediateDump = context.project().dumpModules(isAndroidByPath)
      }
    }
    GradleSyncContributor.EP_NAME.point.registerExtension(testSyncContributor, disposable)
  }

  companion object {
    val phasedSyncTestProjects = listOf(
      TestProject.SIMPLE_APPLICATION,
      TestProject.SIMPLE_APPLICATION_VIA_SYMLINK,
      TestProject.SIMPLE_APPLICATION_APP_VIA_SYMLINK,
      TestProject.SIMPLE_APPLICATION_NOT_AT_ROOT,
      TestProject.SIMPLE_APPLICATION_MULTIPLE_ROOTS,
      TestProject.SIMPLE_APPLICATION_WITH_UNNAMED_DIMENSION,
      TestProject.SIMPLE_APPLICATION_WITH_ANDROID_CAR,
      TestProject.SIMPLE_APPLICATION_WITH_SCREENSHOT_TEST,
      TestProject.PURE_JAVA_PROJECT,
      TestProject.MAIN_IN_ROOT,
      TestProject.NESTED_MODULE,
      TestProject.BASIC_WITH_EMPTY_SETTINGS_FILE,
      TestProject.TRANSITIVE_DEPENDENCIES,
      TestProject.WITH_GRADLE_METADATA,
      TestProject.TEST_FIXTURES,
      TestProject.TEST_ONLY_MODULE,
      TestProject.APP_WITH_ML_MODELS,
      TestProject.MULTI_FLAVOR,
      TestProject.NON_STANDARD_SOURCE_SET_DEPENDENCIES,
      TestProject.NON_STANDARD_SOURCE_SET_DEPENDENCIES_MANUAL_TEST_FIXTURES_WORKAROUND,
      TestProject.KOTLIN_GRADLE_DSL,
      TestProject.NEW_SYNC_KOTLIN_TEST,
      TestProject.PSD_SAMPLE_GROOVY,
      TestProject.COMPOSITE_BUILD,
      TestProject.APP_WITH_BUILDSRC,
      TestProject.APP_WITH_BUILDSRC_AND_SETTINGS_PLUGIN,
      TestProject.KOTLIN_MULTIPLATFORM,
      TestProject.KOTLIN_MULTIPLATFORM_WITHJS,
      TestProject.KOTLIN_MULTIPLATFORM_IOS,
      TestProject.KOTLIN_MULTIPLATFORM_JVM,
      TestProject.KOTLIN_MULTIPLATFORM_JVM_KMPAPP,
      TestProject.KOTLIN_MULTIPLATFORM_JVM_KMPAPP_WITHINTERMEDIATE,
      TestProject.KOTLIN_MULTIPLATFORM_MULTIPLE_SOURCE_SET_PER_ANDROID_COMPILATION,
      TestProject.KOTLIN_KAPT,
      TestProject.API_DEPENDENCY,
      TestProject.LIGHT_SYNC_REFERENCE,
      TestProject.NON_STANDARD_SOURCE_SETS,
      TestProject.BUILDSRC_WITH_COMPOSITE,
      TestProject.PRIVACY_SANDBOX_SDK,
      TestProject.APP_WITH_BUILD_FEATURES_ENABLED,
      TestProject.DEPENDENT_MODULES_ONLY_APP_RUNTIME,
      TestProject.BUILD_CONFIG_AS_BYTECODE_ENABLED,
      TestProject.TEST_STATIC_DIR,

      TestProject.COMPATIBILITY_TESTS_AS_36_NO_IML,
      TestProject.COMPATIBILITY_TESTS_AS_36,
      TestProject.TWO_JARS,
      TestProject.ANDROID_KOTLIN_MULTIPLATFORM,
    )
  }
}


data class ModuleDumpWithType(
  val rootModuleNames: List<String>,
  val phasedSyncModuleNames : List<String>,
  val androidModuleNames: List<String>,
  val entries: Sequence<String>
)

fun ModuleDumpWithType.join() : String = entries.joinToString(separator = "\n")
fun ModuleDumpWithType.filterOutRootModule() = excludeByModuleName(rootModuleNames)
fun ModuleDumpWithType.filterToPhasedSyncModules() = includeByModuleName(phasedSyncModuleNames)

fun ModuleDumpWithType.filterOutDependencies() = copy(
  entries = entries.filter { line ->
    DEPENDENCY_RELATED_PROPERTIES.none { line.contains("/$it") }
  }
)

fun Project.dumpModules(isAndroidByPath:  Map<File, Boolean>) =
  ModuleDumpWithType(
    rootModuleNames = modules
      .groupBy {
        ExternalSystemModulePropertyManager.getInstance(it).getLinkedProjectPath()
      }.mapValues {
        it.value.minBy { it.name.length }
      }.filter { (linkedProjectPath, module) ->
        ExternalSystemModulePropertyManager.getInstance(module).getRootProjectPath() == linkedProjectPath
      }.values.map {
        it.name
      },
    phasedSyncModuleNames = modules.filter { it.moduleFilePath.isEmpty() }.map { it.name },
    androidModuleNames = modules.filter { isAndroidByPath[it.projectDirectory()] == true }.map { it.name },
    entries = dumpAllModuleEntries()
  )

private fun Project.dumpAllModuleEntries() : Sequence<String> {
  val dumper = ProjectDumper(
    androidSdk = getSdk().toFile(),
    devBuildHome = TestUtils.getWorkspaceRoot().toFile(),
    projectJdk = ProjectRootManager.getInstance(this).projectSdk,
    ignoreTasks = true, // We have to ignore tasks explicitly because they cache some values too early leading to issues.
  )

  modules.sortedBy { it.name }.forEach {
    dumper.dump(it)
  }

  return dumper.toString().nameProperties()
}

private fun Module.projectDirectory(): File? = ExternalSystemModulePropertyManager.getInstance(this).getLinkedProjectPath()?.let { File(it) }

val DEPENDENCY_RELATED_PROPERTIES = setOf(
  "ORDER_ENTRY",
  "LIBRARY",
)


private fun String.nameProperties(): Sequence<String> =
  this
    .splitToSequence('\n')
    .let { nameProperties(it, attachValue = true) }
    .map { it.first }

private fun ModuleDumpWithType.excludeByModuleName(names: List<String>) = copy (
  entries = entries.filter { line ->
    names.none { line.contains("MODULE ($it)") }
  }
)

private fun ModuleDumpWithType.includeByModuleName(names: List<String>) = copy (
  entries = entries.filter { line ->
    names.any { line.contains("MODULE ($it)") }
  }
)
