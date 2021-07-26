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
import com.intellij.openapi.util.text.StringUtil

internal fun UpdaterTreeNode.asString() = printSubTree(this).trim().toString()

private fun printSubTree(node: UpdaterTreeNode, level: Int = 0, result: StringBuilder = StringBuilder()): StringBuilder{
  val renderer = UpdaterTreeNode.Renderer()
  node.customizeRenderer(renderer, null, false, false, false, 0, false)
  val nodeDescription = if (node is RootNode) "Root" else renderer.textRenderer.toString()
  result.append(StringUtil.repeat(" ", level)).append(nodeDescription).append("\n")
  for (i in 0 until node.childCount) {
    printSubTree(node.getChildAt(i) as UpdaterTreeNode, level + 1, result)
  }
  return result
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

