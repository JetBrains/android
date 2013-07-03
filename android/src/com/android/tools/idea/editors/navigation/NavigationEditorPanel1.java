/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation;

import com.android.navigation.Transition;
import com.android.navigation.NavigationModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/*
This class is currently unused.
*/

public class NavigationEditorPanel1 extends JPanel {
  private final NavigationModel myNavigationModel;
  private final AndroidRootComponent mySourcePreviewPanel;
  private final AndroidRootComponent myDestPreviewPanel;
  private final Project myProject;
  private final JComboBox myGestureComboBox;
  private int myCursor = 0;
  private VirtualFileSystem myFileSystem;
  private String myPath;

  private void update() {
    Transition current = getCurrentNavigation();
    VirtualFile source = myFileSystem.findFileByPath(myPath + "/" + current.getSource());
    VirtualFile destination = myFileSystem.findFileByPath(myPath + "/" + current.getDestination());
    mySourcePreviewPanel.render(myProject, source);
    myGestureComboBox.setSelectedItem(current.getType());
    myDestPreviewPanel.render(myProject, destination);
  }

  public void setCursor(int cursor) {
    myCursor = Math.min(Math.max(0, cursor), myNavigationModel.getTransitions().size() - 1);
    update();
  }

  private Transition getCurrentNavigation() {
    return myNavigationModel.getTransitions().get(myCursor);
  }

  private int findFirstNavWith(String name, boolean source) {
    List<Transition> transitions = myNavigationModel.getTransitions();
    for(int i = 0 ; i < transitions.size(); i++) {
      Transition nav = transitions.get(i);
      String field = (source ? nav.getSource() : nav.getDestination()).getState().getControllerClassName();
      if (field.equals(name)) {
        return i;
      }
    }
    return -1;
  }

  public NavigationEditorPanel1(Project project, VirtualFile file, NavigationModel navigationModel) {
    myProject = project;
    myFileSystem = file.getFileSystem();
    myPath = file.getParent().getPath();
    myCursor = 0;
    myNavigationModel = navigationModel;

    mySourcePreviewPanel = new AndroidRootComponent();
    mySourcePreviewPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        right();
      }
    });
    myGestureComboBox = new JComboBox(new Object[]{"", "touch", "swipe"});
    myDestPreviewPanel = new AndroidRootComponent();
    myDestPreviewPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        left();
      }
    });
    Color background = Color.LIGHT_GRAY;

    setLayout(new BorderLayout());
    setBackground(background);
    {
      JPanel panel = new JPanel(new FlowLayout());
      panel.setBackground(background);
      panel.add(mySourcePreviewPanel);
      panel.add(myGestureComboBox);
      panel.add(myDestPreviewPanel);

      add(panel, BorderLayout.CENTER);
    }
    /*
    mySourcePreviewPanel.setFocusable(true);
    mySourcePreviewPanel.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent keyEvent) {
        super.keyTyped(keyEvent);
        System.out.println("keyEvent = " + keyEvent);
      }
    });
    */
    {
      JButton b = new JButton("Previous");
      add(b, BorderLayout.NORTH);
      b.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
          up();
        }
      });
    }
    {
      JButton b = new JButton("Next");
      add(b, BorderLayout.SOUTH);
      b.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
          down();
        }
      });
    }
    update();
  }

  private void up() {
    setCursor(myCursor + 1);
  }

  private void down() {
    setCursor(myCursor - 1);
  }

  private void left() {
    int next = findFirstNavWith(getCurrentNavigation().getSource().getState().getControllerClassName(), false);
    if (next != -1) {
      setCursor(next);
    } else {
      Toolkit.getDefaultToolkit().beep();
    }
  }

  private void right() {
    int next = findFirstNavWith(getCurrentNavigation().getDestination().getState().getControllerClassName(), true);
    if (next != -1) {
      setCursor(next);
    } else {
      Toolkit.getDefaultToolkit().beep();
    }
  }
}
