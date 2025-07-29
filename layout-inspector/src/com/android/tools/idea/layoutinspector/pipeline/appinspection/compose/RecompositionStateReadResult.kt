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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.tools.idea.layoutinspector.model.ComposeViewNode

/** A result from a state read request. */
class RecomposeStateReadResult(
  /** The [ComposeViewNode] composable these state reads are for. */
  val node: ComposeViewNode,
  /** The recomposition these reads are for. */
  val recomposition: Int,
  /** The state reads for this [node] and [recomposition]. */
  val reads: List<RecomposeStateReadData>,
  /** The first recomposition with state reads. */
  val firstObservedRecomposition: Int,
)

/**
 * Holds data for a single state read in compose for a given composable and recomposition number.
 */
data class RecomposeStateReadData(
  /** The [value] of the state variable read. */
  val value: ParameterItem,

  /** The hash of the mutable state holder variable that was read. */
  val valueInstance: Int,

  /** True if this state was invalidated before the actual read. */
  val invalidated: Boolean,

  /** The stacktrace of the state read at the point of the interception. */
  val stacktrace: List<TraceElement>,
)

/** A line in the stacktrace. */
data class TraceElement(
  /** The class of the method called. */
  val declaringClass: String,

  /** The name of the method called. */
  val methodName: String,

  /** The fileName where the method resides. */
  val fileName: String,

  /** The lineNumber in this trace. */
  val lineNumber: Int,
)
