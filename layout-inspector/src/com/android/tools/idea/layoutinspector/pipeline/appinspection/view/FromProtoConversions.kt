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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.view

import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.idea.layoutinspector.resource.data.AppContext
import com.android.tools.idea.layoutinspector.resource.data.Configuration
import com.android.tools.idea.layoutinspector.resource.data.Locale
import com.android.tools.idea.layoutinspector.resource.data.Resource
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import java.awt.Polygon
import java.awt.Shape

fun LayoutInspectorViewProtocol.Screenshot.Type.toImageType(): AndroidWindow.ImageType {
  return when (this) {
    LayoutInspectorViewProtocol.Screenshot.Type.SKP -> AndroidWindow.ImageType.SKP_PENDING
    LayoutInspectorViewProtocol.Screenshot.Type.BITMAP -> AndroidWindow.ImageType.BITMAP_AS_REQUESTED
    else -> AndroidWindow.ImageType.UNKNOWN
  }
}

fun LayoutInspectorViewProtocol.Resource.convert(): Resource {
  return Resource(type, namespace, name)
}

fun LayoutInspectorViewProtocol.Locale.convert(): Locale {
  return Locale(language, country, variant, script)
}

fun LayoutInspectorViewProtocol.Configuration.convert(): Configuration {
  return Configuration(
    fontScale,
    countryCode,
    networkCode,
    locale.convert(),
    screenLayout,
    colorMode,
    touchScreen,
    keyboard,
    keyboardHidden,
    hardKeyboardHidden,
    navigation,
    navigationHidden,
    uiMode,
    smallestScreenWidth,
    density,
    orientation,
    screenWidth,
    screenHeight
  )
}

fun LayoutInspectorViewProtocol.AppContext.convert(): AppContext {
  return AppContext(
    theme.convert(),
    configuration.convert()
  )
}

fun LayoutInspectorViewProtocol.Property.Type.convert(): PropertyType {
  return when (this) {
    LayoutInspectorViewProtocol.Property.Type.STRING -> PropertyType.STRING
    LayoutInspectorViewProtocol.Property.Type.BOOLEAN -> PropertyType.BOOLEAN
    LayoutInspectorViewProtocol.Property.Type.BYTE -> PropertyType.BYTE
    LayoutInspectorViewProtocol.Property.Type.CHAR -> PropertyType.CHAR
    LayoutInspectorViewProtocol.Property.Type.DOUBLE -> PropertyType.DOUBLE
    LayoutInspectorViewProtocol.Property.Type.FLOAT -> PropertyType.FLOAT
    LayoutInspectorViewProtocol.Property.Type.INT16 -> PropertyType.INT16
    LayoutInspectorViewProtocol.Property.Type.INT32 -> PropertyType.INT32
    LayoutInspectorViewProtocol.Property.Type.INT64 -> PropertyType.INT64
    LayoutInspectorViewProtocol.Property.Type.OBJECT -> PropertyType.OBJECT
    LayoutInspectorViewProtocol.Property.Type.COLOR -> PropertyType.COLOR
    LayoutInspectorViewProtocol.Property.Type.GRAVITY -> PropertyType.GRAVITY
    LayoutInspectorViewProtocol.Property.Type.INT_ENUM -> PropertyType.INT_ENUM
    LayoutInspectorViewProtocol.Property.Type.INT_FLAG -> PropertyType.INT_FLAG
    LayoutInspectorViewProtocol.Property.Type.RESOURCE -> PropertyType.RESOURCE
    LayoutInspectorViewProtocol.Property.Type.DRAWABLE -> PropertyType.DRAWABLE
    LayoutInspectorViewProtocol.Property.Type.ANIM -> PropertyType.ANIM
    LayoutInspectorViewProtocol.Property.Type.ANIMATOR -> PropertyType.ANIMATOR
    LayoutInspectorViewProtocol.Property.Type.INTERPOLATOR -> PropertyType.INTERPOLATOR
    LayoutInspectorViewProtocol.Property.Type.DIMENSION -> PropertyType.DIMENSION
    else -> error { "Unhandled property type $this" }
  }
}

fun LayoutInspectorViewProtocol.Quad.toShape(): Shape {
  return Polygon(intArrayOf(x0, x1, x2, x3), intArrayOf(y0, y1, y2, y3), 4)
}
