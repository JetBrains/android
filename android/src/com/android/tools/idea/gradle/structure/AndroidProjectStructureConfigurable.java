/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure;

import com.android.tools.idea.actions.AndroidNewModuleAction;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.structure.AndroidModuleConfigurable;
import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesAlphaComparator;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.IconUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Contents of the "Project Structure" dialog, for Gradle-based Android projects, in Android Studio.
 */
public class AndroidProjectStructureConfigurable extends BaseConfigurable implements GradleSyncListener, SearchableConfigurable,
                                                                                     ConfigurableHost {
  public static final DataKey<AndroidProjectStructureConfigurable> KEY = DataKey.create("AndroidProjectStructureConfiguration");
  @NotNull private final Project myProject;
  @NotNull private final Disposable myDisposable;

  private boolean myUiInitialized;
  private JPanel myNotificationPanel;
  private Splitter mySplitter;
  private SidePanel mySidePanel;
  private ConfigurationErrorsPanel myErrorsPanel;

  @NotNull private final Wrapper myDetails = new Wrapper();
  @NotNull private final UiState myUiState;

  @NotNull private final DefaultSdksConfigurable mySdksConfigurable = new DefaultSdksConfigurable(this);
  @NotNull private final Map<String, AndroidModuleConfigurable> myModuleConfigurablesByName = Maps.newHashMap();

  private JComponent myToFocus;

  @NotNull
  public static AndroidProjectStructureConfigurable getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidProjectStructureConfigurable.class);
  }

  public boolean showDialogAndSelect(@NotNull final Module module) {
    return doShowDialog(new Runnable() {
      @Override
      public void run() {
        mySidePanel.select(module);
      }
    });
  }

  public boolean showDialog() {
    return doShowDialog(null);
  }

  private boolean doShowDialog(@Nullable Runnable advanceInit) {
    return ShowSettingsUtil.getInstance().editConfigurable(myProject, this, advanceInit);
  }

  public AndroidProjectStructureConfigurable(@NotNull Project project) {
    myProject = project;
    myUiState = new UiState(project);

    myDisposable = new Disposable() {
      @Override
      public void dispose() {
      }
    };
  }

  @Override
  @Nls
  public String getDisplayName() {
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
    JComponent component = new MainPanel();

    mySplitter = new Splitter(false, .15f);
    mySplitter.setHonorComponentsMinimumSize(true);

    initSidePanel();

    mySplitter.setFirstComponent(mySidePanel);
    mySplitter.setSecondComponent(myDetails);

    component.add(mySplitter, BorderLayout.CENTER);

    myNotificationPanel = new JPanel();

    Color background = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.GUTTER_BACKGROUND);
    if (background == null) {
      background = JBColor.GRAY;
    }
    myNotificationPanel.setBackground(background);
    myNotificationPanel.setLayout(new BoxLayout(myNotificationPanel, BoxLayout.Y_AXIS));
    component.add(myNotificationPanel, BorderLayout.NORTH);

    myErrorsPanel = new ConfigurationErrorsPanel();
    component.add(myErrorsPanel, BorderLayout.SOUTH);

    myUiInitialized = true;

    MessageBusConnection connection = myProject.getMessageBus().connect(myDisposable);
    connection.subscribe(GradleSyncState.GRADLE_SYNC_TOPIC, this);

    return component;
  }

  private void initSidePanel() {
    mySidePanel = new SidePanel();
  }

  @Override
  public boolean isModified() {
    if (mySdksConfigurable.isModified()) {
      return true;
    }
    for (AndroidModuleConfigurable configurable : myModuleConfigurablesByName.values()) {
      if (configurable.isModified()) {
        return true;
      }
    }
    return super.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    validateState();
    if (myErrorsPanel.hasCriticalErrors()) {
      return;
    }

    apply(mySdksConfigurable);
    for (AndroidModuleConfigurable configurable : myModuleConfigurablesByName.values()) {
      apply(configurable);
    }

    ThreeState syncNeeded = GradleSyncState.getInstance(myProject).isSyncNeeded();
    if (syncNeeded == ThreeState.YES) {
      GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
    }
  }

  private static void apply(@NotNull Configurable c) throws ConfigurationException {
    if (c.isModified()) {
      c.apply();
    }
  }

  @Override
  public void reset() {
    // Need this to ensure VFS operations will not block because of storage flushing and other maintenance IO tasks run in background.
    HeavyProcessLatch.INSTANCE.processStarted();

    try {
      mySdksConfigurable.reset();

      myModuleConfigurablesByName.clear();
      if (myUiInitialized) {
        validateState();

        Module toSelect = null;

        // Populate the "Modules" section.
        ModuleManager moduleManager = ModuleManager.getInstance(myProject);
        mySidePanel.removeModules();

        Module[] modules = moduleManager.getModules();
        Arrays.sort(modules, ModulesAlphaComparator.INSTANCE);

        for (Module module : modules) {
          if (addModule(module)) {
            AndroidModuleConfigurable c = getConfigurableFor(module);
            assert c != null;
            if (myUiState.lastSelectedConfigurable != null && myUiState.lastSelectedConfigurable.equals(c.getDisplayName())) {
              toSelect = module;
            }
          }
        }

        if (myUiState.proportion > 0) {
          mySplitter.setProportion(myUiState.proportion);
        }

        if (toSelect != null) {
          mySidePanel.select(toSelect);
        }
        else {
          mySidePanel.selectSdk();
        }
      }
    }
    finally {
      HeavyProcessLatch.INSTANCE.processFinished();
    }
  }

  private boolean addModule(@NotNull Module module) {
    String gradlePath = getGradlePath(module);
    if (StringUtil.isNotEmpty(gradlePath)) {
      AndroidModuleConfigurable c = new AndroidModuleConfigurable(myProject, gradlePath);
      c.reset();
      myModuleConfigurablesByName.put(module.getName(), c);
      mySidePanel.add(module);
      return true;
    }
    return false;
  }

  private void validateState() {
    myErrorsPanel.removeAllErrors();
    List<ProjectConfigurationError> errors = mySdksConfigurable.validateState();
    if (!errors.isEmpty()) {
      Runnable navigationTask = new Runnable() {
        @Override
        public void run() {
          selectSdkHomeConfigurable(false);
        }
      };
      for (ProjectConfigurationError error : errors) {
        error.setNavigationTask(navigationTask);
      }
    }
    myErrorsPanel.addErrors(errors);
  }

  @Nullable
  private static String getGradlePath(@NotNull Module module) {
    AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
    return gradleFacet != null ? gradleFacet.getConfiguration().GRADLE_PROJECT_PATH : null;
  }

  private void selectSdkHomeConfigurable(boolean requestFocus) {
    selectConfigurable(mySdksConfigurable, requestFocus);
  }

  private void selectModuleConfigurable(@NotNull Module module) {
    AndroidModuleConfigurable configurable = getConfigurableFor(module);
    if (configurable != null) {
      selectConfigurable(configurable, true);
    }
  }

  @Nullable
  private AndroidModuleConfigurable getConfigurableFor(@NotNull Module module) {
    return myModuleConfigurablesByName.get(module.getName());
  }

  private void selectConfigurable(@NotNull Configurable configurable, boolean requestFocus) {
    JComponent content = configurable.createComponent();
    assert content != null;
    myDetails.setContent(content);

    if (requestFocus) {
      JComponent toFocus;
      if (configurable instanceof BaseConfigurable) {
        toFocus = ((BaseConfigurable)configurable).getPreferredFocusedComponent();
      }
      else {
        toFocus = IdeFocusTraversalPolicy.getPreferredFocusedComponent(content);
      }
      if (toFocus == null) {
        toFocus = content;
      }
      myToFocus = toFocus;
      toFocus.requestFocusInWindow();
    }

    myUiState.lastSelectedConfigurable = configurable.getDisplayName();

    revalidateAndRepaint(myDetails);
  }

  @Override
  public void disposeUIResources() {
    if (!myUiInitialized) {
      return;
    }
    myUiState.storeValues(myProject);
    myUiState.proportion = mySplitter.getProportion();

    mySdksConfigurable.disposeUIResources();
    for (AndroidModuleConfigurable configurable : myModuleConfigurablesByName.values()) {
      configurable.disposeUIResources();
    }

    Disposer.dispose(myDisposable);
    Disposer.dispose(myErrorsPanel);

    myUiInitialized = false;
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

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myToFocus;
  }

  @Override
  public void syncStarted(@NotNull Project project) {
    if (myUiInitialized) {
      myNotificationPanel.removeAll();
      EditorNotificationPanel notification = new EditorNotificationPanel();
      notification.setText("Gradle project sync in progress...");
      myNotificationPanel.add(notification);
      revalidateAndRepaint(myNotificationPanel);
    }
  }

  @Override
  public void syncEnded(@NotNull Project project) {
    myNotificationPanel.removeAll();
    revalidateAndRepaint(myNotificationPanel);
    reset();
  }

  @Override
  public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
    myNotificationPanel.removeAll();
    revalidateAndRepaint(myNotificationPanel);
    reset();
  }

  private static void revalidateAndRepaint(@NotNull JComponent c) {
    c.revalidate();
    c.repaint();
  }

  @Override
  public void requestValidation() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (myErrorsPanel != null) {
          validateState();
        }
      }
    });
  }

  private class MainPanel extends JPanel implements DataProvider {
    MainPanel() {
      super(new BorderLayout());
    }

    @Override
    @Nullable
    public Object getData(@NonNls final String dataId) {
      if (KEY.is(dataId)) {
        return AndroidProjectStructureConfigurable.this;
      } else {
        return null;
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(1024, 768);
    }
  }

  private class SidePanel extends JPanel {
    private static final int SDKS_ELEMENT_INDEX = 0;
    private static final int FIRST_MODULE_ELEMENT_INDEX = 1;

    @NotNull private final JBList myList;
    @NotNull private final DefaultListModel myListModel;

    SidePanel() {
      super(new BorderLayout());
      myListModel = new DefaultListModel();
      myListModel.addElement("SDK Location");

      myList = new JBList(myListModel);

      ListItemDescriptor descriptor = new ListItemDescriptor() {
        @Override
        @Nullable
        public String getTextFor(Object value) {
          if (value instanceof Module) {
            return ((Module)value).getName();
          }
          return value != null ? value.toString() : "";
        }

        @Override
        @Nullable
        public String getTooltipFor(Object value) {
          if (value instanceof Module) {
            return new File(((Module)value).getModuleFilePath()).getAbsolutePath();
          }
          return null;
        }

        @Override
        @Nullable
        public Icon getIconFor(Object value) {
          if (value instanceof Module) {
            return AllIcons.Actions.Module;
          }
          return null;
        }

        @Override
        public boolean hasSeparatorAboveOf(Object value) {
          return myListModel.indexOf(value) == FIRST_MODULE_ELEMENT_INDEX;
        }

        @Override
        @Nullable
        public String getCaptionAboveOf(Object value) {
          return hasSeparatorAboveOf(value) ? "Modules" : null;
        }
      };

      myList.setCellRenderer(new GroupedItemsListRenderer(descriptor));
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myList.addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          if (e.getValueIsAdjusting()) {
            return;
          }
          if (myList.getSelectedIndex() == SDKS_ELEMENT_INDEX) {
            selectSdkHomeConfigurable(true);
          }
          else {
            Object selection = myList.getSelectedValue();
            if (selection instanceof Module) {
              selectModuleConfigurable((Module)selection);
            }
          }
        }
      });

      add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER);

      if (!myProject.isDefault()) {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(createAddAction());
        JComponent toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
        add(toolbar, BorderLayout.NORTH);
      }
    }

    @NotNull
    private AnAction createAddAction() {
      AndroidNewModuleAction action = new AndroidNewModuleAction("New Module", null, IconUtil.getAddIcon());
      Keymap active = KeymapManager.getInstance().getActiveKeymap();
      if (active != null) {
        Shortcut[] shortcuts = active.getShortcuts("NewElement");
        action.registerCustomShortcutSet(new CustomShortcutSet(shortcuts), this);
      }
      return action;
    }

    @Override
    public Dimension getMinimumSize() {
      Dimension original = super.getMinimumSize();
      return new Dimension(Math.max(original.width, 100), original.height);
    }

    void removeModules() {
      int size = myListModel.getSize();
      if (size > 1) {
        myListModel.removeRange(FIRST_MODULE_ELEMENT_INDEX, size - 1);
      }
    }

    void add(@NotNull Module module) {
      myListModel.addElement(module);
    }

    void select(@NotNull Module module) {
      myList.setSelectedValue(module, true);
    }

    void selectSdk() {
      myList.setSelectedIndex(SDKS_ELEMENT_INDEX);
    }
  }

  private static class UiState {
    private static final String ANDROID_PROJECT_STRUCTURE_LAST_SELECTED_PROPERTY = "android.project.structure.last.selected";
    private static final String ANDROID_PROJECT_STRUCTURE_PROPORTION_PROPERTY = "android.project.structure.proportion";

    float proportion;
    String lastSelectedConfigurable;

    UiState(@NotNull Project project) {
      PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
      lastSelectedConfigurable = propertiesComponent.getValue(ANDROID_PROJECT_STRUCTURE_LAST_SELECTED_PROPERTY);
      proportion = toFloat(propertiesComponent.getValue(ANDROID_PROJECT_STRUCTURE_PROPORTION_PROPERTY));
    }

    private static float toFloat(@Nullable String val) {
      if (val != null) {
        try {
          return Float.parseFloat(val);
        }
        catch (NumberFormatException ignored) {
        }
      }
      return 0.15f;
    }

    void storeValues(@NotNull Project project) {
      PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
      propertiesComponent.setValue(ANDROID_PROJECT_STRUCTURE_LAST_SELECTED_PROPERTY, lastSelectedConfigurable);
      propertiesComponent.setValue(ANDROID_PROJECT_STRUCTURE_PROPORTION_PROPERTY, String.valueOf(proportion));
    }
  }
}
