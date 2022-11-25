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
package org.jetbrains.android.dom.converters

import com.android.ide.common.rendering.api.AttributeFormat
import com.android.tools.idea.res.parseColor
import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.ResolvingConverter
import org.jetbrains.android.util.AndroidBundle

/**
 * [ResolvingConverter] that accepts a Color resource literal of the form:
 *  * #RGB
 *  * #ARGB
 *  * #RRGGBB
 *  * #AARRGGBB
 *
 * @see AttributeFormat.COLOR
 */
class ColorConverter: ResolvingConverter<String>() {
  override fun toString(t: String?, context: ConvertContext?): String? = t

  override fun fromString(s: String?, context: ConvertContext?): String? {
    if (s != null && s.startsWith("#") && parseColor(s) != null) {
      // Is an inline color
      return s
    }
    return null
  }

  override fun getErrorMessage(s: String?, context: ConvertContext?): String? {
    return s?.let { AndroidBundle.message("cannot.resolve.color.literal.error", s) } ?: super.getErrorMessage(null, context)
  }

  override fun getVariants(context: ConvertContext?): Collection<String> = emptyList()

  companion object {
    @JvmField
    val INSTANCE = ColorConverter()
  }
}