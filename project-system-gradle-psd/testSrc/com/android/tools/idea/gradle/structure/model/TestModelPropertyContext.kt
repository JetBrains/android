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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.KnownValues
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.annotateWithError
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.google.common.util.concurrent.ListenableFuture

/**
 * Supports string with dots properties
 */
class TestModelPropertyContext : ModelPropertyContext<String> {
  override fun parse(value: String): Annotated<ParsedValue<String>> = when {
    value.contains(".") -> ParsedValue.Set.Parsed(value, DslText.Literal).annotated()
    else -> ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText(value)).annotateWithError("invalid")
  }

  override fun format(value: String): String = throw UnsupportedOperationException()

  override fun getKnownValues(): ListenableFuture<KnownValues<String>> =
    throw UnsupportedOperationException()
}