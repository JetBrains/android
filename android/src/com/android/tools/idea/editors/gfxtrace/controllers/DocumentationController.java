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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class DocumentationController extends Controller {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new DocumentationController(editor).myPanel;
  }

  @NotNull private final JPanel myPanel = new JPanel();
  @NotNull private final JBScrollPane myScrollPane = new JBScrollPane();
  @NotNull private final JTextPane myTextPane = new JTextPane();
  @NotNull private Map<String, String> myDocumentationCache = new HashMap<String, String>();
  @NotNull private Set<String> myRequestInProgress = new HashSet<String>();
  private String myTargetUrl;

  private DocumentationController(@NotNull GfxTraceEditor editor) {
    super(editor);
    myScrollPane.setViewportView(myTextPane);
    myPanel.add(myScrollPane, BorderLayout.CENTER);
    myTextPane.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    // TODO: Rewrite to use IntelliJ documentation view.
  }

  public void setDocumentation(@Nullable final String url) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myTextPane.setText(null);
    if (url == null || url.isEmpty()) {
      return;
    }

    myTargetUrl = url;
    final String cachedCopy = myDocumentationCache.get(url);
    if (cachedCopy == null) {
      if (myRequestInProgress.contains(url)) {
        return; // Let the already-in-flight request handle this.
      }
      else {
        myRequestInProgress.add(url);
      }
    }

    // This controller can run on its own. This will not have any adverse side effects on the other controllers if it fails.
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        boolean problemEncountered = false;
        String validCopy = cachedCopy;
        if (validCopy == null) {
          try {
            URLConnection connection = new URL(url).openConnection();
            InputStream response = connection.getInputStream();
            Scanner s = new Scanner(response).useDelimiter("\\A");
            validCopy = s.hasNext() ? s.next() : "";
          }
          catch (MalformedURLException e) {
            problemEncountered = true;
          }
          catch (IOException e) {
            problemEncountered = true;
          }
        }

        final String documentation = validCopy;
        final boolean error = problemEncountered;

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (error) {
              myRequestInProgress.remove(url);
            }
            else if (url.equals(myTargetUrl)) {
              myTextPane.setText(documentation);
            }
            myDocumentationCache.put(url, documentation);
          }
        });
      }
    });
  }

  @Override
  public void notifyPath(PathEvent event) {
    // TODO: detect paths that have associated documentation...
  }
}
