package com.android.tools.idea.logcat.files

import com.android.testutils.TestResources
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import com.android.tools.idea.testing.ApplicationServiceRule
import com.google.common.truth.Truth.assertThat
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RuleChain
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

private val gson = GsonBuilder().setPrettyPrinting().create()

private val zoneId = ZoneId.of("Asia/Yerevan")

/** Tests for [LogcatFileParser] */
class LogcatFileParserTest {
  private val androidLogcatSettings = AndroidLogcatSettings()

  @get:Rule
  val rule =
    RuleChain(
      ApplicationRule(),
      ApplicationServiceRule(AndroidLogcatSettings::class.java, androidLogcatSettings),
    )

  @Test
  fun parseLogcatFile_threadTime() {
    val path = getResourcePath("/logcatFiles/logcat-threadtime-api-25.txt")

    val data = LogcatFileIo(zoneId).readLogcat(path)

    assertThat(data.metadata).isNull()
    val expectedMessages = loadExpectedLogcat("/logcatFiles/logcat-threadtime-api-25-expected.txt")
    assertThat(data.logcatMessages.removeTimestamp())
      .containsExactlyElementsIn(expectedMessages.removeTimestamp())
      .inOrder()
  }

  @Test
  fun parseLogcatFile_firebase() {
    val path = getResourcePath("/logcatFiles/logcat-firebase.txt")

    val data = LogcatFileIo(zoneId).readLogcat(path)

    assertThat(data.metadata).isNull()
    val expectedMessages = loadExpectedLogcat("/logcatFiles/logcat-firebase-expected.txt")
    assertThat(data.logcatMessages.removeTimestamp())
      .containsExactlyElementsIn(expectedMessages.removeTimestamp())
      .inOrder()
  }

  @Test
  fun parseLogcatFile_bufferSize() {
    androidLogcatSettings.bufferSize = 1000
    val path = getResourcePath("/logcatFiles/logcat-threadtime-api-25.txt")

    val data = LogcatFileIo(zoneId).readLogcat(path)

    assertThat(data.metadata).isNull()
    val expectedMessages = loadExpectedLogcat("/logcatFiles/logcat-threadtime-api-25-expected.txt")
    assertThat(data.logcatMessages.removeTimestamp())
      .containsExactlyElementsIn(expectedMessages.takeLast(3).removeTimestamp())
      .inOrder()
  }
}

private fun List<LogcatMessage>.removeTimestamp() = map {
  it.copy(header = it.header.copy(timestamp = Instant.MIN))
}

private fun loadExpectedLogcat(filename: String): List<LogcatMessage> {
  return TestResources.getFile(filename).reader().use {
    gson.fromJson(it, object : TypeToken<List<LogcatMessage>>() {})
  }
}

private fun getResourcePath(filename: String): Path {
  return TestResources.getFile(filename).toPath()
}
