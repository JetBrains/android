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
package com.android.tools.idea.gradle.project.sync.cleanup

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationsConfiguration
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import junit.framework.TestCase
import org.gradle.internal.impldep.com.amazonaws.services.s3.model.NotificationConfiguration
import org.jetbrains.android.AndroidTestBase

class ProjectNotificationsCleanUpTaskTest : AndroidGradleTestCase() {
  override fun createDefaultProject(): Boolean = false

  fun testCorrentGroupName() {
    // Force initialize the implementation to test the registered group name.
    object : AbstractModuleDataService<Nothing>() {
      override fun getTargetDataKey(): Key<Nothing> = throw UnsupportedOperationException()
    }
    assertThat(NotificationGroup.findRegisteredGroup(BUILD_SYNC_ORPHAN_MODULES_NOTIFICATION_GROUP_NAME)).isNotNull()
  }
}