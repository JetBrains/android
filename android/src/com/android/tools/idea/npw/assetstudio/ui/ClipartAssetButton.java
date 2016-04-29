/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.ui;

import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ClipartAsset;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.expressions.Expression;
import com.android.tools.idea.ui.properties.swing.IconProperty;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;

/**
 * Button which wraps a {@link ClipartAsset}, allowing the user to select a clipart target from a
 * grid of choices.
 */
public final class ClipartAssetButton extends JButton implements AssetComponent {

  private static final int CLIPART_BUTTON_SIZE = JBUI.scale(40);
  private static final int CLIPART_DIALOG_BORDER = JBUI.scale(10);
  private static final int DIALOG_HEADER = JBUI.scale(20);

  private final ClipartAsset myClipartAsset = new ClipartAsset();
  private final BindingsManager myBindings = new BindingsManager();
  private final List<ActionListener> myListeners = Lists.newArrayListWithExpectedSize(1);

  public ClipartAssetButton() {

    setIconButtonDimensions(this);
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showClipartDialog();
      }
    });

    myBindings.bind(new IconProperty(this), new Expression<Optional<Icon>>(myClipartAsset.clipartName()) {
      @NotNull
      @Override
      public Optional<Icon> get() {
        try {
          return Optional.of(myClipartAsset.createIcon());
        }
        catch (IOException e) {
          return Optional.absent();
        }
      }
    });

    myClipartAsset.clipartName().addListener(new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
        for (ActionListener listener : myListeners) {
          listener.actionPerformed(e);
        }
      }
    });
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(ClipartAssetButton.class);
  }

  private static void setIconButtonDimensions(@NotNull JButton b) {
    Dimension d = new Dimension(CLIPART_BUTTON_SIZE, CLIPART_BUTTON_SIZE);
    b.setMinimumSize(d);
    b.setMaximumSize(d);
    b.setPreferredSize(d);
  }

  @NotNull
  @Override
  public BaseAsset getAsset() {
    return myClipartAsset;
  }

  @Override
  public void addAssetListener(@NotNull ActionListener l) {
    myListeners.add(l);
  }

  /**
   * Displays a modal dialog with one button for each entry in the clipart library. Clicking on a
   * button selects that clipart entry.
   */
  private void showClipartDialog() {
    Window window = SwingUtilities.getWindowAncestor(this);
    final JDialog dialog = new JDialog(window, "Choose Clipart", Dialog.ModalityType.DOCUMENT_MODAL);
    dialog.getRootPane().setLayout(new BorderLayout());
    dialog.getRootPane()
      .setBorder(new EmptyBorder(CLIPART_DIALOG_BORDER, CLIPART_DIALOG_BORDER, CLIPART_DIALOG_BORDER, CLIPART_DIALOG_BORDER));

    FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
    JPanel iconPanel = new JPanel(layout);

    List<String> clipartNames = ClipartAsset.getAllClipartNames();
    for (final String clipartName : clipartNames) {
      try {
        JButton btn = new JButton();

        btn.setIcon(ClipartAsset.createIcon(clipartName));
        setIconButtonDimensions(btn);
        btn.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            myClipartAsset.clipartName().set(clipartName);
            dialog.setVisible(false);
          }
        });
        iconPanel.add(btn);
      }
      catch (IOException e) {
        getLog().error(e);
      }
    }

    dialog.getRootPane().add(iconPanel);
    // If we have 'n' clipart choices, do our best to make a square of choices (e.g. √n x √n)
    int size = (int)(Math.sqrt(clipartNames.size()) + 1) * (CLIPART_BUTTON_SIZE + layout.getHgap()) + CLIPART_DIALOG_BORDER * 2;
    dialog.setSize(size, size + DIALOG_HEADER);
    dialog.setLocationRelativeTo(window);
    dialog.setVisible(true);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.clear();
  }
}
