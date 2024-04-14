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
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeBaseArtifact
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.gradle.util.GradleBuilds
import com.android.tools.idea.projectsystem.gradle.GradleHolderProjectPath
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath
import com.android.tools.idea.projectsystem.gradle.getGradleIdentityPath
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.resolveIn
import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.android.tools.idea.projectsystem.isHolderModule
import com.android.tools.idea.projectsystem.isMainModule
import com.android.tools.idea.projectsystem.isScreenshotTestModule
import com.android.tools.idea.projectsystem.isUnitTestModule
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.containers.addIfNotNull
import org.gradle.api.plugins.JavaPlugin
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.gradleJava.configuration.kotlinGradleProjectDataOrNull
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.service.project.data.GradleExtensionsDataService
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.Deque

class GradleTaskFinderWorker private constructor(
  private val project: Project,
  private val requestedModules: List<ModuleAndMode>
) {

  constructor(project: Project, buildMode: BuildMode, modules: List<Module>, expandModule: Boolean = false):
    this(
      project,
      requestedModules = modules.mapNotNull {
        if (it.getGradleProjectPath() == null) return@mapNotNull null // Skip any non Gradle projects.
        ModuleAndMode(it, buildMode, expandModule = expandModule)
      }
    )

  fun find(): Map<Path, Collection<String>> {
    val moduleTasks = expandList(requestedModules).mapNotNull { findTasksForModule(it) }

    // Ensure clean tasks are always before any other tasks to avoid e.g. ":foo:assemble :foo:clean". Even with correct task ordering,
    // having e.g. ":a:clean :a:assemble :b:clean :b:assemble" seems to trigger a bug in Gradle (see b/290954881).
    val rootedTasks = moduleTasks.flatMap { it.rootedTasks { cleanTasks } } + moduleTasks.flatMap { it.rootedTasks { tasks } }

    return rootedTasks
      .groupBy(
        keySelector = { it.root },
        valueTransform = { it.taskPath }
      )
      .mapValues { it.value.toSet() }
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

        moduleToProcess.expand().forEach { modulesToProcess.addFirst(it) }
        yield(moduleToProcess)
      }
    }
    return modulesAndModes.toList()
  }

  private fun ModuleAndMode.expand(): List<ModuleAndMode> {
    if (!expandModule) return emptyList()
    val androidModel = androidModel ?: return emptyList()
    val androidProject = androidModel.androidProject
    val buildRoot = module.getGradleProjectPath()?.buildRoot ?: error("No Gradle path for $module")

    return when (androidProject.projectType) {
      IdeAndroidProjectType.PROJECT_TYPE_APP ->
        androidProject
          .dynamicFeatures
          .mapNotNull { GradleSourceSetProjectPath(buildRoot, it, IdeModuleWellKnownSourceSet.MAIN).toModuleAndMode(buildMode) }

      IdeAndroidProjectType.PROJECT_TYPE_TEST ->
        if (buildMode != BuildMode.ASSEMBLE && buildMode != BuildMode.REBUILD) emptyList()
        else androidModel
          .selectedVariant
          .testedTargetVariants
          .map { it.targetProjectPath }
          .mapNotNull {
            GradleSourceSetProjectPath(
              buildRoot,
              it,
              IdeModuleWellKnownSourceSet.MAIN
            ).toModuleAndMode(if (buildMode == BuildMode.REBUILD) BuildMode.ASSEMBLE else buildMode)
          }
      IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE ->
        androidProject
          .baseFeature
          ?.let {
            listOfNotNull(
              GradleHolderProjectPath(buildRoot, it)
                .toModuleAndMode(
                  buildMode = buildMode
                )
            )
          }
          .orEmpty()
      else -> emptyList()
    }
  }

  private fun findTasksForModule(moduleToProcess: ModuleAndMode): ModuleTasks? {
    return when {
      moduleToProcess.androidModel != null -> {
        when (moduleToProcess.buildMode) {
          BuildMode.REBUILD ->
            moduleToProcess.getTasksBy { listOfNotNull(
              it.assembleTaskName,
              it.getPrivacySandboxSdkTask(),
              it.getAdditionalApkSplitTask(),
              it.getPrivacySandboxSdkLegacyTask())
            }.copy( cleanTasks = setOf("clean"))
          // Note, this should eventually include ":clean" tasks, but it is dangerous right now as it might run in a separate but second
          // invocation.
          // TODO(b/235567998): Move all "clean" processing here.
          BuildMode.CLEAN -> moduleToProcess.getTasksBy(isClean = true) { it.ideSetupTaskNames }
          BuildMode.ASSEMBLE ->
            moduleToProcess.getTasksBy {
              listOfNotNull(
                it.assembleTaskName,
                it.getPrivacySandboxSdkTask(),
                it.getAdditionalApkSplitTask(),
                it.getPrivacySandboxSdkLegacyTask())
            }
          BuildMode.COMPILE_JAVA ->
            moduleToProcess.getTaskBy {
                it.compileTaskName
            }

          BuildMode.SOURCE_GEN -> moduleToProcess.getTasksBy { it.ideSetupTaskNames }
          BuildMode.BUNDLE -> {
            moduleToProcess.getTasksBy {
              listOfNotNull(
                (it as? IdeAndroidArtifact)?.buildInformation?.bundleTaskName,
                it.getPrivacySandboxSdkTask(),
                it.getPrivacySandboxSdkLegacyTask()
              ) // Don't need getAdditionalApkSplitTask for bundle deployment
            }
          }
          BuildMode.APK_FROM_BUNDLE -> {
            ModuleTasks(
              module = moduleToProcess.module,
              cleanTasks = emptySet(),
              tasks =
              moduleToProcess.getTasksBy {
                listOfNotNull(
                  (it as? IdeAndroidArtifact)?.buildInformation?.apkFromBundleTaskName,
                  it.getPrivacySandboxSdkTask(),
                  it.getPrivacySandboxSdkLegacyTask()
                ) // Don't need getAdditionalApkSplitTask for bundle deployment
              }.tasks +
              if (moduleToProcess.androidModel.androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE &&
                  (moduleToProcess.module.isAndroidTestModule() || moduleToProcess.module.isHolderModule()))
                setOfNotNull(
                  moduleToProcess.androidModel.selectedVariant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }?.assembleTaskName
                )
              else emptySet()
            )
          }
          BuildMode.BASELINE_PROFILE_GEN -> {
            ModuleTasks(
              module = moduleToProcess.module,
              cleanTasks = emptySet(),
              tasks = setOfNotNull(moduleToProcess.androidModel.getGenerateBaselineProfileTaskNameForSelectedVariant(false))
            )
          }
          BuildMode.BASELINE_PROFILE_GEN_ALL_VARIANTS -> {
            ModuleTasks(
              module = moduleToProcess.module,
              cleanTasks = emptySet(),
              tasks = setOfNotNull(moduleToProcess.androidModel.getGenerateBaselineProfileTaskNameForSelectedVariant(true))
            )
          }
        }
      }

      moduleToProcess.isKmpModule -> {
        when (moduleToProcess.buildMode) {
          BuildMode.ASSEMBLE -> ModuleTasks(
            module = moduleToProcess.module,
            cleanTasks = emptySet(),
            tasks = setOf(GradleBuilds.DEFAULT_ASSEMBLE_TASK_NAME)
          )
          BuildMode.REBUILD -> ModuleTasks(
            module = moduleToProcess.module,
            cleanTasks = setOf(GradleBuilds.CLEAN_TASK_NAME),
            tasks = setOf(GradleBuilds.DEFAULT_ASSEMBLE_TASK_NAME)
          )
          BuildMode.COMPILE_JAVA -> ModuleTasks(
            module = moduleToProcess.module,
            cleanTasks = emptySet(),
            tasks = setOf(JavaPlugin.COMPILE_JAVA_TASK_NAME)
          )
          else -> null
        }
      }

      moduleToProcess.isGradleJavaModule -> {
        ModuleTasks(
          module = moduleToProcess.module,
          cleanTasks = when (moduleToProcess.buildMode) {
            BuildMode.CLEAN -> emptySet() // TODO(b/235567998): Unify clean handling.
            BuildMode.REBUILD -> setOf(GradleBuilds.CLEAN_TASK_NAME)
            BuildMode.ASSEMBLE, BuildMode.COMPILE_JAVA, BuildMode.SOURCE_GEN, BuildMode.BUNDLE, BuildMode.APK_FROM_BUNDLE, BuildMode.BASELINE_PROFILE_GEN, BuildMode.BASELINE_PROFILE_GEN_ALL_VARIANTS -> emptySet()
          },
          tasks = getGradleJavaTaskNames(moduleToProcess.buildMode, moduleToProcess.module)
        )
      }

      else -> null
    }
  }

  private fun IdeBaseArtifact.getPrivacySandboxSdkTask() =
    (this as? IdeAndroidArtifact)?.privacySandboxSdkInfo?.task

  private fun IdeBaseArtifact.getAdditionalApkSplitTask() =
    (this as? IdeAndroidArtifact)?.privacySandboxSdkInfo?.additionalApkSplitTask

  private fun IdeBaseArtifact.getPrivacySandboxSdkLegacyTask() =
    (this as? IdeAndroidArtifact)?.privacySandboxSdkInfo?.taskLegacy

  private fun GradleProjectPath.toModuleAndMode(
    buildMode: BuildMode
  ): ModuleAndMode? =
    resolveIn(project)?.let { ModuleAndMode(it, buildMode = buildMode) }
}

private data class RootedTask(val root: Path, val taskPath: String)
private data class ModuleTasks(val module: Module, val cleanTasks: Set<String>, val tasks: Set<String>)

private fun getTaskRunningInfo(module: Module): Pair<File, String>? {
  val externalRootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module) ?: return null
  val gradleProjectSettings = GradleSettings.getInstance(module.project).getLinkedProjectSettings(externalRootProjectPath) ?: return null

  return if (gradleProjectSettings.resolveGradleVersion() >= supportsDirectTaskInvocationInCompositeBuilds) {
    val gradleIdentityPath = module.getGradleIdentityPath() ?: return null
    File(externalRootProjectPath) to gradleIdentityPath
  } else {
    val gradleProjectPath = module.getGradleProjectPath() ?: return null
    File(gradleProjectPath.buildRoot) to gradleProjectPath.path
  }
}

private fun ModuleTasks.rootedTasks(taskSelector: ModuleTasks.() -> Set<String>): List<RootedTask> {
  val (taskExecutionDir, projectPath) = getTaskRunningInfo(module)?.let {
    it.first.toPath() to it.second.trimEnd(':')
  } ?: return emptyList()

  fun toRooted(taskName: String) = RootedTask(taskExecutionDir, "$projectPath:$taskName")

  return taskSelector(this).map { toRooted(it) }
}

private data class ModuleAndMode(
  val module: Module,
  val buildMode: BuildMode,
  val expandModule: Boolean = false
) {
  val androidModel: GradleAndroidModel? = GradleAndroidModel.get(module)
  val isKmpModule: Boolean = module.isMultiPlatformModule()
  val isGradleJavaModule: Boolean = if (androidModel == null) module.isGradleJavaModule() else false
}

@Suppress("UnstableApiUsage")
private fun Module.isMultiPlatformModule(): Boolean {
  if (isMultiPlatformModule) return true
  // Check to see if the KMP plugin is applied to this project.
  return CachedModuleDataFinder.findMainModuleData(this)?.kotlinGradleProjectDataOrNull?.isHmpp ?: false
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

private fun getGradleJavaTaskNames(buildMode: BuildMode, module: Module): Set<String> {
  return setOfNotNull(
    when (buildMode) {
      BuildMode.ASSEMBLE -> GradleBuilds.DEFAULT_ASSEMBLE_TASK_NAME
      BuildMode.REBUILD -> GradleBuilds.DEFAULT_ASSEMBLE_TASK_NAME
      BuildMode.COMPILE_JAVA -> JavaPlugin.COMPILE_JAVA_TASK_NAME
      BuildMode.CLEAN -> null // Handled directly.
      BuildMode.SOURCE_GEN -> null
      BuildMode.BUNDLE -> null
      BuildMode.APK_FROM_BUNDLE -> null
      BuildMode.BASELINE_PROFILE_GEN -> null
      BuildMode.BASELINE_PROFILE_GEN_ALL_VARIANTS -> null
    },
    if (module.isUnitTestModule() || module.isHolderModule()) {
      when (buildMode) {
        BuildMode.ASSEMBLE -> JavaPlugin.TEST_CLASSES_TASK_NAME
        BuildMode.REBUILD -> JavaPlugin.TEST_CLASSES_TASK_NAME
        BuildMode.COMPILE_JAVA -> JavaPlugin.TEST_CLASSES_TASK_NAME
        BuildMode.CLEAN -> null // Handled directly.
        BuildMode.SOURCE_GEN -> null
        BuildMode.BUNDLE -> null
        BuildMode.APK_FROM_BUNDLE -> null
        BuildMode.BASELINE_PROFILE_GEN -> null
        BuildMode.BASELINE_PROFILE_GEN_ALL_VARIANTS -> null
      }
    } else null
  )
}

private fun ModuleAndMode.getTasksBy(
  isClean: Boolean = false,
  by: (artifact: IdeBaseArtifact) -> List<String>
): ModuleTasks {
  val tasks: Set<String> = androidModel?.selectedVariant?.let { variant ->
    val artifacts =
      mutableListOf<IdeBaseArtifact>().apply {
        addIfNotNull(variant.mainArtifact.takeIf { module.isHolderModule() || module.isMainModule() || (module.isAndroidTestModule() && expandModule) })
        addIfNotNull(variant.hostTestArtifacts.find { it.name == IdeArtifactName.UNIT_TEST }.takeIf { module.isUnitTestModule() || module.isHolderModule() })
        addIfNotNull(variant.hostTestArtifacts.find { it.name == IdeArtifactName.SCREENSHOT_TEST }.takeIf { module.isScreenshotTestModule() || module.isHolderModule() })
        addIfNotNull(variant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }.takeIf { module.isAndroidTestModule() || module.isHolderModule() })
      }
      artifacts.flatMap { by.invoke(it) }.toSet()
  }.orEmpty()
  return ModuleTasks(module, tasks.takeIf { isClean }.orEmpty(), tasks.takeUnless { isClean }.orEmpty())
}

private fun ModuleAndMode.getTaskBy(
  isClean: Boolean = false,
  by: (artifact: IdeBaseArtifact) -> String?
): ModuleTasks {
  return getTasksBy(isClean, fun(artifact: IdeBaseArtifact): List<String> = listOfNotNull(by(artifact)))
}

private val supportsDirectTaskInvocationInCompositeBuilds = GradleVersion.version("6.8")