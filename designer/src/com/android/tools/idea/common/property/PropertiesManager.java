/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.property;

import com.android.ide.common.res2.ResourceItem;
import com.android.tools.adtui.workbench.ToolContent;
import com.android.tools.idea.common.analytics.NlUsageTrackerManager;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.property.editors.PropertyEditors;
import com.android.tools.idea.common.property.inspector.InspectorProviders;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.property.NlProperties;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.util.PropertiesMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class PropertiesManager<Self extends PropertiesManager<Self>>
  implements ToolContent<DesignSurface>, DesignSurfaceListener, ModelListener, Disposable {
  public final static int UPDATE_DELAY_MSECS = 250;
  private final static int START_DELAY_MSECS = 20;
  private final static int MINIMUM_WIDTH = 250;

  private final Project myProject;
  private final AndroidFacet myFacet;
  private final PropertyEditors myEditors;
  protected boolean myLoading;
  private JBLoadingPanel myLoadingPanel;
  private PropertiesPanel<Self> myPropertiesPanel;
  @Nullable private DesignSurface mySurface;
  @Nullable protected SceneView mySceneView;
  private MergingUpdateQueue myUpdateQueue;
  private boolean myFirstLoad = true;
  private int myUpdateCount;
  private JBSplitter mySplitter;


  public PropertiesManager(@NotNull AndroidFacet facet, @Nullable DesignSurface designSurface, @NotNull PropertyEditors editors) {
    myProject = facet.getModule().getProject();
    myFacet = facet;
    mySurface = designSurface;
    myEditors = editors;
    setToolContextWithoutCheck(designSurface);
    Disposer.register(facet.getModule().getProject(), this);
  }

  @Override
  public void setToolContext(@Nullable DesignSurface designSurface) {
    if (designSurface == mySurface) {
      return;
    }
    setToolContextWithoutCheck(designSurface);
  }

  @NotNull
  private JBLoadingPanel getLoadingPanel() {
    if (myLoadingPanel == null) {
      myLoadingPanel = new JBLoadingPanel(new BorderLayout(), this, START_DELAY_MSECS);
      myLoadingPanel.setMinimumSize(new Dimension(JBUI.scale(MINIMUM_WIDTH), 0));
      myLoadingPanel.add(getContentPanel());
    }
    return myLoadingPanel;
  }

  @NotNull
  protected PropertiesPanel<Self> getPropertiesPanel() {
    if (myPropertiesPanel == null) {
      myPropertiesPanel = createPropertiesPanel();
    }
    return myPropertiesPanel;
  }

  @NotNull
  protected abstract PropertiesPanel<Self> createPropertiesPanel();

  @NotNull
  protected JBSplitter getContentPanel() {
    if (mySplitter == null) {
      mySplitter = new JBSplitter(true, 0.8f) {
        {
          setDividerWidth(9);
        }

        @Override
        protected Divider createDivider() {
          Divider divider = new DividerImpl();
          divider.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP + SideBorder.BOTTOM));
          return divider;
        }
      };
      mySplitter.setFirstComponent(getPropertiesPanel());
    }

    return mySplitter;
  }

  private void setToolContextWithoutCheck(@Nullable DesignSurface designSurface) {
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
    return getLoadingPanel();
  }

  @NotNull
  @Override
  public JComponent getFocusedComponent() {
    return getContentPanel();
  }

  @NotNull
  @Override
  public List<AnAction> getGearActions() {
    return ImmutableList.of();
  }

  @NotNull
  @Override
  public List<AnAction> getAdditionalActions() {
    return ImmutableList.of();
  }

  @Override
  public boolean supportsFiltering() {
    return false;
  }

  @Nullable
  public DesignSurface getDesignSurface() {
    return mySurface;
  }

  protected void setSceneView(@Nullable SceneView sceneView) {
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
  public AndroidFacet getFacet() {
    return myFacet;
  }

  @NotNull
  public PropertyEditors getPropertyEditors() {
    return myEditors;
  }

  @NotNull
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
      Table<String, String, NlPropertyItem> properties = NlProperties.getInstance().getProperties(myFacet, this, components);

      UIUtil.invokeLaterIfNeeded(() -> {
        if (myProject.isDisposed()) {
          return;
        }
        getPropertiesPanel().setItems(components, properties);
        if (postUpdateRunnable != null) {
          myLoading = false;
          postUpdateRunnable.run();
        }
      });
    });
  }

  public boolean isLoading() {
    return myLoading;
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
    Map<Object, PropertiesMap> map = view.getSceneManager().getDefaultProperties();
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

  @NotNull
  public abstract InspectorProviders<Self> getInspectorProviders(@NotNull Disposable parentDisposable);

  public void setValue(@NotNull NlProperty property, @Nullable String value) {
    property.setValue(value);

    // TODO: refresh all custom inspectors
  }

  public void updateSelection() {
    if (mySurface == null || mySceneView == null) {
      return;
    }
    List<NlComponent> selection = mySceneView.getSelectionModel().getSelection();
    componentSelectionChanged(mySurface, selection);
    myUpdateCount++;
  }

  @TestOnly
  public int getUpdateCount() {
    return myUpdateCount;
  }

  public void propertyChanged(@NotNull NlProperty property, @Nullable String oldValue, @Nullable String newValue) {
  }

  public void starStateChanged() {
    updateSelection();
  }

  @SuppressWarnings("unused")
  public void resourceChanged(@NotNull ResourceItem item, @Nullable String oldValue, @Nullable String newValue) {
  }

  public abstract void logPropertyChange(@NotNull NlProperty property);

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

    if (!newSelection.isEmpty() && myFirstLoad) {
      myFirstLoad = false;
      getLoadingPanel().startLoading();
    }

    myLoading = true;
    MergingUpdateQueue queue = getUpdateQueue();
    queue.queue(new Update("updateProperties") {
      @Override
      public void run() {
        setSelectedComponents(newSelection, getLoadingPanel()::stopLoading);
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  @Override
  public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
    // Do nothing
  }

  /**
   * Find the preferred attribute of the component specified,
   * and bring focus to the editor of this attribute in the inspector.
   * If the inspector is not the current attribute panel: make it the active panel,
   * if the attributes tool window is minimized: restore the attributes tool window.
   *
   * @return true if a preferred attribute was identified and an editor will eventually gain focus in the inspector.
   * false if no preferred attribute was identified.
   */
  @Override
  public boolean activatePreferredEditor(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    return false;
  }

  @Override
  public void modelChanged(@NotNull NlModel model) {
    getPropertiesPanel().modelRendered();
  }

  @Override
  public void dispose() {
    setToolContext(null);
  }
}
