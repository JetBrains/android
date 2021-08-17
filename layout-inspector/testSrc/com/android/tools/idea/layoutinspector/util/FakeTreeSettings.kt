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
package com.android.tools.idea.layoutinspector.util

import com.android.tools.idea.layoutinspector.tree.DEFAULT_COMPOSE_AS_CALLSTACK
import com.android.tools.idea.layoutinspector.tree.DEFAULT_HIDE_SYSTEM_NODES
import com.android.tools.idea.layoutinspector.tree.DEFAULT_MERGED_SEMANTICS_TREE
import com.android.tools.idea.layoutinspector.tree.DEFAULT_SUPPORT_LINES
import com.android.tools.idea.layoutinspector.tree.DEFAULT_UNMERGED_SEMANTICS_TREE
import com.android.tools.idea.layoutinspector.tree.TreeSettings

class FakeTreeSettings : TreeSettings {
  override var hideSystemNodes: Boolean = DEFAULT_HIDE_SYSTEM_NODES
  override var composeAsCallstack: Boolean = DEFAULT_COMPOSE_AS_CALLSTACK
  override var mergedSemanticsTree: Boolean = DEFAULT_MERGED_SEMANTICS_TREE
  override var unmergedSemanticsTree: Boolean = DEFAULT_UNMERGED_SEMANTICS_TREE
  override var supportLines: Boolean = DEFAULT_SUPPORT_LINES
}