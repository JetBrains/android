package com.android.tools.idea.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ChangeTrackerCachedValueTest {
  @Test
  fun testChangeTracker() = runBlocking {
    val counter = AtomicInteger(0)
    val tracker = AtomicLong(0)
    val value = ChangeTrackerCachedValue.strongReference<String>()
    val provider = {
      counter.getAndIncrement().toString()
    }
    val trackerProvider = { tracker.get() }
    assertEquals("0", ChangeTrackerCachedValue.get(value, provider, trackerProvider))
    assertEquals("0", ChangeTrackerCachedValue.get(value, provider, trackerProvider))
    tracker.incrementAndGet() // Invalidate the value
    assertEquals("1", ChangeTrackerCachedValue.get(value, provider, trackerProvider))
  }

  @Test
  fun testNeverChanging() = runBlocking {
    val counter = AtomicInteger(0)
    val value = ChangeTrackerCachedValue.strongReference<String>()
    val provider = {
      counter.getAndIncrement().toString()
    }
    assertEquals("0", ChangeTrackerCachedValue.get(value, provider, ChangeTracker.NEVER_CHANGE))
    assertEquals("0", ChangeTrackerCachedValue.get(value, provider, ChangeTracker.NEVER_CHANGE))
    assertEquals("0", ChangeTrackerCachedValue.get(value, provider, ChangeTracker.NEVER_CHANGE))
  }

  @Test
  fun testEverChanging() = runBlocking {
    val counter = AtomicInteger(0)
    val value = ChangeTrackerCachedValue.strongReference<String>()
    val provider = suspend {
      delay(350) // Simulate a low provider
      counter.getAndIncrement().toString()
    }
    assertEquals("0", ChangeTrackerCachedValue.get(value, provider, ChangeTracker.EVER_CHANGING))
    assertEquals("1", ChangeTrackerCachedValue.get(value, provider, ChangeTracker.EVER_CHANGING))
    assertEquals("2", ChangeTrackerCachedValue.get(value, provider, ChangeTracker.EVER_CHANGING))
  }
}