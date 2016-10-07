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
package com.android.tools.idea.gradle.structure.editors;

import com.android.tools.idea.structure.EditorPanel;
import com.intellij.ui.HyperlinkAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

import static com.intellij.ide.BrowserUtil.browse;
import static org.jetbrains.android.util.AndroidUiUtil.setUpAsHtmlLabel;

public class DslNotSupportedPanel extends EditorPanel {
  @NotNull private final JEditorPane myMessagePane;

  public DslNotSupportedPanel() {
    super(new BorderLayout());
    myMessagePane = new JEditorPane();
    add(myMessagePane, BorderLayout.NORTH);
    setUpAsHtmlLabel(myMessagePane);
    String msg = "Updating project properties is not supported when Gradle experimental plugin is used. You can edit them directly in " +
                 "build.gradle files. More information about the experimental plugin can be found " +
                 "<a href=http://tools.android.com/tech-docs/new-build-system/gradle-experimental>here</a>.";
    myMessagePane.setText(msg);
    myMessagePane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        browse(e.getURL());
      }
    });
  }

  @Override
  public void apply() {

  }

  @Override
  public boolean isModified() {
    return false;
  }
}
