/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEIcons;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditor;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;

import java.awt.event.KeyEvent;
import javax.swing.*;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.Arrays;

/**
 * Base class for all popup UI's create dialogs.
 * Contains utility functions used by all of them.
 */
public class BaseCreatePanel extends JPanel {
  public static final boolean DEBUG = false;
  MEUI.Popup myDialog;
  Icon icon = MEIcons.CREATE_TRANSITION;
  protected MotionEditor mMotionEditor;
  protected boolean inSubPopup = false;
  private Component mSourceComponent;

  @Override
  public void updateUI() {
    super.updateUI();
    int n = getComponentCount();
    for (int i = 0; i < n; i++) {
      Component c = getComponent(i);
      if (c instanceof  JComponent){
        ((JComponent)c).updateUI();
      }
    }
  }

  BaseCreatePanel() {
    super(new GridBagLayout());
    setFocusCycleRoot(true);
    setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
    Action escapeAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        dismissPopup();
      }
    };
    getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "ESCAPE");
    getActionMap().put("ESCAPE", escapeAction);
  }

  protected static void grid(GridBagConstraints gbc, int x, int y) {
    gbc.gridy = y;
    gbc.gridx = x;
    gbc.gridwidth = 1;
    gbc.gridheight = 1;
  }

  protected static void grid(GridBagConstraints gbc, int x, int y, int w, int h) {
    gbc.gridy = y;
    gbc.gridx = x;
    gbc.gridwidth = w;
    gbc.gridheight = h;
  }

  protected JComboBox<String> newComboBox(String... choices) {
    JComboBox<String> ret = MEUI.makeComboBox(choices);
    return ret;
  }

  protected PromptedTextField newTextField(String prompt, int spaces) {
    return new PromptedTextField(prompt, spaces);
  }

  public static class PromptedTextField extends JTextField {
    String mPromptText;

    public void setPromptText(String prompt) {
      String s = getText();
      mPromptText = prompt;
      setText(prompt);
    }

    PromptedTextField(String prompt, int spaces) {
      mPromptText = prompt;
      char[] xxx = new char[spaces];
      Arrays.fill(xxx, 'X');
      setText(new String(xxx));
      setPreferredSize(getPreferredSize());
      setText(prompt);
      Color normalColor = getForeground();
      setForeground(Color.GRAY);
      addFocusListener(new FocusListener() {
        @Override
        public void focusGained(FocusEvent e) {
          String s = getText();
          if (s.equals(mPromptText)) {
            setText("");
            setForeground(normalColor);
          }
        }

        @Override
        public void focusLost(FocusEvent e) {
          String s = getText();
          if (s.trim().isEmpty()) {
            setText(mPromptText);
            setForeground(Color.GRAY);
          }
        }
      });
    }

    public String getPromptText() {
      return mPromptText;
    }
  }

  protected void showErrorDialog(String str) {
    inSubPopup = true;
    JOptionPane optionPane = new JOptionPane(str, JOptionPane.ERROR_MESSAGE);
    JDialog dialog = optionPane.createDialog(this, "Invalid");
    dialog.setContentPane(optionPane);
    dialog.setTitle("Invalid");
    dialog.setAlwaysOnTop(true);
    dialog.setModal(true);
    dialog.setResizable(false);
    dialog.setLocationRelativeTo(this);
    myDialog.hide();
    dialog.setVisible(true);
    dialog.dispose();
    inSubPopup = false;
    myDialog.show();
  }

  protected void showPreconditionDialog(String str) {
    inSubPopup = true;
    JOptionPane optionPane = new JOptionPane(str, JOptionPane.ERROR_MESSAGE);
    JDialog dialog = optionPane.createDialog(mSourceComponent, "Invalid");
    dialog.setContentPane(optionPane);
    dialog.setTitle("Invalid");
    dialog.setAlwaysOnTop(true);
    dialog.setModal(true);
    dialog.setResizable(false);
    dialog.setLocationRelativeTo(mSourceComponent);
    dialog.setVisible(true);
    dialog.dispose();
    inSubPopup = false;
  }

  public MTag create() {
    dismissPopup();
    return null;
  }

  public void dismissPopup() {
    if (myDialog == null) {
     return;
    }
    myDialog.dismiss();
    myDialog = null;
  }

  public void showPopup(JComponent source, int offx, int offy) {
    updateUI();
    myDialog =  MEUI.createPopup(this,source);
  }
    public void showPopup2(Component source, int offx, int offy) {

    final JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(source),
      "Click a button");
    dialog.setUndecorated(true);

    Point scr = source.getLocationOnScreen();
    dialog.setBounds(scr.x, scr.y, 0, 0);
    Container comp = this.getParent();
    if (comp != null) { // to prevent "adding a container to a container on a different GraphicsDevice" error
      comp.remove(this);
    }
    dialog.setContentPane(this);
    dialog.setAlwaysOnTop(true);
    dialog.pack();

    dialog.addWindowFocusListener(new WindowFocusListener() {
      @Override
      public void windowGainedFocus(WindowEvent e) {

      }

      @Override
      public void windowLostFocus(WindowEvent e) {
        if (inSubPopup) {
          return;
        }
        dialog.setVisible(false);
      }
    });

    //myDialog = dialog;
    dialog.setVisible(true);
    dialog.requestFocus();
  }

  protected boolean populateDialog() {
    return true;
  }

  public Action getAction(JComponent component, MotionEditor motionEditor) {
    mMotionEditor = motionEditor;
    AbstractAction aa = new AbstractAction(getName(), icon) {

      @Override
      public void actionPerformed(ActionEvent e) {
        mSourceComponent = component;
        boolean ok = populateDialog();

        if (ok) {
          MEUI.invokeLater(() -> {
            Debug.log("popup ....");
            showPopup(component, 0, 0);
            motionEditor.dataChanged();
          });
        }
      }
    };
    aa.putValue(Action.SHORT_DESCRIPTION, component.getToolTipText());
    return aa;
  }

  String addIdPrefix(String str) {
    if (str == null) {
      return null;
    }
    if (str.startsWith("@+id/") || str.startsWith("@id/")) {
      return str;
    }
    return "@+id/" + str;
  }
}
