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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.view

import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskCategoryNode
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.getAlarmsCategoryNode
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.getJobsCategoryNode
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.getWakeLocksCategoryNode
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.getWorksCategoryNode
import java.awt.Component
import java.awt.Container
import java.util.stream.Stream
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emptyFlow

object BackgroundTaskViewTestUtils {
  class FakeAppInspectorMessenger(override val scope: CoroutineScope) : AppInspectorMessenger {
    var rawDataSent: ByteArray = ByteArray(0)
    override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
      rawDataSent = rawData
      return rawDataSent
    }

    override val eventFlow = emptyFlow<ByteArray>()
  }

  private fun BackgroundTaskEntriesView.getTreeRoot(): DefaultMutableTreeNode {
    val tree = TreeWalker(this).descendantStream().filter { it is JTree }.findFirst().get() as JTree
    return tree.model.root as DefaultMutableTreeNode
  }

  fun BackgroundTaskEntriesView.getWorksCategoryNode(): BackgroundTaskCategoryNode {
    return getTreeRoot().getWorksCategoryNode()
  }

  fun BackgroundTaskEntriesView.getJobsCategoryNode(): BackgroundTaskCategoryNode {
    return getTreeRoot().getJobsCategoryNode()
  }

  fun BackgroundTaskEntriesView.getAlarmsCategoryNode(): BackgroundTaskCategoryNode {
    return getTreeRoot().getAlarmsCategoryNode()
  }

  fun BackgroundTaskEntriesView.getWakeLocksCategoryNode(): BackgroundTaskCategoryNode {
    return getTreeRoot().getWakeLocksCategoryNode()
  }

  fun JComponent.getValueComponent(key: String): Component = getCategoryPanel(key).getComponent(1)

  fun JComponent.getCategoryPanel(key: String): Container =
    findLabels(key).findFirst().get().parent.parent

  fun JComponent.findLabels(text: String): Stream<Component> =
    TreeWalker(this).descendantStream().filter { (it as? JLabel)?.text == text }
}
