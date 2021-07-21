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
package com.android.tools.idea.diagnostics.crash

import com.android.tools.idea.diagnostics.ExceptionTestUtils
import com.android.tools.idea.serverflags.protos.Date
import com.android.tools.idea.serverflags.protos.ExceptionAction
import com.android.tools.idea.serverflags.protos.ExceptionConfiguration
import com.android.tools.idea.serverflags.protos.ExceptionFilter
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.replaceService
import org.hamcrest.core.Is.`is`
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertThat
import java.util.Calendar
import java.util.GregorianCalendar

internal class ExceptionDataCollectionTest : LightPlatformTestCase() {

  class ExceptionDataConfigurationMock : ExceptionDataConfiguration {
    override fun getConfigurations(): Map<String, ExceptionConfiguration> {
      val cal = GregorianCalendar()
      return mapOf(
        "test_1" to ExceptionConfiguration.newBuilder()
          .setExpirationDate(Date.newBuilder().setYear(cal.get(Calendar.YEAR) + 1).setMonth(12).setDay(31).build())
          .setExceptionFilter(
            ExceptionFilter.newBuilder()
              .setSignature(ex1Sig)
              .build())
          .setAction(
            ExceptionAction.newBuilder()
              .setRequiresConfirmation(false)
              .setIncludeExceptionMessage(false)
              .build())
          .build(),
        "test_2" to ExceptionConfiguration.newBuilder()
          .setExpirationDate(Date.newBuilder().setYear(cal.get(Calendar.YEAR) + 1).setMonth(12).setDay(31).build())
          .setExceptionFilter(
            ExceptionFilter.newBuilder()
              .setSignature(ex2Sig)
              .build())
          .setAction(
            ExceptionAction.newBuilder()
              .setRequiresConfirmation(true)
              .setIncludeExceptionMessage(true)
              .build())
          .build()
      )
    }
  }

  private var fixtureDisposable = object : Disposable {
    override fun dispose() = Unit
  }

  lateinit var service: ExceptionDataCollection

  override fun setUp() {
    super.setUp()
    ApplicationManager.getApplication().replaceService(
      ExceptionDataConfiguration::class.java, ExceptionDataConfigurationMock(),
      fixtureDisposable)
    ApplicationManager.getApplication().replaceService(
      ExceptionDataCollection::class.java, ExceptionDataCollection(),
      fixtureDisposable)
    service = ExceptionDataCollection.getInstance()
  }

  override fun tearDown() {
    Disposer.dispose(fixtureDisposable)
    super.tearDown()
  }

  fun testGetExceptionUploadFields() {
    val uploadFieldsEx1 = service.getExceptionUploadFields(ex1, forceExceptionMessage = false, includeLogs = false)
    assertThat(uploadFieldsEx1.description.replace("\r\n", "\n"), equalTo(ex1DescriptionWithoutMessage))
    assertThat(uploadFieldsEx1.logs.size, `is`(0))
    val uploadFieldsEx2 = service.getExceptionUploadFields(ex2, forceExceptionMessage = false, includeLogs = false)
    assertThat(uploadFieldsEx2.description.replace("\r\n", "\n"), equalTo(ex2Description))
    assertThat(uploadFieldsEx2.logs.size, `is`(0))
  }

  fun testCalculateSignature() {
    val sig1 = service.calculateSignature(ex1)
    assertThat(sig1, equalTo(ex1Sig))

    val sig2 = service.calculateSignature(ex2)
    assertThat(sig2, equalTo(ex2Sig))
  }

  fun testGetDescription() {
    val messageRemoved = service.getDescription(ex1, stripMessage = true, includeFullStack = true)
    assertThat(messageRemoved.replace("\r\n", "\n"), equalTo(ex1DescriptionWithoutMessage))
    val withMessage = service.getDescription(ex1, stripMessage = false, includeFullStack = true)
    assertThat(withMessage.replace("\r\n", "\n"), equalTo(ex1Description))
  }

  fun testRequiresConfirmation() {
    assertThat(service.requiresConfirmation(ex1), `is`(false))
    assertThat(service.requiresConfirmation(ex2), `is`(true))
  }

  companion object {
    const val ex1Description =
      "java.lang.Exception: exception text 123456789\n" +
      "\tat com.intellij.diagnostic.DropAnErrorAction.actionPerformed(dropErrorActions.kt:25)\n"
    const val ex1DescriptionWithoutMessage =
      "java.lang.Exception: <elided>\n" +
      "\tat com.intellij.diagnostic.DropAnErrorAction.actionPerformed(dropErrorActions.kt:25)\n"
    val ex1 = ExceptionTestUtils.createExceptionFromDesc(ex1Description)
    const val ex1Sig = "java.lang.Exception at com.intellij.diagnostic.DropAnErrorAction.actionPerformed-19432f88"

    const val ex2Description =
      "java.lang.Exception: random exception text -7184021398473263170\n" +
      "\tat com.intellij.diagnostic.DropAnErrorAction.actionPerformed(dropErrorActions.kt:25)\n" +
      "\tat com.intellij.openapi.actionSystem.ex.ActionUtil.lambda\$performActionDumbAware\$5(ActionUtil.java:273)\n" +
      "\tat com.intellij.util.SlowOperations.lambda\$allowSlowOperations\$0(SlowOperations.java:77)\n" +
      "\tat com.intellij.util.SlowOperations.allowSlowOperations(SlowOperations.java:68)\n" +
      "\tat com.intellij.util.SlowOperations.allowSlowOperations(SlowOperations.java:76)\n" +
      "\tat com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAware(ActionUtil.java:273)\n" +
      "\tat com.intellij.ide.actions.GotoActionAction.lambda\$performAction\$2(GotoActionAction.java:108)\n" +
      "\tat java.desktop/java.awt.EventDispatchThread.run(EventDispatchThread.java:90)\n"
    val ex2 = ExceptionTestUtils.createExceptionFromDesc(ex2Description)
    const val ex2Sig = "java.lang.Exception at com.intellij.diagnostic.DropAnErrorAction.actionPerformed-2f166b9f"
  }
}