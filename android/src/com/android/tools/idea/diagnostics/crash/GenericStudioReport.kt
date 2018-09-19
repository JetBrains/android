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
package com.android.tools.idea.diagnostics.crash

import com.android.tools.analytics.crash.CrashReport

/**
 * Generic Studio report that makes no assumptions about the kind of data to be reported.
 * Important: data must not contain PII.
 *
 * Example:
 * ```
 * val report = GenericStudioReport.Builder("MyReport")
 *   .addDataNoPii("stackDump", "myStackDump")
 *   .addDataNoPii("summary", "Description of what happened")
 *   .build()
 * StudioCrashReporter.getInstance().submit(report)
 * ```
 */
class GenericStudioReport(version: String?, productData: Map<String, String>?, type: String)
  : BaseStudioReport(version, productData, type) {

  class Builder(private val type: String) : CrashReport.BaseBuilder<GenericStudioReport, Builder>() {

    /** Important: data added must not contain PII. */
    fun addDataNoPii(key: String, value: String): Builder = apply { addProductData(mapOf(key to value)) }

    override fun getThis() = this

    override fun build() = GenericStudioReport(version, productData, type)
  }
}
