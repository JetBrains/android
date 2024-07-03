/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.AccessorySelectionListener;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import com.intellij.ui.JBColor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * This this provides a Mock Motion attribute Panel Should not be displayed.
 * Todo Remove after refactor
 */
public class MotionAttributePanel implements AccessoryPanelInterface {
  private static final boolean DEBUG = false;
  static Color ourSecondaryPanelBackground = new JBColor(0xfcfcfc, 0x313435);
  static Color ourMainBackground = ourSecondaryPanelBackground;

  private MotionAccessoryPanel myMotionAccessoryPanel;
  private final ViewGroupHandler.AccessoryPanelVisibility myVisibilityCallback;
  private final NlComponent myMotionLayout;
  private JPanel myPanel;
  private JEditorPane myEditorPane;
  private NlComponent mySelection;
  public NlModel myNlModel;
  JPanel myAttributeGroups;

  public MotionAttributePanel(@NotNull NlComponent parent, @NotNull ViewGroupHandler.AccessoryPanelVisibility visibility) {
    myMotionLayout = parent;
    myVisibilityCallback = visibility;
    if (DEBUG) {
      Debug.log("MotionAttributePanel");
    }
  }

  @NotNull
  @Override
  public JPanel getPanel() {
    if (myPanel == null) {
      myPanel = createPanel(AccessoryPanel.Type.EAST_PANEL);
    }
    if (DEBUG) {
      Debug.log("getPanel");
    }
    return myPanel;
  }

  @NotNull
  @Override
  public JPanel createPanel(AccessoryPanel.Type type) {
    JPanel panel = new JPanel(new BorderLayout()) {
      {
        setPreferredSize(new Dimension(250, 250));
        myEditorPane = new JEditorPane();
        add(myEditorPane);
      }
    };
    Debug.log("createPanel");

    return panel;
  }

  @Override
  public void updateAccessoryPanelWithSelection(@NotNull AccessoryPanel.Type type, @NotNull List<NlComponent> selection) {
    myEditorPane.setText(myEditorPane.getText() + "\n" + type + " " + ((selection.size() > 0) ? selection.get(0) : "null"));
    if (DEBUG) {
      Debug.log("updateAccessoryPanelWithSelection");
    }
  }

  @Override
  public void deactivate() {
    if (DEBUG) {
      Debug.log("deactivate");
    }
  }

  @Override
  public void updateAfterModelDerivedDataChanged() {
    if (DEBUG) {
      Debug.log("updateAfterModelDerivedDataChanged");
    }
  }

  @Override
  public void requestSelection() {
  }

  @Override
  public void addListener(@NotNull AccessorySelectionListener listener) {
  }

  @Override
  public void removeListener(@NotNull AccessorySelectionListener listener) {
  }
}
