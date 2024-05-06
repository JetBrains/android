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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.model

import com.google.common.truth.Truth.assertThat
import javax.swing.tree.DefaultMutableTreeNode
import org.junit.Test

class BackgroundTaskCategoryNodeTest {

  @Test
  fun showEmptyStateMessageByDefault() {
    val node = BackgroundTaskCategoryNode("test", "empty message")
    assertThat((node.firstChild as EmptyMessageNode).message).isEqualTo("empty message")
  }

  @Test
  fun addEntryRemovesEmptyStateMessage() {
    val node = BackgroundTaskCategoryNode("test", "empty message")
    val childNode = DefaultMutableTreeNode("test entry")
    node.add(childNode)
    assertThat(node.children().toList()).containsExactly(childNode)
  }

  @Test
  fun removeAllEntriesAddsEmptyStateMessage() {
    val node = BackgroundTaskCategoryNode("test", "empty message")
    node.add(DefaultMutableTreeNode())
    node.add(DefaultMutableTreeNode())

    node.removeAllChildren()
    assertThat((node.firstChild as EmptyMessageNode).message).isEqualTo("empty message")
  }
}
