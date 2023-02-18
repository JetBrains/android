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
package com.android.tools.idea.layoutinspector.ui

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanel
import com.android.tools.idea.layoutinspector.tree.TreeViewNode
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.util.find
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private const val SYSTEM_PKG = -1
private const val USER_PKG = 123

class LinePainterTest {
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around((EdtRule()))!!

  @RunsInEdt
  @Test
  fun testSystemNodeWithMultipleChildren() {
    val coroutineScope = AndroidCoroutineScope((projectRule.testRootDisposable))
    val model = InspectorModel(projectRule.project)
    val treeSettings = FakeTreeSettings()
    val clientSettings = InspectorClientSettings(projectRule.project)
    val inspector = LayoutInspector(
      coroutineScope,
      mock(),
      mock(),
      null,
      clientSettings,
      mock(),
      model,
      treeSettings,
      MoreExecutors.directExecutor()
    )
    val treePanel = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val treeModel = treePanel.tree.model
    treePanel.setToolContext(inspector)

    val window = window(ROOT, ROOT) {
      compose(2, "App", composePackageHash = USER_PKG) {
        compose(3, "Theme", composePackageHash = USER_PKG) {
          compose(4, "Surface", composePackageHash = USER_PKG) {
            compose(5, "Box", composePackageHash = USER_PKG) {
              compose(6, "Column", composePackageHash = USER_PKG) {
                compose(7, "Layout", composePackageHash = SYSTEM_PKG) {
                  compose(8, "Text", composePackageHash = USER_PKG)
                  compose(9, "Box", composePackageHash = USER_PKG)
                  compose(10, "Button", composePackageHash = USER_PKG)
                }
              }
            }
          }
        }
      }
    }
    model.update(window, listOf(ROOT), 1)
    treeSettings.hideSystemNodes = true
    treeSettings.composeAsCallstack = true
    treePanel.refresh()

    fun node(name: String): TreeViewNode = model.find(name).treeNode

    // A callstack should not have support lines:
    assertThat(node("App").children.size).isEqualTo(4)
    assertThat(LINES.getLastOfMultipleChildren(treeModel, treeSettings, node("App"))).isNull()

    // With hidden system nodes, Column should have 3 children and show support lines:
    assertThat(node("Column").children.size).isEqualTo(3)
    assertThat(LINES.getLastOfMultipleChildren(treeModel, treeSettings, node("Column"))).isSameAs(node("Button"))

    // Now make all system nodes visible in the tree including "Layout":
    treeSettings.hideSystemNodes = false
    treePanel.refresh()

    // The callstack should still not have support lines:
    assertThat(node("App").children.size).isEqualTo(5)
    assertThat(LINES.getLastOfMultipleChildren(treeModel, treeSettings, node("App"))).isNull()

    // Column should not have children and no support lines:
    assertThat(node("Column").children).isEmpty()
    assertThat(LINES.getLastOfMultipleChildren(treeModel, treeSettings, node("Column"))).isNull()

    // Layout should have 3 children and show support lines:
    assertThat(node("Layout").children.size).isEqualTo(3)
    assertThat(LINES.getLastOfMultipleChildren(treeModel, treeSettings, node("Layout"))).isSameAs(node("Button"))
  }
}
