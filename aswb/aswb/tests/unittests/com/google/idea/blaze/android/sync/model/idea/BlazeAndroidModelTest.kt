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
package com.google.idea.blaze.android.sync.model.idea

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.base.BlazeTestCase
import com.google.idea.blaze.base.run.DeployedApplicationTargetStore
import com.google.idea.blaze.common.Label
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Unit tests for [BlazeAndroidModel]. */
@RunWith(JUnit4::class)
class BlazeAndroidModelTest : BlazeTestCase() {

  private val mockTargetStore = MockDeployedApplicationTargetStore()

  override fun initTest(applicationServices: Container, projectServices: Container) {
    super.initTest(applicationServices, projectServices)
    projectServices.register(DeployedApplicationTargetStore::class.java, mockTargetStore)
  }

  @Test
  fun testAllApplicationIds_returnsAllFromTargetStore() {
    mockTargetStore.trackTargetForApplication("com.example.app1", Label.of("//java/com/example:app1"))
    mockTargetStore.trackTargetForApplication("com.example.app2", Label.of("//java/com/example:app2"))

    val model =
      BlazeAndroidModel(
        project = project,
        minSdkVersion = 28,
      )

    assertThat(model.allApplicationIds).containsExactly("com.example.app1", "com.example.app2")
  }

  @Test
  fun testAllApplicationIds_whenEmpty_returnsEmptySet() {
    val model =
      BlazeAndroidModel(
        project = project,
        minSdkVersion = 28,
      )

    assertThat(model.allApplicationIds).isEmpty()
  }

  private class MockDeployedApplicationTargetStore : DeployedApplicationTargetStore {
    private val targets = mutableMapOf<String, Label>()

    override fun trackTargetForApplication(applicationId: String, target: Label) {
      targets[applicationId] = target
    }

    override fun getTargetForApplication(applicationId: String): Label? = targets[applicationId]

    override fun getAllApplicationIds(): Set<String> = targets.keys
  }
}
