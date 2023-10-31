/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import org.junit.rules.ExternalResource
import javax.swing.Icon

/**
 * A rule that allows verification of [Notification]s posted during a test.
 *
 * The rule subscribes to the [com.intellij.util.messages.MessageBus] and collects posted notifications in a list of data objects that
 * encapsulate the fields of a [Notification] that a test might care about.
 *
 * Note that the [Notification] class itself is not good for us because it doesn't have an equals() method, and it contains fields we
 * definitely do not want to assert on for example, [Notification.id].
 */
class NotificationRule(private val project: () -> Project) : ExternalResource() {

  private val disposable: Disposable = Disposer.newDisposable("NotificationRule")

  constructor(rule: ProjectRule) : this(rule::project)

  constructor(rule: AndroidProjectRule) : this(rule::project)

  constructor(rule: EdtAndroidProjectRule) : this(rule::project)

  private val _notifications: MutableList<NotificationInfo> = mutableListOf()
  val notifications: List<NotificationInfo> = _notifications

  override fun before() {
    project().messageBus.connect(disposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        _notifications.add(NotificationInfo(
          notification.groupId,
          notification.icon,
          notification.title,
          notification.subtitle,
          notification.content,
          notification.type,
          notification.isImportant,
          notification.actions
        ))
      }
    })
  }

  override fun after() {
    Disposer.dispose(disposable)
  }

  /**
   * Encapsulate the fields we typically care about when verifying a notification
   */
  data class NotificationInfo(
    val groupId: String,
    val icon: Icon? = null,
    val title: String? = "",
    val subtitle: String? = null,
    val content: String? = null,
    val type: NotificationType,
    val important: Boolean? = false,
    val actions: MutableList<AnAction>,
  )
}