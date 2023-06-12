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

import java.awt.Component

open class Group internal constructor(internal open val group: javax.swing.GroupLayout.Group, private val layout: GroupLayout) {
  fun sequentialGroup(init: SequentialGroup.() -> Unit) {
    group.addGroup(layout.sequentialGroup(init))
  }

  fun parallelGroup(init: Group.() -> Unit) {
    group.addGroup(layout.parallelGroup(init))
  }

  fun component(component: Component,
                min: Int = javax.swing.GroupLayout.DEFAULT_SIZE,
                pref: Int = javax.swing.GroupLayout.DEFAULT_SIZE,
                max: Int = javax.swing.GroupLayout.DEFAULT_SIZE) {
    group.addComponent(component, min, pref, max)
  }
}
