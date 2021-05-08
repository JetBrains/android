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

import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.idea.layoutinspector.resource.data.Configuration
import com.android.tools.idea.layoutinspector.resource.data.AppContext
import com.android.tools.idea.layoutinspector.resource.data.Locale
import com.android.tools.idea.layoutinspector.resource.data.Resource
import com.android.tools.layoutinspector.proto.LayoutInspectorProto

fun Resource.convert(): LayoutInspectorProto.Resource {
  val self = this
  return LayoutInspectorProto.Resource.newBuilder().apply {
    type = self.type
    namespace = self.namespace
    name = self.name
  }.build()
}

fun PropertyType.convert(): LayoutInspectorProto.Property.Type {
  return LayoutInspectorProto.Property.Type.forNumber(this.ordinal)
}

fun Locale.convert(): LayoutInspectorProto.Locale {
  val self = this
  return LayoutInspectorProto.Locale.newBuilder().apply {
    language = self.language
    country = self.country
    variant = self.variant
    script = self.script
  }.build()
}

fun Configuration.convert(): LayoutInspectorProto.Configuration {
  val self = this
  return LayoutInspectorProto.Configuration.newBuilder().apply {
    fontScale = self.fontScale
    countryCode = self.countryCode
    networkCode = self.networkCode
    locale = self.locale.convert()
    screenLayout = self.screenLayout
    colorMode = self.colorMode
    touchScreen = self.touchScreen
    keyboard = self.keyboard
    keyboardHidden = self.keyboardHidden
    hardKeyboardHidden = self.hardKeyboardHidden
    navigation = self.navigation
    navigationHidden = self.navigationHidden
    uiMode = self.uiMode
    smallestScreenWidth = self.smallestScreenWidth
    density = self.density
    orientation = self.orientation
    screenWidth = self.screenWidth
    screenHeight = self.screenHeight
  }.build()
}

fun AppContext.toResourceConfiguration(): LayoutInspectorProto.ResourceConfiguration {
  val ctx = this
  return LayoutInspectorProto.ResourceConfiguration.newBuilder().apply {
    theme = ctx.theme.convert()
    configuration = ctx.configuration.convert()
  }.build()
}