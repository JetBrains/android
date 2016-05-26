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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.tools.idea.uibuilder.property.NlFlagPropertyItem;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

/**
 * The {@link NlFlagsEditor} is used to edit a {@link NlFlagPropertyItem} by displaying
 * a popup with a list of choices.
 */
public class NlFlagsEditor extends NlBaseComponentEditor implements NlComponentEditor {
  private static final int HORIZONTAL_SPACING = 2;

  private final JPanel myPanel;
  private final JTextField myValue;
  private NlFlagPropertyItem myProperty;

  public static NlFlagsEditor create() {
    return new NlFlagsEditor();
  }

  private NlFlagsEditor() {
    super(DEFAULT_LISTENER);
    AnAction action = createDisplayFlagEditorAction();
    ActionButton button = new ActionButton(action,
                                           action.getTemplatePresentation().clone(),
                                           ActionPlaces.UNKNOWN,
                                           ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE);
    myValue = new JTextField();
    myValue.setEditable(false);
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(0, HORIZONTAL_SPACING, 0, HORIZONTAL_SPACING));
    panel.add(myValue, BorderLayout.CENTER);
    myPanel = new JPanel(new BorderLayout());
    myPanel.add(panel, BorderLayout.CENTER);
    myPanel.add(button, BorderLayout.LINE_END);
    myValue.addActionListener(event -> displayFlagEditor());
    myValue.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        displayFlagEditor();
      }
    });
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public NlProperty getProperty() {
    return myProperty;
  }

  @Override
  public void setProperty(@NotNull NlProperty property) {
    assert property instanceof NlFlagPropertyItem;
    myProperty = (NlFlagPropertyItem)property;
    myValue.setText(property.getValue());
  }

  private AnAction createDisplayFlagEditorAction() {
    return new AnAction() {
      @Override
      public void update(AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        if (myProperty != null) {
          presentation.setIcon(AllIcons.General.Ellipsis);
          presentation.setText("Click to edit");
          presentation.setVisible(true);
          presentation.setEnabled(true);
        }
        else {
          presentation.setIcon(null);
          presentation.setText(null);
          presentation.setVisible(false);
          presentation.setEnabled(false);
        }
      }

      @Override
      public void actionPerformed(AnActionEvent event) {
        displayFlagEditor();
      }
    };
  }

  private void displayFlagEditor() {
    FlagsDialog dialog = new FlagsDialog(myProperty);
    dialog.setInitialLocationCallback(() -> {
      Point location = new Point(0, 0);
      SwingUtilities.convertPointToScreen(location, myPanel);
      return location;
    });
    dialog.show();
  }

  private static class FlagsDialog extends DialogWrapper {
    private final NlFlagPropertyItem myProperty;

    protected FlagsDialog(@NotNull NlFlagPropertyItem property) {
      super(property.getModel().getProject(), false, IdeModalityType.MODELESS);
      myProperty = property;
      setTitle(property.getName());
      init();
      getWindow().addWindowFocusListener(new WindowFocusListener() {
        @Override
        public void windowGainedFocus(WindowEvent e) {
        }

        @Override
        public void windowLostFocus(WindowEvent e) {
          close(0);
        }
      });
    }

    @Override
    @NotNull
    protected Action[] createActions() {
      return new Action[]{getOKAction()};
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
      AttributeDefinition definition = myProperty.getDefinition();
      assert definition != null;
      for (String item : definition.getValues()) {
        NlFlagEditor editor = NlFlagEditor.createForInspector(DEFAULT_LISTENER);
        editor.setProperty(myProperty.getChildProperty(item));
        panel.add(editor.getComponent());
      }
      return panel;
    }
  }
}
