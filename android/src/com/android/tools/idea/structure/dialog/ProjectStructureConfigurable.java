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
package com.android.tools.idea.structure.dialog;

import com.android.tools.idea.gradle.structure.DefaultSdksConfigurable;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ui.configuration.ConfigurationErrorsComponent;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.SidePanel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.BaseStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.navigation.BackAction;
import com.intellij.ui.navigation.ForwardAction;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.util.ui.UIUtil.SIDE_PANEL_BACKGROUND;
import static com.intellij.util.ui.UIUtil.requestFocus;

public class ProjectStructureConfigurable extends BaseConfigurable
  implements SearchableConfigurable, Place.Navigator, Configurable.NoMargin, Configurable.NoScroll {

  public static final DataKey<ProjectStructureConfigurable> KEY = DataKey.create("ProjectStructureConfiguration");
  @NonNls public static final String CATEGORY = "category";

  @NonNls private static final String LAST_EDITED_PROPERTY = "project.structure.last.edited";
  @NonNls private static final String PROPORTION_PROPERTY = "project.structure.proportion";
  @NonNls private static final String SIDE_PROPORTION_PROPERTY = "project.structure.side.proportion";

  @NotNull private final Project myProject;
  @NotNull private final DefaultSdksConfigurable mySdksConfigurable;
  @NotNull private final Wrapper myDetails = new Wrapper();
  @NotNull private final List<Configurable> myConfigurables = Lists.newArrayList();
  @NotNull private final UIState myUiState = new UIState();
  @NotNull private final StructureConfigurableContext myContext;
  @NotNull private final ModulesConfigurator myModulesConfigurator;

  private History myHistory = new History(this);

  private JBSplitter mySplitter;
  private SidePanel mySidePanel;
  private JPanel myNotificationPanel;
  private JComponent myToolbarComponent;
  private ConfigurationErrorsComponent myErrorsComponent;
  private JComponent myToFocus;

  private boolean myUiInitialized;
  private Configurable mySelectedConfigurable;

  private final JLabel myEmptySelection = new JLabel("<html><body><center>Select a setting to view or edit its details here</center></body></html>", JLabel.CENTER);

  @NotNull
  public static ProjectStructureConfigurable getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectStructureConfigurable.class);
  }

  public ProjectStructureConfigurable(@NotNull Project project) {
    myProject = project;
    mySdksConfigurable = new DefaultSdksConfigurable(this, project);
    mySdksConfigurable.setHistory(myHistory);

    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    myUiState.lastEditedConfigurable = propertiesComponent.getValue(LAST_EDITED_PROPERTY);
    String proportion = propertiesComponent.getValue(PROPORTION_PROPERTY);
    myUiState.proportion = parseFloatValue(proportion);
    String sideProportion = propertiesComponent.getValue(SIDE_PROPORTION_PROPERTY);
    myUiState.sideProportion = parseFloatValue(sideProportion);

    myModulesConfigurator = new ModulesConfigurator(myProject);
    myContext = new StructureConfigurableContext(myProject, myModulesConfigurator);
    myModulesConfigurator.setContext(myContext);
  }

  private static float parseFloatValue(@Nullable String value) {
    if (value != null) {
      try {
        return Float.parseFloat(value);
      }
      catch (NumberFormatException ignored) {
      }
    }
    return 0f;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myToFocus;
  }

  public boolean showDialog() {
    return showDialog(null);
  }

  private boolean showDialog(@Nullable Runnable advanceInit) {
    return ShowSettingsUtil.getInstance().editConfigurable(myProject, this, advanceInit);
  }

  @Override
  public void setHistory(History history) {
    myHistory = history;
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    if (place == null) {
      return null;
    }
    Configurable toSelect = (Configurable)place.getPath(CATEGORY);
    JComponent detailsContent = myDetails.getTargetComponent();

    if (mySelectedConfigurable != toSelect) {
      saveSideProportion();
      removeSelected();
    }

    if (toSelect != null) {
      detailsContent = toSelect.createComponent();
      myDetails.setContent(detailsContent);
    }

    mySelectedConfigurable = toSelect;
    if (mySelectedConfigurable != null) {
      myUiState.lastEditedConfigurable = mySelectedConfigurable.getDisplayName();
    }

    if (toSelect instanceof MasterDetailsComponent) {
      MasterDetailsComponent masterDetails = (MasterDetailsComponent)toSelect;
      if (myUiState.sideProportion > 0) {
        masterDetails.getSplitter().setProportion(myUiState.sideProportion);
      }
      masterDetails.setHistory(myHistory);
    }
    else if (toSelect == mySdksConfigurable) {
      mySdksConfigurable.setHistory(myHistory);
    }

    if (toSelect != null) {
      mySidePanel.select(createPlaceFor(toSelect));
    }

    JComponent toFocus = null;
    if (mySelectedConfigurable instanceof BaseConfigurable) {
      BaseConfigurable configurable = (BaseConfigurable)mySelectedConfigurable;
      toFocus = configurable.getPreferredFocusedComponent();
    }
    else if (mySelectedConfigurable instanceof MasterDetailsComponent) {
      MasterDetailsComponent configurable = (MasterDetailsComponent)mySelectedConfigurable;
      toFocus = configurable.getMaster();
    }

    if (toFocus == null && detailsContent != null) {
      toFocus = IdeFocusTraversalPolicy.getPreferredFocusedComponent(detailsContent);
      if (toFocus == null) {
        toFocus = detailsContent;
      }
    }
    myToFocus = toFocus;
    if (myToFocus != null) {
      requestFocus(myToFocus);
    }

    ActionCallback result = new ActionCallback();
    Place.goFurther(toSelect, place, requestFocus).notifyWhenDone(result);

    myDetails.revalidate();
    myDetails.repaint();

    if (!myHistory.isNavigatingNow() && mySelectedConfigurable != null) {
      myHistory.pushQueryPlace();
    }

    return result;
  }

  private void saveSideProportion() {
    if (mySelectedConfigurable instanceof MasterDetailsComponent) {
      myUiState.sideProportion = ((MasterDetailsComponent)mySelectedConfigurable).getSplitter().getProportion();
    }
  }

  private void removeSelected() {
    myDetails.removeAll();
    mySelectedConfigurable = null;
    myUiState.lastEditedConfigurable = null;

    myDetails.add(myEmptySelection, BorderLayout.CENTER);
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    place.putPath(CATEGORY, mySelectedConfigurable);
    Place.queryFurther(mySelectedConfigurable, place);
  }

  @Override
  @NotNull
  public String getId() {
    return "android.project.structure";
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName () {
      return ProjectBundle.message("project.settings.display.name");
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Override
  @Nullable
  public JComponent createComponent() {
    JPanel component = new MyPanel();
    mySplitter = new OnePixelSplitter(false, .15f);
    mySplitter.setHonorComponentsMinimumSize(true);

    initSidePanel();

    JPanel left = new JPanel(new BorderLayout()) {
      @Override
      public Dimension getMinimumSize() {
        Dimension original = super.getMinimumSize();
        return new Dimension(Math.max(original.width, 100), original.height);
      }
    };

    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(new BackAction(component));
    toolbarGroup.add(new ForwardAction(component));

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarGroup, true);
    toolbar.setTargetComponent(component);
    myToolbarComponent = toolbar.getComponent();

    left.setBackground(SIDE_PANEL_BACKGROUND);
    myToolbarComponent.setBackground(SIDE_PANEL_BACKGROUND);

    left.add(myToolbarComponent, BorderLayout.NORTH);
    left.add(mySidePanel, BorderLayout.CENTER);

    mySplitter.setFirstComponent(left);
    mySplitter.setSecondComponent(myDetails);

    component.add(mySplitter, BorderLayout.CENTER);
    myErrorsComponent = new ConfigurationErrorsComponent(myProject);
    component.add(myErrorsComponent, BorderLayout.SOUTH);

    myUiInitialized = true;

    return component;
  }

  private void initSidePanel() {
    boolean isDefaultProject = myProject == ProjectManager.getInstance().getDefaultProject();

    mySidePanel = new SidePanel(this, myHistory);

    addConfigurable(mySdksConfigurable);

    if (!isDefaultProject) {
      addConfigurables();
    }
  }

  private void addConfigurables() {
    for (ModuleStructureConfigurableContributor contributor : ModuleStructureConfigurableContributor.EP_NAME.getExtensions()) {
      Configurable configurable = contributor.getModuleStructureConfigurable(myProject);
      if (configurable != null) {
        addConfigurable(configurable);
        break;
      }
    }

    for (ProjectStructureItemsContributor contributor : ProjectStructureItemsContributor.EP_NAME.getExtensions()) {
      List<ProjectStructureItemGroup> itemGroups = contributor.getItemGroups(myProject);
      for (ProjectStructureItemGroup group : itemGroups) {
        String name = group.getGroupName();
        mySidePanel.addSeparator(name);
        for (Configurable item : group.getItems()) {
          addConfigurable(item);
        }
      }
    }
  }

  private void addConfigurable(@NotNull Configurable configurable) {
    if (configurable instanceof BaseStructureConfigurable) {
      ((BaseStructureConfigurable)configurable).init(myContext);
    }
    myConfigurables.add(configurable);
    mySidePanel.addPlace(createPlaceFor(configurable), new Presentation(configurable.getDisplayName()));
  }

  @NotNull
  private static Place createPlaceFor(@NotNull Configurable configurable) {
    return new Place().putPath(CATEGORY, configurable);
  }

  @Override
  public void apply() throws ConfigurationException {

  }

  @Override
  public void reset() {
    AccessToken token = HeavyProcessLatch.INSTANCE.processStarted("Resetting Project Structure");
    try {
      mySdksConfigurable.reset();
      myContext.reset();

      Configurable toSelect = null;
      for (Configurable each : myConfigurables) {
        if (myUiState.lastEditedConfigurable != null && myUiState.lastEditedConfigurable.equals(each.getDisplayName())) {
          toSelect = each;
        }
        if (each instanceof MasterDetailsComponent) {
          ((MasterDetailsComponent)each).setHistory(myHistory);
        }
        each.reset();
      }

      myHistory.clear();

      if (toSelect == null && !myConfigurables.isEmpty()) {
        toSelect = myConfigurables.get(0);
      }

      removeSelected();

      navigateTo(toSelect != null ? createPlaceFor(toSelect) : null, false);

      if (myUiState.proportion > 0) {
        mySplitter.setProportion(myUiState.proportion);
      }
    }
    finally {
      token.finish();
    }
  }

  @Override
  public void disposeUIResources() {
    if (!myUiInitialized) {
      return;
    }

    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    propertiesComponent.setValue(LAST_EDITED_PROPERTY, myUiState.lastEditedConfigurable);
    propertiesComponent.setValue(PROPORTION_PROPERTY, String.valueOf(myUiState.proportion));
    propertiesComponent.setValue(SIDE_PROPORTION_PROPERTY, String.valueOf(myUiState.sideProportion));

    myUiState.proportion = mySplitter.getProportion();
    saveSideProportion();
    myContext.getDaemonAnalyzer().stop();
    for (Configurable each : myConfigurables) {
      each.disposeUIResources();
    }
    myConfigurables.clear();

    myContext.clear();
    myModulesConfigurator.getFacetsConfigurator().clearMaps();

    Disposer.dispose(myErrorsComponent);

    myUiInitialized = false;
  }

  @Nullable
  public History getHistory() {
    return myHistory;
  }

  private class MyPanel extends JPanel implements DataProvider {
    MyPanel() {
      super(new BorderLayout());
    }

    @Override
    @Nullable
    public Object getData(@NonNls String dataId) {
      if (KEY.is(dataId)) {
        return ProjectStructureConfigurable.this;
      }
      if (History.KEY.is(dataId)) {
        return getHistory();
      }
      return null;
    }
  }

  public static class UIState {
    public float proportion;
    public float sideProportion;
    @Nullable public String lastEditedConfigurable;
  }
}
