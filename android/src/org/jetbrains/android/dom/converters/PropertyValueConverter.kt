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
package org.jetbrains.android.dom.converters

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.WATCH_FACE_FORMAT_VERSION_PROPERTY
import com.intellij.util.xml.Converter
import com.intellij.util.xml.GenericDomValue
import com.intellij.util.xml.WrappingConverter

/**
 * [Converter] that returns a different converter based on the property's `android:name` attribute
 * value.
 *
 * If the attribute's name is [WATCH_FACE_FORMAT_VERSION_PROPERTY], then the `android:value`'s value
 * is expected to be an integer. In that case, an [IntegerConverter] is used.
 *
 * Otherwise, we default to using a [ResourceReferenceConverter].
 */
class PropertyValueConverter : WrappingConverter() {
  override fun getConverter(domElement: GenericDomValue<*>): Converter<*> {
    val isWatchFaceFormatVersionProperty =
      domElement.xmlTag?.getAttributeValue(ATTR_NAME, ANDROID_URI) ==
        WATCH_FACE_FORMAT_VERSION_PROPERTY
    return if (isWatchFaceFormatVersionProperty) IntegerConverter()
    else ResourceReferenceConverter()
  }
}
