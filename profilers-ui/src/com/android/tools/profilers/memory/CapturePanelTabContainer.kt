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
package com.android.tools.profilers.memory

import javax.swing.JComponent

/**
 * Interface for components that provide the body for the {@link CapturePanelUi} tabs.
 * When added to a tab the component is set as the content. When the tab is selected
 * the selected method is triggered. Likewise when deselected the deselected method is
 * triggered. This allows components to react to the current state when being activated.
 */
interface CapturePanelTabContainer {

  /**
   * Body component to be added to the TabPane.
   */
  val component: JComponent

  /**
   * When a tab is selected/deselected and visibility state is about to change.
   */
  fun onSelectionChanged(selected: Boolean) {}
}