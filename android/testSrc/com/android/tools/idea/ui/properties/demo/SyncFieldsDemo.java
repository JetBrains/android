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
package com.android.tools.idea.ui.properties.demo;

import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.expressions.string.FormatExpression;
import com.android.tools.idea.ui.properties.swing.EnabledProperty;
import com.android.tools.idea.ui.properties.swing.SelectedProperty;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Keep project and activity name in sync
 * <p/>
 * This panel presents two input fields, allowing the user to specify a project name and activity
 * name. By default, the activity name should always be "(projectName)Activity", unless the user
 * explicitly breaks the link or chooses not to create an activity at all.
 * <p/>
 * Demonstrates:
 * - Setting up and binding swing properties to one another
 * - Breaking a binding conditionally
 */
public final class SyncFieldsDemo {
  private JTextField myProjectNameField;
  private JTextField myActivityNameField;
  private JCheckBox mySyncActivityNameCheckBox;
  private JCheckBox myCreateActivityCheckBox;
  private JPanel myRootPanel;

  private BindingsManager myBindings = new BindingsManager();

  public static void main(String[] args) {
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        final JFrame frame = new JFrame("SyncFieldsDemo");
        final SyncFieldsDemo demo = new SyncFieldsDemo();
        frame.setContentPane(demo.myRootPanel);
        demo.init();
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.addWindowListener(new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            demo.dispose();
          }
        });
      }
    });
  }

  public void init() {
    // Wrap UI elements in properties
    final StringProperty projectText = new TextProperty(myProjectNameField);
    final StringProperty activityText = new TextProperty(myActivityNameField);
    final BoolProperty isSynced = new SelectedProperty(mySyncActivityNameCheckBox);
    final BoolProperty createActivity = new SelectedProperty(myCreateActivityCheckBox);
    BoolProperty isActivityEnabled = new EnabledProperty(myActivityNameField);
    BoolProperty isLinkEnabled = new EnabledProperty(mySyncActivityNameCheckBox);

    projectText.set("MyProject");

    // Bind activityText <- nameExpression(projectText), but only if conditions are met
    final FormatExpression activityNameExpression = new FormatExpression("%1$sActivity", projectText);
    myBindings.bind(activityText, activityNameExpression, isSynced.and(createActivity));

    myBindings.bind(isActivityEnabled, createActivity);
    myBindings.bind(isLinkEnabled, createActivity);

    // Listen to activityText - if it is changed by the user and not its binding, break syncing!
    activityText.addListener(new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        isSynced.set(activityText.get().equals(activityNameExpression.get()));
      }
    });
  }

  public void dispose() {
    myBindings.releaseAll(); // You should always remember to release your bindings!
  }
}
