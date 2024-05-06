package com.android.tools.idea

import com.intellij.testFramework.ProjectRule
import org.jetbrains.kotlin.backend.common.push
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun formatFullVersion(
  majorVersion: String = "2023",
  minorVersion: String = "1",
  microVersion: String = "1",
  channel: String = "Dev",
): String = "Iguana | ${majorVersion}.${minorVersion}.${microVersion} $channel"

class IdeChannelTest {
  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun `verify channels from version`() {
    assertEquals(IdeChannel.Channel.DEV, IdeChannel.getChannel { formatFullVersion(channel = "dEv") })
    assertEquals(IdeChannel.Channel.DEV, IdeChannel.getChannel { formatFullVersion() })
    assertEquals(IdeChannel.Channel.STABLE, IdeChannel.getChannel())
    // Unit tests return DEV
    assertEquals(IdeChannel.Channel.CANARY, IdeChannel.getChannel{ formatFullVersion(channel = "Canary") })
    assertEquals(IdeChannel.Channel.BETA, IdeChannel.getChannel{ formatFullVersion(channel = "Beta") })
    assertEquals(IdeChannel.Channel.RC, IdeChannel.getChannel{ formatFullVersion(channel = "RC") })
    assertEquals(IdeChannel.Channel.STABLE, IdeChannel.getChannel{ formatFullVersion(channel = "Stable") })
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
      assertTrue(channel.isAtMost(channel))
      assertTrue(channel.isAtLeast(channel))
      alreadyVisited.forEach { lessStableChannel ->
        assertTrue(lessStableChannel.isAtMost(channel))
      }
      alreadyVisited.forEach { lessStableChannel ->
        assertFalse(lessStableChannel.isAtLeast(channel))
      }
      alreadyVisited.push(channel)
    }
  }
}