/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.transport.manager

import com.android.tools.profiler.proto.Common
import java.util.concurrent.Executor

/**
 * The set of parameters that will be used to query results from Transport pipeline.
 */
class StreamEventQuery(
  val eventKind: Common.Event.Kind,
  val processId: (() -> Int)? = null,
  val groupId: (() -> Long)? = null,
  val startTime: (() -> Long)? = null,
  val endTime: () -> Long = { Long.MAX_VALUE },
  /**
   * Custom filtering criteria for events. This is NOT part of the query - it will be executed on events received from query.
   */
  val filter: (Common.Event) -> Boolean = { true },
  /**
   * The sort order is not part of the query. Instead, the results are sorted after they are received.
   */
  val sortOrder: Comparator<Common.Event> = Comparator.comparing(Common.Event::getTimestamp)
)


/**
 * A fork of [TransportEventListener] but adapted to work specifically with [TransportStreamChannel] and [TransportStreamManager].
 *
 * It is used to configure the exact type of events received when using a [TransportStreamChannel].
 */
class TransportStreamEventListener(
  val streamEventQuery: StreamEventQuery,
  /**
   * The executor in which to execute [callback]
   */
  val executor: Executor,
  /**
   * If set to true, remove this listener after first callback.
   */
  val isTransient: Boolean = false,
  /**
   * What to do after an event satisfying the above filtering criteria is received.
   */
  val callback: (Common.Event) -> Unit
) {
  constructor(eventKind: Common.Event.Kind,
              executor: Executor,
              processId: (() -> Int)? = null,
              groupId: (() -> Long)? = null,
              startTime: (() -> Long)? = null,
              endTime: () -> Long = { Long.MAX_VALUE },
              filter: (Common.Event) -> Boolean = { true },
              sortOrder: Comparator<Common.Event> = Comparator.comparing(
                Common.Event::getTimestamp),
              isTransient: Boolean = false,
              callback: (Common.Event) -> Unit) : this(
    StreamEventQuery(eventKind, processId, groupId, startTime, endTime, filter, sortOrder), executor, isTransient, callback)
}