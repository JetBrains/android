/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wear.preview.animation

import com.android.tools.idea.wear.preview.animation.TestDynamicTypeAnimator.Unknowm
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtoAnimationTest {

  @Test
  fun testValidAnimator() {
    val validAnimator =
      TestDynamicTypeAnimator() // Create an instance that implements the interface
    // Should not throw an exception
    ProtoAnimation(validAnimator)
  }

  @Test
  fun testInvalidAnimator() {
    val invalidAnimator = String() // Create an instance that DOES NOT implement the interface
    // Should throw an IllegalArgumentException
    val exception =
      assertThrows(IllegalArgumentException::class.java) { ProtoAnimation(invalidAnimator) }
    assertEquals("Animator must implement DynamicTypeAnimator interface", exception.message)
  }

  @Test
  fun testGetType() {
    val animator = TestDynamicTypeAnimator()

    // Set up the evaluator to be an ArgbEvaluator (simulating a color animation)
    animator.typeEvaluator = TestDynamicTypeAnimator.ArgbEvaluator()
    assertEquals(ProtoAnimation.TYPE.COLOR, ProtoAnimation(animator).type)

    // Set up the evaluator to be an IntEvaluator
    animator.typeEvaluator = TestDynamicTypeAnimator.IntEvaluator()
    assertEquals(ProtoAnimation.TYPE.INT, ProtoAnimation(animator).type)

    // Set up the evaluator to be a FloatEvaluator
    animator.typeEvaluator = TestDynamicTypeAnimator.FloatEvaluator()
    assertEquals(ProtoAnimation.TYPE.FLOAT, ProtoAnimation(animator).type)

    animator.typeEvaluator = Unknowm()
    assertEquals(ProtoAnimation.TYPE.UNKNOWN, ProtoAnimation(animator).type)
  }

  @Test
  fun testGetName() {
    val animator = TestDynamicTypeAnimator()

    // Set up the evaluator to be an ArgbEvaluator
    animator.typeEvaluator = TestDynamicTypeAnimator.ArgbEvaluator()
    assertEquals("COLOR Animation", ProtoAnimation(animator).name)

    // Set up the evaluator to be an IntEvaluator
    animator.typeEvaluator = TestDynamicTypeAnimator.IntEvaluator()
    assertEquals("INT Animation", ProtoAnimation(animator).name)

    // Set up the evaluator to be a FloatEvaluator
    animator.typeEvaluator = TestDynamicTypeAnimator.FloatEvaluator()
    assertEquals("FLOAT Animation", ProtoAnimation(animator).name)

    animator.typeEvaluator = Unknowm()
    assertEquals("UNKNOWN Animation", ProtoAnimation(animator).name)
  }

  @Test
  fun testDelegateMethodCall() {
    val animator = TestDynamicTypeAnimator()
    val protoAnimation = ProtoAnimation(animator)

    // Test a method with no arguments
    val duration = protoAnimation.durationMs
    assertEquals(animator.getDurationMs(), duration)

    // Test a method with arguments
    val newTime = 500L
    protoAnimation.setTime(newTime)
    assertEquals(newTime, animator.currentTime)

    // Test a method that returns a value
    animator.setCurrentValue(123) // Set a current value
    val value = protoAnimation.value
    assertEquals(123, value)
  }

  @Test
  fun testSetFloatValues() {
    val animator = TestDynamicTypeAnimator()
    val protoAnimation = ProtoAnimation(animator)

    val values = floatArrayOf(0.2f, 0.5f, 0.8f)
    protoAnimation.setFloatValues(*values)

    assertArrayEquals(values, animator.getFloatValues(), 0.001f)
  }

  @Test
  fun testSetIntValues() {
    val animator = TestDynamicTypeAnimator()
    val protoAnimation = ProtoAnimation(animator)

    val values = intArrayOf(10, 50, 90)
    protoAnimation.setIntValues(*values)

    assertArrayEquals(values, animator.getIntValues())
  }

  @Test
  fun testIsTerminal() {
    val animator = TestDynamicTypeAnimator()
    val protoAnimation = ProtoAnimation(animator)

    animator.isTerminalInternal = false
    assertThat(protoAnimation.isTerminal).isFalse()

    animator.isTerminalInternal = true
    assertThat(protoAnimation.isTerminal).isTrue()
  }
}
