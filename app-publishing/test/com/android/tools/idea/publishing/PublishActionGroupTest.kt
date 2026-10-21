/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.publishing

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.flags.overrideForTest
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test

class PublishActionGroupTest {
  @get:Rule val projectRule = ProjectRule()

  private val project: Project
    get() = projectRule.project

  @Test
  fun testGetChildren_empty() {
    StudioFlags.PLAY_PUBLISHING_BUILD_MENU_ACTION.overrideForTest(true, projectRule.disposable)
    ExtensionTestUtil.maskExtensions(AppPublisher.EP_NAME, emptyList(), projectRule.disposable)

    val group = PublishActionGroup()
    val event = TestActionEvent.createTestEvent()
    val children = group.getChildren(event)
    assertThat(children).isEmpty()
  }

  @Test
  fun testGetChildren_filtersUnavailable() {
    StudioFlags.PLAY_PUBLISHING_BUILD_MENU_ACTION.overrideForTest(true, projectRule.disposable)
    val availablePublisher =
      object : AppPublisher {
        override val id = "AvailableId"
        override val displayName = "Available"

        override fun isAvailable() = true

        override fun publishApp(project: Project, context: AppPublishingContext) {}
      }
    val unavailablePublisher =
      object : AppPublisher {
        override val id = "UnavailableId"
        override val displayName = "Unavailable"

        override fun isAvailable() = false

        override fun publishApp(project: Project, context: AppPublishingContext) {}
      }

    ExtensionTestUtil.maskExtensions(AppPublisher.EP_NAME, listOf(availablePublisher, unavailablePublisher), projectRule.disposable)

    val group = PublishActionGroup()
    val event = TestActionEvent.createTestEvent()
    val children = group.getChildren(event)
    assertThat(children).hasLength(1)
    assertThat(children[0].templateText).isEqualTo("Available")
  }

  @Test
  fun testUpdate_visibleWhenPublisherAvailable() {
    StudioFlags.PLAY_PUBLISHING_BUILD_MENU_ACTION.overrideForTest(true, projectRule.disposable)
    val publisher =
      object : AppPublisher {
        override val id = "TestPublisherId"
        override val displayName = "TestPublisher"

        override fun isAvailable() = true

        override fun publishApp(project: Project, context: AppPublishingContext) {}
      }
    ExtensionTestUtil.maskExtensions(AppPublisher.EP_NAME, listOf(publisher), projectRule.disposable)

    val group = PublishActionGroup()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))
    group.update(event)
    assertThat(event.presentation.isEnabledAndVisible).isTrue()
  }

  @Test
  fun testUpdate_invisibleWhenNoPublisherAvailable() {
    StudioFlags.PLAY_PUBLISHING_BUILD_MENU_ACTION.overrideForTest(true, projectRule.disposable)
    val publisher =
      object : AppPublisher {
        override val id = "TestPublisherId"
        override val displayName = "TestPublisher"

        override fun isAvailable() = false

        override fun publishApp(project: Project, context: AppPublishingContext) {}
      }
    ExtensionTestUtil.maskExtensions(AppPublisher.EP_NAME, listOf(publisher), projectRule.disposable)

    val group = PublishActionGroup()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))
    group.update(event)
    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun testUpdate_invisibleWhenFlagDisabled() {
    StudioFlags.PLAY_PUBLISHING_BUILD_MENU_ACTION.overrideForTest(false, projectRule.disposable)
    val publisher =
      object : AppPublisher {
        override val id = "TestPublisherId"
        override val displayName = "TestPublisher"

        override fun isAvailable() = true

        override fun publishApp(project: Project, context: AppPublishingContext) {}
      }
    ExtensionTestUtil.maskExtensions(AppPublisher.EP_NAME, listOf(publisher), projectRule.disposable)

    val group = PublishActionGroup()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))
    group.update(event)
    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun testPublishAppAction_executesPublisher() {
    var published = false
    val publisher =
      object : AppPublisher {
        override val id = "TestPublisherId"
        override val displayName = "TestPublisher"

        override fun isAvailable() = true

        override fun publishApp(project: Project, context: AppPublishingContext) {
          published = true
          assertThat(context.publishingSource).isEqualTo(AppPublishingSource.BUILD_MENU)
        }
      }
    ExtensionTestUtil.maskExtensions(AppPublisher.EP_NAME, listOf(publisher), projectRule.disposable)

    val action = PublishAppAction(publisher)
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))
    action.actionPerformed(event)
    assertThat(published).isTrue()
  }
}
