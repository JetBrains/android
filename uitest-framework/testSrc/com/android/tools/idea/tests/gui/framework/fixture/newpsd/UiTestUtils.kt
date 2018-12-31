// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import com.android.tools.adtui.HtmlLabel
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.IdeFrameContainerFixture
import com.android.tools.idea.tests.gui.framework.find
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture
import com.android.tools.idea.tests.gui.framework.matcher
import com.android.tools.idea.tests.gui.framework.robot
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBList
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.timing.Wait
import org.jetbrains.kotlin.utils.addToStdlib.cast
import sun.awt.PeerEvent
import java.awt.Container
import java.awt.Robot
import java.awt.Toolkit
import java.awt.event.InvocationEvent
import java.awt.event.KeyEvent
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

fun HtmlLabel.plainText(): String = document.getText(0, document.length)

private val lock = run {
  val f = LaterInvocator::class.java.getDeclaredField("LOCK") //NoSuchFieldException
  f.setAccessible(true)
  f.get(null)
}

fun waitForIdle() {
  val start = System.currentTimeMillis()
  val lastEvents = ConcurrentLinkedQueue<String>()  // Always updated on EDT but can be read immediately after timeout.
  fun getDetails() =
    try {
      buildString {
        appendln("TrueCurrentEvent: ${IdeEventQueue.getInstance().trueCurrentEvent} (${IdeEventQueue.getInstance().eventCount})")
        appendln("peekEvent(): ${IdeEventQueue.getInstance().peekEvent()}")
        appendln("lastEvents:")
        lastEvents.forEach { appendln(it) }
        appendln("EDT: ${ThreadDumper.dumpEdtStackTrace(ThreadDumper.getThreadInfos())}")
      }
    }
    catch (t: Throwable) {
      t.message.orEmpty()
    }

  try {
    val d = Disposer.newDisposable()
    try {
      IdeEventQueue.getInstance().addDispatcher(IdeEventQueue.EventDispatcher { e ->
        val eventString = e.toString()
        lastEvents.offer("[${System.currentTimeMillis() - start}] ${eventString}")
        if (e is InvocationEvent && eventString.contains("LaterInvocator.FlushQueue")) {
          @Suppress("INACCESSIBLE_TYPE")
          synchronized(lock) {
            LaterInvocator.getLaterInvocatorQueue().cast<Collection<Any>>().forEach {
              lastEvents.offer(it.toString())
            }
          }
        }
        if (lastEvents.size > 500) lastEvents.remove()
        false
      }, d)
      oneFullSync()
    }
    finally {
      Disposer.dispose(d)
    }
  }
  catch (e: WaitTimedOutError) {
    throw WaitTimedOutError("${e.message}\n${getDetails()}")
  }
}

internal fun IdeFrameContainerFixture.clickToolButton(titlePrefix: String) {
  fun ActionButton.matches() = toolTipText?.startsWith(titlePrefix) ?: false
  // Find the topmost tool button. (List/Map editors may contains similar buttons)
  val button =
    ActionButtonFixture(
      robot(),
      robot()
        .finder()
        .findAll(container, matcher<ActionButton> { it.matches() })
        .minBy { button -> generateSequence<Container>(button) { it.parent }.count() }
      ?: robot().finder().find<ActionButton>(container) { it.matches() })
  Wait.seconds(1).expecting("Enabled").until { button.isEnabled }
  button.click()
}

/**
 * Returns the popup list being displayed, assuming there is one and it is the only one.
 */
internal fun IdeFrameContainerFixture.getList(): JBList<*> {
  return GuiTests.waitUntilShowingAndEnabled<JBList<*>>(robot(), null, object : GenericTypeMatcher<JBList<*>>(JBList::class.java) {
    override fun isMatching(list: JBList<*>): Boolean {
      return list.javaClass.name == "com.intellij.ui.popup.list.ListPopupImpl\$MyList"
    }
  })
}

private val awtRobot = Robot()

private fun oneFullSync() {
  val lock = ReentrantLock()
  val condition = lock.newCondition()
  val start = System.currentTimeMillis()

  fun xQueueSync() {
    val d = Disposer.newDisposable()
    try {
      IdeEventQueue.getInstance().addDispatcher(IdeEventQueue.EventDispatcher { e ->
        if (e !is KeyEvent || e.keyCode != KeyEvent.VK_PAUSE) false
        else {
          if (e.id == KeyEvent.KEY_RELEASED) {
            lock.withLock { condition.signalAll() }
          }
          true
        }
      }, d)
      awtRobot.keyPress(KeyEvent.VK_PAUSE)
      awtRobot.keyRelease(KeyEvent.VK_PAUSE)
      lock.withLock {
        while (System.currentTimeMillis() - start < 15_000) {
          if (condition.await(100, TimeUnit.MILLISECONDS)) return
        }
      }
      throw WaitTimedOutError("Timed out waiting for sync event.")
    }
    finally {
      Disposer.dispose(d)
    }
  }

  fun eventQueueSync() {
    fun postTryIdleMessage(expectNumber: Int) {
      if (IdeEventQueue.getInstance().eventCount == expectNumber) {
        lock.withLock { condition.signalAll() }
      }
      else {
        val expectEventCount = IdeEventQueue.getInstance().eventCount + 1
        IdeEventQueue.getInstance().postEvent(
          PeerEvent(Toolkit.getDefaultToolkit(), { postTryIdleMessage(expectEventCount) }, PeerEvent.LOW_PRIORITY_EVENT))
      }
    }

    postTryIdleMessage(-1)
    lock.withLock {
      while (System.currentTimeMillis() - start < 15_000) {
        if (condition.await(100, TimeUnit.MILLISECONDS)) return
      }
    }
    throw WaitTimedOutError("Timed out waiting for idle.")
  }

  xQueueSync()
  eventQueueSync()
}
