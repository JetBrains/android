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

/** This is still incubating. Do not use this. */
class GroupLayout private constructor(host: Container) {
  companion object {
    fun groupLayout(host: Container, init: GroupLayout.() -> Unit): LayoutManager = GroupLayout(host).apply(init).layout
  }

  private val layout = javax.swing.GroupLayout(host)

  fun sequentialGroup(init: SequentialGroup.() -> Unit): javax.swing.GroupLayout.Group =
    SequentialGroup(layout.createSequentialGroup(), this).apply(init).group

  fun parallelGroup(init: Group.() -> Unit) = Group(layout.createParallelGroup(), this).apply(init).group

  fun horizontalGroup(horizontalGroup: () -> javax.swing.GroupLayout.Group) {
    layout.setHorizontalGroup(horizontalGroup())
  }

  fun verticalGroup(verticalGroup: () -> javax.swing.GroupLayout.Group) {
    layout.setVerticalGroup(verticalGroup())
  }
}
