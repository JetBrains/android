package com.android.tools.idea.uibuilder.property

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LazyCachedValueTest {
  @Test
  fun `first invocation returns initial value`() = runBlocking {
    val valueLoaded = CompletableDeferred<Int>()
    val lazyValue =
      LazyCachedValue(
        this,
        {
          delay(100) // Simulate slow loading
          3
        },
        { valueLoaded.complete(it) },
        -1,
      )
    assertEquals(-1, lazyValue.getCachedValueOrUpdate())
    valueLoaded.await()
    assertEquals(3, lazyValue.getCachedValueOrUpdate())
    assertEquals(3, lazyValue.getCachedValueOrUpdate())
  }

  @Test
  fun `getValue triggers cache update`() = runBlocking {
    val valueLoaded = CompletableDeferred<Int>()
    val lazyValue =
      LazyCachedValue(
        this,
        {
          delay(100) // Simulate slow loading
          3
        },
        { valueLoaded.complete(it) },
        -1,
      )
    assertEquals(3, lazyValue.getValue())
    valueLoaded.await()
    assertEquals(3, lazyValue.getCachedValueOrUpdate())
  }
}
