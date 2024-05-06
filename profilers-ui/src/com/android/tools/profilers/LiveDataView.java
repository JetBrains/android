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

import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TooltipView;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.adtui.model.ViewBinder;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public abstract class LiveDataView<T extends LiveDataModel> extends AspectObserver {
  @NotNull private final T myLiveDataModel;
  private final JPanel myContainer;

  public LiveDataView(@NotNull T liveDataModel) {
    myLiveDataModel = liveDataModel;
    myContainer = new JBPanel();
    myContainer.setOpaque(true);
    myContainer.setBorder(ProfilerLayout.MONITOR_BORDER);
    int minimumLiveViewHeight = JBUI.scale(50);
    myContainer.setMinimumSize(new Dimension(0, minimumLiveViewHeight));
    // When the container gains focus we set our focus state on the monitor to
    // keep the monitor in the same state.
    myContainer.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        myLiveDataModel.setFocus(true);
      }

      @Override
      public void focusLost(FocusEvent e) {
        myLiveDataModel.setFocus(false);
      }
    });
    myLiveDataModel.addDependency(this).onChange(LiveDataModel.Aspect.FOCUS, this::focusChanged);
    focusChanged();
  }

  /**
   * @return the vertical weight this live data view should have in a layout.
   */
  public float getVerticalWeight() {
    return 1f;
  }

  @NotNull
  public JComponent getComponent() {
    return myContainer;
  }

  protected void focusChanged() {
    boolean highlight = myLiveDataModel.isFocused();
    myContainer.setBackground(highlight ? ProfilerColors.MONITOR_FOCUSED : ProfilerColors.DEFAULT_BACKGROUND);
  }

  /**
   * Helper function to register LiveAllocation components on tooltips. This function is responsible for setting the
   * active tooltip on the stage when a mouse enters the desired component.
   */
  protected abstract void registerTooltip(ViewBinder<StageView, TooltipModel, TooltipView> binder,
                                          @NotNull RangeTooltipComponent tooltip,
                                          Stage stage);

  abstract protected void populateUi(RangeTooltipComponent tooltipComponent);

  /**
   * To get toolbar icons specific to live data view.
   * @return JComponent
   */
  public JComponent getToolbar() {
    return null;
  }
}
