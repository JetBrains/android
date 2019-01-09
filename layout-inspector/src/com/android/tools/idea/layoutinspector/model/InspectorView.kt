/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.ide.common.resources.ResourceItem
import org.jetbrains.kotlin.utils.keysToMap
import java.awt.Image

class InspectorView(val id: String,
                    val type: String,
                    var x: Int,
                    var y: Int,
                    var width: Int,
                    var height: Int,
                    _properties: Collection<ResourceItem>) {

  var image: Image? = null
  var imageGenerationTime: Long? = null

  val properties: MutableMap<ResourceItem, PropertyTrace?> = _properties.keysToMap { null }.toMutableMap()

  val children: MutableList<InspectorView> = mutableListOf()
}