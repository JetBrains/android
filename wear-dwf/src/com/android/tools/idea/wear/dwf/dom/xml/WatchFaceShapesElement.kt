/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.xml

import com.intellij.util.xml.Convert
import com.intellij.util.xml.DefinesXml
import com.intellij.util.xml.GenericAttributeValue
import com.intellij.util.xml.NameStrategy
import com.intellij.util.xml.Required
import org.jetbrains.android.dom.AndroidDomElement
import org.jetbrains.android.dom.AndroidResourceType
import org.jetbrains.android.dom.converters.ResourceReferenceConverter
import org.jetbrains.android.dom.converters.StaticEnumConverter
import org.jetbrains.android.dom.motion.PascalNameStrategy
import org.jetbrains.android.dom.resources.ResourceValue

/**
 * Marker interface to be used for WatchFaces resources (files in res/xml/ directory).
 *
 * @see WatchFaceShapesDescription
 */
@DefinesXml
@NameStrategy(PascalNameStrategy::class)
interface WatchFaceShapesElement : AndroidDomElement {
  val watchFaces: List<WatchFaceElement>

  @DefinesXml
  interface WatchFaceElement : AndroidDomElement {
    @get:Required
    @get:AndroidResourceType("raw")
    @get:Convert(ResourceReferenceConverter::class)
    val file: GenericAttributeValue<ResourceValue>

    @get:Convert(ShapeConverter::class) val shape: GenericAttributeValue<String>

    @get:AndroidResourceType("dimen")
    @get:Convert(ResourceReferenceConverter::class)
    val height: GenericAttributeValue<ResourceValue>

    @get:AndroidResourceType("dimen")
    @get:Convert(ResourceReferenceConverter::class)
    val width: GenericAttributeValue<ResourceValue>

    private class ShapeConverter : StaticEnumConverter("CIRCLE", "RECTANGLE")
  }
}
