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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.testing.ui.flatten
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.EditorNotificationPanel.Status
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import javax.swing.JLabel

class InspectorBannerTest {

  @get:Rule val projectRule = ProjectRule()

  @get:Rule val disposableRule = DisposableRule()

  @Test
  fun testInitiallyHidden() {
    val notificationModel = NotificationModel(projectRule.project)
    val banner = InspectorBanner(disposableRule.disposable, notificationModel)
    assertThat(banner.isVisible).isFalse()
  }

  @Test
  fun testVisibleWithStatus() {
    val notificationModel = NotificationModel(projectRule.project)
    val banner = InspectorBanner(disposableRule.disposable, notificationModel)
    notificationModel.addNotification(
      "key1",
      "There is an error somewhere <a>",
      Status.Error,
      emptyList(),
    )
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
    assertThat(banner.isVisible).isTrue()
    val label = banner.flatten().filterIsInstance<JLabel>().first()
    assertThat(label.text).isEqualTo("<html>There is an error somewhere &lt;a&gt;</html>")
  }

  @Test
  fun testInvisibleAfterEmptyStatus() {
    val notificationModel = NotificationModel(projectRule.project)
    val banner = InspectorBanner(disposableRule.disposable, notificationModel)
    notificationModel.addNotification(
      "key1",
      "There is an error somewhere",
      Status.Error,
      emptyList(),
    )
    notificationModel.clear()
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
    assertThat(banner.isVisible).isFalse()
  }

  @Test
  fun testListenersRemovedOnDispose() {
    val notificationModel = NotificationModel(projectRule.project)
    val banner = InspectorBanner(disposableRule.disposable, notificationModel)
    notificationModel.addNotification(
      "key1",
      "There is an error somewhere",
      Status.Error,
      emptyList(),
    )

    assertThat(notificationModel.notificationListeners).hasSize(1)

    Disposer.dispose(disposableRule.disposable)

    assertThat(notificationModel.notificationListeners).hasSize(0)
  }
}
