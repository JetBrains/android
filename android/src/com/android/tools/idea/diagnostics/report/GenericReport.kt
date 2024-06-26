/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.report

import com.android.tools.analytics.crash.CrashReport
import com.android.tools.analytics.crash.GoogleCrashReporter
import com.google.gson.stream.JsonWriter
import org.apache.http.entity.mime.MultipartEntityBuilder

class GenericReport(
  type: String,
  val fields: Map<String, String>,
  baseProperties: DiagnosticReportProperties = DiagnosticReportProperties()
) : DiagnosticReport(type, baseProperties) {

  override fun serializeReportProperties(writer: JsonWriter) {
    for ((fieldName, value) in fields) {
      writer.name(fieldName).value(value)
    }
  }

  override fun asCrashReport(): CrashReport {
    return object: DiagnosticCrashReport(type, properties) {
      override fun serialize(builder: MultipartEntityBuilder) {
        super.serialize(builder)
        for ((fieldName, value) in fields) {
          GoogleCrashReporter.addBodyToBuilder(builder, fieldName, value)
        }
      }
    }
  }

  companion object {
    fun deserialize(type: String,
                    baseReportProperties: DiagnosticReportProperties,
                    fieldNames: List<String>,
                    properties: Map<String, String>,
                    format: Long): GenericReport {
      val fields = mutableMapOf<String, String>()
      for (fieldName in fieldNames) {
        fields[fieldName] = properties[fieldName] ?: ""
      }
      return GenericReport(type, fields, baseReportProperties)
    }
  }
}