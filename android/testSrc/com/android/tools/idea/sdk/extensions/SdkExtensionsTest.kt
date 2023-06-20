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
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.projectRoots.impl.MockSdk
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.MultiMap
import org.jetbrains.android.sdk.AndroidSdkType
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SdkExtensionsTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

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
  ): MockSdk {
    val rootsMap = MultiMap.create<OrderRootType, VirtualFile>()
    roots.forEach { (rootType, name) ->
      rootsMap.putValue(rootType, MockVirtualFile(name))
    }
    return MockSdk(name, path, version, rootsMap, sdkTpe)
  }
}