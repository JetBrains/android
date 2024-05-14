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
package com.android.tools.asdriver.inject

import javax.accessibility.AccessibleAction
import javax.accessibility.AccessibleContext
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JLabel

/**
 * Wrapper class for Compose button to allow trampolining from a Jbutton
 * to an AccessibleContext
 */
class ComposeJButtonWrapper(con: AccessibleContext) : JButton() {
  private val context: AccessibleContext = con
  private val accessibleAction: AccessibleAction = con.accessibleAction
  private val accessibleName: String = con.accessibleName

  override fun getAccessibleContext(): AccessibleContext {
    return context
  }

  override fun doClick() {
    accessibleAction.doAccessibleAction(0)
  }

  override fun getAction(): Action? {
    return null
  }

  override fun getText(): String {
    return accessibleName
  }
}

/**
 * Wrapper class for Compose label to allow trampolining from a JLabel
 * to an AccessibleContext
 */
class ComposeJLabelWrapper(con: AccessibleContext) : JLabel() {
  private val context: AccessibleContext = con
  private val accessibleName: String = con.accessibleName

  override fun getAccessibleContext(): AccessibleContext {
    return context
  }

  override fun getText(): String {
    return accessibleName
  }

}