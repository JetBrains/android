/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless

import com.android.tools.idea.ui.OneTimeOverrideFocusTraversalPolicy
import com.google.common.truth.Truth
import java.awt.Component
import java.awt.Container
import java.awt.FocusTraversalPolicy
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

class OneTimeOverrideFocusTraversalPolicyTest {
  private val mockPolicy = Mockito.mock(FocusTraversalPolicy::class.java)
  private val container = Mockito.mock(Container::class.java)
  private val component1 = Mockito.mock(Component::class.java)
  private val component2 = Mockito.mock(Component::class.java)
  private val component3 = Mockito.mock(Component::class.java)

  @Before
  fun setUp() {
    // Setup a focus cycle from [component1, component2, component3, component1, ...]
    whenever(mockPolicy.getComponentAfter(container, component1)).thenReturn(component2)
    whenever(mockPolicy.getComponentAfter(container, component2)).thenReturn(component3)
    whenever(mockPolicy.getComponentAfter(container, component3)).thenReturn(component1)

    whenever(mockPolicy.getComponentBefore(container, component1)).thenReturn(component3)
    whenever(mockPolicy.getComponentBefore(container, component2)).thenReturn(component1)
    whenever(mockPolicy.getComponentBefore(container, component3)).thenReturn(component2)

    whenever(mockPolicy.getFirstComponent(container)).thenReturn(component1)

    whenever(mockPolicy.getLastComponent(container)).thenReturn(component3)

    whenever(mockPolicy.getDefaultComponent(container)).thenReturn(component1)
  }

  @Test
  fun defaultShouldNotOverrideAnything() {
    // Prepare
    val policy = OneTimeOverrideFocusTraversalPolicy(mockPolicy)

    // Act
    val after = policy.getComponentAfter(container, component1)
    val before = policy.getComponentBefore(container, component1)
    val first = policy.getFirstComponent(container)
    val last = policy.getLastComponent(container)
    val default = policy.getDefaultComponent(container)

    // Assert
    Truth.assertThat(after).isEqualTo(component2)
    Truth.assertThat(before).isEqualTo(component3)
    Truth.assertThat(first).isEqualTo(component1)
    Truth.assertThat(last).isEqualTo(component3)
    Truth.assertThat(default).isEqualTo(component1)
  }

  @Test
  fun overrideComponentAfterShouldWork() {
    // Prepare
    val policy = OneTimeOverrideFocusTraversalPolicy(mockPolicy)
    policy.oneTimeComponentAfter.set(component3)

    // Act
    val after1 = policy.getComponentAfter(container, component1)
    val after2 = policy.getComponentAfter(container, component1)

    // Assert
    Truth.assertThat(after1).isEqualTo(component3)
    Truth.assertThat(after2).isEqualTo(component2)
  }

  @Test
  fun overrideComponentBeforeShouldWork() {
    // Prepare
    val policy = OneTimeOverrideFocusTraversalPolicy(mockPolicy)
    policy.oneTimeComponentBefore.set(component3)

    // Act
    val before1 = policy.getComponentBefore(container, component2)
    val before2 = policy.getComponentBefore(container, component2)

    // Assert
    Truth.assertThat(before1).isEqualTo(component3)
    Truth.assertThat(before2).isEqualTo(component1)
  }

  @Test
  fun overrideFirstComponentShouldWork() {
    // Prepare
    val policy = OneTimeOverrideFocusTraversalPolicy(mockPolicy)
    policy.oneTimeFirstComponent.set(component3)

    // Act
    val first1 = policy.getFirstComponent(container)
    val first2 = policy.getFirstComponent(container)

    // Assert
    Truth.assertThat(first1).isEqualTo(component3)
    Truth.assertThat(first2).isEqualTo(component1)
  }

  @Test
  fun overrideLastComponentShouldWork() {
    // Prepare
    val policy = OneTimeOverrideFocusTraversalPolicy(mockPolicy)
    policy.oneTimeLastComponent.set(component1)

    // Act
    val last1 = policy.getLastComponent(container)
    val last2 = policy.getLastComponent(container)

    // Assert
    Truth.assertThat(last1).isEqualTo(component1)
    Truth.assertThat(last2).isEqualTo(component3)
  }

  @Test
  fun overrideDefaultComponentShouldWork() {
    // Prepare
    val policy = OneTimeOverrideFocusTraversalPolicy(mockPolicy)
    policy.oneTimeDefaultComponent.set(component3)

    // Act
    val default1 = policy.getDefaultComponent(container)
    val default2 = policy.getDefaultComponent(container)

    // Assert
    Truth.assertThat(default1).isEqualTo(component3)
    Truth.assertThat(default2).isEqualTo(component1)
  }
}
