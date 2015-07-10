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
package com.android.tools.idea.uibuilder.property;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.property.ptable.PTable;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class NlPropertiesPanel extends JPanel implements DesignSurfaceListener {
  public final static int UPDATE_DELAY_MSECS = 250;

  private final PTable myTable;
  private final NlPropertiesModel myModel;
  private DesignSurface mySurface;
  private MergingUpdateQueue myUpdateQueue;
  private JBLabel mySelectedComponentLabel;

  public NlPropertiesPanel(@NonNull DesignSurface designSurface) {
    super(new BorderLayout());
    setOpaque(true);
    setFocusable(true);
    setRequestFocusEnabled(true);
    setBackground(UIUtil.TRANSPARENT_COLOR);

    myModel = new NlPropertiesModel();
    myTable = new PTable(myModel);

    myTable.getEmptyText().setText("No selected component");

    JPanel headerPanel = createHeaderPanel();
    add(headerPanel, BorderLayout.NORTH);
    add(new JBScrollPane(myTable), BorderLayout.CENTER);

    setDesignSurface(designSurface);
  }

  @NonNull
  private JPanel createHeaderPanel() {
    JBPanel panel = new JBPanel(new BorderLayout());

    mySelectedComponentLabel = new JBLabel("");
    panel.add(mySelectedComponentLabel, BorderLayout.CENTER);

    ShowExpertProperties showExpertAction = new ShowExpertProperties(myModel);
    ActionButton showExpertButton = new ActionButton(showExpertAction, showExpertAction.getTemplatePresentation(), ActionPlaces.UNKNOWN,
                                                     ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    panel.add(showExpertButton, BorderLayout.LINE_END);

    return panel;
  }

  public void setDesignSurface(@Nullable DesignSurface designSurface) {
    if (designSurface == mySurface) {
      return;
    }
    if (mySurface != null) {
      mySurface.removeListener(this);
    }
    mySurface = designSurface;
    if (mySurface != null) {
      mySurface.addListener(this);
      ScreenView screenView = mySurface.getCurrentScreenView();
      List<NlComponent> selection = screenView != null ?
                                    screenView.getSelectionModel().getSelection() : Collections.<NlComponent>emptyList();
      componentSelectionChanged(mySurface, selection);
    }
  }

  // ---- Implements DesignSurfaceListener ----

  @Override
  public void componentSelectionChanged(@NonNull DesignSurface surface, @NonNull final List<NlComponent> newSelection) {
    if (surface != mySurface) {
      return;
    }
    myTable.setPaintBusy(true);
    MergingUpdateQueue queue = getUpdateQueue();
    if (queue == null) {
      return;
    }
    queue.queue(new Update("updateProperties") {
      @Override
      public void run() {
        myModel.update(newSelection, new Runnable() {
          @Override
          public void run() {
            // TODO: handle multiple selections
            NlComponent first = newSelection.size() == 1 ? newSelection.get(0) : null;
            if (first != null) {
              mySelectedComponentLabel.setText(first.getTagName());
            }
            else {
              mySelectedComponentLabel.setText("");
            }
            myTable.setPaintBusy(false);
          }
        });
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  @Override
  public void screenChanged(@NonNull DesignSurface surface, @Nullable ScreenView screenView) {
  }

  @Override
  public void modelChanged(@NonNull DesignSurface surface, @Nullable NlModel model) {
  }

  @Nullable
  private MergingUpdateQueue getUpdateQueue() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myUpdateQueue == null) {
      myUpdateQueue = new MergingUpdateQueue("android.layout.propertysheet", UPDATE_DELAY_MSECS, true, null, mySurface, null,
                                             Alarm.ThreadToUse.SWING_THREAD);
    }
    return myUpdateQueue;
  }
}
