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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.structure.GradleResolver
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.meta.*
import java.util.concurrent.TimeUnit

internal fun <T> ResolvedValue<T>.asTestValue(): T? = (this as? ResolvedValue.Set<T>)?.resolved
internal fun <T> Annotated<ParsedValue<T>>.asTestValue(): T? = (value as? ParsedValue.Set.Parsed<T>)?.value
internal fun <T : Any> T.asParsed(): ParsedValue<T> = ParsedValue.Set.Parsed(this, DslText.Literal)
internal fun <T> Annotated<ParsedValue<T>>.asUnparsedValue(): String? =
  ((value as? ParsedValue.Set.Parsed<T>)?.dslText as? DslText.OtherUnparsedDslText)?.text
internal val <T> Annotated<PropertyValue<T>>.resolved get() = value.resolved
internal val <T> Annotated<PropertyValue<T>>.parsedValue get() = value.parsedValue

fun PsProjectImpl.testResolve() {
  refreshFrom(GradleResolver().requestProjectResolved(ideProject, ideProject).get(30, TimeUnit.SECONDS))
}
