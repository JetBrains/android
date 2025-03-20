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
import org.jetbrains.android.dom.motion.PascalNameStrategy
import org.jetbrains.android.dom.resources.ResourceValue

/**
 * Marker interface to be used for WatchFaceInfo resources (files in res/xml/ directory).
 *
 * @see WatchFaceInfoDescription
 */
@DefinesXml
@NameStrategy(PascalNameStrategy::class)
interface WatchFaceInfoElement : AndroidDomElement {
  @get:Required
  val preview: Preview
  val category: Category?
  val availableInRetail: AvailableInRetail?
  val multipleInstancesAllowed: MultipleInstancesAllowed?
  val editable: Editable?

  @DefinesXml
  interface Preview : AndroidDomElement {
    @get:Required
    @get:AndroidResourceType("drawable")
    @get:Convert(ResourceReferenceConverter::class)
    val value: GenericAttributeValue<ResourceValue>
  }

  @DefinesXml
  interface Category : AndroidDomElement {
    @get:Required
    @get:Convert(ResourceReferenceConverter::class)
    @get:AndroidResourceType("string")
    val value: GenericAttributeValue<String>
  }

  @DefinesXml
  interface AvailableInRetail : AndroidDomElement {
    @get:Required
    @get:Convert(ResourceReferenceConverter::class)
    @get:AndroidResourceType("bool")
    val value: GenericAttributeValue<String>
  }

  @DefinesXml
  interface MultipleInstancesAllowed : AndroidDomElement {
    @get:Required
    @get:Convert(ResourceReferenceConverter::class)
    @get:AndroidResourceType("bool")
    val value: GenericAttributeValue<String>
  }

  @DefinesXml
  interface Editable : AndroidDomElement {
    @get:Required
    @get:Convert(ResourceReferenceConverter::class)
    @get:AndroidResourceType("bool")
    val value: GenericAttributeValue<String>
  }
}
