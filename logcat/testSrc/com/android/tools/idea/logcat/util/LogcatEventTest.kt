package com.android.tools.idea.logcat.util

import com.android.tools.idea.logcat.FakeLogcatPresenter
import com.android.tools.idea.logcat.util.LogcatEvent.LogcatMessagesEvent
import com.android.tools.idea.logcat.util.LogcatEvent.LogcatPanelVisibility
import com.android.tools.idea.testing.ApplicationServiceRule
import com.android.tools.idea.testing.TestLoggerRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [LogcatEvent]
 */
class LogcatEventTest {

  @get:Rule
  val rule = RuleChain(ApplicationRule(), ApplicationServiceRule(TempFileFactory::class.java, TestTempFileFactory()), TestLoggerRule())

  private val channel = Channel<LogcatEvent>()
  private val fakeLogcatPresenter = FakeLogcatPresenter()

  @Test
  fun panelShowing_sendsMessagesToPresenter(): Unit = runBlocking {
    fakeLogcatPresenter.showing = true
    val job = launch { channel.consumeAsFlow().consume(fakeLogcatPresenter, "id", 10000) }

    channel.send(LogcatMessagesEvent(listOf(logcatMessage(message = "Foo"))))

    channel.cancel()
    job.join()
    assertThat(fakeLogcatPresenter.messageBatches).containsExactly(listOf(logcatMessage(message = "Foo")))
  }

  @Test
  fun panelNotShowing_doesNotSentToPresenter(): Unit = runBlocking {
    fakeLogcatPresenter.showing = false
    val job = launch { channel.consumeAsFlow().consume(fakeLogcatPresenter, "id", 10000) }

    channel.send(LogcatMessagesEvent(listOf(logcatMessage(message = "Foo"))))

    channel.cancel()
    job.join()

    assertThat(fakeLogcatPresenter.messageBatches).isEmpty()
  }

  @Test
  fun panelBecomesVisible_readsMessagesFromFile(): Unit = runBlocking {
    fakeLogcatPresenter.showing = false
    val job = launch { channel.consumeAsFlow().consume(fakeLogcatPresenter, "id", 10000) }

    channel.send(LogcatMessagesEvent(listOf(logcatMessage(message = "Foo"))))
    channel.send(LogcatPanelVisibility(true))

    channel.cancel()
    job.join()
    assertThat(fakeLogcatPresenter.messageBatches).containsExactly(listOf(logcatMessage(message = "Foo")))
  }

  @Test
  fun panelBecomesInvisible_entersInvisibleMode(): Unit = runBlocking {
    fakeLogcatPresenter.showing = true
    val job = launch { channel.consumeAsFlow().consume(fakeLogcatPresenter, "id", 10000) }

    channel.send(LogcatMessagesEvent(listOf(logcatMessage(message = "Foo"))))
    channel.send(LogcatPanelVisibility(false))

    channel.cancel()
    job.join()
    assertThat(fakeLogcatPresenter.messageBatches).isEmpty()
  }
}