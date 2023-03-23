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
package com.android.tools.idea.insights.events.actions

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Job

/** Represents an in progress operation. */
interface CancellationToken {
  /** Action tracked by this [CancellationToken] */
  val action: Action

  /** Issues a conditional cancellation of this token. */
  fun cancel(reason: Action): CancellationToken?

  infix fun and(other: CancellationToken?): CancellationToken {
    return when {
      other == null -> this
      action.isNoop -> other
      other.action.isNoop -> this
      else -> CompositeCancellationToken(listOf(this, other))
    }
  }

  companion object {

    fun noop(action: Action): CancellationToken {
      return object : CancellationToken {
        override val action = action
        override fun cancel(reason: Action) = null
      }
    }
  }
}

/** Represents multiple [CancellationToken]s under a single one. */
class CompositeCancellationToken(private val tokens: List<CancellationToken>) : CancellationToken {
  override val action: Action by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
      tokens.fold(Action.NONE) { acc, token -> acc and token.action }
    }
  override fun cancel(reason: Action): CancellationToken? {
    val result = tokens.mapNotNull { it.cancel(reason) }
    return when {
      result.isEmpty() -> null
      result.size == 1 -> result[0]
      else -> CompositeCancellationToken(result)
    }
  }
}

/** A token backed by Kotlin's [Job]. */
class JobCancellationToken(
  private val job: Job,
  override val action: Action.Single,
) : CancellationToken {

  override fun cancel(reason: Action): JobCancellationToken? {
    if (job.isCompleted) return null
    if (action.maybeCancel(reason) != null) {
      Logger.getInstance(ActionDispatcher::class.java).info("Not cancelling job \"$action\"")
      return this
    }
    Logger.getInstance(ActionDispatcher::class.java).info("Cancelling job \"$action\"")
    job.cancel()
    return null
  }
}

fun Job.toToken(action: Action.Single): CancellationToken = JobCancellationToken(this, action)
