/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.model

import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import java.awt.Shape
import kotlin.math.absoluteValue

private val systemPackageHashes = setOf(
  -1,
  packageNameHash("androidx.compose.animation"),
  packageNameHash("androidx.compose.animation.core"),
  packageNameHash("androidx.compose.desktop"),
  packageNameHash("androidx.compose.foundation"),
  packageNameHash("androidx.compose.foundation.layout"),
  packageNameHash("androidx.compose.foundation.text"),
  packageNameHash("androidx.compose.material"),
  packageNameHash("androidx.compose.material.ripple"),
  packageNameHash("androidx.compose.runtime"),
  packageNameHash("androidx.compose.ui"),
  packageNameHash("androidx.compose.ui.layout"),
  packageNameHash("androidx.compose.ui.platform"),
  packageNameHash("androidx.compose.ui.tooling"),
  packageNameHash("androidx.compose.ui.selection"),
  packageNameHash("androidx.compose.ui.semantics"),
  packageNameHash("androidx.compose.ui.viewinterop"),
  packageNameHash("androidx.compose.ui.window"),
)

// Must match packageNameHash in androidx.ui.tooling.inspector.LayoutInspectorTree
fun packageNameHash(packageName: String): Int =
  packageName.fold(0) { hash, char -> hash * 31 + char.toInt() }.absoluteValue

/**
 * A view node represents a composable in the view hierarchy as seen on the device.
 */
class ComposeViewNode(
  drawId: Long,
  qualifiedName: String,
  layout: ResourceReference?,
  x: Int,
  y: Int,
  width: Int,
  height: Int,
  transformedBounds: Shape?,
  viewId: ResourceReference?,
  textValue: String,
  layoutFlags: Int,
  var composeFilename: String,
  var composePackageHash: Int,
  var composeOffset: Int,
  var composeLineNumber: Int
): ViewNode(drawId, qualifiedName, layout, x, y, width, height, transformedBounds, viewId, textValue, layoutFlags) {

  override val isSystemNode: Boolean
    get() = systemPackageHashes.contains(composePackageHash)
            && parent is ComposeViewNode // The top node is usually create by the user, but it has no location i.e. packageHash is -1

  override val isSingleCall: Boolean
    get() = TreeSettings.composeAsCallstack && (parent as? ComposeViewNode)?.children?.size == 1 && children.size == 1 &&
            (TreeSettings.composeDrawablesInCallstack || drawId < 0)
}
