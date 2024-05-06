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
package com.android.tools.adtui.visualtests

import com.android.tools.adtui.HorizontalSpinner
import com.android.tools.adtui.model.updater.Updatable
import javax.swing.JPanel

class HorizontalSpinnerVisualTest : VisualTest() {
  override fun createModelList(): MutableList<Updatable> = mutableListOf()

  override fun populateUi(panel: JPanel) {
    val listElementSelector = HorizontalSpinner.forStrings(arrayOf("String 1", "String 2", "String 3"))
    panel.add(listElementSelector);
  }

  override fun getName(): String = HorizontalSpinner::class.simpleName!!
}