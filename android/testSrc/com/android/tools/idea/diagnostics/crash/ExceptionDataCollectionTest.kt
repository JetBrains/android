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
import com.android.tools.idea.serverflags.protos.ExceptionSeverity
import com.android.tools.idea.serverflags.protos.LogFilter
import com.android.tools.idea.serverflags.protos.MessageFilter
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.replaceService
import org.apache.log4j.LogManager
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
          .build(),
        "test_3" to ExceptionConfiguration.newBuilder()
          .setExpirationDate(Date.newBuilder().setYear(cal.get(Calendar.YEAR) + 1).setMonth(12).setDay(31).build())
          .setExceptionFilter(
            ExceptionFilter.newBuilder()
              .setSignature(ex3Sig)
              .build())
          .setAction(
            ExceptionAction.newBuilder()
              .setRequiresConfirmation(true)
              .setIncludeExceptionMessage(true)
              .setLogFilter(
                LogFilter.newBuilder()
                  .setMaxMessageCount(5)
                  .addMessageFilter(
                    MessageFilter.newBuilder()
                      .setSeverity(ExceptionSeverity.INFO)
                      .setLoggerCategory(exceptionDataCollectionTestLoggerName)
                  )
              )
              .build())
          .build(),
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
    service.unregisterLogAppenders()
    super.tearDown()
  }

  fun testGetExceptionUploadFields() {
    val uploadFieldsEx1 = service.getExceptionUploadFields(ex1, forceExceptionMessage = false, includeLogs = false)
    assertThat(uploadFieldsEx1.description.replace("\r\n", "\n")).isEqualTo(ex1DescriptionWithoutMessage)
    assertThat(uploadFieldsEx1.logs.size).isEqualTo(0)
    val uploadFieldsEx2 = service.getExceptionUploadFields(ex2, forceExceptionMessage = false, includeLogs = false)
    assertThat(uploadFieldsEx2.description.replace("\r\n", "\n")).isEqualTo(ex2Description)
    assertThat(uploadFieldsEx2.logs.size).isEqualTo(0)
  }

  fun testRegisteringAppenders() {
    val registeredAppenders = service.registeredAppenders
    assertThat(registeredAppenders).hasSize(1)
    assertThat(registeredAppenders[0].logger.name).isEqualTo("#com.android.tools.idea.diagnostics.crash.ExceptionDataCollectionTest")
  }

  fun testLogCollection() {
    //val log4jLogger = LogManager.getLogger(exceptionDataCollectionTestLoggerName)
    val logger = java.util.logging.LogManager.getLogManager().getLogger(exceptionDataCollectionTestLoggerName)
    with(exceptionDataCollectionTestLogger) {
      for (i in 1..5) {
        trace("trace message #$i")
        debug("debug message #$i")
        info("info message #$i")
        warn("warn message #$i")
        // use logger directly as
        logger.severe("severe message #$i")
      }
    }
    val exceptionUploadFields = service.getExceptionUploadFields(ex3, forceExceptionMessage = false, includeLogs = true)
    var result = exceptionUploadFields.logs["test_3"]!!
    // remove time information
    result = result.replace(Regex("^\\[[ 0-9]+\\]", RegexOption.MULTILINE), "[<time>]")
    assertThat(result.trimEnd()).isEqualTo("""
[<time>] W [sh.ExceptionDataCollectionTest] warn message #4
[<time>] S [sh.ExceptionDataCollectionTest] severe message #4
[<time>] I [sh.ExceptionDataCollectionTest] info message #5
[<time>] W [sh.ExceptionDataCollectionTest] warn message #5
[<time>] S [sh.ExceptionDataCollectionTest] severe message #5
    """.trimIndent())
  }

  fun testCalculateSignature() {
    val sig1 = service.calculateSignature(ex1)
    assertThat(sig1).isEqualTo(ex1Sig)

    val sig2 = service.calculateSignature(ex2)
    assertThat(sig2).isEqualTo(ex2Sig)

    val sig3 = service.calculateSignature(ex3)
    assertThat(sig3).isEqualTo(ex3Sig)
  }

  fun testCalculateSignatueMissingStack() {
    val sig3 = service.calculateSignature(exNoStack)
    assertThat(sig3).isEqualTo(exNoStackSig)
  }

  fun testGetDescription() {
    val messageRemoved = service.getDescription(ex1, stripMessage = true, includeFullStack = true)
    assertThat(messageRemoved.replace("\r\n", "\n")).isEqualTo(ex1DescriptionWithoutMessage)
    val withMessage = service.getDescription(ex1, stripMessage = false, includeFullStack = true)
    assertThat(withMessage.replace("\r\n", "\n")).isEqualTo(ex1Description)
  }

  fun testRequiresConfirmation() {
    assertThat(service.requiresConfirmation(ex1)).isFalse()
    assertThat(service.requiresConfirmation(ex2)).isTrue()
  }

  companion object {

    val exceptionDataCollectionTestLoggerName = "#" + ExceptionDataCollectionTest::class.java.name;
    val exceptionDataCollectionTestLogger = Logger.getInstance(exceptionDataCollectionTestLoggerName)

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

    const val exNoStackDescription =
      "java.lang.Exception: sample message\n"
    val exNoStack = ExceptionTestUtils.createExceptionFromDesc(exNoStackDescription)
    const val exNoStackSig = "MissingCrashedThreadStack"

    const val ex3Description =
      "java.lang.Exception: sample message\n" +
      "\tat com.android.SomeClass.someMethod(FileName.java:100)\n"

    val ex3 = ExceptionTestUtils.createExceptionFromDesc(ex3Description)
    val ex3Sig = "java.lang.Exception at com.android.SomeClass.someMethod-d3f18885"
  }
}