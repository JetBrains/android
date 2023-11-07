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
package org.jetbrains.android.dom.xml

import com.intellij.util.xml.Attribute
import com.intellij.util.xml.Convert
import com.intellij.util.xml.DefinesXml
import org.jetbrains.android.dom.AndroidAttributeValue
import org.jetbrains.android.dom.AndroidResourceType
import org.jetbrains.android.dom.converters.ResourceReferenceConverter
import org.jetbrains.android.dom.resources.ResourceValue

@DefinesXml
interface Restrictions : XmlResourceElement {
  fun getRestrictions(): List<Restriction>

  fun addRestriction(): Restriction
}

interface Restriction : XmlResourceElement {

  @Attribute("defaultValue") fun getDefaultValue(): AndroidAttributeValue<String>

  @Convert(ResourceReferenceConverter::class)
  @AndroidResourceType("array")
  fun getEntries(): AndroidAttributeValue<ResourceValue>

  @Convert(ResourceReferenceConverter::class)
  @AndroidResourceType("array")
  @Attribute("entryValues")
  fun getEntryValues(): AndroidAttributeValue<ResourceValue>

  @Convert(ResourceReferenceConverter::class)
  @AndroidResourceType("string")
  fun getDescription(): AndroidAttributeValue<ResourceValue>

  fun getKey(): AndroidAttributeValue<String>

  @Attribute("restrictionType") fun getRestrictionType(): AndroidAttributeValue<String>

  @Convert(ResourceReferenceConverter::class)
  @AndroidResourceType("string")
  fun getTitle(): AndroidAttributeValue<ResourceValue>
}
