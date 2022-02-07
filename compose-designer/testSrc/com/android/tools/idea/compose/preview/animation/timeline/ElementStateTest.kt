package com.android.tools.idea.compose.preview.animation.timeline

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
  fun `locked callback is invoked`() {
    val state = ElementState()
    assertFalse { state.locked }
    var callbackCalls = 0
    state.addLockedListener { callbackCalls++ }
    state.locked = true
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