/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.updater.configure

import com.android.repository.Revision
import com.android.repository.impl.meta.TypeDetails
import com.android.repository.testframework.FakePackage
import org.junit.Assert

internal data class Node(val nodeName: String, val children: List<Node>? = null)

internal fun verifyNodes(expected: Node, actual: UpdaterTreeNode) {
  val renderer = UpdaterTreeNode.Renderer()
  actual.customizeRenderer(renderer, null, false, false, false, 0, false)
  Assert.assertEquals(expected.nodeName, renderer.textRenderer.toString())
  Assert.assertEquals(expected.children?.size ?: 0, actual.childCount)
  expected.children?.zip(actual.children().toList())?.forEach { (expected, actual) -> verifyNodes(expected, actual as UpdaterTreeNode) }
}

internal fun createLocalPackage(
  path: String,
  major: Int,
  minor: Int? = null,
  micro: Int? = null,
  preview: Int? = null,
  typeDetails: TypeDetails? = null
): FakePackage.FakeLocalPackage {
  val fakeLocalPackage = FakePackage.FakeLocalPackage(path)
  fakeLocalPackage.setRevision(Revision(major, minor, micro, preview))
  fakeLocalPackage.displayName = path
  typeDetails?.let { fakeLocalPackage.typeDetails = it }
  return fakeLocalPackage
}

