/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.componenttree.api

import javax.swing.Icon
import javax.swing.JComponent

/**
 * A badge is an optional icon that can be shown to the right of a tree item.
 */
interface BadgeItem {

  /**
   * Return the icon for the specified [item].
   */
  fun getIcon(item: Any): Icon?

  /**
   * Return the icon while hovering over the specified [item].
   *
   * (The icon from getIcon will be hidden while this hover icon is displayed.)
   */
  fun getHoverIcon(item: Any): Icon? = null

  /**
   * Return the tooltip text for the icon of the specified [item].
   */
  fun getTooltipText(item: Any?): String

  /**
   * Display a divider on the left.
   */
  val leftDivider: Boolean
    get() = false

  /**
   * Perform this action when the icon is clicked on.
   */
  fun performAction(item: Any)

  /**
   * Show an (optional) popup after a right click on the icon.
   */
  fun showPopup(item: Any, component: JComponent, x: Int, y: Int)
}
