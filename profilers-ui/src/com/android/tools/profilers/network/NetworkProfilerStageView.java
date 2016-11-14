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
package com.android.tools.profilers.network;

import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioMonitorStage;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;

public class NetworkProfilerStageView extends StageView {
  private final NetworkProfilerStage myStage;
  private final JPanel myConnectionDetails;
  private JPanel myConnectionData;
  private Splitter myComponent;

  public NetworkProfilerStageView(NetworkProfilerStage stage) {
    super(stage);
    myStage = stage;
    myConnectionDetails = new JPanel(new BorderLayout());
    myConnectionData = new JPanel(new BorderLayout());

    stage.aspect.addDependency()
      .setExecutor(ApplicationManager.getApplication()::invokeLater)
      .onChange(NetworkProfilerAspect.CONNECTION_DATA, this::updateConnectionData)
      .onChange(NetworkProfilerAspect.CONNECTION, this::updateConnection);

    JPanel top = new JPanel();
    top.add(new JLabel("TODO: Network profiler"));
    JButton button = new JButton("Go back");
    button.addActionListener(action -> {
      StudioProfilers profilers = getStage().getStudioProfilers();
      StudioMonitorStage monitor = new StudioMonitorStage(profilers);
      profilers.setStage(monitor);
    });
    top.add(button);
    button = new JButton("Open details pane");
    button.addActionListener(action -> myStage.setEnableConnectionData(!myStage.isConnectionDataEnabled()));
    top.add(button);

    myComponent = new Splitter(false);

    Splitter splitter = new Splitter(true);
    splitter.setFirstComponent(top);
    splitter.setSecondComponent(myConnectionData);

    myComponent.setFirstComponent(splitter);
    myComponent.setSecondComponent(myConnectionDetails);

    updateConnectionData();
    updateConnection();
  }

  private void updateConnection() {
    // TODO: Get the data from the model and fill it/update it.
    myConnectionDetails.removeAll();
    myConnectionDetails.setBackground(JBColor.background());
    myConnectionDetails.add(new JLabel("Connection ID: " + myStage.getConnectionId()), BorderLayout.CENTER);
    myConnectionDetails.setVisible(myStage.isConnectionDataEnabled() && myStage.getConnectionId() != 0);
    myConnectionDetails.revalidate();
  }

  private void updateConnectionData() {
    // TODO: Get the data from the model and fill it/update it.
    myConnectionData.removeAll();
    JPanel panel = new JPanel();
    panel.setBackground(JBColor.background());
    panel.add(new JLabel("TODO: Connections"));

    JButton button = new JButton("Unselect");
    button.addActionListener(action -> myStage.setConnectionId(0));
    panel.add(button);

    button = new JButton("Select 1");
    button.addActionListener(action -> myStage.setConnectionId(1));
    panel.add(button);

    button = new JButton("Select 2");
    button.addActionListener(action -> myStage.setConnectionId(2));
    panel.add(button);

    myConnectionData.add(panel, BorderLayout.CENTER);
    myConnectionData.setVisible(myStage.isConnectionDataEnabled());
    myConnectionData.revalidate();
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public JComponent getToolbar() {
    // TODO Add network profiler toolbar elements.
    return new JPanel();
  }
}
