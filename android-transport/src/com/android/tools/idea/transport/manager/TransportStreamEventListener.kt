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
 * A fork of [TransportEventListener] but adapted to work specifically with [TransportStreamChannel] and [TransportStreamManager].
 *
 * It is used to configure the exact type of events received when using a [TransportStreamChannel].
 */
data class TransportStreamEventListener(
  val eventKind: Common.Event.Kind,
  /**
   * The executor in which to execute [callback]
   */
  val executor: Executor,
  /**
   * Custom filtering criteria for events. This is NOT part of the query - it will be executed on events received from query.
   */
  val filter: (Common.Event) -> Boolean = { true },
  val processId: (() -> Int)? = null,
  val groupId: (() -> Long)? = null,
  val startTime: (() -> Long)? = null,
  val endTime: () -> Long = { Long.MAX_VALUE },
  val sortOrder: Comparator<Common.Event> = Comparator.comparing(Common.Event::getTimestamp),
  /**
   * If set to true, remove this listener after first callback.
   */
  val isTransient: Boolean = false,
  /**
   * What to do after an event satisfying the above filtering criteria is received.
   */
  val callback: (Common.Event) -> Unit
)