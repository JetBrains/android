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
package com.android.tools.idea.uibuilder.mockup;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 *
 */
public class MockupEditor {
  public static final String TITLE = "Mockup Editor";
  private static final double RELATIVE_SIZE_TO_SOURCE = 0.90;
  private static FrameWrapper ourPopupInstance = null;

  private JTextField myViewTypeTextField;
  private TextFieldWithBrowseButton myFileName;
  private JPanel myCenterPanel;
  private JTextField myTextField1;
  private JPanel myContentPane;
  private JButton myCloseButton;


  public MockupEditor() {
    myCloseButton.addActionListener(e -> {
      Disposer.dispose(ourPopupInstance);
      ourPopupInstance = null;
    });
  }

  /**
   * Create a popup showing the tools to edit the mockup of the selected component
   */
  public static void create(ScreenView screenView, @Nullable NlComponent component) {
    // Close any pop-up already opened
    if (ourPopupInstance != null) {
      Disposer.dispose(ourPopupInstance);
      ourPopupInstance = null;
    }

    // Do not show the popup if nothing is selected
    if (component == null) {
      return;
    }
    final Mockup mockup = Mockup.create(component, true);
    if (mockup == null) {
      return;
    }

    final DesignSurface designSurface = screenView.getSurface();
    final MockupEditor mockupEditorPopup = new MockupEditor();
    Component rootPane = SwingUtilities.getRoot(designSurface);
    final Dimension minSize = new Dimension((int)Math.round(rootPane.getWidth() * RELATIVE_SIZE_TO_SOURCE),
                                            (int)Math.round(rootPane.getHeight() * RELATIVE_SIZE_TO_SOURCE));

    FrameWrapper frame = new FrameWrapper(designSurface.getProject());
    frame.setTitle(TITLE);
    frame.setComponent(mockupEditorPopup.myContentPane);
    frame.setSize(minSize);

    Point point = new Point(
      (int)Math.round(rootPane.getX() + (rootPane.getWidth()) / 2 - minSize.getWidth() / 2),
      (int)Math.round(rootPane.getY() + (rootPane.getHeight()) / 2 - minSize.getHeight() / 2));

    frame.setLocation(point);
    frame.getFrame().setSize(minSize);
    frame.show();
    ourPopupInstance = frame;
  }
}
