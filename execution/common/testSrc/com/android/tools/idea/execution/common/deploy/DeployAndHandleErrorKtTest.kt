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
package com.android.tools.idea.execution.common.deploy

import com.android.tools.deployer.Deployer
import com.android.tools.deployer.DeployerException
import com.android.tools.idea.execution.common.AndroidExecutionException
import com.android.tools.idea.execution.common.debug.createFakeExecutionEnvironment
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.NotificationRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.notification.NotificationType
import com.intellij.testFramework.RuleChain
import junit.framework.TestCase.fail
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assume.assumeThat
import org.junit.Rule
import org.junit.Test

class DeployAndHandleErrorKtTest {
  private val ACTIVITY_NAME = "com.example.Activity"
  private val APPLICATION_ID = "com.example"
  private val deployerResult = Deployer.Result(false, false, false,
                                               createApp(APPLICATION_ID, activitiesName = listOf(ACTIVITY_NAME)))

  val projectRule = AndroidProjectRule.inMemory()
  val notificationRule = NotificationRule(projectRule)

  @get:Rule
  val ruleChain = RuleChain(projectRule, notificationRule)

  @Test
  fun deployWithoutException() {
    val env = createFakeExecutionEnvironment(projectRule.project, "test")

    deployAndHandleError(env, { listOf(deployerResult) })
  }

  @Test
  fun deployWithException_noResolution() {
    val env = createFakeExecutionEnvironment(projectRule.project, "test")

    val exception = DeployerException.unsupportedArch()
    assumeThat(exception.error.resolution, equalTo(DeployerException.ResolutionAction.NONE))

    try {
      deployAndHandleError(env, { throw exception })
      fail()
    }
    catch (e: AndroidExecutionException) {
      assertThat(e.errorId).isEqualTo(exception.id)
    }
  }

  @Test
  fun deployWithException_retry_automaticallyApplyResolutionAction() {
    val env = createFakeExecutionEnvironment(projectRule.project, "test")

    val exception = DeployerException.preinstallFailed("test")
    assumeThat(exception.error.resolution, equalTo(DeployerException.ResolutionAction.RETRY))

    var invocationCount = 0

    deployAndHandleError(env, {
      invocationCount++
      if (invocationCount == 1) {
        throw exception
      }
      if (invocationCount == 2) {
        return@deployAndHandleError listOf(deployerResult)
      }
      throw RuntimeException("Invoked more than 2 times")
    }, automaticallyApplyResolutionAction = true)
  }

  @Test
  fun deployWithException_retry_automaticallyApplyResolutionAction_failTwice() {
    val env = createFakeExecutionEnvironment(projectRule.project, "test")

    val exception = DeployerException.preinstallFailed("test")
    assumeThat(exception.error.resolution, equalTo(DeployerException.ResolutionAction.RETRY))

    var invocationCount = 0

    try {
      deployAndHandleError(env, {
        invocationCount++
        if (invocationCount == 1) {
          throw exception
        }
        if (invocationCount == 2) {
          throw exception
        }
        throw RuntimeException("Invoked more than 2 times")
      }, automaticallyApplyResolutionAction = true)
      fail()
    }
    catch (e: AndroidExecutionException) {
      assertThat(e.errorId).isEqualTo(exception.id)
    }

    val notificationInfo = notificationRule.notifications.find {
      it.type == NotificationType.ERROR &&
      it.content == "Installation failed\nThe application could not be installed."
      it.actions.single().templateText == "Retry"
    }

    assertThat(notificationInfo).isNotNull()
  }

  @Test
  fun deployWithException_runApp() {
    val env = createFakeExecutionEnvironment(projectRule.project, "test")

    val exception = DeployerException.processCrashing(APPLICATION_ID)
    assumeThat(exception.error.resolution, equalTo(DeployerException.ResolutionAction.RUN_APP))


    try {
      deployAndHandleError(env, { throw exception })
      fail()
    }
    catch (e: AndroidExecutionException) {
      assertThat(e.errorId).isEqualTo(exception.id)
    }

    val notificationInfo = notificationRule.notifications.find {
      it.type == NotificationType.ERROR &&
      it.content == "Installation failed\n" +
      "Apply Changes could not complete because an application process is crashed."
      it.actions.single().templateText == "Reinstall and restart app"
    }

    assertThat(notificationInfo).isNotNull()
  }

  @Test
  fun deployWithException_runApp_automaticallyApplyResolutionAction() {
    val env = createFakeExecutionEnvironment(projectRule.project, "test")

    val exception = DeployerException.processCrashing(APPLICATION_ID)
    assumeThat(exception.error.resolution, equalTo(DeployerException.ResolutionAction.RUN_APP))


    try {
      deployAndHandleError(env, { throw exception }, automaticallyApplyResolutionAction = true)
      fail()
    }
    catch (e: AndroidExecutionException) {
      assertThat(e.errorId).isEqualTo(exception.id)
    }

    val notificationInfo = notificationRule.notifications.find {
      it.type == NotificationType.ERROR &&
      it.content == "Installation failed\n" +
      "Reinstall and restart app will be done automatically" &&
      it.actions.isEmpty()
    }

    assertThat(notificationInfo).isNotNull()
  }

  @Test
  fun deployWithException_applyChanges() {

    val env = createFakeExecutionEnvironment(projectRule.project, "test")

    val exception = DeployerException.changedCrashlyticsBuildId("")
    assumeThat(exception.error.resolution, equalTo(DeployerException.ResolutionAction.APPLY_CHANGES))


    try {
      deployAndHandleError(env, { throw exception })
      fail()
    }
    catch (e: AndroidExecutionException) {
      assertThat(e.errorId).isEqualTo(exception.id)
    }

    val notificationInfo = notificationRule.notifications.find {
      it.type == NotificationType.ERROR &&
      it.content == "Installation failed\n" +
      "Crashlytics modified your build ID, which requires an activity restart. <a href=\"https://d.android.com/r/studio-ui/apply-changes-crashlytics-buildid\">See here</a>"
      it.actions.single().templateText == "Apply changes and restart activity"
    }

    assertThat(notificationInfo).isNotNull()
  }

  @Test
  fun deployWithException_applyChanges_remapToRun() {

    // Resolutions to Apply Changes in Debug mode needs to be remapped to Rerun.
    val env = createFakeExecutionEnvironment(projectRule.project, "test", DefaultDebugExecutor.getDebugExecutorInstance())

    val exception = DeployerException.changedCrashlyticsBuildId("")
    assumeThat(exception.error.resolution, equalTo(DeployerException.ResolutionAction.APPLY_CHANGES))


    try {
      deployAndHandleError(env, { throw exception })
      fail()
    }
    catch (e: AndroidExecutionException) {
      assertThat(e.errorId).isEqualTo(exception.id)
    }

    val notificationInfo = notificationRule.notifications.find {
      it.type == NotificationType.ERROR &&
      it.content == "Installation failed\n" +
      "Apply Changes could not complete because an application process is crashed."
      it.actions.single().templateText == "Rerun"
    }

    assertThat(notificationInfo).isNotNull()
  }
}