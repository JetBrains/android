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
package com.android.tools.idea.sdk.extensions

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.DisposableRule
import org.jetbrains.android.sdk.AndroidSdkType
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SdkExtensionsTest {

  @Rule
  @JvmField
  val projectRule = AndroidProjectRule.inMemory()

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  @Test
  fun `Given Sdks with different naming When compare They are different`() {
    val sdkA = createMockSdk(name = "nameA")
    val sdkB = createMockSdk(name = "nameB")
    assertFalse(sdkA.isEqualTo(sdkB))
  }

  @Test
  fun `Given Sdks with different paths When compare They are different`() {
    val sdkA = createMockSdk(path = "pathA")
    val sdkB = createMockSdk(path = "pathB")
    assertFalse(sdkA.isEqualTo(sdkB))
  }

  @Test
  fun `Given Sdks with different versions When compare They are different`() {
    val sdkA = createMockSdk(version = "versionA")
    val sdkB = createMockSdk(version = "versionB")
    assertFalse(sdkA.isEqualTo(sdkB))
  }

  @Test
  fun `Given Sdks with different roots When compare They are different`() {
    val sdkA = createMockSdk(roots = listOf(
      Pair(OrderRootType.CLASSES, "root1"),
      Pair(OrderRootType.CLASSES, "rootA2"),
    ))
    val sdkB = createMockSdk(roots = listOf(
      Pair(OrderRootType.CLASSES, "root1"),
      Pair(OrderRootType.CLASSES, "rootB2"),
    ))
    assertFalse(sdkA.isEqualTo(sdkB))
  }

  @Test
  fun `Given Sdks with different type When compare They are different`() {
    val sdkA = createMockSdk(sdkTpe = KotlinSdkType())
    val sdkB = createMockSdk(sdkTpe = AndroidSdkType())
    assertFalse(sdkA.isEqualTo(sdkB))
  }

  @Test
  fun `Given Sdks with same values When compare They are equal`() {
    val sdkA = createMockSdk(
      name = "sdk",
      path = "testPath",
      version = "testVersion",
      sdkTpe = KotlinSdkType(),
      roots = listOf(
        Pair(OrderRootType.CLASSES, "class1"),
        Pair(OrderRootType.CLASSES, "class2"),
        Pair(OrderRootType.SOURCES, "source"),
        Pair(OrderRootType.DOCUMENTATION, "documentation"),
        Pair(AnnotationOrderRootType.getInstance(), "annotation"),
      )
    )
    val sdkB = createMockSdk(
      name = "sdk",
      path = "testPath",
      version = "testVersion",
      sdkTpe = KotlinSdkType(),
      roots = listOf(
        Pair(OrderRootType.CLASSES, "class1"),
        Pair(OrderRootType.CLASSES, "class2"),
        Pair(OrderRootType.SOURCES, "source"),
        Pair(OrderRootType.DOCUMENTATION, "documentation"),
        Pair(AnnotationOrderRootType.getInstance(), "annotation"),
      )
    )
    assertTrue(sdkA.isEqualTo(sdkB))
  }

  private fun createMockSdk(
    name: String = "name",
    path: String = "path",
    version: String = "version",
    sdkTpe: SdkTypeId = SimpleJavaSdkType(),
    roots: List<Pair<OrderRootType, String>> = emptyList()
  ): Sdk {
    val sdk = ProjectJdkTable.getInstance().createSdk(name, sdkTpe)
    val sdkModificator = sdk.sdkModificator
    sdkModificator.homePath = path
    sdkModificator.versionString = version

    val application = ApplicationManager.getApplication()
    application.invokeAndWait {
      application.runWriteAction {
        val virtualFileManager = VirtualFileManager.getInstance()
        roots.forEach { (rootType, name) ->
          val tmpVirtualFile = virtualFileManager.findFileByUrl("temp:///")!!
          val virtualFile = tmpVirtualFile.findOrCreateChildData(null, name)
          sdkModificator.addRoot(virtualFile, rootType)
        }
        sdkModificator.commitChanges()
      }
    }
    (sdk as? Disposable)?.let {
      Disposer.register(disposableRule.disposable, it)
    }
    return sdk
  }
}