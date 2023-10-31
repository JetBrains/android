/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.componenttree.util

import com.google.common.truth.Truth.assertThat
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes

class Fragment(val text: String, val attr: SimpleTextAttributes) {
  fun check(expected: Fragment, name: String = "Fragment") {
    assertThat(text).named(name).isEqualTo(expected.text)
    assertThat(attr.fgColor).named("$name with: Font Color").isEqualTo(expected.attr.fgColor)
    assertThat(attr.fontStyle).named("$name with: Font Style").isEqualTo(expected.attr.fontStyle)
    assertThat(attr.style).named("$name with: Style").isEqualTo(expected.attr.style)
  }
}

val SimpleColoredComponent.fragments: List<Fragment>
  get() {
    val fragments = mutableListOf<Fragment>()
    val iterator = iterator()
    for (part in iterator) {
      fragments.add(Fragment(part, iterator.textAttributes))
    }
    return fragments
  }
