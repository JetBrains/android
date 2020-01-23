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
package com.android.tools.idea.emulator;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import java.awt.BorderLayout;
import java.awt.LayoutManager;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class EmulatorToolWindow implements DumbAware {
  public static final boolean SHUTDOWN_CAPABLE = false;
  public static final String ID = "Emulator";

  private JPanel myToolWindowContent;
  private boolean myInitialized;

  public EmulatorToolWindow(Project project) {
    myToolWindowContent = new JPanel(new BorderLayout());

    // Lazily initialize content since we can only have one frame
    project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void stateChanged() {
        if (!SHUTDOWN_CAPABLE && myInitialized) {
          return;
        }

        // We need to query the tool window again, because it might have been unregistered when closing the project.
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ID);
        if (window == null) {
          return;
        }

        if (window.isVisible()) {
          // TODO: How do I unsubscribe? This will keep notifying me of all tool windows, forever.
          if (myInitialized) {
            return;
          }

          myInitialized = true;
          createContent(window);
        }
        else if (SHUTDOWN_CAPABLE && myInitialized) {
          destroyContent();
        }
      }
    });
  }

  public JComponent getComponent() {
    return myToolWindowContent;
  }

  private void createContent(ToolWindow window) {
    myInitialized = true;
    try {
      // TODO: Well we should probably fetch the proper emulator port from somewhere.
      JPanel emulatorPanel = EmulatorJarLoader.createView(5554);

      // Tor modifications: wrap in another JPanel to keep aspect ratio
      LayoutManager layoutManager = new EmulatorLayoutManager(emulatorPanel);
      myToolWindowContent.add(emulatorPanel);
      myToolWindowContent.setLayout(layoutManager);
      myToolWindowContent.repaint();
      window.setTitle(EmulatorJarLoader.getCurrentAvdName(emulatorPanel));

    }
    catch (Exception ex) {
      String label = "Unable to load emulator view: " + ex.toString();
      myToolWindowContent.add(new JLabel(label), BorderLayout.CENTER);
    }
  }

  private void destroyContent() {
    myInitialized = false;
    myToolWindowContent.setLayout(new BorderLayout());
    myToolWindowContent.removeAll();
  }
}
