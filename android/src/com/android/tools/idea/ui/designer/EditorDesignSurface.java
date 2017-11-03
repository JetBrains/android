/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.ui.designer;

import com.android.tools.idea.configurations.Configuration;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * A layout editor design surface.
 */
public abstract class EditorDesignSurface extends JPanel {
  public EditorDesignSurface(LayoutManager layout) {
    super(layout);
  }

  @Nullable
  abstract public Configuration getConfiguration();

  /**
   * When called, this will trigger a refresh of the layout. Only call this method if the action is initiated by the user.
   */
  abstract public void forceUserRequestedRefresh();
}
