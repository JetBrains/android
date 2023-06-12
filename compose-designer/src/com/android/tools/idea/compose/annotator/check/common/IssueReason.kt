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
package com.android.tools.idea.compose.annotator.check.common

/**
 * Base class for Issues found in the annotation.
 *
 * @param parameterName name of the parameter associated with the issue
 */
internal sealed class IssueReason(open val parameterName: String)

/**
 * Used when the existing value doesn't match the expected type for the parameter it's assigned to.
 */
internal class BadType(parameterName: String, val expected: ExpectedValueType) :
  IssueReason(parameterName)

/** For parameters not found that are expected to be present. */
internal class Missing(parameterName: String) : IssueReason(parameterName)

/** For parameters included more than once. */
internal class Repeated(parameterName: String) : IssueReason(parameterName)

/** For parameters found that are not expected/supported. */
internal class Unknown(parameterName: String) : IssueReason(parameterName)

/**
 * Represents issues external to the contents of the annotation (e.g: Failed to read the annotation
 * content).
 */
internal class Failure(val failureMessage: String) : IssueReason("")
