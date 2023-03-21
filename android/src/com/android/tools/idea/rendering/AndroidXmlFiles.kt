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
@file:JvmName("AndroidXmlFiles")
package com.android.tools.idea.rendering

import com.android.SdkConstants.ATTR_CONTEXT
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.idea.rendering.parsers.RenderXmlFile

/** Looks up the declared associated context/activity for the given XML file and returns the resolved fully qualified name if found. */
fun getDeclaredContextFqcn(resourcePackage: String?, xmlFile: RenderXmlFile): String? {
  val context = xmlFile.getRootTagAttribute(ATTR_CONTEXT, TOOLS_URI);
  if (!context.isNullOrEmpty()) {
    val startsWithDot = context[0] == '.'
    if (startsWithDot || context.indexOf('.') == -1) {
      // Prepend application package
      return if (startsWithDot) "$resourcePackage$context" else "$resourcePackage.$context"
    }
    return context
  }
  return null;
}