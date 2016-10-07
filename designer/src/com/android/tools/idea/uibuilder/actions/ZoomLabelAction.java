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
package com.android.tools.idea.uibuilder.actions;

import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Action which shows a zoom percentage
 */
public class ZoomLabelAction extends AnAction implements CustomComponentAction {
  @NotNull private final DesignSurface mySurface;

  public ZoomLabelAction(@NotNull DesignSurface surface) {
    mySurface = surface;
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription("Current Zoom Level");
    updatePresentation(presentation);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    updatePresentation(e.getPresentation());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    // No-op: only label matters
  }

  private void updatePresentation(Presentation presentation) {
    double scale = mySurface.getScale();
    if (SystemInfo.isMac && UIUtil.isRetina()) {
      scale *= 2;
    }

    String label = String.format("%d%% ", (int)(100 * scale));
    presentation.setText(label);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    JBLabel label = new JBLabel() {
      private PropertyChangeListener myPresentationSyncer;
      private Presentation myPresentation = presentation;

      @Override
      public void addNotify() {
        super.addNotify();
        if (myPresentationSyncer == null) {
          myPresentationSyncer = new PresentationSyncer();
          myPresentation.addPropertyChangeListener(myPresentationSyncer);
        }
        setText(myPresentation.getText());
      }

      @Override
      public void removeNotify() {
        if (myPresentationSyncer != null) {
          myPresentation.removePropertyChangeListener(myPresentationSyncer);
          myPresentationSyncer = null;
        }
        super.removeNotify();
      }

      class PresentationSyncer implements PropertyChangeListener {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          String propertyName = evt.getPropertyName();
          if (Presentation.PROP_TEXT.equals(propertyName)) {
            setText((String)evt.getNewValue());
            invalidate();
            repaint();
          }
        }
      }
    };
    label.setFont(UIUtil.getToolTipFont());
    return label;
  }
}
