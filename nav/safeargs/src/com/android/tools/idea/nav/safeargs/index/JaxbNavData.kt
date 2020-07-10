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
import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement
import javax.xml.bind.annotation.adapters.XmlAdapter
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter

// NOTE: If you change any class in this file, you should also increment NavXmlIndex#getVersion()

/**
 * Adapter to handle serializing attributes that represent android IDs (e.g. "@id/asdf")
 */
private class AndroidIdAdapter : XmlAdapter<String, String>() {
  override fun marshal(s: String) = "@id/$s"
  override fun unmarshal(s: String) = ResourceUrl.parse(s)?.name ?: ""
}

/**
 * Adapter to handle serializing attributes that represent android IDs that might not be
 * present. See also: [AndroidIdAdapter]
 */
private class OptionalAndroidIdAdapter : XmlAdapter<String?, String?>() {
  private val delegateAdapter = AndroidIdAdapter()

  override fun marshal(s: String?) = s?.let { delegateAdapter.marshal(it) }
  override fun unmarshal(s: String?) = s?.let { delegateAdapter.unmarshal(it) }
}

@XmlRootElement(name = "argument")
@XmlAccessorType(XmlAccessType.FIELD)
data class MutableNavArgumentData(
  @field:XmlAttribute(namespace = ANDROID_URI)
  override var name: String,

  @field:XmlAttribute(namespace = AUTO_URI, name = "argType")
  override var type: String?,

  @field:XmlAttribute(namespace = AUTO_URI, name = "nullable")
  override var nullable: String?,

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

  @field:XmlJavaTypeAdapter(AndroidIdAdapter::class)
  @field:XmlAttribute(namespace = AUTO_URI)
  override var destination: String,

  @field:XmlElement(name = "argument")
  override var arguments: List<MutableNavArgumentData>
) : NavActionData {
  constructor() : this("", "", mutableListOf())
}

@XmlRootElement(name = "activity")
@XmlAccessorType(XmlAccessType.FIELD)
data class MutableNavActivityData(
  @field:XmlJavaTypeAdapter(AndroidIdAdapter::class)
  @field:XmlAttribute(namespace = ANDROID_URI)
  override var id: String,

  @field:XmlAttribute(namespace = ANDROID_URI)
  override var name: String,

  @field:XmlElement(name = "argument")
  override var arguments: List<MutableNavArgumentData>,

  @field:XmlElement(name = "action")
  override var actions: List<MutableNavActionData>
) : NavDestinationData {
  constructor() : this("", "", mutableListOf(), mutableListOf())
}

@XmlRootElement(name = "dialog")
@XmlAccessorType(XmlAccessType.FIELD)
data class MutableNavDialogData(
  @field:XmlJavaTypeAdapter(AndroidIdAdapter::class)
  @field:XmlAttribute(namespace = ANDROID_URI)
  override var id: String,

  @field:XmlAttribute(namespace = ANDROID_URI)
  override var name: String,

  @field:XmlElement(name = "argument")
  override var arguments: List<MutableNavArgumentData>,

  @field:XmlElement(name = "action")
  override var actions: List<MutableNavActionData>
) : NavDestinationData {
  constructor() : this("", "", mutableListOf(), mutableListOf())
}

@XmlRootElement(name = "fragment")
@XmlAccessorType(XmlAccessType.FIELD)
data class MutableNavFragmentData(
  @field:XmlJavaTypeAdapter(AndroidIdAdapter::class)
  @field:XmlAttribute(namespace = ANDROID_URI)
  override var id: String,

  @field:XmlAttribute(namespace = ANDROID_URI)
  override var name: String,

  @field:XmlElement(name = "argument")
  override var arguments: List<MutableNavArgumentData>,

  @field:XmlElement(name = "action")
  override var actions: List<MutableNavActionData>
) : NavDestinationData {
  constructor() : this("", "", mutableListOf(), mutableListOf())
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

  @field:XmlElement(name = "action")
  override var actions: List<MutableNavActionData>,

  @field:XmlElement(name = "activity")
  override val activities: List<MutableNavActivityData>,

  @field:XmlElement(name = "dialog")
  override val dialogs: List<MutableNavDialogData>,

  @field:XmlElement(name = "fragment")
  override var fragments: List<MutableNavFragmentData>,

  @field:XmlElement(name = "navigation")
  override var navigations: List<MutableNavNavigationData>
) : NavNavigationData {
  constructor() : this(null, "", mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())
}
