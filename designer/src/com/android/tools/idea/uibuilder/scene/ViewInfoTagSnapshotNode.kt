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
package com.android.tools.idea.uibuilder.scene

import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.NlModel.TagSnapshotTreeNode
import com.android.tools.rendering.parsers.TagSnapshot
import com.intellij.util.containers.ContainerUtil

/** A TagSnapshot tree that mirrors the ViewInfo tree. */
internal class ViewInfoTagSnapshotNode constructor(private val myViewInfo: ViewInfo) :
  TagSnapshotTreeNode {
  override fun getTagSnapshot(): TagSnapshot? {
    val result = myViewInfo.cookie
    return if (result is TagSnapshot) result else null
  }

  override fun getChildren(): List<TagSnapshotTreeNode> {
    return ContainerUtil.map<ViewInfo, TagSnapshotTreeNode>(myViewInfo.children) { info: ViewInfo ->
      ViewInfoTagSnapshotNode(info)
    }
  }
}
