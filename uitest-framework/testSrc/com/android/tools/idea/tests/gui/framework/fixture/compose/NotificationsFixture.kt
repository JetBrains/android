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
package com.android.tools.idea.tests.gui.framework.fixture.compose

import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixture
import junit.framework.TestCase
import org.fest.swing.core.Robot
import org.fest.swing.timing.Wait
import org.junit.Assert.assertTrue
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Fixture for the Compose preview notifications panel
 */
class NotificationsFixture(val robot: Robot, private val notificationsPanel: JComponent) :
  ComponentFixture<NotificationsFixture, JComponent>(
    NotificationsFixture::class.java, robot, notificationsPanel) {
  fun assertNotificationTextContains(text: String) {
    TestCase.assertNotNull(robot.finder().find(target()) {
      it is JLabel && it.text.contains(text)
    })
  }

  fun assertNoNotifications(): NotificationsFixture {
    assertTrue(target().components.isEmpty())
    return this
  }

  fun waitForNotificationContains(text: String, wait: Wait = Wait.seconds(60)) {
    wait.expecting("Notification '$text' is displaying").until {
      robot.finder().findAll(target()) {
        it is JLabel && it.text.contains(text)
      }.isNotEmpty()
    }
  }
}

fun SplitEditorFixture.getNotificationsFixture(): NotificationsFixture =
  NotificationsFixture(robot, robot.finder().findByName(target(), "NotificationsPanel", JComponent::class.java, false))