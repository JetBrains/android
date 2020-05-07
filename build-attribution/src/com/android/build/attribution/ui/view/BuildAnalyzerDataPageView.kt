/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.attribution.ui.view

import javax.swing.JPanel

interface BuildAnalyzerDataPageView {

  /**
   * The panel containing the page's main UI.
   */
  val component: JPanel

  /**
   * The panel containing the page's additional controls.
   * Additional controls are added to the top bap of the main view next to the data page selector.
   */
  val additionalControls: JPanel
}