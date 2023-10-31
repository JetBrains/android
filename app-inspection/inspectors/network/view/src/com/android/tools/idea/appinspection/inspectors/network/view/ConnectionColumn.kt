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
package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.getUrlName
import com.intellij.openapi.util.text.StringUtil
import java.util.Locale

/** Columns for each connection information */
internal enum class ConnectionColumn(var widthRatio: Double, val type: Class<*>) {
  NAME(0.25, String::class.java) {
    override fun getValueFrom(data: HttpData): Any {
      return data.getUrlName()
    }
  },
  SIZE(0.25 / 4, java.lang.Integer::class.java) {
    override fun getValueFrom(data: HttpData): Any {
      return data.responsePayload.size()
    }
  },
  TYPE(0.25 / 4, String::class.java) {
    override fun getValueFrom(data: HttpData): Any {
      val type = data.responseHeader.contentType
      val mimeTypeParts = type.mimeType.split("/")
      return mimeTypeParts[mimeTypeParts.size - 1]
    }
  },
  STATUS(0.25 / 4, java.lang.Integer::class.java) {
    override fun getValueFrom(data: HttpData): Any {
      return data.responseHeader.statusCode
    }
  },
  TIME(0.25 / 4, java.lang.Long::class.java) {
    override fun getValueFrom(data: HttpData): Any {
      return data.connectionEndTimeUs - data.requestStartTimeUs
    }
  },
  TIMELINE(0.5, java.lang.Long::class.java) {
    override fun getValueFrom(data: HttpData): Any {
      return data.requestStartTimeUs
    }
  };

  fun toDisplayString(): String {
    return StringUtil.capitalize(name.lowercase(Locale.getDefault()))
  }

  abstract fun getValueFrom(data: HttpData): Any
}
