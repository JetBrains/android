/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.adblib

import com.android.adblib.tools.AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_SHORT
import com.android.adblib.tools.AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.BalloonLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Rectangle
import java.time.Duration
import javax.swing.JComponent

class AndroidAdbSessionHostTest {
  @get:Rule
  val projectRule = ProjectRule()

  private lateinit var host: AndroidAdbSessionHost

  @Before
  fun before() {
    host = AndroidAdbSessionHost()
  }

  @After
  fun after() {
    host.close()
  }

  @Test
  fun overridingPropertyValueThrowsForNonVolatileProperty() {
    // Act/Assert
    assertThrows(IllegalArgumentException::class.java) {
      host.overridePropertyValue(PROCESS_PROPERTIES_COLLECTOR_DELAY_SHORT, Duration.ofMillis(10))
    }
  }

  @Test
  fun propertiesCollectorUseShortDelayIsTrueByDefault() {
    // Act
    val useShortDelay = host.getPropertyValue(PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT)

    // Assert
    assertEquals("This test depends on `ApplicationManager.getApplication().isActive` returning `true`", true, useShortDelay)
  }

  @Test
  fun propertiesCollectorUseShortDelayIsFalseWhenInactive() {
    // Prepare
    val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(ApplicationActivationListener.TOPIC)
    val ideFrame = TestingIdeFrame()

    // Act
    publisher.applicationDeactivated(ideFrame)
    val useShortDelay = host.getPropertyValue(PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT)

    // Assert
    assertEquals(false, useShortDelay)
  }

  @Test
  fun propertiesCollectorUseShortDelayIsTrueWhenReActivated() {
    // Prepare
    val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(ApplicationActivationListener.TOPIC)
    val ideFrame = TestingIdeFrame()

    // Act
    publisher.applicationDeactivated(ideFrame)
    val useShortDelay1 = host.getPropertyValue(PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT)

    publisher.applicationActivated(ideFrame)
    val useShortDelay2 = host.getPropertyValue(PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT)

    // Assert
    assertEquals(false, useShortDelay1)
    assertEquals(true, useShortDelay2)
  }

  private class TestingIdeFrame : IdeFrame {
    override fun getStatusBar(): StatusBar? {
      TODO("Not yet implemented")
    }

    override fun suggestChildFrameBounds(): Rectangle {
      TODO("Not yet implemented")
    }

    override fun getProject(): Project? {
      TODO("Not yet implemented")
    }

    override fun setFrameTitle(title: String?) {
      TODO("Not yet implemented")
    }

    override fun getComponent(): JComponent {
      TODO("Not yet implemented")
    }

    override fun getBalloonLayout(): BalloonLayout? {
      TODO("Not yet implemented")
    }

  }
}
