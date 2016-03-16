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
package com.android.tools.idea.updater.configure;

import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.*;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Dialog box allowing the user to edit or create an {@link RepositorySource}. Does some very basic validation.
 */
public class EditSourceDialog extends DialogWrapper {
  private JPanel contentPane;
  private JTextField myNameField;
  private JTextField myUrlField;
  private JBLabel myErrorLabel;

  private RepositorySourceProvider myProvider;
  private RepositorySource myExistingSource;

  private boolean myUrlSet = false;

  public EditSourceDialog(@NotNull RepositorySourceProvider provider, @Nullable RepositorySource existing) {
    super(null);
    myProvider = provider;
    myExistingSource = existing;
    myNameField.setText(existing == null ? "Custom Update Site" : existing.getDisplayName());
    myUrlField.setText(existing == null ? "http://" : existing.getUrl());
    setModal(true);

    myUrlField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myUrlSet = true;
        validateUrl(myUrlField.getText());
      }
    });

    myUrlField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        myUrlSet = true;
        validateUrl(myUrlField.getText());
      }
    });

    myUrlField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        if (myUrlSet) {
          validateUrl(myUrlField.getText() + e.getKeyChar());
        }
      }
    });

    init();
  }

  private boolean validateUrl(String url) {
    String error = getErrorMessage(url);
    if (error == null) {
      myErrorLabel.setVisible(false);
      setOKActionEnabled(true);
      contentPane.repaint();
      return true;
    }
    else {
      myErrorLabel.setText(error);
      myErrorLabel.setVisible(true);
      setOKActionEnabled(false);
      contentPane.repaint();
      return false;
    }
  }

  @Nullable
  private String getErrorMessage(String urlString) {
    try {
      new URL(urlString);
    }
    catch (MalformedURLException e) {
      return "URL is invalid";
    }
    if (myExistingSource == null) {
      // Reject URLs that are already in the source list.
      // URLs are generally case-insensitive (except for file:// where it all depends
      // on the current OS so we'll ignore this case.)
      // If we're editing a source, skip this.
      for (RepositorySource s : myProvider.getSources(null, null, new StudioLoggerProgressIndicator(getClass()), false)) {
        if (urlString.equalsIgnoreCase(s.getUrl())) {
          return "An update site with this URL already exists";
        }
      }
    }
    return null;
  }

  public String getUiName() {
    return myNameField.getText();
  }

  public String getUrl() {
    return myUrlField.getText();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Override
  protected void doOKAction() {
    myUrlSet = true;
    if (validateUrl(myUrlField.getText())) {
      super.doOKAction();
    }
  }
}
