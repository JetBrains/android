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
package com.android.tools.idea.gradle.structure.configurables;

import com.android.tools.idea.gradle.structure.configurables.ui.PsdUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowHeader;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.navigation.Place;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;

public abstract class BasePerspectiveConfigurable extends MasterDetailsComponent
  implements SearchableConfigurable, Disposable, Place.Navigator {

  @NotNull private final PsProject myProjectModel;
  @NotNull private final PsdContext myContext;

  protected boolean myUiDisposed = true;

  private ToolWindowHeader myHeader;
  private JComponent myCenterComponent;

  private boolean myTreeInitialized;
  private boolean myTreeMinimized;

  protected BasePerspectiveConfigurable(@NotNull PsProject projectModel, @NotNull PsdContext context) {
    myProjectModel = projectModel;

    myContext = context;
    myContext.addListener(new PsdContext.ChangeListener() {
      @Override
      public void moduleSelectionChanged(@NotNull String module, @NotNull Object source) {
        if (source != BasePerspectiveConfigurable.this) {
          PsModule moduleModel = myProjectModel.findModuleByName(module);
          if (moduleModel != null) {
            MyNode node = findNodeByObject(myRoot, moduleModel);
            if (node != null) {
              selectNodeInTree(module);
              setSelectedNode(node);
            }
          }
        }
      }
    }, this);

    PsdUISettings settings = PsdUISettings.getInstance();
    myTreeMinimized = settings.MODULES_LIST_MINIMIZE;
    if (myTreeMinimized) {
      myToReInitWholePanel = true;
      reInitWholePanelIfNeeded();
    }
    settings.addListener(new PsdUISettings.ChangeListener() {
      @Override
      public void settingsChanged(@NotNull PsdUISettings settings) {
        if (settings.MODULES_LIST_MINIMIZE != myTreeMinimized) {
          myTreeMinimized = settings.MODULES_LIST_MINIMIZE;
          myToReInitWholePanel = true;
          reInitWholePanelIfNeeded();
        }
      }
    }, this);
  }

  @Override
  protected void updateSelection(@Nullable NamedConfigurable configurable) {
    super.updateSelection(configurable);
    if (configurable instanceof BaseNamedConfigurable) {
      BaseNamedConfigurable baseConfigurable = (BaseNamedConfigurable)configurable;
      PsModule moduleModel = baseConfigurable.getEditableObject();
      myContext.setSelectedModule(moduleModel.getName(), this);
    }
  }

  @Override
  protected void reInitWholePanelIfNeeded() {
    if (!myToReInitWholePanel) {
      return;
    }

    if (myTreeMinimized) {
      Splitter splitter = getSplitter();
      myCenterComponent = splitter.getSecondComponent();

      if (myCenterComponent == null) {
        super.reInitWholePanelIfNeeded();
        splitter = getSplitter();
        myCenterComponent = splitter.getSecondComponent();
      }
      myToReInitWholePanel = false;

      splitter.setSecondComponent(null);
      myWholePanel.remove(splitter);
      myWholePanel.add(myCenterComponent, BorderLayout.CENTER);
      revalidateAndRepaintWholePanel();
    }
    else {
      if (myWholePanel == null) {
        super.reInitWholePanelIfNeeded();
      }
      myToReInitWholePanel = false;

      Splitter splitter = getSplitter();
      if (myCenterComponent != null && myCenterComponent != splitter) {
        myWholePanel.remove(myCenterComponent);
        splitter.setSecondComponent(myCenterComponent);
        myWholePanel.add(splitter);
        revalidateAndRepaintWholePanel();
      }

      myCenterComponent = splitter;

      JComponent first = splitter.getFirstComponent();
      if (first instanceof JPanel && myHeader == null) {
        JPanel panel = (JPanel)first;
        myHeader = ToolWindowHeader.createAndAdd("Modules", AllIcons.Nodes.Module, panel, ToolWindowAnchor.LEFT);
        myHeader.setPreferredFocusedComponent(myTree);
        myHeader.addMinimizeListener(new ToolWindowHeader.MinimizeListener() {
          @Override
          public void minimized() {
            modulesTreeMinimized();
            reInitWholePanelIfNeeded();
          }
        });
      }
    }
  }

  private void revalidateAndRepaintWholePanel() {
    myWholePanel.revalidate();
    myWholePanel.repaint();
  }

  private void modulesTreeMinimized() {
    PsdUISettings settings = PsdUISettings.getInstance();
    settings.MODULES_LIST_MINIMIZE = myTreeMinimized = myToReInitWholePanel = true;
    settings.fireUISettingsChanged();
  }

  @Override
  public void reset() {
    myUiDisposed = false;

    if (!myTreeInitialized) {
      initTree();
      myTree.setShowsRootHandles(false);
      loadTree();
    }
    else {
      super.disposeUIResources();
      myTree.setShowsRootHandles(false);
      loadTree();
    }

    super.reset();
  }

  @Override
  protected void initTree() {
    if (myTreeInitialized) {
      return;
    }
    myTreeInitialized = true;
    super.initTree();
    myTree.setRootVisible(false);

    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Override
      public String convert(final TreePath treePath) {
        return ((MyNode)treePath.getLastPathComponent()).getDisplayName();
      }
    }, true);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    // myTree.setCellRenderer(new ProjectStructureElementRenderer(myContext));
  }

  protected void loadTree() {
    createModuleNodes();
    ((DefaultTreeModel)myTree.getModel()).reload();
    myUiDisposed = false;
  }

  private void createModuleNodes() {
    for (PsModule moduleModel : myProjectModel.getModules()) {
      NamedConfigurable<? extends PsModule> configurable = getConfigurable(moduleModel);
      if (configurable != null) {
        MyNode moduleNode = new MyNode(configurable);
        myRoot.add(moduleNode);
      }
    }
  }

  @Nullable
  protected abstract NamedConfigurable<? extends PsModule> getConfigurable(@NotNull PsModule moduleModel);

  @NotNull
  protected PsProject getProjectModel() {
    return myProjectModel;
  }

  @NotNull
  protected PsdContext getContext() {
    return myContext;
  }

  @Override
  public void disposeUIResources() {
    if (myUiDisposed) {
      return;
    }
    super.disposeUIResources();
    myUiDisposed = true;
    myAutoScrollHandler.cancelAllRequests();
    Disposer.dispose(this);
  }

  @Override
  public void dispose() {
    if (myHeader != null) {
      Disposer.dispose(myHeader);
    }
  }

  @Override
  protected void processRemovedItems() {
  }

  @Override
  protected boolean wasObjectStored(Object editableObject) {
    return false;
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
