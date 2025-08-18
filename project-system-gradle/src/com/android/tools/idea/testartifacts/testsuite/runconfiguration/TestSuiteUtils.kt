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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
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
   * Returns the name of the Gradle task for running the given test suite on a connected device
   * for the currently selected variant.
   */
  fun getTestSuiteTaskName(selectedVariant: IdeVariantCore, testSuiteName: String): String? {
    val testSuiteVariantTarget = selectedVariant.testSuiteArtifacts.find { it.suiteName == testSuiteName } ?: return null
    val connectedDeviceTarget = testSuiteVariantTarget.targets.find {
      it.targetedDevices.isEmpty()
    }
    return connectedDeviceTarget?.testTaskName
  }

  private fun isFileInTestSuite(file: File, testSuite: IdeTestSuite): Boolean {
    val sources = testSuite.sources
    for (source in sources) {
      when (source.type) {
        IdeTestSuiteSource.SourceType.ASSETS -> {
          for (assetsSourceDirectory in source.sourceProvider.assetsDirectories) {
            if (FileUtil.isAncestor(assetsSourceDirectory, file, true)) {
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
  private fun getTestSuiteRoot(testSuite: IdeTestSuite): File? {
    for (source in testSuite.sources) {
      source.sourceProvider.assetsDirectories.find { it.isDirectory && it.name == testSuite.name }?.let { return it }
      source.sourceProvider.javaDirectories.find { it.isDirectory && it.name == testSuite.name }?.let { return it }
      source.sourceProvider.kotlinDirectories.find { it.isDirectory && it.name == testSuite.name }?.let { return it }
    }

    return null
  }

}
