/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport.poller

import com.android.tools.profiler.proto.Common
import java.util.concurrent.Executor

/**
 * Individual listeners that handle one event kind each. `TransportEventPoller` subscribers can register
 * multiple listeners to a single poller.
 */
data class TransportEventListener @JvmOverloads constructor(
  val eventKind: Common.Event.Kind,
  val executor: Executor,
  val filter: (Common.Event) -> Boolean = { true },
  val streamId: (() -> Long)? = null,
  val processId: (() -> Int)? = null,
  val groupId: (() -> Long)? = null,
  val startTime: (() -> Long)? = null,
  val endTime: () -> Long = { Long.MAX_VALUE },
  // Upon completing the callback, whether the listener should be removed.
  val callback: (Common.Event) -> Boolean)
