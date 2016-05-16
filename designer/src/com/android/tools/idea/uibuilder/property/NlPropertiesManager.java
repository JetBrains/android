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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.idea.uibuilder.editor.NlPropertiesWindowManager;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.property.editors.NlPropertyEditors;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.util.PropertiesMap;
import com.google.common.collect.Table;
import com.intellij.designer.LightToolWindowContent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class NlPropertiesManager implements DesignSurfaceListener, ModelListener, LightToolWindowContent {
  public final static int UPDATE_DELAY_MSECS = 250;

  private final Project myProject;
  private final JBLoadingPanel myLoadingPanel;
  private final NlPropertiesPanel myPropertiesPanel;
  private final NlPropertyEditors myEditors;

  @Nullable private DesignSurface mySurface;
  @Nullable private ScreenView myScreenView;

  private MergingUpdateQueue myUpdateQueue;
  private boolean myFirstLoad = true;
  private boolean myLoading;

  public static NlPropertiesManager get(@NotNull Project project) {
    NlPropertiesWindowManager windowManager = NlPropertiesWindowManager.get(project);
    return windowManager.getPropertiesManager();
  }

  public NlPropertiesManager(@NotNull Project project, @Nullable DesignSurface designSurface) {
    myProject = project;
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), project, 20);
    myEditors = new NlPropertyEditors(project);
    myPropertiesPanel = new NlPropertiesPanel(project);
    myLoadingPanel.add(myPropertiesPanel);
    setDesignSurface(designSurface);
  }

  public void setDesignSurface(@Nullable DesignSurface designSurface) {
    if (designSurface == mySurface) {
      return;
    }

    if (mySurface != null) {
      mySurface.removeListener(this);
    }

    mySurface = designSurface;
    if (mySurface == null) {
      setScreenView(null);
    }
    else {
      mySurface.addListener(this);
      ScreenView screenView = mySurface.getCurrentScreenView();
      setScreenView(screenView);
      List<NlComponent> selection = screenView != null ?
                                    screenView.getSelectionModel().getSelection() : Collections.emptyList();
      componentSelectionChanged(mySurface, selection);
    }
  }

  private void setScreenView(@Nullable ScreenView screenView) {
    if (screenView == myScreenView) {
      return;
    }

    if (myScreenView != null) {
      myScreenView.getModel().removeListener(this);
    }

    myScreenView = screenView;
    if (myScreenView != null) {
      myScreenView.getModel().addListener(this);
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public NlPropertyEditors getPropertyEditors() {
    return myEditors;
  }

  @NotNull
  public JComponent getConfigurationPanel() {
    return myLoadingPanel;
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

  private void setSelectedComponents(@NotNull List<NlComponent> components, @Nullable Runnable postUpdateRunnable) {
    assert !components.isEmpty();

    // Obtaining the properties, especially the first time around on a big project
    // can take close to a second, so we do it on a separate thread..
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Table<String, String, NlPropertyItem> properties = NlProperties.getInstance().getProperties(components);

      UIUtil.invokeLaterIfNeeded(() -> {
        if (myProject.isDisposed()) {
          return;
        }
        myPropertiesPanel.setItems(components, properties, this);
        if (postUpdateRunnable != null) {
          myLoading = false;
          postUpdateRunnable.run();
        }
      });
    });
  }

  @NotNull
  public PropertiesMap getDefaultProperties(@NotNull List<NlComponent> components) {
    if (components.isEmpty()) {
      return PropertiesMap.EMPTY_MAP;
    }
    if (mySurface == null) {
      return PropertiesMap.EMPTY_MAP;
    }
    ScreenView view = mySurface.getCurrentScreenView();
    if (view == null) {
      return PropertiesMap.EMPTY_MAP;
    }
    Map<Object, PropertiesMap> map = view.getModel().getDefaultProperties();
    List<PropertiesMap> propertiesMaps = new ArrayList<>(components.size());
    for (NlComponent component : components) {
      PropertiesMap propertiesMap = map.get(component.getSnapshot());
      if (propertiesMap == null) {
        return PropertiesMap.EMPTY_MAP;
      }
      propertiesMaps.add(propertiesMap);
    }
    PropertiesMap first = propertiesMaps.get(0);
    if (propertiesMaps.size() == 1) {
      return first;
    }
    PropertiesMap commonProperties = new PropertiesMap();
    for (Map.Entry<String, PropertiesMap.Property> property : first.entrySet()) {
      boolean include = true;
      for (int index = 1; index < propertiesMaps.size(); index++) {
        PropertiesMap other = propertiesMaps.get(index);
        if (!property.getValue().equals(other.get(property.getKey()))) {
          include = false;
          break;
        }
      }
      if (include) {
        commonProperties.put(property.getKey(), property.getValue());
      }
    }
    return commonProperties;
  }

  public void setValue(@NotNull NlProperty property, @Nullable String value) {
    property.setValue(value);

    // TODO: refresh all custom inspectors
  }

  public void activatePreferredEditor() {
    myPropertiesPanel.activatePreferredEditor(myLoading);
  }

  // ---- Implements DesignSurfaceListener ----

  @Override
  public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull final List<NlComponent> newSelection) {
    if (surface != mySurface) {
      return;
    }

    MergingUpdateQueue queue = getUpdateQueue();
    if (queue == null) {
      return;
    }

    if (newSelection.isEmpty()) {
      return;
    }

    if (myFirstLoad) {
      myFirstLoad = false;
      myLoadingPanel.startLoading();
    }
    myLoading = true;
    queue.queue(new Update("updateProperties") {
      @Override
      public void run() {
        setSelectedComponents(newSelection, myLoadingPanel::stopLoading);
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  @Override
  public void screenChanged(@NotNull DesignSurface surface, @Nullable ScreenView screenView) {
  }

  @Override
  public void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model) {
  }

  @Override
  public void modelChanged(@NotNull NlModel model) {
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
    myPropertiesPanel.modelRendered(this);
  }

  @Override
  public void dispose() {
  }
}
