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
package com.android.tools.idea.testartifacts.testsuite.runconfiguration

import com.android.tools.idea.gradle.model.IdeTestSuite
import com.android.tools.idea.gradle.model.IdeTestSuiteSource
import com.android.tools.idea.gradle.model.IdeVariantCore
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.TEST_SUITE_ASSETS_CUSTOM_SOURCE_DIRECTORY
import com.android.tools.idea.projectsystem.gradle.getHolderModule
import com.android.tools.idea.projectsystem.gradle.getTestSuiteModules
import com.android.tools.idea.projectsystem.gradle.isTestSuiteModule
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object TestSuiteUtils {

  /**
   * Finds the [IdeTestSuite] that contains the given [VirtualFile].

   * This is determined by checking if the file's path is a descendant of any source provider
   * directories associated with the test suites in this project.
   */
  fun getTestSuiteContainingFile(testSuites: List<IdeTestSuite>, virtualFile: VirtualFile): IdeTestSuite? {
    if (virtualFile.fileSystem != LocalFileSystem.getInstance()) {
      return null
    }

    val file = VfsUtilCore.virtualToIoFile(virtualFile)

    // A file will only belong to one test suite so just return the first match.
    return testSuites.firstOrNull { isFileInTestSuite(file, it) }
  }

  /**
   * Returns the [IdeTestSuite] that has the given [VirtualFile] as its root directory.
   */
  fun getTestSuiteAtRoot(testSuites: List<IdeTestSuite>, virtualFile: VirtualFile): IdeTestSuite? {
    if (virtualFile.fileSystem != LocalFileSystem.getInstance()) {
      return null
    }

    val file = VfsUtilCore.virtualToIoFile(virtualFile)

    // Test suites have distinct roots so just return the first match.
    return testSuites.firstOrNull { FileUtil.filesEqual(getTestSuiteRoot(it), file) }
  }

  /**
   * Represents a target for a test suite that can be executed.
   *
   * A single test suite can have multiple targets (e.g., one for emulators, one for physical
   * devices). This class holds the information needed to run a specific target.
   *
   * @param targetName The name of the target.
   * @param testTaskName The name of the Gradle task used to run this test suite target.
   */
  data class TestSuiteTarget(val targetName: String, val testTaskName: String)

  /**
   * Returns the list of [TestSuiteTarget] for the given [testSuiteName] that are applicable to the currently selected variant.
   *
   * Only targets that run on a connected device, i.e. have no target device, will be returned.
   * TODO(b/447100167): Add support for running targets configured with GMD devices
   */
  fun getTestSuiteTargets(selectedVariant: IdeVariantCore, testSuiteName: String): List<TestSuiteTarget> {
    val testSuiteVariantTarget = selectedVariant.testSuiteArtifacts.find { it.suiteName == testSuiteName } ?: return emptyList()
    return testSuiteVariantTarget.targets
      .filter { it.targetedDevices.isEmpty() }
      .map { TestSuiteTarget(it.targetName, it.testTaskName) }
  }

  private fun isFileInTestSuite(file: File, testSuite: IdeTestSuite): Boolean {
    val sources = testSuite.sources
    for (source in sources) {
      when (source.type) {
        IdeTestSuiteSource.SourceType.ASSETS -> {
          for (customSourceDirectory in source.sourceProvider.customSourceDirectories) {
            if (FileUtil.isAncestor(customSourceDirectory.directory, file, true)) {
              return true
            }
          }
        }

        IdeTestSuiteSource.SourceType.HOST_JAR -> {
          for (javaSourceDirectory in source.sourceProvider.javaDirectories) {
            if (FileUtil.isAncestor(javaSourceDirectory, file, true)) {
              return true
            }
          }
          for (kotlinSourceDirectory in source.sourceProvider.kotlinDirectories) {
            if (FileUtil.isAncestor(kotlinSourceDirectory, file, true)) {
              return true
            }
          }
        }

        else -> {
          Logger.getInstance(GradleAndroidModel::class.java).warn("Unsupported source type: ${source.type}")
        }
      }
    }

    return false
  }

  /**
   * Temporary function to fetch the file that represents the test suite root directory.
   * This will be made available within the IdeTestSuite model in the future.
   * TODO(b/445649353): Access the root directory via the IdeTestSuite model
   */
  fun getTestSuiteRoot(testSuite: IdeTestSuite): File? {
    for (source in testSuite.sources) {
      source.sourceProvider.customSourceDirectories.find { it.sourceTypeName == TEST_SUITE_ASSETS_CUSTOM_SOURCE_DIRECTORY && it.directory.isDirectory && it.directory.name == testSuite.name }?.let { return it.directory }
      source.sourceProvider.javaDirectories.find { it.parentFile.isDirectory && it.parentFile.name == testSuite.name }?.let { return it.parentFile }
      source.sourceProvider.kotlinDirectories.find { it.parentFile.isDirectory && it.parentFile.name == testSuite.name }?.let { return it.parentFile }
    }

    return null
  }

  /**
   * Returns the root directory [File] for the given [testSuiteModule].
   */
  fun getTestSuiteRoot(testSuiteModule: Module): File? {
    if (!testSuiteModule.isTestSuiteModule()) return null

    val testSuiteName = getTestSuiteNameFromModule(testSuiteModule)
    val testSuite = GradleAndroidModel.get(testSuiteModule)?.testSuites?.find { it.name == testSuiteName } ?: return null

    return getTestSuiteRoot(testSuite)
  }

  /**
   * Parses the test suite name from the given [testSuiteModule]
   */
  fun getTestSuiteNameFromModule(testSuiteModule: Module): String {
    val appModule = testSuiteModule.getHolderModule()
    return testSuiteModule.name.substringAfterLast("${appModule.name}.")
  }

  /**
   * Returns the [Module] for the test suite associated with the given [runConfiguration].
   */
  fun getTestSuiteModule(runConfiguration: TestSuiteRunConfiguration): Module? {
    val appModule = runReadAction {
      val file = VfsUtil.findFileByIoFile(File(runConfiguration.settings.externalProjectPath), false) ?: return@runReadAction null
      ProjectFileIndex.getInstance(runConfiguration.project).getModuleForFile(file, false)
    } ?: return null

    val testTaskName = runConfiguration.getTaskNames().firstOrNull() ?: return null
    val androidModel = GradleAndroidModel.get(appModule) ?: return null
    val testSuiteName = getTestSuiteNameWithTestTaskName(androidModel.variants, testTaskName)

    return appModule.getTestSuiteModules().find { it.name == "${appModule.name}.$testSuiteName" }
  }

  /**
   * Returns the test suite name associated with the given [testTaskName].
   *
   * This is an N^3 solution, so pretty in-efficient, but it is currently the only way to get the
   * test suite name from a run configuration - since the user can manually change the task name.
   * TODO(b/458035847): Replace with a more efficient lookup when the test suite name, variant and
   * target are stored against the run configuration.
   */
  private fun getTestSuiteNameWithTestTaskName(variants: List<IdeVariantCore>, testTaskName: String): String? {
    for (variant in variants) {
      for (testSuiteArtifact in variant.testSuiteArtifacts) {
        for (target in testSuiteArtifact.targets) {
          if (target.testTaskName == testTaskName) {
            return testSuiteArtifact.suiteName
          }
        }
      }
    }
    return null
  }
}
