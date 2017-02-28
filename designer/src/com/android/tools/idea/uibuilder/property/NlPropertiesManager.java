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

import com.android.SdkConstants;
import com.android.ide.common.res2.ResourceItem;
import com.android.tools.adtui.workbench.ToolContent;
import com.android.tools.idea.uibuilder.analytics.NlUsageTrackerManager;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.property.editors.NlPropertyEditors;
import com.android.tools.idea.uibuilder.property.inspector.InspectorPanel;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneView;
import com.android.util.PropertiesMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.IdeaApplication;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.uibuilder.property.ToggleSlicePropertyEditor.SLICE_PROPERTY_EDITOR;

public class NlPropertiesManager implements ToolContent<DesignSurface>, DesignSurfaceListener, ModelListener {
  public final static int UPDATE_DELAY_MSECS = 250;

  private final Project myProject;
  private final JBLoadingPanel myLoadingPanel;
  private final NlPropertiesPanel myPropertiesPanel;
  private final NlPropertyEditors myEditors;

  @Nullable private DesignSurface mySurface;
  @Nullable private SceneView mySceneView;

  private MergingUpdateQueue myUpdateQueue;
  private boolean myFirstLoad = true;
  private boolean myLoading;
  private int myUpdateCount;

  public NlPropertiesManager(@NotNull Project project, @Nullable NlDesignSurface designSurface) {
    myProject = project;
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), project, 20);
    myEditors = NlPropertyEditors.getInstance(project);
    myPropertiesPanel = new NlPropertiesPanel(this, this);
    myLoadingPanel.add(myPropertiesPanel);
    setToolContext(designSurface);
  }

  // TODO:
  public void activatePropertySheet() {
    myPropertiesPanel.activatePropertySheet();
  }

  // TODO:
  public void activateInspector() {
    myPropertiesPanel.activateInspector();
  }

  @Override
  public void setToolContext(@Nullable DesignSurface designSurface) {
    if (designSurface == mySurface) {
      return;
    }

    if (mySurface != null) {
      mySurface.removeListener(this);
    }

    mySurface = designSurface;
    if (mySurface == null) {
      setSceneView(null);
    }
    else {
      mySurface.addListener(this);
      SceneView sceneView = mySurface.getCurrentSceneView();
      setSceneView(sceneView);
      List<NlComponent> selection = sceneView != null ?
                                    sceneView.getSelectionModel().getSelection() : Collections.emptyList();
      componentSelectionChanged(mySurface, selection);
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myLoadingPanel;
  }

  @NotNull
  @Override
  public JComponent getFocusedComponent() {
    return myPropertiesPanel;
  }

  @NotNull
  @Override
  public List<AnAction> getGearActions() {
    if (!Boolean.getBoolean(IdeaApplication.IDEA_IS_INTERNAL_PROPERTY)) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new ToggleSlicePropertyEditor(this));
  }

  @NotNull
  @Override
  public List<AnAction> getAdditionalActions() {
    return Collections.singletonList(new ViewAllPropertiesAction(myPropertiesPanel));
  }

  @Override
  public boolean supportsFiltering() {
    return true;
  }

  @Override
  public void setFilter(@NotNull String filter) {
    myPropertiesPanel.setFilter(filter);
  }

  @Override
  @NotNull
  public KeyListener getFilterKeyListener() {
    return myPropertiesPanel.getFilterKeyListener();
  }

  @Nullable
  public DesignSurface getDesignSurface() {
    return mySurface;
  }

  private void setSceneView(@Nullable SceneView sceneView) {
    if (sceneView == mySceneView) {
      return;
    }

    if (mySceneView != null) {
      mySceneView.getModel().removeListener(this);
    }

    mySceneView = sceneView;
    if (mySceneView != null) {
      mySceneView.getModel().addListener(this);
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
  public InspectorPanel getInspector() {
    return myPropertiesPanel.getInspector();
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
    // Obtaining the properties, especially the first time around on a big project
    // can take close to a second, so we do it on a separate thread..
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Table<String, String, NlPropertyItem> properties =
        !components.isEmpty() ? NlProperties.getInstance().getProperties(this, components)
                              : ImmutableTable.of();

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
    SceneView view = mySurface.getCurrentSceneView();
    if (view == null) {
      return PropertiesMap.EMPTY_MAP;
    }
    Map<Object, PropertiesMap> map = ((LayoutlibSceneManager)view.getSceneManager()).getDefaultProperties();
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

  public void updateSelection() {
    if (mySurface == null || mySceneView == null) {
      return;
    }
    List<NlComponent> selection = mySceneView.getModel().getSelectionModel().getSelection();
    componentSelectionChanged(mySurface, selection);
    myUpdateCount++;
  }

  @TestOnly
  int getUpdateCount() {
    return myUpdateCount;
  }

  public void propertyChanged(@NotNull NlProperty property, @Nullable String oldValue, @Nullable String newValue) {
    if (property.getComponents().size() == 1 &&
        SdkConstants.VIEW_MERGE.equals(property.getComponents().get(0).getTagName()) &&
        SdkConstants.TOOLS_URI.equals(property.getNamespace()) &&
        SdkConstants.ATTR_PARENT_TAG.equals(property.getName())) {
      // Special case: When the tools:parentTag is updated on a <merge> tag, the set of attributes for
      // the <merge> tag may change e.g. if the value is set to "LinearLayout" the <merge> tag will
      // then have all attributes from a <LinearLayout>. Force an update of all properties:
      updateSelection();
      return;
    }

    if (PropertiesComponent.getInstance().getBoolean(SLICE_PROPERTY_EDITOR) &&
        (NlPropertyItem.isReference(oldValue) || NlPropertyItem.isReference(newValue))) {
      updateSelection();
    }
  }

  public void starStateChanged() {
    updateSelection();
  }

  @SuppressWarnings("unused")
  public void resourceChanged(@NotNull ResourceItem item, @Nullable String oldValue, @Nullable String newValue) {
    if (PropertiesComponent.getInstance().getBoolean(SLICE_PROPERTY_EDITOR) &&
        (NlPropertyItem.isReference(oldValue) || NlPropertyItem.isReference(newValue))) {
      updateSelection();
    }
  }

  public void logPropertyChange(@NotNull NlProperty property) {
    NlUsageTrackerManager.getInstance(mySurface).logPropertyChange(
      property,
      myPropertiesPanel.getPropertiesViewMode(),
      myPropertiesPanel.getFilterMatchCount());
  }

  public void logFavoritesChange(@NotNull String added, @NotNull String removed, @NotNull List<String> favorites) {
    if (mySceneView == null) {
      return;
    }
    NlUsageTrackerManager.getInstance(mySurface).logFavoritesChange(added, removed, favorites, mySceneView.getModel().getFacet());
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

    if (!newSelection.isEmpty() && myFirstLoad) {
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
  public void sceneChanged(@NotNull DesignSurface surface, @Nullable SceneView sceneView) {
  }

  @Override
  public void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model) {
  }

  @Override
  public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
    // Do nothing
  }

  @Override
  public boolean activatePreferredEditor(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    ViewHandler handler = component.getViewHandler();
    String propertyName = handler != null ? handler.getPreferredProperty() : null;
    if (propertyName == null) {
      return false;
    }
    return myPropertiesPanel.activatePreferredEditor(propertyName, myLoading);
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
    myPropertiesPanel.modelRendered(this);
  }

  @Override
  public void dispose() {
    setToolContext(null);
  }
}
