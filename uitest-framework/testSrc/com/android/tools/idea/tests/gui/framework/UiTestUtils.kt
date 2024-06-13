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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE") // TODO: remove usage of sun.awt.PeerEvent.
package com.android.tools.idea.tests.gui.framework

import com.android.tools.adtui.HtmlLabel
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture
import com.android.utils.TraceUtils
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBList
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.fixture.JListFixture
import org.fest.swing.timing.Wait
import sun.awt.PeerEvent
import java.awt.AWTEvent
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.Robot
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

fun HtmlLabel.plainText(): String = document.getText(0, document.length)

fun waitForIdle() = actAndWaitFor(
  isAwaitedEvent = InputEvent::isSyncKeyEvent,
  performAction = ::sendSynKeyEvents
)

fun actAndWaitFor(
  isAwaitedEvent: InputEvent.() -> AwaitedEventKind,
  performAction: () -> Unit
) {
  fun getDetails() =
    try {
      buildString {
        appendLine("TrueCurrentEvent: ${IdeEventQueue.getInstance().trueCurrentEvent} (${IdeEventQueue.getInstance().eventCount})")
        appendLine("peekEvent(): ${IdeEventQueue.getInstance().peekEvent()}")
        appendLine("EDT: ${ThreadDumper.dumpEdtStackTrace(ThreadDumper.getThreadInfos())}")
      }
    }
    catch (t: Throwable) {
      t.message.orEmpty()
    }

  try {
    oneFullSync(isAwaitedEvent, performAction)
  }
  catch (e: WaitTimedOutError) {
    throw WaitTimedOutError("${e.message}\n${getDetails()}")
  }
}

internal fun ContainerFixture<*>.clickToolButton(titlePrefix: String) {
  fun ActionButton.matches() = toolTipText?.startsWith(titlePrefix) ?: false ||
                               accessibleContext.accessibleName?.startsWith(titlePrefix) ?: false

  // Find the topmost tool button. (List/Map editors may contains similar buttons)
  val button =
    ActionButtonFixture(
      robot(),
      robot()
        .finder()
        .findAll(target(), matcher<ActionButton> { it.matches() })
        .minByOrNull { button -> generateSequence<Container>(button) { it.parent }.count() }
      ?: robot().finder().find<ActionButton>(target()) { it.matches() })
  Wait.seconds(1).expecting("Enabled").until { button.isEnabled }
  button.click()
}

/**
 * Returns the popup list being displayed, assuming there is one and it is the only one.
 */
internal fun ContainerFixture<*>.getList(): JBList<*> {
  return GuiTests.waitUntilShowingAndEnabled<JBList<*>>(robot(), null, object : GenericTypeMatcher<JBList<*>>(JBList::class.java) {
    override fun isMatching(list: JBList<*>): Boolean {
      return list.javaClass.name == "com.intellij.ui.popup.list.ListPopupImpl\$MyList"
    }
  })
}

private val awtRobot = Robot()
private const val SYNC_KEY_CODE = KeyEvent.VK_F11
private const val SYNC_KEY_TIMEOUT_SEC = 3

enum class AwaitedEventKind(val done: Boolean, val consume: Boolean) {
  UNKNOWN(done = false, consume = false),
  AWAITED_CONSUME_AND_CONTINUE(done = false, consume = true),
  AWAITED_DONE(done = true, consume = false),
  AWAITED_CONSUME_AND_DONE(done = true, consume = true),
}

private fun oneFullSync(
  isAwaitedEvent: InputEvent.() -> AwaitedEventKind,
  performAction: () -> Unit
) {
  val lock = ReentrantLock()
  val condition = lock.newCondition()
  val start = System.currentTimeMillis()

  fun testAndLog(message: String, body: () -> Boolean) =
    body().also {
      if (it) {
        println("Skipping wait for sync even: $message")
      }
    }

  fun xQueueSync() {
    var done = false
    fun shouldContinue() = !done && System.currentTimeMillis() - start < 1000 * SYNC_KEY_TIMEOUT_SEC

    do {
      val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
      if (testAndLog("focusManager.focusOwner?.isShowing != true")
        { focusManager.focusOwner?.isShowing != true }) return // Nowhere to send key presses to.

      val activeWindow = focusManager
                           .activeWindow
                           ?.takeUnless { testAndLog("!it.isShowing") { !it.isShowing } }
                         ?: return // Nowhere to send key presses to.
      val d = Disposer.newDisposable()
      try {
        fun handleEvent(e: AWTEvent): Boolean {
          if (done) return false
          if (e !is InputEvent) return false
          val awaitedEventKind = e.isAwaitedEvent()
          if (awaitedEventKind.done) {
              lock.withLock { done = true; condition.signalAll() }
          }
          if (awaitedEventKind.consume) e.consume()
          return awaitedEventKind.consume
        }

        IdeEventQueue.getInstance().addDispatcher(::handleEvent, d)

        performAction()
        lock.withLock {
          do {
            condition.await(100, TimeUnit.MILLISECONDS)

            if (!done &&
            // Changing the active window might eat key presses/releases. It happens when closing a project, for example.
              testAndLog("focusManager.activeWindow !== activeWindow") { focusManager.activeWindow !== activeWindow } ||
              testAndLog("focusManager.focusOwner?.isShowing != true") { focusManager.focusOwner?.isShowing != true } ||
              // Closing a project may bring us into this erroneous state when a closed frame remains active and focus is nowhere.
              testAndLog("!activeWindow.isShowing") { !activeWindow.isShowing }
            ) {
              break;
            }
          }
          while (shouldContinue())
        }
      }
      finally {
        Disposer.dispose(d)
      }
    }
    while (shouldContinue())
    if (!done) {
      println("Awaited event has not been received within $SYNC_KEY_TIMEOUT_SEC sec. Giving up...\n\n${TraceUtils.currentStack}");
    }
  }

  fun eventQueueSync() {
    var done = false
    fun postTryIdleMessage(expectNumber: Int) {
      if (IdeEventQueue.getInstance().eventCount == expectNumber) {
        lock.withLock { done = true; condition.signalAll() }
      }
      else {
        val expectEventCount = IdeEventQueue.getInstance().eventCount + 1
        IdeEventQueue.getInstance().postEvent(
          PeerEvent(Toolkit.getDefaultToolkit(), { postTryIdleMessage(expectEventCount) }, 0))
      }
    }

    postTryIdleMessage(-1)
    lock.withLock {
      while (!done && System.currentTimeMillis() - start < 15_000) {
        condition.await(100, TimeUnit.MILLISECONDS)
      }
    }
    if (!done) throw WaitTimedOutError("Timed out waiting for idle.")
  }

  xQueueSync()
  eventQueueSync()
}

private fun InputEvent.isSyncKeyEvent(): AwaitedEventKind {
  return if (this is KeyEvent && this.keyCode == SYNC_KEY_CODE) {
    if (this.id == KeyEvent.KEY_RELEASED) {
      return AwaitedEventKind.AWAITED_CONSUME_AND_DONE
    }
    AwaitedEventKind.AWAITED_CONSUME_AND_CONTINUE
  }
  else AwaitedEventKind.UNKNOWN
}

private fun sendSynKeyEvents() {
  awtRobot.keyPress(SYNC_KEY_CODE)
  awtRobot.keyRelease(SYNC_KEY_CODE)
}
fun JListFixture.dragAndClickItem(text: String) {
  drag(text)
  clickItem(text)
}

fun JListFixture.dragAndClickItem(index: Int) {
  drag(index)
  clickItem(index)
}

fun org.fest.swing.core.Robot.fixupWaiting(): org.fest.swing.core.Robot = (this as? ReliableRobot) ?: ReliableRobot(this)
