/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.idea.common.model.NlComponent
import com.intellij.util.xml.DomManager
import org.jetbrains.android.dom.AndroidDomElement
import org.jetbrains.android.dom.AttributeProcessingUtil

/**
 * Returns the obsolete attributes for this NlComponent
 *
 * Obsolete attributes are attributes no longer needed for the provided [component]. For example
 * `orientation` will be removed for a `RelativeLayout`
 */
fun getObsoleteAttributes(component: NlComponent): Set<QualifiedName> {
  val tag = component.tag
  val facet = component.model.facet
  val domElement = DomManager.getDomManager(tag.project).getDomElement(tag) as? AndroidDomElement ?: return emptySet()
  val validAttributes = mutableListOf<QualifiedName>() // Pair of namespace and name
  AttributeProcessingUtil.processAttributes(domElement, facet, false) { xmlName, attributeDefinition, _ ->
    validAttributes.add(QualifiedName(xmlName.namespaceKey ?: ANDROID_URI, attributeDefinition.name))
    return@processAttributes null
  }
  val currentAttibutes = tag.attributes.map { attibute -> QualifiedName(attibute.namespace, attibute.localName) }.toList()
  return currentAttibutes.subtract(validAttributes)
}

data class QualifiedName(val namespace: String, val name: String)
