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
package com.android.tools.idea.device.benchmark

import com.android.tools.idea.util.fsm.StateMachine
import com.google.common.math.Quantiles
import com.intellij.openapi.diagnostic.Logger
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Class that conducts a generic benchmarking operation. */
@OptIn(ExperimentalTime::class)
class Benchmarker<InputType>(
  private val adapter: Adapter<InputType>,
  inputRateHz: Int = 60,
  protected val timeSource: TimeSource = TimeSource.Monotonic,
  timer: Timer = Timer(),
) {
  var failureMsg: String = "Benchmarking terminated unexpectedly"
    private set

  private val frameDurationMillis: Long = (1000 / inputRateHz.toDouble()).roundToLong()

  // ┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐  ┌────────┐
  // │INITIAL├─►│GETTING├─►│SENDING├─►│WAITING├─►│COMPLETE│
  // └───┬───┘  └───┬───┘  └───┬───┘  └───┬───┘  └────────┘
  //     │          │          │          │
  //     │          │          │          │       ┌───────┐
  //     └──────────┴──────────┴──────────┴──────►│STOPPED│
  //                                              └───────┘
  private val stateMachine = StateMachine.stateMachine(
    State.INITIALIZED, StateMachine.Config(logger = Benchmarker.LOG, timeSource = timeSource)) {
    State.INITIALIZED.transitionsTo(State.GETTING_READY, State.STOPPED)
    State.GETTING_READY {
      transitionsTo(State.SENDING_INPUTS, State.STOPPED)
      onEnter {
        adapter.ready()
      }
    }
    State.SENDING_INPUTS {
      transitionsTo(State.WAITING_FOR_OUTSTANDING_INPUTS, State.STOPPED)
      onEnter {
        timer.scheduleAtFixedRate(delay = 0, period = frameDurationMillis) {
          dispatchNextInput()
        }
      }
      onExit {
        adapter.finalizeInputs()
        timer.cancel()
      }
    }
    State.WAITING_FOR_OUTSTANDING_INPUTS.transitionsTo(State.STOPPED, State.COMPLETE)
    State.STOPPED.onEnter {
      adapter.cleanUp()
      callbacks.forEach {
        it.onStopped()
        it.onFailure(failureMsg)
      }
    }
    State.COMPLETE.onEnter {
      adapter.cleanUp()
      val results = Results(inputRoundTrips)
      callbacks.forEach {
        it.onStopped()
        it.onComplete(results)
      }
    }
  }

  private var state : State by stateMachine::state

  private val callbacks: MutableList<Callbacks<InputType>> = mutableListOf()

  private val outstandingInputs: MutableMap<InputType, TimeMark> = LinkedHashMap()
  private val inputRoundTrips: MutableMap<InputType, Duration> = mutableMapOf()

  init {
    val callbacks = object : Adapter.Callbacks<InputType> {
      override fun inputReturned(input: InputType, effectiveDispatchTime: TimeMark) {
        this@Benchmarker.inputReturned(input, effectiveDispatchTime)
      }

      override fun onReady() {
        callbacks.forEach { it.onProgress(/* dispatched = */ 0.0, /* returned = */0.0) }
        state = State.SENDING_INPUTS
      }

      override fun onFailedToBecomeReady(msg: String) {
        LOG.warn(msg)
        failureMsg = msg
        state = State.STOPPED
      }
    }
    adapter.setCallbacks(callbacks)
  }

  @Synchronized
  private fun inputReturned(input: InputType, effectiveDispatchTime: TimeMark) {
    if (state in listOf(State.INITIALIZED, State.STOPPED, State.COMPLETE)) return
    LOG.trace("Got input $input")
    if (input in outstandingInputs) {
      val iterator = outstandingInputs.iterator()
      while (iterator.hasNext()) {
        // Complete all previously dispatched touches.
        val cur = iterator.next()
        iterator.remove()
        inputRoundTrips[cur.key] = cur.value.elapsedNow() - effectiveDispatchTime.elapsedNow()
        if (cur.key == input) break
      }
    }
    val denominator = adapter.numInputs().toDouble()
    val dispatchedProgress = (inputRoundTrips.size + outstandingInputs.size) / denominator
    val returnedProgress = inputRoundTrips.size / denominator
    callbacks.forEach { it.onProgress(dispatchedProgress, returnedProgress) }
    if (state == State.WAITING_FOR_OUTSTANDING_INPUTS && outstandingInputs.isEmpty()) {
      state = State.COMPLETE
    }
  }

  @Synchronized
  fun addCallbacks(callbacks: Callbacks<InputType>) {
    this.callbacks.add(callbacks)
  }

  @Synchronized
  fun start() {
    state = State.GETTING_READY
  }

  @Synchronized
  fun stop() {
    failureMsg = "Benchmarking was canceled."
    state = State.STOPPED
  }

  @Synchronized
  fun isDone(): Boolean = state == State.COMPLETE

  @Synchronized
  private fun dispatchNextInput() {
    if (adapter.inputs().hasNext()) {
      adapter.inputs().next().let {
        outstandingInputs[it] = timeSource.markNow()
        LOG.trace("Dispatching input $it.")
        adapter.dispatch(it)
      }
      if (!adapter.inputs().hasNext()) state = State.WAITING_FOR_OUTSTANDING_INPUTS
    }
  }

  class Results<InputType>(val raw: Map<InputType, Duration>) {
    val percentiles: Map<Int, Double> =
      Quantiles.percentiles().indexes(IntRange(1, 100).toList()).compute(raw.values.map { it.inWholeMilliseconds })
  }

  /** Callbacks for various stages of benchmarking. */
  interface Callbacks<InputType> {
    /** Indicates what fraction of events have been [dispatched] and [returned] so far. */
    fun onProgress(dispatched: Double, returned: Double)
    /** Indicates that benchmarking has stopped. */
    fun onStopped()
    /** Indicates that benchmarking failed. */
    fun onFailure(failureMessage: String)
    /** Indicates that benchmarking has completed successfully. */
    fun onComplete(results: Results<InputType>)
  }

  /** An object that handles interactions with whatever is being benchmarked. */
  interface Adapter<InputType> {
    /**
     * Gets the iterator for the inputs that the [Benchmarker] can dispatch.
     *
     * Each call will return the same object.
     */
    fun inputs(): Iterator<InputType>

    /** Gets the total number of inputs that can be dispatched. */
    fun numInputs(): Int

    /** Dispatches the input to the object being benchmarked. */
    fun dispatch(input: InputType)

    /** Sets the callbacks the [Adapter] will call during benchmarking. */
    fun setCallbacks(callbacks: Callbacks<InputType>)

    /** Gets the object being benchmarked into a state where it is ready to receive inputs. */
    fun ready()

    /** Indicates no further inputs will be dispatched. */
    fun finalizeInputs()

    /** Indicates that benchmarking is over. */
    fun cleanUp()

    /** Callbacks to return data to the [Benchmarker] during benchmarking. */
    interface Callbacks<InputType> {
      /**
       * Indicates that the given [input] was returned from the object being benchmarked, along with an
       * [effectiveDispatchTime] that can be used to compute the round-trip from dispatch to return,
       * minus any processing time.
       */
      fun inputReturned(input: InputType, effectiveDispatchTime: TimeMark)

      /** Indicates that the [Benchmarker] can begin dispatching inputs. */
      fun onReady()

      /** Indicates that the object failed to become ready to receive inputs and benchmarking cannot proceed. */
      fun onFailedToBecomeReady(msg: String)
    }
  }

  private enum class State {
    INITIALIZED,
    GETTING_READY,
    SENDING_INPUTS,
    WAITING_FOR_OUTSTANDING_INPUTS,
    STOPPED,
    COMPLETE,
  }

  companion object {
    private val LOG = Logger.getInstance(Benchmarker::class.java)
  }
}
