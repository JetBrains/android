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
package com.android.tools.idea.layoutinspector.pipeline.transport

import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.idea.layoutinspector.resource.data.Configuration
import com.android.tools.idea.layoutinspector.resource.data.AppContext
import com.android.tools.idea.layoutinspector.resource.data.Locale
import com.android.tools.idea.layoutinspector.resource.data.Resource
import com.android.tools.layoutinspector.proto.LayoutInspectorProto

fun LayoutInspectorProto.ComponentTreeEvent.PayloadType.toImageType(): AndroidWindow.ImageType {
  return when (this) {
    LayoutInspectorProto.ComponentTreeEvent.PayloadType.SKP -> AndroidWindow.ImageType.SKP
    LayoutInspectorProto.ComponentTreeEvent.PayloadType.BITMAP_AS_REQUESTED -> AndroidWindow.ImageType.BITMAP_AS_REQUESTED
    else -> AndroidWindow.ImageType.UNKNOWN
  }
}

fun LayoutInspectorProto.Property.Type.convert(): PropertyType {
  return PropertyType.values()[this.number]
}

fun LayoutInspectorProto.Resource.convert(): Resource {
  return Resource(type, namespace, name)
}

fun LayoutInspectorProto.Locale.convert(): Locale {
  return Locale(language, country, variant, script)
}

fun LayoutInspectorProto.Configuration.convert(): Configuration {
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

fun LayoutInspectorProto.ResourceConfiguration.toAppContext(): AppContext {
  return AppContext(
    theme.convert(),
    configuration.convert()
  )
}
