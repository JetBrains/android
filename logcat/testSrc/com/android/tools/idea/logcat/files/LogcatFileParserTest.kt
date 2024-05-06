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
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

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
    val path = getResourcePathWithExpectedTimestamp("/logcatFiles/logcat-threadtime-api-25.txt")

    val data = LogcatFileIo(zoneId).readLogcat(path)

    assertThat(data.metadata).isNull()
    val expectedMessages = loadExpectedLogcat("/logcatFiles/logcat-threadtime-api-25-expected.txt")
    assertThat(data.logcatMessages).containsExactlyElementsIn(expectedMessages).inOrder()
  }

  @Test
  fun parseLogcatFile_firebase() {
    val path = getResourcePathWithExpectedTimestamp("/logcatFiles/logcat-firebase.txt")

    val data = LogcatFileIo(zoneId).readLogcat(path)

    assertThat(data.metadata).isNull()
    val expectedMessages = loadExpectedLogcat("/logcatFiles/logcat-firebase-expected.txt")
    assertThat(data.logcatMessages).containsExactlyElementsIn(expectedMessages).inOrder()
  }

  @Test
  fun parseLogcatFile_bufferSize() {
    androidLogcatSettings.bufferSize = 1000
    val path = getResourcePathWithExpectedTimestamp("/logcatFiles/logcat-threadtime-api-25.txt")

    val data = LogcatFileIo(zoneId).readLogcat(path)

    assertThat(data.metadata).isNull()
    val expectedMessages = loadExpectedLogcat("/logcatFiles/logcat-threadtime-api-25-expected.txt")
    assertThat(data.logcatMessages)
      .containsExactlyElementsIn(expectedMessages.takeLast(3))
      .inOrder()
  }
}

private fun loadExpectedLogcat(filename: String): List<LogcatMessage> {
  return TestResources.getFile(filename).reader().use {
    gson.fromJson(it, object : TypeToken<List<LogcatMessage>>() {})
  }
}

private fun getResourcePathWithExpectedTimestamp(filename: String): Path {
  val path = TestResources.getFile(filename).toPath()
  val time = FileTime.from(Instant.from(ZonedDateTime.of(2023, 5, 17, 16, 8, 0, 0, zoneId)))
  // For some reason, updating `creationTime` by using only the 3rd argument (which is
  // `creationTime`) does not work.
  // It does work when using only the first one (which is `lastModifiedTime`) but just in case, we
  // set them all.
  Files.getFileAttributeView(path, BasicFileAttributeView::class.java).setTimes(time, time, time)
  return path
}
