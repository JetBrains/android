/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.profilers

import com.intellij.testFramework.TestActionEvent

/**
 * Helper function to simulate selecting an item from a [ProfilerDropDownComponent].
 */
fun <T> selectItem(component: ProfilerDropDownComponent<T>, itemToSelect: T) {
  // Get the underlying dropdown action, which is exposed for testing.
  val dropdownAction = component.dropDownAction
  val testEvent = TestActionEvent.createTestEvent()
  // Call updateActions to populate the dropdown's children.
  dropdownAction.updateActions(testEvent.dataContext)
  // Find the specific child action for the item we want to select and perform it.
  val actionToPerform = dropdownAction.getChildren(testEvent).first { it.templateText == itemToSelect.toString() }
  actionToPerform.actionPerformed(testEvent)
}