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
package com.android.tools.idea.util.fsm

import com.android.utils.time.TimeSource
import com.android.utils.time.TimeSource.TimeMark
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import java.util.Locale

typealias Callback = () -> Unit

/**
 * State machine class that operates over an enum of states.
 *
 * Provides various levels of checking for state transitions and callbacks on state enter, exit, and
 * for specific state transitions. Can be created via the [Builder] in Java or via DSL (using the
 * [stateMachine] method) in Kotlin.
 *
 * e.g.
 *
 * ```
 * enum class State {
 *   INITIAL, FLIP, FLOP, TERMINAL
 * }
 * val myStateMachine = stateMachine(State.INITIAL) {
 *   State.INITIAL.transitionsTo(State.FLIP) { println("Flipping for the first time!") }
 *   State.FLIP.transitionsTo(State.FLOP) { println("Flopping!") }
 *   State.FLOP {
 *     transitionsTo(State.FLIP) { println("Flipping again!") }
 *     transitionsTo(State.TERMINAL) { println("Done flipflopping forever!") }
 *   }
 * }
 *
 * (0 until 10).forEach {
 *   myStateMachine.state = State.FLIP
 *   myStateMachine.state = State.FLOP
 * }
 * myStateMachine.state = State.TERMINAL
 * ```
 */
class StateMachine<StateEnum : Enum<StateEnum>>
private constructor(
  private val config: Config,
  initialState: StateEnum,
  private val transitions: Map<StateEnum, Collection<StateEnum>>,
  private val transitionEnterCallbacks: Map<StateEnum, List<Callback>>,
  private val transitionCallbacks: Map<Pair<StateEnum, StateEnum>, List<Callback>>,
  private val transitionExitCallbacks: Map<StateEnum, List<Callback>>,
  private val illegalTransitionHandler: IllegalTransitionHandler<StateEnum>,
) {
  private var lastTransitionTime: TimeMark = config.timeSource.markNow()

  @Volatile var state: StateEnum = initialState
    /**
     * Transitions the [StateMachine] to a new state.
     *
     * Callbacks will be executed directly within a lock in the following order:
     * 1. Callbacks associated with exiting the current state
     * 2. Callbacks associated with the transition between the current state and [newState]
     * 3. Callbacks associated with entering [newState]
     *
     * Within these groups, callbacks are executed in the order they were added to the [Builder].
     *
     * If the [SelfTransitionBehavior] is [SelfTransitionBehavior.NOOP] and [newState] is the same
     * as the current state, this method does nothing.
     */
    @Synchronized
    set(newState) {
      if (config.selfTransitionBehavior == SelfTransitionBehavior.NOOP && state == newState) {
        config.logger.debug { "Ignoring self-transition in state $state." }
        return
      }

      if (transitions[state]?.contains(newState) == true ||
          illegalTransitionHandler.handleTransition(state, newState, config)
      ) {
        val oldState = state
        config.logger.debug { "Transition from $oldState to $newState." }
        transitionExitCallbacks[oldState]?.execute()
        field = newState
        lastTransitionTime = config.timeSource.markNow()
        transitionCallbacks[oldState to newState]?.execute()
        transitionEnterCallbacks[newState]?.execute()
      }
    }
    @Synchronized get

  /** Interface that handles illegal transitions. */
  fun interface IllegalTransitionHandler<StateEnum : Enum<StateEnum>> {
    /** Returns `true` iff the [StateMachine] should allow the transition to complete. */
    fun handleTransition(state: StateEnum, newState: StateEnum, config: Config): Boolean

    companion object {
      /**
       * Returns an [IllegalTransitionHandler] that allows illegal transitions, i.e. the state does
       * change.
       */
      @JvmStatic
      fun <StateEnum : Enum<StateEnum>> allow() =
        IllegalTransitionHandler<StateEnum> { state, newState, config ->
          config.logger.warn("${illegalTransitionMsg(state, newState)} Allowing anyway.")
          true
        }
      /**
       * Returns an [IllegalTransitionHandler] that ignores illegal transitions, i.e. the state does
       * not change.
       */
      @JvmStatic
      fun <StateEnum : Enum<StateEnum>> ignore() =
        IllegalTransitionHandler<StateEnum> { state, newState, config ->
          config.logger.debug { illegalTransitionMsg(state, newState) }
          false
        }
      /**
       * Returns an [IllegalTransitionHandler] that is the same as [ignore] but logs at
       * [LogLevel.WARNING] instead of the default [LogLevel.DEBUG].
       */
      @JvmStatic
      fun <StateEnum : Enum<StateEnum>> warn() =
        IllegalTransitionHandler<StateEnum> { state, newState, config ->
          config.logger.warn(illegalTransitionMsg(state, newState))
          false
        }
      /**
       * Returns an [IllegalTransitionHandler] that throws [IllegalArgumentException] when an
       * illegal transition is requested.
       */
      @JvmStatic
      fun <StateEnum : Enum<StateEnum>> throwException() =
        IllegalTransitionHandler<StateEnum> { state, newState, _ ->
          throw IllegalArgumentException(illegalTransitionMsg(state, newState))
        }
    }
  }

  /** Different options for interpreting a transition from a state to itself. */
  enum class SelfTransitionBehavior {
    /** Transitions from a state to itself are treated like any other state transition. */
    NORMAL,

    /** Transitions from a state to itself are ignored. */
    NOOP,
  }

  /**
   * Asserts that the [StateMachine] is in the given state.
   *
   * @throws IllegalStateException if the [StateMachine] is in any other state.
   */
  @Synchronized
  fun assertState(s: StateEnum) {
    check(state == s) { "Expected to be in state $s, but was in state $state!" }
  }

  /**
   * Returns the [kotlin.time.Duration] the [StateMachine] has spent in the current state.
   */
  @JvmSynthetic
  fun getDurationInCurrentState() = lastTransitionTime.elapsedNow()

  /**
   * Returns the number of milliseconds the [StateMachine] has spent in the current state.
   */
  fun getMillisecondsInCurrentState() = getDurationInCurrentState().inWholeMilliseconds

  private fun List<Callback>.execute() {
    forEach { it() }
  }

  /** Configuration options for a [StateMachine]. */
  data class Config(
    val selfTransitionBehavior: SelfTransitionBehavior = SelfTransitionBehavior.NORMAL,
    val logger: Logger = stateMachineClassLogger,
    val timeSource: TimeSource = TimeSource.Monotonic
  ) {
    /** A builder class to improve Java usage. */
    class Builder {
      private var selfTransitionBehavior: SelfTransitionBehavior = SelfTransitionBehavior.NORMAL
      private var logger: Logger = stateMachineClassLogger
      private var timeSource: TimeSource = TimeSource.Monotonic

      fun setSelfTransitionBehavior(selfTransitionBehavior: SelfTransitionBehavior) = apply {
        this.selfTransitionBehavior = selfTransitionBehavior
      }

      fun setLogger(logger: Logger) = apply { this.logger = logger }

      fun setTimeSource(timeSource: TimeSource) = apply { this.timeSource = timeSource }

      fun build(): Config = Config(selfTransitionBehavior, logger, timeSource)
    }
  }

  /** Builder class to build a [StateMachine]. */
  class Builder<StateEnum : Enum<StateEnum>>
  @JvmOverloads
  constructor(private val initialState: StateEnum, private val config: Config = Config()) {
    private val transitions: MutableMap<StateEnum, MutableCollection<StateEnum>> = mutableMapOf()
    private val transitionEnterCallbacks: MutableMap<StateEnum, MutableList<Callback>> =
      mutableMapOf()
    private val transitionCallbacks: MutableMap<Pair<StateEnum, StateEnum>, MutableList<Callback>> =
      mutableMapOf()
    private val transitionExitCallbacks: MutableMap<StateEnum, MutableList<Callback>> =
      mutableMapOf()
    private var illegalTransitionHandler: IllegalTransitionHandler<StateEnum> =
      IllegalTransitionHandler.throwException()

    /**
     * Specifies a valid transition between two states.
     *
     * @param fromState the starting state of the transition.
     * @param toState the finishing state of the transition.
     */
    fun addTransition(fromState: StateEnum, toState: StateEnum) = apply {
      transitions.getOrPut(fromState, ::mutableSetOf).add(toState)
    }

    /**
     * Adds a callback to be invoked when a given state is entered.
     *
     * @param state which state to invoke the callback upon entering.
     * @param callback the actual callback to invoke.
     */
    @JvmSynthetic
    fun addTransitionEnterCallback(
      state: StateEnum,
      callback: Callback,
    ) = apply { transitionEnterCallbacks.getOrPut(state, ::mutableListOf).add(callback) }

    /**
     * Adds a callback to be invoked when a given state is entered.
     *
     * @param state which state to invoke the callback upon entering.
     * @param runnable the actual callback to invoke.
     */
    fun addTransitionEnterCallback(
      state: StateEnum,
      runnable: Runnable,
    ) = addTransitionEnterCallback(state, runnable::run)

    /**
     * Adds a callback to be invoked when a transition occurs between two states.
     *
     * @param fromState the start state of the transition.
     * @param toState the end state of the transition.
     * @param callback the actual callback to invoke.
     */
    @JvmSynthetic
    fun addTransitionCallback(
      fromState: StateEnum,
      toState: StateEnum,
      callback: Callback,
    ) = apply { transitionCallbacks.getOrPut(fromState to toState, ::mutableListOf).add(callback) }

    /**
     * Adds a callback to be invoked when a transition occurs between two states.
     *
     * @param fromState the start state of the transition.
     * @param toState the end state of the transition.
     * @param runnable the actual callback to invoke.
     */
    fun addTransitionCallback(
      fromState: StateEnum,
      toState: StateEnum,
      runnable: Runnable,
    ) = addTransitionCallback(fromState, toState, runnable::run)

    /**
     * Adds a callback to be invoked when a given state is exited.
     *
     * @param state which state to invoke the callback upon exiting.
     * @param callback the actual callback to invoke.
     */
    @JvmSynthetic
    fun addTransitionExitCallback(
      state: StateEnum,
      callback: Callback,
    ) = apply { transitionExitCallbacks.getOrPut(state, ::mutableListOf).add(callback) }

    /**
     * Adds a callback to be invoked when a given state is exited.
     *
     * @param state which state to invoke the callback upon exiting.
     * @param runnable the actual callback to invoke.
     */
    fun addTransitionExitCallback(state: StateEnum, runnable: Runnable) =
      addTransitionExitCallback(state, runnable::run)

    fun setIllegalTransitionHandler(illegalTransitionHandler: IllegalTransitionHandler<StateEnum>) =
      apply {
        this.illegalTransitionHandler = illegalTransitionHandler
      }

    /**
     * Builds the [StateMachine].
     *
     * @throws IllegalStateException if the [initialState] has not been set.
     */
    fun build(): StateMachine<StateEnum> =
      StateMachine(
        config,
        initialState,
        transitions,
        transitionEnterCallbacks,
        transitionCallbacks,
        transitionExitCallbacks,
        illegalTransitionHandler,
      )
  }

  class StateMachineBuilderScope<StateEnum : Enum<StateEnum>>
  internal constructor(
    initialState: StateEnum,
    config: Config,
  ) {
    private val builder = Builder(initialState, config)

    operator fun StateEnum.invoke(block: StateEnum.() -> Unit) = apply(block)

    fun StateEnum.transitionsTo(vararg states: StateEnum, callback: Callback? = null) {
      val stateSet = states.toSet()
      stateSet.forEach { builder.addTransition(this, it) }
      callback?.let { stateSet.forEach { state -> builder.addTransitionCallback(this, state, it) } }
    }

    fun StateEnum.onEnter(callback: Callback) {
      builder.addTransitionEnterCallback(this, callback)
    }

    fun StateEnum.onExit(callback: Callback) {
      builder.addTransitionExitCallback(this, callback)
    }

    fun build(): StateMachine<StateEnum> = builder.build()
  }

  companion object {
    private val stateMachineClassLogger = Logger.getInstance(StateMachine::class.java)

    private fun <StateEnum: Enum<StateEnum>> illegalTransitionMsg(fromState: StateEnum, toState: StateEnum): String =
      "Illegal state transition from %s to %s!".format(Locale.US, fromState, toState)

    @JvmSynthetic
    fun <StateEnum : Enum<StateEnum>> stateMachine(
      initialState: StateEnum,
      config: Config = Config(),
      builder: StateMachineBuilderScope<StateEnum>.() -> Unit,
    ): StateMachine<StateEnum> =
      StateMachineBuilderScope(initialState, config).apply(builder).build()
  }
}

/**
 * Specifies a valid transition between two states and adds a callback to be invoked on that
 * transition.
 *
 * This method is a convenience and is equivalent to
 * ```
 * addTransition(fromState, toState).addTransitionCallback(fromState, toState, callback)
 * ```
 */
fun <StateEnum : Enum<StateEnum>> StateMachine.Builder<StateEnum>.addTransition(
  fromState: StateEnum,
  toState: StateEnum,
  callback: Callback,
): StateMachine.Builder<StateEnum> =
  addTransition(fromState, toState).addTransitionCallback(fromState, toState, callback)
