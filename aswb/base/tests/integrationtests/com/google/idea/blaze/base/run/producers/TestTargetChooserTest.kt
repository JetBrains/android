/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.producers

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.base.BlazeIntegrationTestCase
import com.google.idea.blaze.base.dependencies.TargetInfo
import com.google.idea.blaze.base.model.primitives.Label
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for [TestTargetChooser]. */
@RunWith(JUnit4::class)
class TestTargetChooserTest : BlazeIntegrationTestCase() {

  @After
  fun resetHook() {
    TestTargetChooser.testSelectionHook = null
  }

  @Test
  fun emptyTargetList_returnsNullWithoutInvokingHook() = runBlocking {
    var hookInvoked = false
    TestTargetChooser.testSelectionHook = {
      hookInvoked = true
      it.firstOrNull()
    }

    val result = TestTargetChooser.chooseTarget(null, emptyList())

    assertThat(result).isNull()
    assertThat(hookInvoked).isFalse()
  }

  @Test
  fun singleTarget_returnsTargetImmediatelyWithoutInvokingHook() = runBlocking {
    var hookInvoked = false
    TestTargetChooser.testSelectionHook = {
      hookInvoked = true
      it.firstOrNull()
    }
    val target = TargetInfo(Label.create("//foo:single_test"), "java_test")

    val result = TestTargetChooser.chooseTarget(null, listOf(target))

    assertThat(result).isEqualTo(target)
    assertThat(hookInvoked).isFalse()
  }

  @Test
  fun multipleTargets_invokesHookAndReturnsSelectedTarget() = runBlocking {
    val target1 = TargetInfo(Label.create("//foo:first_test"), "java_test")
    val target2 = TargetInfo(Label.create("//foo:second_test"), "java_test")
    var passedCandidates: List<TargetInfo>? = null

    TestTargetChooser.testSelectionHook = { candidates ->
      passedCandidates = candidates
      candidates[1]
    }

    val result = TestTargetChooser.chooseTarget(null, listOf(target1, target2))

    assertThat(result).isEqualTo(target2)
    assertThat(passedCandidates).containsExactly(target1, target2).inOrder()
  }

  @Test
  fun multipleTargets_userCancels_returnsNull() = runBlocking {
    val target1 = TargetInfo(Label.create("//foo:first_test"), "java_test")
    val target2 = TargetInfo(Label.create("//foo:second_test"), "java_test")

    TestTargetChooser.testSelectionHook = { null }

    val result = TestTargetChooser.chooseTarget(null, listOf(target1, target2))

    assertThat(result).isNull()
  }
}
