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
package com.android.tools.idea.common.property2.api

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.Icon

/**
 * Support for a property action button shown to the right of a property editor.
 *
 * A [PropertyItem] may optionally implement this interface and supply
 * actions that can be used to manage the value of the property in ways
 * the supplied editor cannot.
 */
interface ActionButtonSupport {
  /**
   * Return true if this property should show an action button.
   */
  val showActionButton: Boolean

  /**
   * Return true if the action icon should be focusable.
   *
   * If [getAction] is not null this should be true to make the
   * button accessible from the keyboard.
   */
  val actionButtonFocusable: Boolean

  /**
   * Return the icon indicating the nature of this action button.
   *
   * An implementation may throw an exception if [showActionButton] is false.
   */
  fun getActionIcon(focused: Boolean): Icon

  /**
   * Return the action to be performed when the user activates the action button.
   *
   * If the action provided is an [ActionGroup] a menu will be shown instead.
   * An implementation may return null if [showActionButton] is false or if
   * the icon is for information purposes only.
   */
  fun getAction(): AnAction?
}
