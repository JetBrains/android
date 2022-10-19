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

import com.android.tools.idea.util.fsm.StateMachine.Config
import com.android.tools.idea.util.fsm.StateMachine.SelfTransitionBehavior
import com.android.utils.time.TestTimeSource
import com.android.utils.time.TimeSource
import com.android.utils.time.TimeSource.TimeMark
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.diagnostic.Logger
import org.apache.log4j.Level
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@RunWith(JUnit4::class)
class StateMachineTest {
  private val fakeLogger = FakeLogger()
  val calls = mutableListOf<String>()

  enum class MyGreatFsmState {
    INITIAL,
    AWESOME,
    AWESOMER,
  }

  @Test
  fun builder_builds() {
    val stateMachine = StateMachine.Builder(MyGreatFsmState.INITIAL).build()
    assertThat(stateMachine.state).isEqualTo(MyGreatFsmState.INITIAL)
  }

  @Test
  fun configBuilder_builds() {
    val defaultConfig = Config.Builder().build()
    assertThat(defaultConfig).isEqualTo(Config())

    val logger = Logger.getInstance("Testing")
    val timeSource = object : TimeSource {
      override fun markNow(): TimeMark {
        throw NotImplementedError("Should never be called")
      }
    }

    val config =
      Config.Builder()
        .setSelfTransitionBehavior(SelfTransitionBehavior.NOOP)
        .setLogger(logger)
        .setTimeSource(timeSource)
        .build()
    assertThat(config)
      .isEqualTo(
        Config(
          selfTransitionBehavior = SelfTransitionBehavior.NOOP,
          logger = logger,
          timeSource = timeSource,
        )
      )
  }

  @Test
  fun logging_usesProvidedLogger() {
    val stateMachine =
      StateMachine.stateMachine(
        MyGreatFsmState.INITIAL,
        Config(logger = fakeLogger)
      ) { MyGreatFsmState.INITIAL.transitionsTo(MyGreatFsmState.AWESOME) }

    stateMachine.state = MyGreatFsmState.AWESOME

    assertThat(fakeLogger.debugLogs).containsExactly("Transition from INITIAL to AWESOME.")
  }

  @Test
  fun illegalTransition_throws() {
    val stateMachine = StateMachine.Builder(MyGreatFsmState.INITIAL).build()
    assertFailsWith<IllegalArgumentException> {
      stateMachine.state = MyGreatFsmState.AWESOME
    }
  }

  @Test
  fun illegalTransition_allowed() {
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL,
                           Config(logger = fakeLogger))
        .setIllegalTransitionHandler(StateMachine.IllegalTransitionHandler.allow())
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(stateMachine.state).isEqualTo(MyGreatFsmState.AWESOME)

    assertThat(fakeLogger.warnLogs).containsExactly("Illegal state transition from INITIAL to AWESOME! Allowing anyway.")
    assertThat(fakeLogger.debugLogs).containsExactly("Transition from INITIAL to AWESOME.")
  }

  @Test
  fun illegalTransition_doesNotThrow_ignore() {
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL)
        .setIllegalTransitionHandler(StateMachine.IllegalTransitionHandler.ignore())
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(stateMachine.state).isEqualTo(MyGreatFsmState.INITIAL)
  }

  @Test
  fun illegalTransition_doesNotThrow_warn() {
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL, Config(logger = fakeLogger))
        .setIllegalTransitionHandler(StateMachine.IllegalTransitionHandler.warn())
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(stateMachine.state).isEqualTo(MyGreatFsmState.INITIAL)
    assertThat(fakeLogger.warnLogs).containsExactly("Illegal state transition from INITIAL to AWESOME!")
  }

  @Test
  fun illegalTransition_doesNotThrowButLogs_ignore() {
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL, Config(logger = fakeLogger))
        .setIllegalTransitionHandler(StateMachine.IllegalTransitionHandler.ignore())
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(stateMachine.state).isEqualTo(MyGreatFsmState.INITIAL)
    assertThat(fakeLogger.debugLogs).containsExactly("Illegal state transition from INITIAL to AWESOME!")
  }

  @Test
  fun illegalTransition_callsHandler() {
    val illegalTransitions: MutableList<Pair<MyGreatFsmState, MyGreatFsmState>> = mutableListOf()
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL)
        .setIllegalTransitionHandler { s1, s2, _ ->
          illegalTransitions += s1 to s2
          true
        }
        .build()

    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(illegalTransitions)
      .containsExactly(MyGreatFsmState.INITIAL to MyGreatFsmState.AWESOME)
    stateMachine.state = MyGreatFsmState.AWESOMER
    assertThat(illegalTransitions)
      .containsExactly(
        MyGreatFsmState.INITIAL to MyGreatFsmState.AWESOME,
        MyGreatFsmState.AWESOME to MyGreatFsmState.AWESOMER
      )
      .inOrder()
  }

  @Test
  fun legalTransition_logs() {
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL, Config(logger = fakeLogger))
        .addTransition(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME)
        .addTransition(MyGreatFsmState.AWESOME, MyGreatFsmState.AWESOMER)
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(stateMachine.state).isEqualTo(MyGreatFsmState.AWESOME)

    assertThat(fakeLogger.debugLogs).containsExactly("Transition from INITIAL to AWESOME.")
    stateMachine.state = MyGreatFsmState.AWESOMER
    assertThat(stateMachine.state).isEqualTo(MyGreatFsmState.AWESOMER)
    assertThat(fakeLogger.debugLogs).containsExactly(
      "Transition from INITIAL to AWESOME.", "Transition from AWESOME to AWESOMER.")
  }

  @Test
  fun transition_callsExitTransitionCallback() {
    var calls = 0
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL)
        .addTransition(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME)
        .addTransitionExitCallback(MyGreatFsmState.INITIAL) { calls++ }
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(calls).isEqualTo(1)
  }

  @Test
  fun transition_callsExitTransitionCallback_java() {
    var calls = 0
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL)
        .addTransition(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME)
        .addTransitionExitCallback(MyGreatFsmState.INITIAL, Runnable { calls++ })
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(calls).isEqualTo(1)
  }

  @Test
  fun transition_callsTransitionCallback() {
    var calls = 0
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL)
        .addTransition(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME)
        .addTransitionCallback(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME) { calls++ }
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(calls).isEqualTo(1)
  }

  @Test
  fun transition_callsTransitionCallback_java() {
    var calls = 0
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL)
        .addTransition(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME)
        .addTransitionCallback(
          MyGreatFsmState.INITIAL,
          MyGreatFsmState.AWESOME,
          Runnable { calls++ }
        )
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(calls).isEqualTo(1)
  }

  @Test
  fun transition_callsEnterTransitionCallback() {
    var calls = 0
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL)
        .addTransition(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME)
        .addTransitionEnterCallback(MyGreatFsmState.AWESOME) { calls++ }
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(calls).isEqualTo(1)
  }

  @Test
  fun transition_callsEnterTransitionCallback_java() {
    var calls = 0
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL)
        .addTransition(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME)
        .addTransitionEnterCallback(MyGreatFsmState.AWESOME, Runnable { calls++ })
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(calls).isEqualTo(1)
  }

  @Test
  fun transition_callsCallbacksInOrder() {
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL)
        .addTransition(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME)
        .addTransitionEnterCallback(MyGreatFsmState.AWESOME) { calls += "fifth" }
        .addTransitionCallback(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME) {
          calls += "third"
        }
        .addTransitionEnterCallback(MyGreatFsmState.AWESOME) { calls += "sixth" }
        .addTransitionExitCallback(MyGreatFsmState.INITIAL) { calls += "first" }
        .addTransitionCallback(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME) {
          calls += "fourth"
        }
        .addTransitionExitCallback(MyGreatFsmState.INITIAL) { calls += "second" }
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(calls)
      .containsExactly("first", "second", "third", "fourth", "fifth", "sixth")
      .inOrder()
  }

  @Test
  fun transition_callsCallbacksInOrder_java() {
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL)
        .addTransition(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME)
        .addTransitionEnterCallback(MyGreatFsmState.AWESOME, Runnable { calls += "fifth" })
        .addTransitionEnterCallback(MyGreatFsmState.AWESOME, Runnable { calls += "sixth" })
        .addTransitionCallback(
          MyGreatFsmState.INITIAL,
          MyGreatFsmState.AWESOME,
          Runnable { calls += "third" }
        )
        .addTransitionCallback(
          MyGreatFsmState.INITIAL,
          MyGreatFsmState.AWESOME,
          Runnable { calls += "fourth" }
        )
        .addTransitionExitCallback(MyGreatFsmState.INITIAL, Runnable { calls += "first" })
        .addTransitionExitCallback(MyGreatFsmState.INITIAL, Runnable { calls += "second" })
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(calls)
      .containsExactly("first", "second", "third", "fourth", "fifth", "sixth")
      .inOrder()
  }

  @Test
  fun transition_callsCallbacksInline() {
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL)
        .addTransition(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME)
        .addTransitionEnterCallback(MyGreatFsmState.AWESOME) { calls += "fifth" }
        .addTransitionEnterCallback(MyGreatFsmState.AWESOME) { calls += "sixth" }
        .addTransitionCallback(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME) {
          calls += "third"
        }
        .addTransitionCallback(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME) {
          calls += "fourth"
        }
        .addTransitionExitCallback(MyGreatFsmState.INITIAL) { calls += "first" }
        .addTransitionExitCallback(MyGreatFsmState.INITIAL) { calls += "second" }
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(calls)
      .containsExactly("first", "second", "third", "fourth", "fifth", "sixth")
      .inOrder()
  }

  @Test
  fun transition_callsConvenienceMethodCallback() {
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL)
        .addTransition(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME) { calls += "first" }
        .addTransitionCallback(MyGreatFsmState.INITIAL, MyGreatFsmState.AWESOME) {
          calls += "second"
        }
        .build()
    stateMachine.state = MyGreatFsmState.AWESOME
    assertThat(calls).containsExactly("first", "second").inOrder()
  }

  @Test
  fun selfTransition_noop() {
    val stateMachine =
      StateMachine.Builder(
        MyGreatFsmState.INITIAL,
        Config(selfTransitionBehavior = SelfTransitionBehavior.NOOP, logger = fakeLogger)
      )
        .addTransitionEnterCallback(MyGreatFsmState.INITIAL) { calls += "fifth" }
        .addTransitionEnterCallback(MyGreatFsmState.INITIAL) { calls += "sixth" }
        .addTransitionCallback(MyGreatFsmState.INITIAL, MyGreatFsmState.INITIAL) {
          calls += "third"
        }
        .addTransitionCallback(MyGreatFsmState.INITIAL, MyGreatFsmState.INITIAL) {
          calls += "fourth"
        }
        .addTransitionExitCallback(MyGreatFsmState.INITIAL) { calls += "first" }
        .addTransitionExitCallback(MyGreatFsmState.INITIAL) { calls += "second" }
        .build()
    stateMachine.state = MyGreatFsmState.INITIAL
    assertThat(fakeLogger.debugLogs).containsExactly("Ignoring self-transition in state INITIAL.")
    assertThat(calls).isEmpty()
  }

  @Test
  fun selfTransition_normal_legal() {
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL, Config(logger = fakeLogger))
        .addTransition(MyGreatFsmState.INITIAL, MyGreatFsmState.INITIAL)
        .addTransitionEnterCallback(MyGreatFsmState.INITIAL) { calls += "fifth" }
        .addTransitionEnterCallback(MyGreatFsmState.INITIAL) { calls += "sixth" }
        .addTransitionCallback(MyGreatFsmState.INITIAL, MyGreatFsmState.INITIAL) {
          calls += "third"
        }
        .addTransitionCallback(MyGreatFsmState.INITIAL, MyGreatFsmState.INITIAL) {
          calls += "fourth"
        }
        .addTransitionExitCallback(MyGreatFsmState.INITIAL) { calls += "first" }
        .addTransitionExitCallback(MyGreatFsmState.INITIAL) { calls += "second" }
        .build()
    stateMachine.state = MyGreatFsmState.INITIAL
    assertThat(fakeLogger.debugLogs).containsExactly("Transition from INITIAL to INITIAL.")
    assertThat(calls)
      .containsExactly("first", "second", "third", "fourth", "fifth", "sixth")
      .inOrder()
  }

  @Test
  fun selfTransition_normal_illegal() {
    val stateMachine = StateMachine.Builder(MyGreatFsmState.INITIAL).build()
    assertFailsWith<IllegalArgumentException> {
      stateMachine.state = MyGreatFsmState.INITIAL
    }
  }

  @Test
  fun assertState() {
    val stateMachine = StateMachine.Builder(MyGreatFsmState.INITIAL).build()
    assertFailsWith<IllegalStateException> {
      stateMachine.assertState(MyGreatFsmState.AWESOME)
    }
  }

  @Test
  fun getDurationInCurrentState() {
    val timeSource = TestTimeSource()
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL, Config(timeSource = timeSource))
        .addTransition(MyGreatFsmState.INITIAL, MyGreatFsmState.INITIAL)
        .build()
    assertThat(stateMachine.getDurationInCurrentState()).isEqualTo(Duration.ZERO)
    timeSource += 42.milliseconds
    assertThat(stateMachine.getDurationInCurrentState()).isEqualTo(42.milliseconds)
    timeSource += 58.milliseconds
    assertThat(stateMachine.getDurationInCurrentState()).isEqualTo(100.milliseconds)

    stateMachine.state = MyGreatFsmState.INITIAL
    assertThat(stateMachine.getDurationInCurrentState()).isEqualTo(Duration.ZERO)
    timeSource += 31.milliseconds
    assertThat(stateMachine.getDurationInCurrentState()).isEqualTo(31.milliseconds)
    timeSource += 49.milliseconds
    assertThat(stateMachine.getDurationInCurrentState()).isEqualTo(80.milliseconds)

    stateMachine.state = MyGreatFsmState.INITIAL
    assertThat(stateMachine.getDurationInCurrentState()).isEqualTo(Duration.ZERO)
    timeSource += 57.milliseconds
    assertThat(stateMachine.getDurationInCurrentState()).isEqualTo(57.milliseconds)
    timeSource += 15.milliseconds
    assertThat(stateMachine.getDurationInCurrentState()).isEqualTo(72.milliseconds)
  }

  @Test
  fun getMillisecondsInCurrentState() {
    val timeSource = TestTimeSource()
    val stateMachine =
      StateMachine.Builder(MyGreatFsmState.INITIAL, Config(timeSource = timeSource))
        .addTransition(MyGreatFsmState.INITIAL, MyGreatFsmState.INITIAL)
        .build()
    assertThat(stateMachine.getMillisecondsInCurrentState()).isEqualTo(0)
    timeSource += 42.milliseconds
    assertThat(stateMachine.getMillisecondsInCurrentState()).isEqualTo(42)
    timeSource += 58.milliseconds
    assertThat(stateMachine.getMillisecondsInCurrentState()).isEqualTo(100)

    stateMachine.state = MyGreatFsmState.INITIAL
    assertThat(stateMachine.getMillisecondsInCurrentState()).isEqualTo(0)
    timeSource += 31.milliseconds
    assertThat(stateMachine.getMillisecondsInCurrentState()).isEqualTo(31)
    timeSource += 49.milliseconds
    assertThat(stateMachine.getMillisecondsInCurrentState()).isEqualTo(80)

    stateMachine.state = MyGreatFsmState.INITIAL
    assertThat(stateMachine.getMillisecondsInCurrentState()).isEqualTo(0)
    timeSource += 57.milliseconds
    assertThat(stateMachine.getMillisecondsInCurrentState()).isEqualTo(57)
    timeSource += 15.milliseconds
    assertThat(stateMachine.getMillisecondsInCurrentState()).isEqualTo(72)
  }

  @Test
  fun dslBuilderSyntax() {
    val stateMachine =
      StateMachine.stateMachine(MyGreatFsmState.INITIAL) {
        MyGreatFsmState.INITIAL {
          onExit { calls += "exitInitial" }
          transitionsTo(MyGreatFsmState.INITIAL, MyGreatFsmState.INITIAL, MyGreatFsmState.INITIAL) {
            calls += "initialToInitial"
          }
          transitionsTo(MyGreatFsmState.AWESOME) { calls += "initialToAwesome" }
        }
        // Also exercise the non-nested syntax.
        MyGreatFsmState.AWESOME.onEnter { calls += "enterAwesome" }
        MyGreatFsmState.AWESOME.onExit { calls += "exitAwesome" }
        MyGreatFsmState.AWESOME.transitionsTo(MyGreatFsmState.AWESOMER, MyGreatFsmState.INITIAL) {
          calls += "awesomeToSomethingElse"
        }
      }

    stateMachine.state = MyGreatFsmState.INITIAL
    stateMachine.state = MyGreatFsmState.AWESOME
    stateMachine.state = MyGreatFsmState.AWESOMER
    assertThat(calls)
      .containsExactly(
        "exitInitial",
        "initialToInitial",
        "exitInitial",
        "initialToAwesome",
        "enterAwesome",
        "exitAwesome",
        "awesomeToSomethingElse"
      )
      .inOrder()
  }

  private class FakeLogger : Logger() {
    val debugLogs: MutableCollection<String> = mutableListOf()
    val warnLogs: MutableCollection<String> = mutableListOf()

    override fun isDebugEnabled(): Boolean = true

    override fun debug(message: String) {
      debugLogs.add(message)
    }

    override fun debug(t: Throwable?) {
      throw NotImplementedError()
    }

    override fun debug(message: String, t: Throwable?) {
      debugLogs.add(message)
    }

    override fun info(message: String) {
      throw NotImplementedError()
    }

    override fun info(message: String, t: Throwable?) {
      throw NotImplementedError()
    }

    override fun warn(message: String) {
      warnLogs.add(message)
    }

    override fun warn(message: String, t: Throwable?) {
      throw NotImplementedError()
    }

    override fun error(message: String, t: Throwable?, vararg details: String?) {
      throw NotImplementedError()
    }

    override fun setLevel(level: Level) {
      throw NotImplementedError()
    }
  }
}
