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
import com.android.tools.idea.layoutinspector.tree.DEFAULT_HIGHLIGHT_SEMANTICS
import com.android.tools.idea.layoutinspector.tree.DEFAULT_RECOMPOSITIONS
import com.android.tools.idea.layoutinspector.tree.DEFAULT_SUPPORT_LINES
import com.android.tools.idea.layoutinspector.tree.TreeSettings

class FakeTreeSettings(
  override var hideSystemNodes: Boolean = DEFAULT_HIDE_SYSTEM_NODES,
  override var composeAsCallstack: Boolean = DEFAULT_COMPOSE_AS_CALLSTACK,
  override var highlightSemantics: Boolean = DEFAULT_HIGHLIGHT_SEMANTICS,
  override var supportLines: Boolean = DEFAULT_SUPPORT_LINES,
  override var showRecompositions: Boolean = DEFAULT_RECOMPOSITIONS,
) : TreeSettings
