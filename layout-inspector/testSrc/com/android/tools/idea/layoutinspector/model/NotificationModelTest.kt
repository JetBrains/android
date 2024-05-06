/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.model

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test

class NotificationModelTest {
  @get:Rule val projectRule = ProjectRule()

  @Test
  fun testHasNotification() {
    val notificationModel = NotificationModel(projectRule.project)

    val hasNotification1 = notificationModel.hasNotification("notification.id")
    assertThat(hasNotification1).isFalse()

    notificationModel.addNotification("notification.id", "test notification")

    val hasNotification2 = notificationModel.hasNotification("notification.id")
    assertThat(hasNotification2).isTrue()
  }
}
