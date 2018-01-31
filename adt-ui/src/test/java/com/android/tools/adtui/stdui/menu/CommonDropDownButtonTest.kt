/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.stdui.menu

import com.android.tools.adtui.model.stdui.CommonAction
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.beans.PropertyChangeListener
import java.util.*

@RunWith(JUnit4::class)
class CommonDropDownButtonTest {

  @Test
  fun testProperyChangedListeners() {
    val parent = CommonAction("Parent", null)
    val child1 = CommonAction("Child1", null)
    val child2 = CommonAction("Child2", null)
    val grandChild1 = CommonAction("Grandchild1", null)
    parent.addChildrenActions(child1, child2)
    child1.addChildrenActions(grandChild1)

    var actions = Arrays.asList(parent, child1, child2, grandChild1)
    for (action in actions) {
      assertThat<PropertyChangeListener>(action.propertyChangeListeners).asList().isEmpty()
    }

    // Listeners should be hooked up after creating the dropdown.
    val dropdown = CommonDropDownButton(parent)
    for (action in actions) {
      assertThat<PropertyChangeListener>(action.propertyChangeListeners).asList().containsExactly(dropdown)
    }

    // Modifying the first child should clear listeners on grandChild1
    child1.clear()
    assertThat<PropertyChangeListener>(grandChild1.propertyChangeListeners).asList().isEmpty()

    // Modifying the second child should add listeners for grandChild2
    val grandChild2 = CommonAction("Grandchild2", null)
    child2.addChildrenActions(grandChild2)
    actions = Arrays.asList(parent, child1, child2, grandChild2)
    for (action in actions) {
      assertThat<PropertyChangeListener>(action.propertyChangeListeners).asList().containsExactly(dropdown)
    }
  }
}