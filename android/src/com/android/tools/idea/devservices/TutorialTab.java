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
package com.android.tools.idea.devservices;

import com.android.annotations.Nullable;
import com.android.utils.HtmlBuilder;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Tutorial tab for hosting text/html content.
 */
public class TutorialTab extends JPanel {
  private final String myContentRoot;
  private JPanel myRootPanel;
  private JBScrollPane myScrollPane;
  private JEditorPane myContentPane;

  public TutorialTab(@Nullable String contentRoot) {
    super(new BorderLayout());
    myContentRoot = contentRoot;

    myScrollPane.add(myContentPane);
    myScrollPane.setViewportView(myContentPane);

    add(myRootPanel);

    // Dummy content to populate at instantiation time.
    HtmlBuilder builder = new HtmlBuilder();
    builder.addHeading("Welcome to the Tutorial Tab!", "black");
    builder.newline();
    builder.newline();
    builder.add("You can add ");
    builder.addBold("formatted");
    // So there is a blank space between *formatted* and _content_.
    builder.add(" ");
    builder.addItalic("content");
    builder.addLink(" and ", "links", ".", "http://www.android.com");
    updateTutorialContent(builder.getHtml());
  }

  public void updateTutorialContent(@NotNull String newContent) {
    myContentPane.setText(newContent);
  }
}
