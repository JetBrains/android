/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation;

import com.android.annotations.NonNull;
import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;

public class Inspector {
  public final JPanel container;
  public final SelectionModel selectionModel;

  public Inspector(SelectionModel selectionModel) {
    this.container = new JPanel(new BorderLayout());
    container.setBackground(JBColor.WHITE);
    this.selectionModel = selectionModel;
    this.selectionModel.listeners.add(new Listener<Event>() {
      @Override
      public void notify(@NonNull Event event) {
        if (event == SelectionModel.SELECTION_UPDATED) {
          Inspector.this.selectionModel.getSelection().configureInspector(Inspector.this);
        }
      }
    });
  }

  public void setInspectorComponent(JComponent c) {
    container.removeAll();
    container.add(c);
    container.revalidate();
    container.repaint();
  }
}
