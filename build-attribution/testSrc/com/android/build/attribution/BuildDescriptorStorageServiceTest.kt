/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.attribution

import com.google.common.truth.Truth
import com.intellij.testFramework.ProjectRule
import com.intellij.util.xmlb.XmlSerializer
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [BuildDescriptorStorageService]
 */
class BuildDescriptorStorageServiceTest {
  @get:Rule
  val projectRule = ProjectRule()

  private val firstDescriptor = BuildDescriptorImpl("First descriptor", 2134, 123524)
  private val secondDescriptor = BuildDescriptorImpl("Second descriptor", 213544635, 4234)

  @Test
  fun simpleAdd() {
    addToStorage(firstDescriptor)
    addToStorage(secondDescriptor)
    Truth.assertThat(BuildDescriptorStorageService.getInstance(projectRule.project).state.descriptors)
      .containsExactly(firstDescriptor, secondDescriptor)
  }

  @Test
  fun testSerialization() {
    addToStorage(firstDescriptor)
    addToStorage(secondDescriptor)
    val state = BuildDescriptorStorageService.getInstance(projectRule.project).state
    val element = XmlSerializer.serialize(state)
    val deserialized = XmlSerializer.deserialize(element, BuildDescriptorStorageService.State::class.java)
    Truth.assertThat(deserialized).isEqualTo(state)
  }

  private fun addToStorage(descriptor: BuildDescriptor) {
    BuildDescriptorStorageService.getInstance(projectRule.project).add(descriptor.buildSessionID,
                                                                       descriptor.buildFinishedTimestamp,
                                                                       descriptor.totalBuildTimeMs)
  }
}