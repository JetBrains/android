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
package com.android.tools.adtui.swing

import javax.swing.JComboBox

fun <E> JComboBox<E>.selectFirstMatch(text: String) {
  for (i in 0 until model.size) {
    if (model.getElementAt(i).toString() == text) {
      this.selectedIndex = i
      return
    }
  }
}

fun <E> JComboBox<E>.options(): List<E> =
  (0 until model.size).map {
    model.getElementAt(it)
  }

fun <E> JComboBox<E>.optionsAsString(): List<String> =
  options().map { it.toString() }
