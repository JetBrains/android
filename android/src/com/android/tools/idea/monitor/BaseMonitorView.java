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
package com.android.tools.idea.monitor;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.chartlib.EventData;
import com.android.tools.chartlib.TimelineComponent;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.EdtExecutor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.util.Key;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;

public abstract class BaseMonitorView<T extends DeviceSampler>
  implements HierarchyListener, TimelineEventListener, DeviceContext.DeviceSelectionListener {
  public static final Key<BaseMonitorView> MONITOR_VIEW_KEY = Key.create("MONITOR_VIEW_KEY");

  @NotNull private static final String PAUSED_LABEL = "This monitor is disabled.";
  @NotNull private static final String PAUSED_KEY = ".paused";
  @NotNull private static final String POSITION_KEY = ".position";
  @NotNull private static final String MINIMIZED_KEY = ".minimized";
  @NotNull private static final Integer OVERLAY_LAYER = JLayeredPane.DEFAULT_LAYER + 10;
  @NotNull private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();

  protected static final int PAUSED_LABEL_PRIORITY = 5;

  @NotNull protected Project myProject;
  @NotNull protected DeviceContext myDeviceContext;
  @NotNull protected JLayeredPane myContentPane;
  @NotNull protected volatile TimelineComponent myTimelineComponent;
  @NotNull protected final T mySampler;
  @NotNull protected final EventData myEvents = new EventData();

  @NotNull private JPanel myTextPanel;
  @NotNull private JTextPane myOverlayText;
  @NotNull private HashMap<String, ZOrderedOverlayText> myOverlayLookup;
  @NotNull private PriorityQueue<ZOrderedOverlayText> myVisibleOverlays;
  private int myPosition;
  private boolean myIsMinimized;

  private static class ZOrderedOverlayText {
    @NotNull private String myText;
    private int myZ;

    private ZOrderedOverlayText(@NotNull String text, int z) {
      myText = text;
      myZ = z;
    }
  }

  protected BaseMonitorView(@NotNull Project project,
                            @NotNull DeviceContext deviceContext,
                            @NotNull T sampler,
                            float bufferTime,
                            float initialMax,
                            float absoluteMax,
                            float initialMarkerSeparation) {
    myProject = project;
    myDeviceContext = deviceContext;
    myDeviceContext.addListener(this, project);
    mySampler = sampler;
    mySampler.addListener(this);
    myTimelineComponent =
      new TimelineComponent(mySampler.getTimelineData(), myEvents, bufferTime, initialMax, absoluteMax, initialMarkerSeparation);
    myContentPane = new JBLayeredPane() {
      @Override
      public void doLayout() {
        final Component[] components = getComponents();
        final Rectangle r = getBounds();
        for (Component c : components) {
          c.setBounds(0, 0, r.width, r.height);
        }
      }

      @Override
      public Dimension getPreferredSize() {
        return getBounds().getSize();
      }
    };

    // Use GridBagLayout because it center aligns content by default
    //noinspection UseJBColor
    myTextPanel = new JPanel(new GridBagLayout()) {
      Color translucentBackgroundColor = ColorUtil.toAlpha(BACKGROUND_COLOR, 192);

      @Override
      public void paintComponent(@NotNull Graphics g) {
        g.setColor(translucentBackgroundColor);
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
      }
    };
    myTextPanel.setOpaque(false);

    myOverlayText = new JTextPane();
    myOverlayText.setEditable(false);
    myOverlayText.setOpaque(false);
    myOverlayText.setEditorKit(UIUtil.getHTMLEditorKit());
    myOverlayText.setBackground(UIUtil.TRANSPARENT_COLOR);
    myOverlayText.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

    myOverlayLookup = new HashMap<String, ZOrderedOverlayText>();
    myVisibleOverlays = new PriorityQueue<ZOrderedOverlayText>(5, new Comparator<ZOrderedOverlayText>() {
      @Override
      public int compare(ZOrderedOverlayText a, ZOrderedOverlayText b) {
        return a.myZ - b.myZ;
      }
    });

    myTextPanel.add(myOverlayText);
    myTextPanel.setVisible(false);
    myContentPane.add(myTextPanel, OVERLAY_LAYER, 0);
    myContentPane.addHierarchyListener(this);

    addOverlayText(PAUSED_LABEL, PAUSED_LABEL_PRIORITY);
    performPausing(getPausedSetting());

    myPosition = getPositionSetting();
    myIsMinimized = getIsMinimizedSetting();
  }

  @Override
  public void hierarchyChanged(HierarchyEvent hierarchyEvent) {
    if ((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
      if (!mySampler.isRunning()) {
        mySampler.start();
      }
    }
  }

  @NotNull
  public ComponentWithActions createComponent() {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(myContentPane, BorderLayout.CENTER);
    return new ComponentWithActions.Impl(getToolbarActions(), null, null, null, wrapper);
  }

  public abstract ActionGroup getToolbarActions();

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public DeviceContext getDeviceContext() {
    return myDeviceContext;
  }

  @NotNull
  public EventData getEvents() {
    return myEvents;
  }

  @Override
  public void clientSelected(@Nullable Client c) {
    mySampler.setClient(c);
  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {
  }

  @Override
  public void deviceSelected(@Nullable IDevice device) {
  }

  /**
   * Pauses and records the state in the project properties.
   */
  public void setIsPaused(boolean isPaused) {
    performPausing(isPaused);
    setProperty(PAUSED_KEY, Boolean.toString(isPaused), Boolean.toString(getPreferredPausedState()));
  }

  public boolean getIsPaused() {
    return mySampler.getIsPaused();
  }

  public void setIsMinimized(boolean minimized) {
    setProperty(MINIMIZED_KEY, Boolean.toString(minimized), Boolean.toString(false));
    myIsMinimized = minimized;
  }

  public boolean getIsMinimized() {
    return myIsMinimized;
  }

  public void setPosition(int position) {
    setProperty(POSITION_KEY, Integer.toString(position), Integer.toString(getDefaultPosition()));
    myPosition = position;
  }

  public int getPosition() {
    return myPosition;
  }

  @Override
  public void onStart() {
    EdtExecutor.INSTANCE.execute(new Runnable() {
      @Override
      public void run() {
        setOverlayEnabled(PAUSED_LABEL, false);
      }
    });
    myTimelineComponent.setUpdateData(true);
  }

  @Override
  public void onStop() {
    EdtExecutor.INSTANCE.execute(new Runnable() {
      @Override
      public void run() {
        setOverlayEnabled(PAUSED_LABEL, true);
      }
    });
    myTimelineComponent.setUpdateData(false);
  }

  @NotNull
  public abstract String getTitleName();

  @NotNull
  public abstract Icon getTitleIcon();

  @NotNull
  public abstract String getMonitorName();

  @NotNull
  public abstract String getDescription();

  @NotNull
  public Color getViewBackgroundColor() {
    return myTimelineComponent.getBackground();
  }

  /**
   * Registers the given text in the list of possible strings usable in the overlay.
   *
   * @param text  is the unique text reference representing the text to be displayed in the overlay
   * @param index is the priority of the text, in the range of [0, Integer.MAX_VALUE]
   */
  protected final void addOverlayText(@NotNull String text, int index) {
    assert !myOverlayLookup.containsKey(text) && index >= 0 && index < Integer.MAX_VALUE;
    myOverlayLookup.put(text, new ZOrderedOverlayText(text, index));
  }

  /**
   * Enables and, if lowest {@code index} in all calls to {@link #addOverlayText(String, int)}, displays the text over this view.
   *
   * @param text    is the unique text reference given to {@link #addOverlayText(String, int)}
   * @param enabled true to enable, false to disable
   */
  protected final void setOverlayEnabled(@NotNull String text, boolean enabled) {
    assert myOverlayLookup.containsKey(text);

    ZOrderedOverlayText orderedText = myOverlayLookup.get(text);

    if (enabled) {
      if (!myVisibleOverlays.contains(orderedText)) {
        myVisibleOverlays.add(orderedText);
        updateOverlayText(myVisibleOverlays.peek().myText);
      }
    }
    else {
      myVisibleOverlays.remove(orderedText);
      if (myVisibleOverlays.size() > 0) {
        updateOverlayText(myVisibleOverlays.peek().myText);
      }
      else {
        updateOverlayText("");
      }
    }
  }

  protected final void setViewComponent(@NotNull JComponent component) {
    myContentPane.add(component, JLayeredPane.DEFAULT_LAYER, 0);
  }

  /**
   * Returns the preferred paused state of this monitor. Can be overridden.
   */
  protected boolean getPreferredPausedState() {
    return false;
  }

  protected abstract int getDefaultPosition();

  /**
   * Gets whether the paused state has been set in the project. If not, returns the preferred paused state of this monitor.
   */
  private boolean getPausedSetting() {
    return getBooleanProperty(PAUSED_KEY, getPreferredPausedState());
  }

  private int getPositionSetting() {
    return getIntProperty(POSITION_KEY, getDefaultPosition());
  }

  private boolean getIsMinimizedSetting() {
    return getBooleanProperty(MINIMIZED_KEY, false);
  }

  private void setProperty(@NotNull String propertyId, @NotNull String propertyValue, @NotNull String defaultValue) {
    PropertiesComponent.getInstance(myProject).setValue(getMonitorName() + propertyId, propertyValue, defaultValue);
  }

  private boolean getBooleanProperty(@NotNull String propertyId, boolean defaultValue) {
    return PropertiesComponent.getInstance(myProject).getBoolean(getMonitorName() + propertyId, defaultValue);
  }

  private int getIntProperty(@NotNull String propertyId, int defaultValue) {
    return PropertiesComponent.getInstance(myProject).getInt(getMonitorName() + propertyId, defaultValue);
  }

  /**
   * Performs the steps necessary to pause the monitor.
   */
  private void performPausing(boolean paused) {
    mySampler.setIsPaused(paused);
    setOverlayEnabled(PAUSED_LABEL, paused);
    myTimelineComponent.setUpdateData(!paused);
  }

  private void updateOverlayText(String text) {
    myOverlayText.setText(text);
    myTextPanel.setVisible(!text.isEmpty());
    myTextPanel.invalidate();
  }
}
