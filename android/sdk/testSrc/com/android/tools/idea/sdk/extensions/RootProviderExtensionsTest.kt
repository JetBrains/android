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
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.projectRoots.impl.MockSdk
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.MultiMap
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootProviderExtensionsTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `Given RootProviders with empty roots When compare They are equal`() {
    val rootProviderA = createMockRootProvider()
    val rootProviderB = createMockRootProvider()
    assertTrue(rootProviderA.isEqualTo(rootProviderB))
  }

  @Test
  fun `Given RootProviders with same roots When compare They are equal`() {
    val rootProviderA = createMockRootProvider(
      Pair(OrderRootType.CLASSES, "class1"),
      Pair(OrderRootType.CLASSES, "class2"),
      Pair(OrderRootType.SOURCES, "source"),
      Pair(OrderRootType.DOCUMENTATION, "documentation"),
      Pair(AnnotationOrderRootType.getInstance(), "annotation")
    )
    val rootProviderB = createMockRootProvider(
      Pair(OrderRootType.CLASSES, "class1"),
      Pair(OrderRootType.CLASSES, "class2"),
      Pair(OrderRootType.SOURCES, "source"),
      Pair(OrderRootType.DOCUMENTATION, "documentation"),
      Pair(AnnotationOrderRootType.getInstance(), "annotation")
    )
    assertTrue(rootProviderA.isEqualTo(rootProviderB))
  }

  @Test
  fun `Given RootProviders with different class root When compare They are different`() {
    val rootProviderA = createMockRootProvider(
      Pair(OrderRootType.CLASSES, "classA")
    )
    val rootProviderB = createMockRootProvider(
      Pair(OrderRootType.CLASSES, "classB")
    )
    assertFalse(rootProviderA.isEqualTo(rootProviderB))
  }

  @Test
  fun `Given RootProviders with different source root When compare They are different`() {
    val rootProviderA = createMockRootProvider(
      Pair(OrderRootType.SOURCES, "sourceA")
    )
    val rootProviderB = createMockRootProvider(
      Pair(OrderRootType.SOURCES, "sourceB")
    )
    assertFalse(rootProviderA.isEqualTo(rootProviderB))
  }

  @Test
  fun `Given RootProviders with different documentation root When compare They are different`() {
    val rootProviderA = createMockRootProvider(
      Pair(OrderRootType.DOCUMENTATION, "documentationA")
    )
    val rootProviderB = createMockRootProvider(
      Pair(OrderRootType.DOCUMENTATION, "documentationB")
    )
    assertFalse(rootProviderA.isEqualTo(rootProviderB))
  }

  @Test
  fun `Given RootProviders with different annotation root When compare They are different`() {
    val rootProviderA = createMockRootProvider(
      Pair(AnnotationOrderRootType.getInstance(), "annotationA")
    )
    val rootProviderB = createMockRootProvider(
      Pair(AnnotationOrderRootType.getInstance(), "annotationB")
    )
    assertFalse(rootProviderA.isEqualTo(rootProviderB))
  }

  private fun createMockRootProvider(
    vararg roots: Pair<OrderRootType, String>
  ): RootProvider {
    val rootsMap = MultiMap.create<OrderRootType, VirtualFile>()
    roots.forEach { (rootType, name) ->
      rootsMap.putValue(rootType, MockVirtualFile(name))
    }
    val mockSdk = MockSdk("name", "path", "version", rootsMap, SimpleJavaSdkType())
    return mockSdk.rootProvider
  }
}