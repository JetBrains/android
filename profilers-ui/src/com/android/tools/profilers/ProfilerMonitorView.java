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
package com.android.tools.profilers;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.AspectObserver;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public abstract class ProfilerMonitorView<T extends ProfilerMonitor> extends AspectObserver {

  private static final int MINIMUM_MONITOR_HEIGHT = JBUI.scale(50);

  @NotNull private final T myMonitor;

  private JPanel myContainer;

  public ProfilerMonitorView(@NotNull T monitor) {
    myMonitor = monitor;
    myContainer = new JBPanel();
    myContainer.setOpaque(true);
    myContainer.setBorder(ProfilerLayout.MONITOR_BORDER);
    myContainer.setMinimumSize(new Dimension(0, MINIMUM_MONITOR_HEIGHT));
    // When the container gains focus we set our focus state on the monitor to
    // keep the monitor in the same state.
    myContainer.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        myMonitor.setFocus(true);
      }

      @Override
      public void focusLost(FocusEvent e) {
        myMonitor.setFocus(false);
      }
    });

    myMonitor.addDependency(this).onChange(ProfilerMonitor.Aspect.ENABLE, this::monitorEnabledChanged);
    monitorEnabledChanged();

    myMonitor.addDependency(this).onChange(ProfilerMonitor.Aspect.FOCUS, this::focusChanged);
    focusChanged();
  }

  @NotNull
  public JComponent getComponent() {
    return myContainer;
  }

  protected void focusChanged() {
    if (myMonitor.isEnabled()) {
      boolean highlight = myMonitor.isFocused() && myMonitor.canExpand();
      myContainer.setBackground(highlight ? ProfilerColors.MONITOR_FOCUSED : ProfilerColors.DEFAULT_BACKGROUND);
    }
    else {
      myContainer.setBackground(ProfilerColors.MONITOR_DISABLED);
    }
  }

  @NotNull
  protected final T getMonitor() {
    return myMonitor;
  }

  /**
   * @return the vertical weight this monitor view should have in a layout.
   */
  public float getVerticalWeight() {
    return 1f;
  }

  @NotNull
  public String getDisabledMessage() {
    return "Advanced profiling is unavailable for the selected process";
  }

  private void monitorEnabledChanged() {
    myContainer.removeAll();
    if (getMonitor().isEnabled()) {
      myContainer.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
      populateUi(myContainer);
    }
    else {
      myContainer.setBackground(ProfilerColors.MONITOR_DISABLED);
      myContainer.setLayout(new TabularLayout("*,Fit,*", "6*,4*"));

      JLabel disabledMessage = new JLabel(getDisabledMessage());
      disabledMessage.setHorizontalAlignment(SwingConstants.CENTER);
      disabledMessage.setVerticalAlignment(SwingConstants.CENTER);
      disabledMessage.setFont(disabledMessage.getFont().deriveFont(15.5f));
      myContainer.add(disabledMessage, new TabularLayout.Constraint(0, 0, 3));

      HyperlinkLabel linkToConfigMessage = new HyperlinkLabel();
      linkToConfigMessage.setHyperlinkText("Configure this setting in the ", "Run Configuration", "");
      linkToConfigMessage.addHyperlinkListener(new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(HyperlinkEvent e) {
          myMonitor.getProfilers().getIdeServices().enableAdvancedProfiling();
        }
      });
      myContainer.add(linkToConfigMessage, new TabularLayout.Constraint(1, 1));
      linkToConfigMessage.setFont(linkToConfigMessage.getFont().deriveFont(12f));
    }
  }

  abstract protected void populateUi(JPanel container);
}
