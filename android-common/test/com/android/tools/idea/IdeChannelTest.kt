package com.android.tools.idea

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.BuildNumber
import com.intellij.testFramework.ProjectRule
import org.jetbrains.kotlin.backend.common.push
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

private class TestApplicationInfo(
  private val majorVersion: String = "2023",
  private val minorVersion: String = "1",
  private val microVersion: String = "1",
  private val patchVersion: String = "0",
  private val channel: String = "Dev",
) : ApplicationInfo() {
  private val buildDate = Calendar.getInstance().also {
    it.set(2023, 11, 15)
  }
  private val buildNumber = BuildNumber("code", 0, 0)

  override fun getBuild(): BuildNumber = buildNumber
  override fun getBuildDate(): Calendar = buildDate
  override fun getApiVersion(): String = "0"
  override fun getMajorVersion(): String = majorVersion
  override fun getMinorVersion(): String = minorVersion
  override fun getMicroVersion(): String = microVersion
  override fun getPatchVersion(): String = patchVersion
  override fun getVersionName(): String = "Android Studio"
  override fun getCompanyName(): String = ""
  override fun getShortCompanyName(): String = ""
  override fun getCompanyURL(): String = ""
  override fun getJetBrainsTvUrl(): String = ""
  override fun getKeyConversionUrl(): String = ""
  override fun hasHelp(): Boolean = false
  override fun hasContextHelp(): Boolean = false
  override fun getFullVersion(): String = "Iguana | ${majorVersion}.${minorVersion}.${microVersion} $channel"
  override fun getStrictVersion(): String = "${majorVersion}.${minorVersion}.${microVersion}.${patchVersion}"
  override fun getFullApplicationName(): String = ""
}

class IdeChannelTest {
  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun `verify channels from version`() {
    assertEquals(IdeChannel.Channel.DEV, IdeChannel.getChannel(TestApplicationInfo(channel = "dEv")))
    assertEquals(IdeChannel.Channel.DEV, IdeChannel.getChannel(TestApplicationInfo()))
    // Unit tests return DEV
    assertEquals(IdeChannel.Channel.DEV, IdeChannel.getChannel())
    assertEquals(IdeChannel.Channel.CANARY, IdeChannel.getChannel(TestApplicationInfo(channel = "Canary")))
    assertEquals(IdeChannel.Channel.BETA, IdeChannel.getChannel(TestApplicationInfo(channel = "Beta")))
    assertEquals(IdeChannel.Channel.RC, IdeChannel.getChannel(TestApplicationInfo(channel = "RC")))
    assertEquals(IdeChannel.Channel.STABLE, IdeChannel.getChannel(TestApplicationInfo(channel = "")))
  }

  @Test
  fun `stability checks`() {
    val sortedChannels = IdeChannel.Channel.values()
      .also {
        it.shuffle()
        it.sort()
      }
    assertArrayEquals(
      arrayOf(
        IdeChannel.Channel.DEV,
        IdeChannel.Channel.NIGHTLY,
        IdeChannel.Channel.CANARY,
        IdeChannel.Channel.BETA,
        IdeChannel.Channel.RC,
        IdeChannel.Channel.STABLE
      ),
      sortedChannels
    )

    // Verify the relative ordering using isLessStableThan and isMoreStableThan
    val alreadyVisited = mutableListOf<IdeChannel.Channel>()
    sortedChannels.forEach { channel ->
      assertFalse(channel.isLessStableThan(channel))
      assertFalse(channel.isMoreStableThan(channel))
      alreadyVisited.forEach { lessStableChannel ->
        assertTrue(lessStableChannel.isLessStableThan(channel))
      }
      alreadyVisited.forEach { lessStableChannel ->
        assertFalse(lessStableChannel.isMoreStableThan(channel))
      }
      alreadyVisited.push(channel)
    }
  }
}