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
package com.android.tools.componenttree.treetable

import javax.swing.event.TreeModelEvent
import javax.swing.tree.TreePath

/**
 * A TreeModelEvent with information of whether this change implies a root change.
 */
class TreeTableModelEvent(source: Any, path: TreePath?, val rootChanged: Boolean) : TreeModelEvent(source, path)

/**
 * Return true if the root has changed with a given tree model change event.
 */
val TreeModelEvent.rootChanged: Boolean
  get() = (this as? TreeTableModelEvent)?.rootChanged ?: false
