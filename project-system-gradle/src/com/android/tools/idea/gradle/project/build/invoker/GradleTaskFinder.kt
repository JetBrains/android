/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModel.Companion.get
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.gradle.util.GradleBuilds
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.gradle.util.GradleProjects
import com.android.tools.idea.projectsystem.gradle.GradleHolderProjectPath
import com.android.tools.idea.projectsystem.gradle.buildRootDir
import com.android.tools.idea.projectsystem.gradle.findModule
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.ListMultimap
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import org.gradle.api.plugins.JavaPlugin
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetProperties
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.model.GradleProperty
import org.jetbrains.plugins.gradle.service.project.data.GradleExtensionsDataService
import java.nio.file.Path
import java.util.Collections

class GradleTaskFinder {
  fun findTasksToExecuteForTest(
    modules: Array<Module>,
    testModules: Array<Module>,
    buildMode: BuildMode,
    testCompileType: TestCompileType
  ): ListMultimap<Path, String> {
    val allTasks: ListMultimap<Path, String> = findTasksToExecuteCore(modules, buildMode, TestCompileType.NONE)
    val testedModulesTasks: ListMultimap<Path, String> = findTasksToExecuteCore(testModules, buildMode, testCompileType)

    // Add testedModulesTasks to allTasks without duplicate
    for ((key, value) in testedModulesTasks.entries()) {
      if (!allTasks.containsEntry(key, value)) {
        allTasks.put(key, value)
      }
    }
    if (allTasks.isEmpty) {
      notifyNoTaskFound(
        modules + testModules,
        buildMode,
        testCompileType
      )
    }
    return allTasks
  }

  fun findTasksToExecute(
    modules: Array<Module>,
    buildMode: BuildMode,
    testCompileType: TestCompileType
  ): ListMultimap<Path, String> {
    val result = findTasksToExecuteCore(modules, buildMode, testCompileType)
    if (result.isEmpty) {
      notifyNoTaskFound(modules, buildMode, testCompileType)
    }
    return result
  }

  private fun findTasksToExecuteCore(
    modules: Array<Module>,
    buildMode: BuildMode,
    testCompileType: TestCompileType
  ): ArrayListMultimap<Path, String> {
    val tasks = LinkedHashMultimap.create<Path, String>()
    val cleanTasks = LinkedHashMultimap.create<Path, String>()
    val allModules: MutableSet<Module> = LinkedHashSet()
    for (module in modules) {
      allModules.addAll(GradleProjectSystemUtil.getModulesToBuild(module))
    }

    // Instrumented test support for Dynamic Features: base-app module should be added explicitly for gradle tasks
    if (testCompileType === TestCompileType.ANDROID_TESTS) {
      for (module in modules) {
        val baseAppModule = DynamicAppUtils.getBaseFeature(module)
        if (baseAppModule != null) {
          allModules.add(baseAppModule)
        }
      }
    }
    for (module in allModules) {
      val moduleTasks: MutableSet<String> = LinkedHashSet()
      val gradleProjectPath = module.getGradleProjectPath()
      if (gradleProjectPath != null) {
        findAndAddGradleBuildTasks(module, gradleProjectPath.path, buildMode, moduleTasks, testCompileType)
        val gradleProjectPathCore = module.getGradleProjectPath() ?: continue
        val keyPath = gradleProjectPathCore.buildRootDir.toPath()
        if (buildMode == BuildMode.REBUILD && !moduleTasks.isEmpty()) {
          // Clean only if other tasks are needed
          cleanTasks.put(keyPath, GradleProjectSystemUtil.createFullTaskName(gradleProjectPath.path, GradleBuilds.CLEAN_TASK_NAME))
        }

        // Remove duplicates and prepend moduleTasks to tasks.
        // TODO(xof): investigate whether this effective reversal is necessary for or neutral regarding correctness.
        moduleTasks.addAll(tasks[keyPath])
        tasks.removeAll(keyPath)
        tasks.putAll(keyPath, moduleTasks)
      }
    }
    val result = ArrayListMultimap.create<Path, String>()
    for (key in cleanTasks.keySet()) {
      val keyTasks: List<String> = ArrayList(cleanTasks[key])
      // We effectively reversed the per-module tasks, other than clean, above; reverse the clean tasks here.
      Collections.reverse(keyTasks)
      result.putAll(key, keyTasks)
    }
    for ((key, value) in tasks.entries()) {
      result.put(key, value)
    }
    return result
  }

  private fun notifyNoTaskFound(modules: Array<Module>, mode: BuildMode, type: TestCompileType) {
    if (modules.isEmpty()) return
    val project = modules[0].project
    val logModuleNames = modules
      .take(MAX_MODULES_TO_INCLUDE_IN_LOG_MESSAGE)
      .mapNotNull { module: Module ->
        GradleProjects.getGradleModulePath(module)
      }
      .joinToString(", ") + if (modules.size > MAX_MODULES_TO_INCLUDE_IN_LOG_MESSAGE) "..." else ""

    val logMessage =
      String.format("Unable to find Gradle tasks to build: [%s]. Build mode: %s. Tests: %s.", logModuleNames, mode, type.displayName)
    logger.warn(logMessage)
    val moduleNames = modules
      .take(MAX_MODULES_TO_SHOW_IN_NOTIFICATION)
      .mapNotNull { module: Module ->
        GradleProjects.getGradleModulePath(module)
      }
      .joinToString(", ") +if (modules.size > 5) "..." else ""

    val message =
      String.format("Unable to find Gradle tasks to build: [%s]. <br>Build mode: %s. <br>Tests: %s.", moduleNames, mode, type.displayName)
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Android Gradle Tasks")
      .createNotification(message, NotificationType.WARNING)
      .notify(project)
  }

  companion object {

    @JvmStatic
    fun getInstance(): GradleTaskFinder {
      return ApplicationManager.getApplication().getService(GradleTaskFinder::class.java)
    }

    private fun findAndAddGradleBuildTasks(
      module: Module,
      gradlePath: String,
      buildMode: BuildMode,
      tasks: MutableSet<String>,
      testCompileType: TestCompileType
    ) {
      val androidFacet = AndroidFacet.getInstance(module)
      if (androidFacet != null) {
        val properties = androidFacet.properties
        val androidModel = get(module)
        when (buildMode) {
          BuildMode.CLEAN, BuildMode.SOURCE_GEN -> {
            addAfterSyncTasks(tasks, gradlePath, properties)
            if (androidModel != null) {
              addAfterSyncTasksForTestArtifacts(tasks, gradlePath, testCompileType, androidModel)
            }
          }
          BuildMode.ASSEMBLE, BuildMode.REBUILD -> {
            addTaskIfSpecified(tasks, gradlePath, properties.ASSEMBLE_TASK_NAME)

            // Add assemble tasks for tests.
            if (testCompileType !== TestCompileType.ALL) {
              if (androidModel != null) {
                for (artifact in testCompileType.getArtifacts(androidModel.selectedVariant)) {
                  addTaskIfSpecified(tasks, gradlePath, artifact.assembleTaskName)
                }
              }
            }

            // Add assemble tasks for tested variants in test-only modules
            addAssembleTasksForTargetVariants(tasks, module)
          }
          BuildMode.BUNDLE ->           // The "Bundle" task is only valid for base (app) module, not for features, libraries, etc.
            if (androidModel != null && androidModel.androidProject.projectType === IdeAndroidProjectType.PROJECT_TYPE_APP) {
              val taskName = androidModel.selectedVariant.mainArtifact.buildInformation.bundleTaskName
              addTaskIfSpecified(tasks, gradlePath, taskName)
            }
          BuildMode.APK_FROM_BUNDLE ->           // The "ApkFromBundle" task is only valid for base (app) module, and for features if it's for instrumented tests
            if (androidModel != null && androidModel.androidProject.projectType === IdeAndroidProjectType.PROJECT_TYPE_APP) {
              val taskName = androidModel.selectedVariant.mainArtifact.buildInformation.apkFromBundleTaskName
              addTaskIfSpecified(tasks, gradlePath, taskName)
            } else if (androidModel != null &&
              androidModel.androidProject.projectType === IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE
            ) {
              // Instrumented test support for Dynamic Features: Add assembleDebugAndroidTest tasks
              if (testCompileType === TestCompileType.ANDROID_TESTS) {
                for (artifact in testCompileType.getArtifacts(androidModel.selectedVariant)) {
                  addTaskIfSpecified(tasks, gradlePath, artifact.assembleTaskName)
                }
              }
            }
          else -> {
            addAfterSyncTasks(tasks, gradlePath, properties)
            if (androidModel != null) {
              addAfterSyncTasksForTestArtifacts(tasks, gradlePath, testCompileType, androidModel)
              for (artifact in testCompileType.getArtifacts(androidModel.selectedVariant)) {
                addTaskIfSpecified(tasks, gradlePath, artifact.compileTaskName)
              }
            }
            // When compiling for unit tests, run only COMPILE_JAVA_TEST_TASK_NAME, which will run javac over main and test code. If the
            // Jack compiler is enabled in Gradle, COMPILE_JAVA_TASK_NAME will end up running e.g. compileDebugJavaWithJack, which produces
            // no *.class files and would be just a waste of time.
            if (testCompileType !== TestCompileType.UNIT_TESTS) {
              addTaskIfSpecified(tasks, gradlePath, properties.COMPILE_JAVA_TASK_NAME)
            }
          }
        }
      } else {
        val gradleModuleData = CachedModuleDataFinder.getGradleModuleData(module)
        // buildSrc modules are handled by Gradle so we don't need to run any tasks for them
        if (gradleModuleData == null || gradleModuleData.isBuildSrcModule) return
        val extensions = gradleModuleData.findAll(GradleExtensionsDataService.KEY).stream().findFirst().orElse(null) ?: return

        // Check to see if the Java plugin is applied to this project
        if (extensions.extensions.stream().map { obj: GradleProperty -> obj.name }
            .noneMatch { name: String -> name == "java" }) {
          return
        }
        val taskName = getGradleTaskName(buildMode)
        if (taskName != null) {
          tasks.add(GradleProjectSystemUtil.createFullTaskName(gradlePath, taskName))
          if (TestCompileType.UNIT_TESTS == testCompileType || TestCompileType.ALL == testCompileType) {
            tasks.add(GradleProjectSystemUtil.createFullTaskName(gradlePath, JavaPlugin.TEST_CLASSES_TASK_NAME))
          }
        }
      }
    }

    fun getGradleTaskName(buildMode: BuildMode): String? {
      return when (buildMode) {
        BuildMode.ASSEMBLE -> GradleBuilds.DEFAULT_ASSEMBLE_TASK_NAME
        BuildMode.COMPILE_JAVA -> JavaPlugin.COMPILE_JAVA_TASK_NAME
        else -> null
      }
    }

    private fun addAssembleTasksForTargetVariants(tasks: MutableSet<String>, testOnlyModule: Module) {
      val testAndroidModel = get(testOnlyModule)
      if (testAndroidModel == null ||
        testAndroidModel.androidProject.projectType !== IdeAndroidProjectType.PROJECT_TYPE_TEST
      ) {
        // If we don't have the target module and variant to be tested, no task should be added.
        return
      }
      for (testedTargetVariant in testAndroidModel.selectedVariant.testedTargetVariants) {
        val targetProjectGradlePath = testedTargetVariant.targetProjectPath
        val gradleProjectPath = testOnlyModule.getGradleProjectPath() ?: return
        val targetModule = testOnlyModule.project.findModule(GradleHolderProjectPath(gradleProjectPath.buildRoot, targetProjectGradlePath))


        // Adds the assemble task for the tested variants
        if (targetModule != null) {
          val targetAndroidModel = get(targetModule)
          if (targetAndroidModel != null) {
            val targetVariantName = testedTargetVariant.targetVariant
            val targetVariant = targetAndroidModel.findVariantByName(targetVariantName)
            if (targetVariant != null) {
              addTaskIfSpecified(tasks, targetProjectGradlePath, targetVariant.mainArtifact.assembleTaskName)
            }
          }
        }
      }
    }

    private val logger: Logger
      private get() = Logger.getInstance(GradleTaskFinder::class.java)

    private fun addAfterSyncTasksForTestArtifacts(
      tasks: MutableSet<String>,
      gradlePath: String,
      testCompileType: TestCompileType,
      androidModel: GradleAndroidModel
    ) {
      val variant = androidModel.selectedVariant
      val testArtifacts = testCompileType.getArtifacts(variant)
      for (artifact in testArtifacts) {
        for (taskName in artifact.ideSetupTaskNames) {
          addTaskIfSpecified(tasks, gradlePath, taskName)
        }
      }
    }

    private fun addAfterSyncTasks(
      tasks: MutableSet<String>,
      gradlePath: String,
      properties: AndroidFacetProperties
    ) {
      // Make sure all the generated sources, unpacked aars and mockable jars are in place. They are usually up to date, since we
      // generate them at sync time, so Gradle will just skip those tasks. The generated files can be missing if this is a "Rebuild
      // Project" run or if the user cleaned the project from the command line. The mockable jar is necessary to run unit tests, but the
      // compilation tasks don't depend on it, so we have to call it explicitly.
      for (taskName in properties.AFTER_SYNC_TASK_NAMES) {
        addTaskIfSpecified(tasks, gradlePath, taskName)
      }
    }

    private fun addTaskIfSpecified(tasks: MutableSet<String>, gradlePath: String, gradleTaskName: String?) {
      if (!gradleTaskName.isNullOrEmpty()) {
        val buildTask = GradleProjectSystemUtil.createFullTaskName(gradlePath, gradleTaskName!!)
        tasks.add(buildTask)
      }
    }

    private const val MAX_MODULES_TO_INCLUDE_IN_LOG_MESSAGE = 50
    private const val MAX_MODULES_TO_SHOW_IN_NOTIFICATION = 5
  }
}