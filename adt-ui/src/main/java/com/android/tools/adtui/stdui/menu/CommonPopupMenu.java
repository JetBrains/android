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
package com.android.tools.adtui.stdui.menu;

import javax.swing.*;

/**
 * TODO currently this class does nothing more than changing colors and borders.
 * The reason we need this is because {@link JMenu} does not expose its internal popup menu used for showing sub-menus, and thus there is no
 * way for us to control its style. The current solution essentially duplicates the {@link JMenu} implementation into {@link CommonMenu},
 * changing the internal logic to instantiate this {@link CommonPopupMenu} instead, where we can control the style. A better solution might
 * be to go through the laf system instead, which would eliminate the need to duplicate/subclass the entire menu chain.
 */
public class CommonPopupMenu extends JPopupMenu {
  @Override
  public void updateUI() {
    super.updateUI();
    setUI(new CommonPopupMenuUI());
  }
}
