/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("LayoutFilePullParser")

package com.android.tools.rendering.parsers

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_IGNORE
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.EXPANDABLE_LIST_VIEW
import com.android.SdkConstants.GRID_VIEW
import com.android.SdkConstants.LIST_VIEW
import com.android.SdkConstants.SPINNER
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.SdkConstants.VIEW_INCLUDE
import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ValueXmlHelper
import com.android.ide.common.util.PathString
import com.android.support.FragmentTagUtil.isFragmentTag
import com.android.tools.apk.analyzer.ResourceIdResolver
import com.android.tools.rendering.LayoutMetadata
import com.android.tools.res.FileResourceReader
import com.android.tools.res.ids.ResourceIdManager
import org.xmlpull.v1.XmlPullParser

/** Creates a new [ILayoutPullParser] for the given XML file. */
fun create(
  xml: PathString,
  namespace: ResourceNamespace,
  resIdManager: ResourceIdManager?
): ILayoutPullParser? {
  val parser =
    FileResourceReader.createXmlPullParser(xml) { i ->
      resIdManager?.let { it.findById(i)?.resourceUrl?.toString() }
        ?: ResourceIdResolver.NO_RESOLUTION.resolve(i)
    } ?: return null
  return LayoutPullParserImpl(parser, namespace)
}

/**
 * Modified [XmlPullParser] that adds the methods of [ILayoutPullParser], and performs other
 * layout-specific parser behavior like translating fragment tags into include tags.
 */
private class LayoutPullParserImpl
constructor(private val delegate: XmlPullParser, private val layoutNamespace: ResourceNamespace) :
  ILayoutPullParser, XmlPullParser by delegate {
  /** The layout to be shown for the current `<fragment>` tag. Usually null. */
  private var fragmentLayout: String? = null

  // --- Layoutlib API methods.

  override fun getLayoutNamespace(): ResourceNamespace {
    return layoutNamespace
  }

  override fun getViewCookie(): Any? {
    val name = delegate.name ?: return null

    // Store tools attributes if this looks like a layout we'll need adapter view
    // bindings for in the LayoutlibCallback.
    if (LIST_VIEW != name && EXPANDABLE_LIST_VIEW != name && GRID_VIEW != name && SPINNER != name) {
      return null
    }

    var map: MutableMap<String, String>? = null
    val count = delegate.attributeCount
    for (i in 0 until count) {
      val namespace = delegate.getAttributeNamespace(i)
      if (namespace != null && namespace == TOOLS_URI) {
        val attribute = delegate.getAttributeName(i)
        if (attribute == ATTR_IGNORE) {
          continue
        }
        if (map == null) {
          map = HashMap(7)
        }
        map[attribute] = delegate.getAttributeValue(i)
      }
    }

    return map
  }

  // --- XmlPullParser overrides.

  override fun getName(): String? {
    val name = delegate.name

    // At design time, replace fragments with includes.
    if (isFragmentTag(name)) {
      fragmentLayout = LayoutMetadata.getProperty(delegate, LayoutMetadata.KEY_FRAGMENT_LAYOUT)
      if (fragmentLayout != null) {
        return VIEW_INCLUDE
      }
    } else {
      fragmentLayout = null
    }

    return name
  }

  override fun getAttributeValue(namespace: String?, localName: String): String? {
    if (ATTR_LAYOUT == localName && fragmentLayout != null) {
      return fragmentLayout
    }

    var value: String? = delegate.getAttributeValue(namespace, localName)

    // On the fly convert match_parent to fill_parent for compatibility with older platforms.
    if (
      VALUE_MATCH_PARENT == value &&
        (ATTR_LAYOUT_WIDTH == localName || ATTR_LAYOUT_HEIGHT == localName) &&
        ANDROID_URI == namespace
    ) {
      return VALUE_FILL_PARENT
    }

    if (namespace != null) {
      if (namespace == ANDROID_URI) {
        // Allow the tools namespace to override the framework attributes at design time.
        val designValue = delegate.getAttributeValue(TOOLS_URI, localName)
        if (designValue != null) {
          value =
            if (value == null || !designValue.isEmpty()) {
              designValue
            } else {
              // An empty value of the design time attribute erases the value of the runtime
              // attribute if it was set.
              null
            }
        }
      } else if (value == null) {
        // Auto-convert http://schemas.android.com/apk/res-auto resources. The lookup
        // will be for the current application's resource package, e.g.
        // http://schemas.android.com/apk/res/foo.bar, but the XML document will
        // be using http://schemas.android.com/apk/res-auto in library projects:
        value = delegate.getAttributeValue(AUTO_URI, localName)
      }
    }

    // Handle unicode and XML escapes.
    if (value != null && value.any { it == '&' || it == '\\' }) {
      return ValueXmlHelper.unescapeResourceString(value, true, false)
    }

    return value
  }
}
