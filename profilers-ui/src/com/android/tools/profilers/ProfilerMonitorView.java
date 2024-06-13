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

import static com.android.tools.profilers.ProfilerFonts.H2_FONT;
import static com.android.tools.profilers.ProfilerFonts.STANDARD_FONT;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.idea.IdeInfo;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;

public abstract class ProfilerMonitorView<T extends ProfilerMonitor> extends AspectObserver {

  private static final int MINIMUM_MONITOR_HEIGHT = JBUI.scale(50);

  @NotNull private final T myMonitor;

  // The purpose of having myProfilersView here is that, monitorEnabledChanged method is called in constructor which in turn invokes
  // populateCustomLoadingPanel (in EventMonitorView) method which uses the myProfilersView. Having myProfilersView field defined in
  // EventMonitorView will lead to nullPointerException and hence defined here.
  @NotNull protected final StudioProfilersView myProfilersView;

  private JPanel myContainer;

  public ProfilerMonitorView(@NotNull StudioProfilersView profilersView, @NotNull T monitor) {
    myMonitor = monitor;
    myProfilersView = profilersView;
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
      myContainer.setBackground(getDisabledBackground());
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
    return isPreO()
           ? "Additional profiling support is unavailable for the selected process"
           : "There was an error loading this feature. Try restarting the profiler to fix it.";
  }

  private Color getDisabledBackground() {
    return isPreO() ? ProfilerColors.MONITOR_DISABLED : ProfilerColors.MONITOR_ERROR;
  }

  private boolean isPreO() {
    return myMonitor.getProfilers().getDevice() != null &&
           myMonitor.getProfilers().getDevice().getFeatureLevel() < AndroidVersion.VersionCodes.O;
  }

  private void monitorEnabledChanged() {
    myContainer.removeAll();
    if (getMonitor().isEnabled()) {
      myContainer.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
      populateUi(myContainer);
    }
    else {
      if (hasCustomLoadingPanel()) {
        populateCustomLoadingPanel(myContainer);
      }
      else {
        myContainer.setBackground(getDisabledBackground());
        populateDisabledView(myContainer);
      }
    }
  }

  protected boolean hasCustomLoadingPanel() {
    return false;
  }

  protected void populateCustomLoadingPanel(JPanel container) {}

  protected void populateDisabledView(JPanel container) {
    TabularLayout layout = new TabularLayout("*,Fit-,*", "*");
    myContainer.setLayout(layout);
    if (isPreO()) {
      layout.setRowSizing(0, "6*");
      layout.setRowSizing(1, "4*");
    }

    JLabel disabledMessage = new JLabel(getDisabledMessage());
    disabledMessage.setHorizontalAlignment(SwingConstants.CENTER);
    disabledMessage.setVerticalAlignment(SwingConstants.CENTER);
    disabledMessage.setFont(H2_FONT);
    myContainer.add(disabledMessage, new TabularLayout.Constraint(0, 0, 3));

    if (isPreO()) {
      if (IdeInfo.isGameTool()) {
        HyperlinkLabel linkToInstructionMessage = new HyperlinkLabel();
        linkToInstructionMessage.setTextWithHyperlink("Please recompile the APK with <hyperlink>advanced profiling enabled</hyperlink>.");
        linkToInstructionMessage.setHyperlinkTarget("https://developer.android.com/r/studio-ui/advanced-profiling");
        myContainer.add(linkToInstructionMessage, new TabularLayout.Constraint(1, 1));
        linkToInstructionMessage.setFont(STANDARD_FONT);
      }
      else {
        HyperlinkLabel linkToConfigMessage = new HyperlinkLabel();
        linkToConfigMessage.setTextWithHyperlink("Configure this setting in the <hyperlink>Run Configuration</hyperlink>");
        linkToConfigMessage.addHyperlinkListener(new HyperlinkAdapter() {
          @Override
          protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
            myMonitor.getProfilers().getIdeServices().enableAdvancedProfiling();
          }
        });
        myContainer.add(linkToConfigMessage, new TabularLayout.Constraint(1, 1));
        linkToConfigMessage.setFont(STANDARD_FONT);
      }
    }
  }

  /**
   * Helper function to register monitor components on tooltips. This function is responsible for setting the
   * active tooltip on the stage when a mouse enters the desired component.
   */
  public void registerTooltip(@NotNull RangeTooltipComponent tooltip, Stage stage) {
    JComponent component = getComponent();
    component.addMouseListener(new ProfilerTooltipMouseAdapter(stage, () -> myMonitor.isEnabled() ? myMonitor.buildTooltip() : null));
    tooltip.registerListenersOn(component);
  }

  abstract protected void populateUi(JPanel container);
}
