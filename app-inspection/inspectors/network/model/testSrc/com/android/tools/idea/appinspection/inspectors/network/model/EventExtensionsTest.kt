package com.android.tools.idea.appinspection.inspectors.network.model

import com.android.tools.adtui.model.Range
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.Event

/** Tests for EventExtensions */
class EventExtensionsTest {

  @Test
  fun findStartIndex() {
    assertThat(eventsOf(1, 2, 3, 3, 3, 4, 5, 6).findStartIndex(3)).isEqualTo(2)
    assertThat(eventsOf(3, 3, 3, 4, 5, 6).findStartIndex(3)).isEqualTo(0)
    assertThat(eventsOf(1, 2, 3, 3, 3).findStartIndex(3)).isEqualTo(2)
    assertThat(eventsOf(1, 2, 4, 5).findStartIndex(3)).isEqualTo(2)
    assertThat(eventsOf(4, 5).findStartIndex(3)).isEqualTo(0)
    assertThat(eventsOf(1, 2).findStartIndex(3)).isEqualTo(2)
  }

  @Test
  fun findEndIndex() {
    assertThat(eventsOf(1, 2, 3, 3, 3, 4, 5, 6).findEndIndex(3)).isEqualTo(4)
    assertThat(eventsOf(3, 3, 3, 4, 5, 6).findEndIndex(3)).isEqualTo(2)
    assertThat(eventsOf(1, 2, 3, 3, 3).findEndIndex(3)).isEqualTo(4)
    assertThat(eventsOf(1, 2, 4, 5).findEndIndex(3)).isEqualTo(1)
    assertThat(eventsOf(4, 5).findEndIndex(3)).isEqualTo(-1)
    assertThat(eventsOf(1, 2).findEndIndex(3)).isEqualTo(1)
  }

  @Test
  fun searchRange() {
    val events = eventsOf(3, 5, 5, 5, 7)

    assertThat(events.searchRange(3..7)).containsExactly(3, 5, 5, 5, 7)
    assertThat(events.searchRange(5..7)).containsExactly(5, 5, 5, 7)
    assertThat(events.searchRange(3..5)).containsExactly(3, 5, 5, 5)
    assertThat(events.searchRange(5..5)).containsExactly(5, 5, 5)
    assertThat(events.searchRange(4..6)).containsExactly(5, 5, 5)
    assertThat(events.searchRange(2..8)).containsExactly(3, 5, 5, 5, 7)
    assertThat(events.searchRange(1..2)).containsExactly()
    assertThat(events.searchRange(8..9)).containsExactly()
  }
}

private fun eventsOf(vararg timestamps: Long) =
  timestamps.map { Event.newBuilder().setTimestamp(MICROSECONDS.toNanos(it)).build() }

/** Convenience method that handles Micro <-> Nano & Int <-> Long conversions */
private fun List<Event>.searchRange(range: IntRange) =
  searchRange(Range(range.first.toDouble(), range.last.toDouble())).map {
    NANOSECONDS.toMicros(it.timestamp).toInt()
  }

/** Convenience method that handles Micro <-> Nano & Int <-> Long conversions */
private fun List<Event>.findStartIndex(minUs: Int): Int =
  findStartIndex(MICROSECONDS.toNanos(minUs.toLong()))

/** Convenience method that handles Micro <-> Nano & Int <-> Long conversions */
private fun List<Event>.findEndIndex(minUs: Int): Int =
  findEndIndex(MICROSECONDS.toNanos(minUs.toLong()))
