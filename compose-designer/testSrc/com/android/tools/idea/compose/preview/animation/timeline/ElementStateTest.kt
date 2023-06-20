package com.android.tools.idea.compose.preview.animation.timeline

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test

class ElementStateTest {
  @Test
  fun `value callback is invoked`() {
    val state = ElementState()
    assertEquals(0, state.valueOffset)
    var callbackCalls = 0
    state.addValueOffsetListener { callbackCalls++ }
    state.valueOffset = 10
    assertEquals(1, callbackCalls)
  }

  @Test
  fun `freeze callback is invoked`() {
    val state = ElementState()
    assertFalse { state.frozen }
    var callbackCalls = 0
    state.addFreezeListener { callbackCalls++ }
    state.frozen = true
    assertEquals(1, callbackCalls)
  }

  @Test
  fun `expanded callback is invoked`() {
    val state = ElementState()
    assertFalse { state.expanded }
    var callbackCalls = 0
    state.addExpandedListener { callbackCalls++ }
    state.expanded = true
    assertEquals(1, callbackCalls)
  }
}
