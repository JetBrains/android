/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.intellij.ui.components.JBPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import org.jetbrains.annotations.NotNull;

public final class DetailsPanelPanel2 extends JBPanel<DetailsPanelPanel2> {
  public static final boolean ENABLED = false;

  public DetailsPanelPanel2(@NotNull Component component) {
    super(new BorderLayout());
    add(component);
  }
}
