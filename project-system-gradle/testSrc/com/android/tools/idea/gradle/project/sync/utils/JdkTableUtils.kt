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
package com.android.tools.idea.gradle.project.sync.utils

import com.android.tools.idea.gradle.project.sync.extensions.addRoots
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.JdkRootsType.DETACHED
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.JdkRootsType.INVALID
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.JdkRootsType.VALID
import com.android.tools.idea.sdk.extensions.isEqualTo
import com.intellij.openapi.Disposable
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import java.io.File
import java.io.FileNotFoundException
import kotlin.io.path.Path

object JdkTableUtils {

  data class Jdk(
    val name: String,
    val path: String,
    val rootsType: JdkRootsType = VALID
  )

  enum class JdkRootsType {
    VALID,
    INVALID,
    DETACHED
  }

  fun removeAllJavaSdkFromJdkTable() {
    ProjectJdkTable.getInstance()
      .allJdks
      .filter { it.sdkType is JavaSdk }
      .forEach { SdkConfigurationUtil.removeSdk(it) }
  }

  fun getJdkPathFromJdkTable(jdkName: String): String? {
    val currentJdk = ProjectJdkTable.getInstance().findJdk(jdkName)
    return currentJdk?.homePath
  }

  fun containsValidJdkTableEntry(jdkName: String): Boolean {
    return ProjectJdkTable.getInstance().findJdk(jdkName)?.let { jdkTableEntry ->
      val recreatedJdkTableEntry = JavaSdk.getInstance().createJdk(jdkName, jdkTableEntry.homePath.orEmpty())
      jdkTableEntry.isEqualTo(recreatedJdkTableEntry).also {
        Disposer.dispose(recreatedJdkTableEntry as Disposable)
      }
    } ?: run {
      false
    }
  }

  fun populateJdkTableWith(
    jdks: List<Jdk>,
    tempDir: File
  ) {
    jdks.forEach { (name, path, rootsType) ->
      val jdkPath = findOrCreateTempDir(path, tempDir).path
      val createdJdk = JavaSdk.getInstance().createJdk(name, jdkPath)
      when (rootsType) {
        INVALID -> replaceJdkRoots(createdJdk, roots = generateCorruptedJdkRoots(createdJdk, tempDir))
        DETACHED -> replaceJdkRoots(createdJdk, roots = emptyList())
        VALID -> {}
      }
      SdkConfigurationUtil.addSdk(createdJdk)
    }
  }

  private fun replaceJdkRoots(sdk: Sdk, roots: List<Pair<VirtualFile, OrderRootType>>) {
    sdk.sdkModificator.run {
      removeAllRoots()
      addRoots(roots)
      commitChanges()
    }
  }

  private fun generateCorruptedJdkRoots(
    jdk: Sdk,
    tempDir: File
  ): List<Pair<VirtualFile, OrderRootType>> {
    val tempCorruptedJdk = findOrCreateTempDir("jdk-corrupted", tempDir)
    val jdkRootsMap = mapOf<OrderRootType, Array<VirtualFile>>(
      OrderRootType.SOURCES to jdk.rootProvider.getFiles(OrderRootType.SOURCES),
      OrderRootType.CLASSES to jdk.rootProvider.getFiles(OrderRootType.CLASSES)
    ).flatMap { (rootType, files) ->
      files.map {
        val fakeFile = VfsTestUtil.createFile(tempCorruptedJdk, it.name)
        Pair(fakeFile, rootType)
      }.toList()
    }
    return jdkRootsMap
  }

  private fun findOrCreateTempDir(path: String, tempDir: File): VirtualFile {
    return VfsUtil.findFile(Path(path), true) ?: run {
      val vfsTempDir = VfsUtil.findFile(tempDir.toPath(), true)
                       ?: throw FileNotFoundException("Unable to find ${tempDir.path}")
      VfsTestUtil.createDir(vfsTempDir, path)
    }
  }
}