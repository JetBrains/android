/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker

import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeBaseArtifact
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.gradle.util.GradleBuilds
import com.android.tools.idea.projectsystem.gradle.GradleHolderProjectPath
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath
import com.android.tools.idea.projectsystem.gradle.BuildRelativeGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.buildNamePrefixedGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.getBuildAndRelativeGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.resolveIn
import com.android.tools.idea.projectsystem.gradle.rootBuildPath
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.gradle.api.plugins.JavaPlugin
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.service.project.data.GradleExtensionsDataService
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.Deque

class GradleTaskFinderWorker private constructor(
  private val project: Project,
  private val requestedModules: List<ModuleAndMode>
) {

  constructor(project: Project, buildMode: BuildMode, testCompileType: TestCompileType, modules: List<Module>):
    this(
      project,
      requestedModules = modules.mapNotNull {
        if (it.getGradleProjectPath() == null) return@mapNotNull null // Skip any non Gradle projects.
        ModuleAndMode(it, buildMode, testCompileType)
      }
    )

  fun find(): Map<Path, Collection<String>> {
    val moduleTasks = findTasks()

    val rootedTasks =
      moduleTasks.flatMap { it.rootedTasks { cleanTasks } } + moduleTasks.flatMap { it.rootedTasks { tasks } }

    return rootedTasks
      .groupBy(
        keySelector = { it.root },
        valueTransform = { it.taskPath }
      )
      .mapValues { it.value.toSet() }
  }

  private fun findTasks(): List<ModuleTasks> {
    val modulesAndModes = expandList(requestedModules)
    return modulesAndModes.mapNotNull { findTasksForModule(it) }
  }

  /**
   * Expands the list of [modules] to include those that won't be built by the build system but are required to be built by the selected
   * build mode. For example, assembling an app assumes assembling its dynamic features.
   */
  private fun expandList(modules: List<ModuleAndMode>): List<ModuleAndMode> {
    val modulesToProcess: Deque<ModuleAndMode> = ArrayDeque(modules)

    val modulesAndModes = sequence {
      val seen = mutableSetOf<ModuleAndMode>()
      while (modulesToProcess.isNotEmpty()) {
        val moduleToProcess = modulesToProcess.poll()
        if (!seen.add(moduleToProcess)) continue

        expandModule(moduleToProcess).forEach { modulesToProcess.addFirst(it) }
        yield(moduleToProcess)
      }
    }
    return modulesAndModes.toList()
  }

  /**
   * Includes targets required by the requested build mode but such that are not implicitly build by tasks invoked on the current module.
   *
   * Examples:
   *   `TEST_ONLY_MODULE` does not build its target APK
   *   `APP`s do not build their dynamic features
   *   `DYNAMIC_FEATURE`s need to be built in the scope of the whole APP for deployment from a bundle.
   */
  private fun expandModule(moduleToProcess: ModuleAndMode): List<ModuleAndMode> {
    return when (moduleToProcess.buildMode) {
      BuildMode.CLEAN ->
        // TODO(b/235567998): Why? CLEAN should be applied to the same projects to which ASSEMBLE is applied.
        if (moduleToProcess.androidModel?.androidProject?.projectType == IdeAndroidProjectType.PROJECT_TYPE_TEST) emptyList()
        else moduleToProcess.expand()

      BuildMode.ASSEMBLE -> moduleToProcess.expand()
      BuildMode.REBUILD -> moduleToProcess.expand()

      BuildMode.BUNDLE -> moduleToProcess.expand() // TODO(b/235567998): emptyList() // Do not expand for BUNDLE as one can only bundle an app.
      BuildMode.APK_FROM_BUNDLE -> moduleToProcess.expand()

      BuildMode.COMPILE_JAVA -> moduleToProcess.expand() // TODO(b/235567998): no need - compilation naturally follows dependencies.
      BuildMode.SOURCE_GEN -> moduleToProcess.expand()  // TODO(b/235567998): no need - invoked on this module only. It should be invoked
                                                        // on all modules when needed.
    }
  }

  private fun ModuleAndMode.expand(): List<ModuleAndMode> {
    if (!expand) return emptyList()
    val androidModel = androidModel ?: return emptyList()
    val androidProject = androidModel.androidProject
    val buildRoot = gradleProjectPath.rootBuildPath()

    return when (androidProject.projectType) {
      IdeAndroidProjectType.PROJECT_TYPE_LIBRARY -> emptyList()
      IdeAndroidProjectType.PROJECT_TYPE_ATOM -> emptyList()
      IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP -> emptyList() // Builds everything needed.
      IdeAndroidProjectType.PROJECT_TYPE_FEATURE -> emptyList() // Is treated like a library module.

      IdeAndroidProjectType.PROJECT_TYPE_APP ->
        androidProject
          .dynamicFeatures
          .mapNotNull { GradleHolderProjectPath(buildRoot, it).toModuleAndMode(buildMode, testCompileMode = testCompileMode) }

      IdeAndroidProjectType.PROJECT_TYPE_TEST ->
        // TODO(b/235567998): Review. It does not look right that building a test module to test the app deployed from a bundle
        // should not build APKs from bundle but should build APKs directly. There should not be a difference between a test module
        // and androidTests in general.
        if (buildMode != BuildMode.ASSEMBLE && buildMode != BuildMode.REBUILD) emptyList()
        else androidModel
          .selectedVariant
          .testedTargetVariants
          .map { it.targetProjectPath }
          // TODO(b/235567998): expand REBUILD to CLEAN + ASSEMBLE at the first step when CLEAN reworked.
          .mapNotNull { GradleHolderProjectPath(buildRoot, it).toModuleAndMode(if (buildMode == BuildMode.REBUILD) BuildMode.ASSEMBLE else buildMode) }

      IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE ->
        // TODO(b/235567998): Review. Assembling/bundling etc. a feature involves its app. Compiling should follow dependencies and does
        //  not need handling by expand.
        if (!testCompileMode.compileAndroidTests) emptyList()
        else {
          androidProject
            .baseFeature
            ?.let {
              listOfNotNull(
                GradleHolderProjectPath(buildRoot, it)
                  .toModuleAndMode(
                    buildMode = buildMode,
                    testCompileMode =
                    // TODO(b/235567998):tests should come from the first module in the chain only.
                    if (buildMode == BuildMode.BUNDLE || buildMode == BuildMode.APK_FROM_BUNDLE) TestCompileType.NONE else testCompileMode,
                    // TODO(b/235567998): Not clear why bundling needs to expand further. In general if we build a feature we do not need
                    //  not build all features and bundling builds all of them anyway.
                    // TODO(b/235567998): change to false // No need to include other dynamic features.
                    expand = (buildMode == BuildMode.BUNDLE || buildMode == BuildMode.APK_FROM_BUNDLE)
                  )
              )
            }
            .orEmpty()
        }
    }
  }

  private fun findTasksForModule(moduleToProcess: ModuleAndMode): ModuleTasks? {
    return when {
      moduleToProcess.androidModel != null -> {
        when (moduleToProcess.buildMode) {
          BuildMode.REBUILD ->
            moduleToProcess.getTasksBy { listOfNotNull(
              it.assembleTaskName,
              it.getPrivacySandboxSdkTask())
            }.copy( cleanTasks = setOf("clean"))
          // Note, this should eventually include ":clean" tasks, but it is dangerous right now as it might run in a separate but second
          // invocation.
          // TODO(b/235567998): Move all "clean" processing here.
          BuildMode.CLEAN -> moduleToProcess.getTasksBy(isClean = true) { it.ideSetupTaskNames }
          BuildMode.ASSEMBLE ->
            moduleToProcess.getTasksBy {
              listOfNotNull(
                it.assembleTaskName,
                it.getPrivacySandboxSdkTask())
            }
          BuildMode.COMPILE_JAVA ->
            moduleToProcess
              // TODO(b/235567998): Review. This is to exclude main artifact compile task when building unit tests, but probably applies to
              // android tests as well. It looks like it might be simpler to expand the test compile mode similarly to build modes and
              // handle this at expand level.
              .getTaskBy(implicitMain = moduleToProcess.testCompileMode == TestCompileType.UNIT_TESTS) { it.compileTaskName }

          BuildMode.SOURCE_GEN -> moduleToProcess.getTasksBy { it.ideSetupTaskNames }
          BuildMode.BUNDLE -> {
            moduleToProcess.getTasksBy {
              listOfNotNull(
                (it as? IdeAndroidArtifact)?.buildInformation?.bundleTaskName,
                it.getPrivacySandboxSdkTask())
            }
          }
          BuildMode.APK_FROM_BUNDLE -> {
            ModuleTasks(
              moduleToProcess.gradleProjectPath,
              cleanTasks = emptySet(),
              tasks =
              // TODO(b/235567998): Review. Maybe replace with test compile mode expansion.
              moduleToProcess.getTasksBy {
                listOfNotNull(
                  (it as? IdeAndroidArtifact)?.buildInformation?.apkFromBundleTaskName,
                  it.getPrivacySandboxSdkTask()
                )
              }.tasks +
              if (moduleToProcess.androidModel.androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE && moduleToProcess.testCompileMode.compileAndroidTests)
                setOfNotNull(moduleToProcess.androidModel.selectedVariant.androidTestArtifact?.assembleTaskName)
              else emptySet()
            )
          }
        }
      }

      moduleToProcess.isGradleJavaModule -> {
        ModuleTasks(
          gradlePath = moduleToProcess.gradleProjectPath,
          cleanTasks = when (moduleToProcess.buildMode) {
            BuildMode.CLEAN -> emptySet() // TODO(b/235567998): Unify clean handling.
            BuildMode.REBUILD -> setOf("clean")
            BuildMode.ASSEMBLE, BuildMode.COMPILE_JAVA, BuildMode.SOURCE_GEN, BuildMode.BUNDLE, BuildMode.APK_FROM_BUNDLE -> emptySet()
          },
          tasks = getGradleJavaTaskNames(moduleToProcess.buildMode, moduleToProcess.testCompileMode)
        )
      }

      else -> null
    }
  }

  private fun IdeBaseArtifact.getPrivacySandboxSdkTask() =
    (this as? IdeAndroidArtifact)?.privacySandboxSdkInfo?.task


  private fun GradleProjectPath.toModuleAndMode(
    buildMode: BuildMode,
    expand: Boolean = true,
    testCompileMode: TestCompileType = TestCompileType.NONE
  ): ModuleAndMode? =
    resolveIn(project)?.let { ModuleAndMode(it, buildMode = buildMode, expand = expand, testCompileMode = testCompileMode) }

}

private data class RootedTask(val root: Path, val taskPath: String)
private data class ModuleTasks(val gradlePath: BuildRelativeGradleProjectPath, val cleanTasks: Set<String>, val tasks: Set<String>)

private fun ModuleTasks.rootedTasks(tasks: ModuleTasks.() -> Set<String>): List<RootedTask> {
  return tasks().map {
    RootedTask(gradlePath.rootBuildId.toPath(), "${gradlePath.buildNamePrefixedGradleProjectPath().trimEnd(':')}:$it")
  }
}

private data class ModuleAndMode(
  val module: Module,
  val buildMode: BuildMode,
  val testCompileMode: TestCompileType,
  val expand: Boolean = true
) {
  val gradleProjectPath: BuildRelativeGradleProjectPath =
    module.getBuildAndRelativeGradleProjectPath() ?: error("Module $module should have been skipped")

  val androidModel: GradleAndroidModel? = GradleAndroidModel.get(module)
  val isGradleJavaModule: Boolean = if (androidModel == null) module.isGradleJavaModule() else false
}

@Suppress("UnstableApiUsage")
private fun Module.isGradleJavaModule(): Boolean {
  val gradleModuleData =
    CachedModuleDataFinder.getGradleModuleData(this) // `buildSrc` modules are handled by Gradle so we don't need to run any tasks for them.
  if (gradleModuleData == null || gradleModuleData.isBuildSrcModule) return false
  val extensions = gradleModuleData.findAll(GradleExtensionsDataService.KEY).firstOrNull() ?: return false

  // Check to see if the Java plugin is applied to this project.
  return extensions.extensions.any { it.name == "java" }
}

private fun getGradleJavaTaskNames(buildMode: BuildMode, testCompileMode: TestCompileType): Set<String> {
  return setOfNotNull(
    when (buildMode) {
      BuildMode.ASSEMBLE -> GradleBuilds.DEFAULT_ASSEMBLE_TASK_NAME
      BuildMode.REBUILD -> GradleBuilds.DEFAULT_ASSEMBLE_TASK_NAME
      BuildMode.COMPILE_JAVA -> JavaPlugin.COMPILE_JAVA_TASK_NAME
      BuildMode.CLEAN -> null // Handled directly.
      BuildMode.SOURCE_GEN -> null
      BuildMode.BUNDLE -> null
      BuildMode.APK_FROM_BUNDLE -> null
    },
    if (testCompileMode.compileUnitTests) {
      when (buildMode) {
        BuildMode.ASSEMBLE -> JavaPlugin.TEST_CLASSES_TASK_NAME
        BuildMode.REBUILD -> JavaPlugin.TEST_CLASSES_TASK_NAME
        BuildMode.COMPILE_JAVA -> JavaPlugin.TEST_CLASSES_TASK_NAME
        BuildMode.CLEAN -> null // Handled directly.
        BuildMode.SOURCE_GEN -> null
        BuildMode.BUNDLE -> null
        BuildMode.APK_FROM_BUNDLE -> null
      }
    } else null
  )
}

private fun ModuleAndMode.getTasksBy(
  isClean: Boolean = false,
  implicitMain: Boolean = false,
  by: (artifact: IdeBaseArtifact) -> List<String>
): ModuleTasks {
  val tasks: Set<String> = androidModel?.selectedVariant?.let { variant ->
    listOfNotNull(
      variant.mainArtifact.takeUnless { implicitMain && (testCompileMode.compileAndroidTests || testCompileMode.compileUnitTests) },
      variant.unitTestArtifact.takeIf { testCompileMode.compileUnitTests },
      variant.androidTestArtifact.takeIf { testCompileMode.compileAndroidTests },
    ).flatMap { by.invoke(it) }.toSet()
  }.orEmpty()
  return ModuleTasks(gradleProjectPath, tasks.takeIf { isClean }.orEmpty(), tasks.takeUnless { isClean }.orEmpty())
}

private fun ModuleAndMode.getTaskBy(
  isClean: Boolean = false,
  implicitMain: Boolean = false,
  by: (artifact: IdeBaseArtifact) -> String?
): ModuleTasks {
  return getTasksBy(isClean, implicitMain, fun(artifact: IdeBaseArtifact): List<String> = listOfNotNull(by(artifact)))
}
