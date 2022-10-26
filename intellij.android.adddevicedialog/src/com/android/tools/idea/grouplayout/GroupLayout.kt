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
package com.android.tools.idea.grouplayout

import java.awt.Container
import java.awt.LayoutManager

internal class GroupLayout private constructor(host: Container) {
  internal companion object {
    internal fun groupLayout(host: Container, init: GroupLayout.() -> Unit): LayoutManager = GroupLayout(host).apply(init).layout
  }

  private val layout = javax.swing.GroupLayout(host)

  internal fun parallelGroup(init: Group.() -> Unit) = Group(layout.createParallelGroup()).apply(init).group
  internal fun sequentialGroup(init: Group.() -> Unit) = Group(layout.createSequentialGroup()).apply(init).group

  internal fun horizontalGroup(horizontalGroup: () -> javax.swing.GroupLayout.Group) {
    layout.setHorizontalGroup(horizontalGroup())
  }

  internal fun verticalGroup(verticalGroup: () -> javax.swing.GroupLayout.Group) {
    layout.setVerticalGroup(verticalGroup())
  }
}
