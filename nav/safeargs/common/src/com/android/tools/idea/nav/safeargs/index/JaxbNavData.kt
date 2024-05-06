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
@file:Suppress("unused") // Used by JAXB via reflection

package com.android.tools.idea.nav.safeargs.index

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.resources.ResourceUrl
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlAnyElement
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement
import javax.xml.bind.annotation.adapters.XmlAdapter
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter
import javax.xml.transform.dom.DOMResult

// NOTE: If you change any class in this file, you should also increment NavXmlIndex#getVersion()

/** Adapter to handle serializing attributes that represent android IDs (e.g. "@id/asdf") */
private class AndroidIdAdapter : XmlAdapter<String, String>() {
  override fun marshal(s: String) = "@id/$s"

  override fun unmarshal(s: String) = ResourceUrl.parse(s)?.name ?: ""
}

/**
 * Adapter to handle serializing attributes that represent android IDs that might not be present.
 * See also: [AndroidIdAdapter]
 */
private class OptionalAndroidIdAdapter : XmlAdapter<String?, String?>() {
  private val delegateAdapter = AndroidIdAdapter()

  override fun marshal(s: String?) = s?.let { delegateAdapter.marshal(it) }

  override fun unmarshal(s: String?) = s?.let { delegateAdapter.unmarshal(it) }
}

/**
 * An adapter which allows us to catch any unspecified tag and try to fit it into a
 * [MaybeNavDestinationData].
 *
 * This adapter will always succeed at creating a "maybe" destination, but only if expected
 * attributes are found on the custom tag will it return a non-null destination if
 * [MaybeNavDestinationData.toDestination] is called.
 */
private class MaybeDestinationAdapter : XmlAdapter<Element, MaybeNavDestinationData>() {
  private val jaxbContext = JAXBContext.newInstance(MutableMaybeNavDestinationData::class.java)

  override fun marshal(value: MaybeNavDestinationData): Element {
    val marshaller = jaxbContext.createMarshaller()
    val result = DOMResult()
    marshaller.marshal(value, result)
    return ((result.node) as Document).documentElement
  }

  override fun unmarshal(value: Element): MaybeNavDestinationData {
    val unmarshaller = jaxbContext.createUnmarshaller()
    val result = unmarshaller.unmarshal(value, MutableMaybeNavDestinationData::class.java)
    return result.value
  }
}

@XmlRootElement(name = "argument")
@XmlAccessorType(XmlAccessType.FIELD)
data class MutableNavArgumentData(
  @field:XmlAttribute(namespace = ANDROID_URI) override var name: String,
  @field:XmlAttribute(namespace = AUTO_URI, name = "argType") override var type: String?,
  @field:XmlAttribute(namespace = AUTO_URI, name = "nullable") override var nullable: String?,
  @field:XmlAttribute(namespace = ANDROID_URI, name = "defaultValue")
  override var defaultValue: String?
) : NavArgumentData {
  constructor() : this("", null, null, null)
}

@XmlRootElement(name = "action")
@XmlAccessorType(XmlAccessType.FIELD)
data class MutableNavActionData(
  @field:XmlJavaTypeAdapter(AndroidIdAdapter::class)
  @field:XmlAttribute(namespace = ANDROID_URI)
  override var id: String,
  @field:XmlJavaTypeAdapter(OptionalAndroidIdAdapter::class)
  @field:XmlAttribute(namespace = AUTO_URI)
  override var destination: String?,
  @field:XmlJavaTypeAdapter(OptionalAndroidIdAdapter::class)
  @field:XmlAttribute(namespace = AUTO_URI)
  override var popUpTo: String?,
  @field:XmlElement(name = "argument") override var arguments: List<MutableNavArgumentData>
) : NavActionData {
  constructor() : this("", null, null, mutableListOf())
}

@XmlRootElement(
  name = "maybeDestination"
) // Fake root element name only used for indexing, required by JAXB marshalling
@XmlAccessorType(XmlAccessType.FIELD)
data class MutableMaybeNavDestinationData(
  @field:XmlJavaTypeAdapter(OptionalAndroidIdAdapter::class)
  @field:XmlAttribute(namespace = ANDROID_URI)
  var id: String?,
  @field:XmlAttribute(namespace = ANDROID_URI) var name: String?,
  @field:XmlElement(name = "argument") var arguments: List<MutableNavArgumentData>,
  @field:XmlElement(name = "action") var actions: List<MutableNavActionData>
) : MaybeNavDestinationData {
  constructor() : this(null, null, mutableListOf(), mutableListOf())

  override fun toDestination(): NavDestinationData? {
    val id = id ?: return null
    val name = name ?: return null

    return object : NavDestinationData {
      override val id = id
      override val name = name
      override val arguments = this@MutableMaybeNavDestinationData.arguments
      override val actions = this@MutableMaybeNavDestinationData.actions
    }
  }
}

@XmlRootElement(name = "navigation")
@XmlAccessorType(XmlAccessType.FIELD)
data class MutableNavNavigationData(
  @field:XmlJavaTypeAdapter(OptionalAndroidIdAdapter::class)
  @field:XmlAttribute(namespace = ANDROID_URI)
  override var id: String?,
  @field:XmlJavaTypeAdapter(AndroidIdAdapter::class)
  @field:XmlAttribute(namespace = AUTO_URI)
  override var startDestination: String,
  @field:XmlElement(name = "action") override var actions: List<MutableNavActionData>,
  @field:XmlElement(name = "argument") override var arguments: List<MutableNavArgumentData>,
  @field:XmlElement(name = "navigation") override var navigations: List<MutableNavNavigationData>,
  @field:XmlAnyElement()
  @field:XmlJavaTypeAdapter(MaybeDestinationAdapter::class)
  override var potentialDestinations: List<MaybeNavDestinationData>
) : NavNavigationData {
  constructor() : this(null, "", mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())
}
