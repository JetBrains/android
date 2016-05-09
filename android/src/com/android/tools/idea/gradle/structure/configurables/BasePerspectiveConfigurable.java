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

import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowHeader;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.revalidateAndRepaint;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.ui.navigation.Place.goFurther;
import static com.intellij.ui.navigation.Place.queryFurther;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;

public abstract class BasePerspectiveConfigurable extends MasterDetailsComponent
  implements SearchableConfigurable, Disposable, Place.Navigator {

  @NotNull private final PsContext myContext;

  protected boolean myUiDisposed = true;

  private ToolWindowHeader myHeader;
  private JBLoadingPanel myLoadingPanel;
  private JComponent myCenterComponent;

  private boolean myTreeInitiated;
  private boolean myTreeMinimized;

  // This flag prevents an infinite loop started when a module is selected.
  // When a module is selected, the selected module is recorded in PsContext. PsContext then notifies every configurable about the module
  // selection change. Each configurable adjusts its selected module, but if they notify PsContext about this change, the notification
  // cycle will start all over again.
  private volatile boolean mySelectModuleQuietly;

  protected BasePerspectiveConfigurable(@NotNull PsContext context) {
    context.add(new GradleSyncListener.Adapter() {
      @Override
      public void syncStarted(@NotNull Project project) {
        if (myLoadingPanel != null) {
          myLoadingPanel.startLoading();
        }
      }

      @Override
      public void syncSucceeded(@NotNull Project project) {
        stopSyncAnimation();
      }

      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        stopSyncAnimation();
      }
    }, this);

    myContext = context;
    myContext.add((moduleName, source) -> {
      if (source != this) {
        mySelectModuleQuietly = true;
        selectModule(moduleName);
      }
    }, this);
    myContext.getAnalyzerDaemon().add(model -> {
      if (myTree.isShowing()) {
        // If issues are updated and the tree is showing, trigger a repaint so the proper highlight and tooltip is applied.
        invokeLaterIfNeeded(() -> revalidateAndRepaint(myTree));
      }
    }, this);

    myTreeMinimized = PsUISettings.getInstance().MODULES_LIST_MINIMIZE;
    if (myTreeMinimized) {
      myToReInitWholePanel = true;
      reInitWholePanelIfNeeded();
    }
    PsUISettings.getInstance().addListener(settings -> {
      if (settings.MODULES_LIST_MINIMIZE != myTreeMinimized) {
        myTreeMinimized = settings.MODULES_LIST_MINIMIZE;
        myToReInitWholePanel = true;
        reInitWholePanelIfNeeded();
      }
    }, this);
  }

  private void stopSyncAnimation() {
    if (myLoadingPanel != null) {
      myLoadingPanel.stopLoading();
    }
  }

  private void selectModule(@NotNull String moduleName) {
    PsModule module = findModule(moduleName);
    if (module != null) {
      MyNode node = findNodeByObject(myRoot, module);
      if (node != null) {
        selectNodeInTree(moduleName);
        setSelectedNode(node);
      }
    }
  }

  @Nullable
  protected PsModule findModule(@NotNull String moduleName) {
    PsModule module = myContext.getProject().findModuleByName(moduleName);
    if (module == null) {
      for (PsModule extraModule : getExtraTopModules()) {
        if (moduleName.equals(extraModule.getName())) {
          module = extraModule;
          break;
        }
      }
    }
    return module;
  }

  @Override
  protected void updateSelection(@Nullable NamedConfigurable configurable) {
    super.updateSelection(configurable);
    if (configurable instanceof BaseNamedConfigurable) {
      BaseNamedConfigurable baseConfigurable = (BaseNamedConfigurable)configurable;
      PsModule module = baseConfigurable.getEditableObject();
      if (!mySelectModuleQuietly) {
        myContext.setSelectedModule(module.getName(), this);
      }
      else {
        mySelectModuleQuietly = false;
      }
    }
    myHistory.pushQueryPlace();
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
      revalidateAndRepaint(myWholePanel);
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
        revalidateAndRepaint(myWholePanel);
      }

      myCenterComponent = splitter;

      JComponent first = splitter.getFirstComponent();
      if (first instanceof JPanel && myHeader == null) {
        JPanel panel = (JPanel)first;
        myHeader = ToolWindowHeader.createAndAdd("Modules", AllIcons.Nodes.Module, panel, ToolWindowAnchor.LEFT);
        myHeader.setPreferredFocusedComponent(myTree);
        myHeader.addMinimizeListener(() -> {
          modulesTreeMinimized();
          reInitWholePanelIfNeeded();
        });
      }
    }
  }

  private void modulesTreeMinimized() {
    PsUISettings settings = PsUISettings.getInstance();
    settings.MODULES_LIST_MINIMIZE = myTreeMinimized = myToReInitWholePanel = true;
    settings.fireUISettingsChanged();
  }

  @Override
  public void reset() {
    myUiDisposed = false;

    if (!myTreeInitiated) {
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
    if (myTreeInitiated) {
      return;
    }
    myTreeInitiated = true;
    super.initTree();
    myTree.setRootVisible(false);

    new TreeSpeedSearch(myTree, treePath -> ((MyNode)treePath.getLastPathComponent()).getDisplayName(), true);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    myTree.setCellRenderer(new PsModuleCellRenderer(myContext));
  }

  protected void loadTree() {
    List<PsModule> extraTopModules = getExtraTopModules();
    createModuleNodes(extraTopModules);
    ((DefaultTreeModel)myTree.getModel()).reload();
    myUiDisposed = false;
  }

  @Override
  @NotNull
  public JComponent createComponent() {
    JComponent contents = super.createComponent();
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), this);
    myLoadingPanel.setLoadingText("Syncing Project with Gradle");
    myLoadingPanel.add(contents, BorderLayout.CENTER);
    return myLoadingPanel;
  }

  @NotNull
  protected List<PsModule> getExtraTopModules() {
    return Collections.emptyList();
  }

  private void createModuleNodes(@NotNull List<PsModule> extraTopModules) {
    extraTopModules.forEach(this::addConfigurableFor);
    myContext.getProject().forEachModule(this::addConfigurableFor);
  }

  private void addConfigurableFor(@NotNull PsModule module) {
    NamedConfigurable<? extends PsModule> configurable = getConfigurable(module);
    if (configurable != null) {
      MyNode node = new MyNode(configurable);
      myRoot.add(node);
    }
  }

  @Nullable
  protected abstract NamedConfigurable<? extends PsModule> getConfigurable(@NotNull PsModule module);

  @NotNull
  protected PsContext getContext() {
    return myContext;
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    if (place != null) {
      Object path = place.getPath(getNavigationPathName());
      if (path instanceof String) {
        String moduleName = (String)path;
        if (!isEmpty(moduleName)) {
          ActionCallback callback = new ActionCallback();
          getContext().setSelectedModule(moduleName, this);
          selectModule(moduleName);
          NamedConfigurable selectedConfigurable = getSelectedConfigurable();
          if (selectedConfigurable != null) {
            goFurther(selectedConfigurable, place, requestFocus).notifyWhenDone(callback);
            return callback;
          }
        }
      }
    }
    return ActionCallback.DONE;
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    NamedConfigurable selectedConfigurable = getSelectedConfigurable();
    if (selectedConfigurable instanceof BaseNamedConfigurable) {
      PsModule module = ((BaseNamedConfigurable)selectedConfigurable).getEditableObject();
      String moduleName = module.getName();
      place.putPath(getNavigationPathName(), moduleName);
      queryFurther(selectedConfigurable, place);
      return;
    }
    place.putPath(getNavigationPathName(), "");
  }

  @NotNull
  protected abstract String getNavigationPathName();

  @Override
  @Nullable
  public NamedConfigurable getSelectedConfigurable() {
    TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      MyNode node = (MyNode)selectionPath.getLastPathComponent();
      return node.getConfigurable();
    }
    return null;
  }

  public void putNavigationPath(@NotNull Place place, @NotNull String moduleName, @NotNull String dependency) {
    place.putPath(getNavigationPathName(), moduleName);

    PsModule module = findModule(moduleName);
    assert module != null;

    MyNode node = findNodeByObject(myRoot, module);
    assert node != null;

    NamedConfigurable configurable = node.getConfigurable();
    assert configurable instanceof BaseNamedConfigurable;

    BaseNamedConfigurable dependenciesConfigurable = (BaseNamedConfigurable)configurable;
    dependenciesConfigurable.putNavigationPath(place, dependency);
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
