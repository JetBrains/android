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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.tools.idea.layoutinspector.properties.PropertyType
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol
import java.awt.Polygon
import java.awt.Shape

fun LayoutInspectorComposeProtocol.Quad.toShape(): Shape {
  return Polygon(intArrayOf(x0, x1, x2, x3), intArrayOf(y0, y1, y2, y3), 4)
}

fun LayoutInspectorComposeProtocol.Parameter.Type.convert(): PropertyType {
  return PropertyType.values()[this.number]
}
