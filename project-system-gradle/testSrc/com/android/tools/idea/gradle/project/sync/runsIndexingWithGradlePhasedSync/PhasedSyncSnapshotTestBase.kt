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
import com.android.tools.idea.gradle.project.sync.internal.dumpAndroidIdeModel
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.testing.nameProperties
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.application
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncListener
import java.io.File

abstract class PhasedSyncSnapshotTestBase {

  private val modelDumpSyncContributor = ModelDumpSyncContributor()
  internal val intermediateDump get() = modelDumpSyncContributor.intermediateDump
  internal val knownAndroidPaths get() = modelDumpSyncContributor.knownAndroidPaths


  @Suppress("UnstableApiUsage")
  fun setupPhasedSyncIntermediateStateCollector(disposable: Disposable) {
    application.messageBus.connect(disposable)
      .subscribe(GradleSyncListener.TOPIC, modelDumpSyncContributor)
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
      TestProject.MULTI_FLAVOR_SWITCH_VARIANT,
      TestProject.MULTI_FLAVOR_WITH_FILTERING,
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
  val projectStructure: Sequence<String>,
  val ideModels: Sequence<String>
)

fun ModuleDumpWithType.projectStructure() : String = annotate().projectStructure.joinToString(separator = "\n")
fun ModuleDumpWithType.ideModels() : String = annotate().ideModels.joinToString(separator = "\n")
fun ModuleDumpWithType.filterOutRootModule() = excludeByModuleName(rootModuleNames)
fun ModuleDumpWithType.filterToPhasedSyncModules() = includeByModuleName(phasedSyncModuleNames)
fun ModuleDumpWithType.filterToAndroidModules() = includeByModuleName(androidModuleNames)

fun ModuleDumpWithType.annotate() = copy(
  projectStructure = projectStructure.annotate(phasedSyncModuleNames, androidModuleNames),
  ideModels = ideModels.annotate(phasedSyncModuleNames, androidModuleNames)
)

fun Sequence<String>.annotate(
  phasedSyncModuleNames: List<String>,
  androidModuleNames: List<String>
) = this.map { line ->
  buildString {
    if (phasedSyncModuleNames.any { line.contains("MODULE ($it)") })
      append("PHASED")
    else
      append("LEGACY")
    append(" ")
    if (androidModuleNames.any { line.contains("MODULE ($it)") })
      append("ANDROID")
    else
      append("NON-ANDROID")
    append(" ")
    append(line)
  }
}

fun ModuleDumpWithType.filterOutExpectedInconsistencies() = copy(
  projectStructure = projectStructure.filter { line ->
    DEPENDENCY_RELATED_PROPERTIES.none { line.contains(it) } && // We don't set up dependencies in phased sync
    !line.contains("BUILD_TASKS") // We don't set up tasks in phased sync
  },
  ideModels = ideModels.filter { line ->
    IDE_MODEL_DEPENDENCY_RELATED_PROPERTIES.none { line.contains(it) } // We don't set up dependencies in phased sync
  }
)

fun Project.dumpModules(knownAndroidPaths: Set<File>) =
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
    androidModuleNames = modules.filter { it.projectDirectory() in knownAndroidPaths }.map { it.name },
    projectStructure = dumpAllModuleEntries(),
    ideModels = dumpAllIdeModels()
  )

private fun Project.dumpAllModuleEntries() : Sequence<String> {
  val dumper = createDumper()
  modules.sortedBy { it.name }.forEach {
    dumper.dump(it)
  }

  return dumper.toString().nameProperties()
}

private fun Project.dumpAllIdeModels() : Sequence<String> {
  val dumper = createDumper()
  dumper.dumpAndroidIdeModel(
      this,
      kotlinModels = { null },
      kaptModels = { null },
      mppModels = { null },
      externalProjects = { null },
      // We have full variant information set up in the GradleAndroidModel in phased sync case
      // without the dependencies, whereas that's not the case formerly.
      dumpAllVariants = false,
      // IdeModelDumper dump the root project structure by default, we don't want that here
      dumpRootModuleProjectStructure = false,
      // IdeModelDumper dumps only one entry from each linked group to prevent noise, but we should
      // compare everything here.
      dumpAllLinkedModules = true
  )

  return dumper.toString().nameProperties()
}


private fun Project.createDumper() = ProjectDumper(
  androidSdk = getSdk().toFile(),
  devBuildHome = TestUtils.getWorkspaceRoot().toFile(),
  projectJdk = ProjectRootManager.getInstance(this).projectSdk
)

private fun Module.projectDirectory(): File? = ExternalSystemModulePropertyManager.getInstance(this).getLinkedProjectPath()?.let { File(it) }

val DEPENDENCY_RELATED_PROPERTIES = setOf(
  "/ORDER_ENTRY",
  "/LIBRARY",
)

val IDE_MODEL_DEPENDENCY_RELATED_PROPERTIES = setOf(
  "LIBRARY_TABLE",
  "Artifact/Dependencies",
  "/ProvidedDependencies",
  "/RuntimeOnlyClasses"
)


private fun String.nameProperties(): Sequence<String> =
  this
    .splitToSequence('\n')
    .let { nameProperties(
      it,
      attachValue = true,
      // Make sure the top level entries where all children end up being filtered also gets filtered
      skipTopLevel = true
    ) }
    .map { it.first }

private fun ModuleDumpWithType.excludeByModuleName(names: List<String>) = copy (
  projectStructure = projectStructure.filter { line ->
    names.none { line.contains("MODULE ($it)") }
  },
  ideModels = ideModels.filter { line ->
    names.none { line.contains("MODULE ($it)") }
  }
)

private fun ModuleDumpWithType.includeByModuleName(names: List<String>) = copy (
  projectStructure = projectStructure.filter { line ->
    names.any { line.contains("MODULE ($it)") }
  },
  ideModels = ideModels.filter { line ->
    names.any { line.contains("MODULE ($it)") }
  }
)

@Suppress("UnstableApiUsage")
internal class ModelDumpSyncContributor: GradleSyncListener {
  val knownAndroidPaths = mutableSetOf<File>()
  lateinit var intermediateDump: ModuleDumpWithType

  override fun onModelFetchCompleted(context: ProjectResolverContext) {
    // Multiple composite builds can invoke this method, so keeping track of all android projects
    knownAndroidPaths += context.allBuilds.flatMap { buildModel ->
      buildModel.projects.filter { projectModel ->
        context.getProjectModel(projectModel, Versions::class.java) != null
      }.map {
        it.projectDirectory
      }
    }

    intermediateDump = context.project.dumpModules(knownAndroidPaths)
  }
}
