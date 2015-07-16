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
package com.android.tools.idea.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;

/**
 * A text field with an accompanying button to launch the browser with a given URL.
 */
public class TextFieldWithLaunchBrowserButton extends TextFieldWithBrowseButton {
  private static final Logger LOG = Logger.getInstance(TextFieldWithLaunchBrowserButton.class);
  private final String myUrl;


  public TextFieldWithLaunchBrowserButton(String url) {
    myUrl = url;
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        try {
          if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(myUrl));
          } else {
            Messages.showErrorDialog("Please visit \n" + myUrl + "\n to retrieve this value", "Could Not Open Web Browser");
          }
        }
        catch (IOException error) {
          LOG.error(error);
        }
      }
    });
  }
}
