/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:JvmName("ResourceUtils")
package com.android.tools.idea.res

import com.android.SdkConstants
import com.intellij.openapi.util.text.StringUtil

/**
 * Studio Independent resource util functions
 */


/**
 * @return ResourceUrl representation of a style from qualifiedName
 * e.g. for "android:Theme" returns "@android:style/Theme" or for "AppTheme" returns "@style/AppTheme"
 */
fun getStyleResourceUrl(qualifiedName: String): String {
  return getResourceUrlFromQualifiedName(qualifiedName, SdkConstants.TAG_STYLE)
}

fun getResourceUrlFromQualifiedName(qualifiedName: String, type: String): String {
  val startChar = if (SdkConstants.TAG_ATTR == type) SdkConstants.PREFIX_THEME_REF else SdkConstants.PREFIX_RESOURCE_REF
  val colonIndex = qualifiedName.indexOf(':')
  if (colonIndex != -1) {
    // The theme name contains a namespace, change the format to be "@namespace:style/ThemeName".
    val namespace = qualifiedName.substring(0, colonIndex + 1) // Namespace plus + colon
    val themeNameWithoutNamespace = StringUtil.trimStart(qualifiedName, namespace)
    return "$startChar$namespace$type/$themeNameWithoutNamespace"
  }
  return "$startChar$type/$qualifiedName"
}

