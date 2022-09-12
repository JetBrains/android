/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.intellij.util.containers.nullize
import com.intellij.util.text.nullize
import javax.swing.tree.TreeNode

internal data class TestTree(val text: String, val children: List<TestTree>?) {
  override fun toString(): String =
    listOfNotNull(text, children?.nullize()?.joinToString("\n")?.prependIndent("    ")).joinToString("\n")
}

internal fun AbstractPsNode.testStructure(filter: (AbstractPsNode) -> Boolean = { true }): TestTree =
  TestTree(let {
    update()
    name ?: "(null)"
  },
           children.mapNotNull { it as? AbstractPsNode }.filter { filter(it) }.map { it.testStructure(filter) })

internal fun TreeNode.testStructure(filter: (TreeNode) -> Boolean = { true }): TestTree =
  TestTree(toString().nullize(nullizeSpaces = true) ?: "(null)",
           children().asSequence().mapNotNull { it as? TreeNode }.filter { filter(it) }.map { it.testStructure(filter) }.toList())

