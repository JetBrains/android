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

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.actions.AndroidNewModuleAction;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.structure.editors.AndroidModuleConfigurable;
import com.android.tools.idea.gradle.structure.editors.AndroidProjectConfigurable;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.ModuleTypeComparator;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.stats.UsageTracker;
import com.android.tools.idea.structure.services.DeveloperService;
import com.android.tools.idea.structure.services.DeveloperServices;
import com.android.tools.idea.structure.services.ServiceCategory;
import com.android.tools.idea.structure.services.view.ServiceCategoryConfigurable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ThreeState;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.stats.UsageTracker.*;

/**
 * Contents of the "Project Structure" dialog, for Gradle-based Android projects, in Android Studio.
 */
public class AndroidProjectStructureConfigurable extends BaseConfigurable implements GradleSyncListener, SearchableConfigurable, Configurable.NoScroll {
  public static final DataKey<AndroidProjectStructureConfigurable> KEY = DataKey.create("AndroidProjectStructureConfiguration");

  private static final Logger LOG = Logger.getInstance(AndroidProjectStructureConfigurable.class);

  @NotNull private final Project myProject;
  @NotNull private final Disposable myDisposable;

  private boolean myUiInitialized;
  private JPanel myNotificationPanel;
  private Splitter mySplitter;
  private SidePanel mySidePanel;
  private ConfigurationErrorsPanel myErrorsPanel;

  @NotNull private final Wrapper myDetails = new Wrapper();
  @NotNull private final UiState myUiState;

  @NotNull private final IdeSdksConfigurable mySdksConfigurable;
  @NotNull private final List<Configurable> myConfigurables = Lists.newLinkedList();

  private final GradleSettingsFile mySettingsFile;

  @NotNull
  public static AndroidProjectStructureConfigurable getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidProjectStructureConfigurable.class);
  }

  public boolean showDialogAndChooseJdkLocation() {
    return doShowDialog(new Runnable() {
      @Override
      public void run() {
        mySidePanel.chooseJdkLocation();
      }
    });
  }

  public boolean showDialogAndSelectSdksPage() {
    return doShowDialog(new Runnable() {
      @Override
      public void run() {
        mySidePanel.selectSdk();
      }
    });
  }

  public boolean showDialogAndSelect(@NotNull final Module module) {
    return doShowDialog(new Runnable() {
      @Override
      public void run() {
        mySidePanel.select(module);
      }
    });
  }

  public boolean showDialogAndOpenSigningConfiguration(@NotNull final Module module) {
    return doShowDialog(new Runnable() {
      @Override
      public void run() {
        AndroidModuleConfigurable configurable = mySidePanel.select(module);
        if (configurable != null) {
          configurable.openSigningConfiguration();
        }
      }
    });
  }

  public boolean showDialogAndSelectDependency(@NotNull final Module module, @NotNull final GradleCoordinate dependency) {
    return doShowDialog(new Runnable() {
      @Override
      public void run() {
        AndroidModuleConfigurable configurable = mySidePanel.select(module);
        if (configurable != null) {
          configurable.selectDependency(dependency);
        }
      }
    });
  }

  public boolean showDialogAndSelectBuildTypesEditor(@NotNull final Module module) {
    return doShowDialog(new Runnable() {
      @Override
      public void run() {
        AndroidModuleConfigurable configurable = mySidePanel.select(module);
        if (configurable != null) {
          configurable.selectBuildTypesTab();
        }
      }
    });
  }

  public boolean showDialogAndSelectFlavorsEditor(@NotNull final Module module) {
    return doShowDialog(new Runnable() {
      @Override
      public void run() {
        AndroidModuleConfigurable configurable = mySidePanel.select(module);
        if (configurable != null) {
          configurable.selectFlavorsTab();
        }
      }
    });
  }

  public boolean showDialogAndSelectDependenciesEditor(@NotNull final Module module) {
    return doShowDialog(new Runnable() {
      @Override
      public void run() {
        AndroidModuleConfigurable configurable = mySidePanel.select(module);
        if (configurable != null) {
          configurable.selectDependenciesTab();
        }
      }
    });
  }

  public boolean showDialog() {
    return doShowDialog(null);
  }

  private boolean doShowDialog(@Nullable Runnable advanceInit) {
    UsageTracker.getInstance().trackEvent(CATEGORY_PROJECT_STRUCTURE_DIALOG, ACTION_PROJECT_STRUCTURE_DIALOG_OPEN, null, null);
    return ShowSettingsUtil.getInstance().editConfigurable(myProject, this, advanceInit);
  }

  public AndroidProjectStructureConfigurable(@NotNull Project project) {
    myProject = project;
    myUiState = new UiState(project);
    mySdksConfigurable = new IdeSdksConfigurable(this, project);

    myConfigurables.add(mySdksConfigurable);
    if (!project.isDefault()) {
      myConfigurables.add(new AndroidProjectConfigurable(project));
    }

    mySettingsFile = GradleSettingsFile.get(project);

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

    GradleSyncState.subscribe(myProject, this);

    return component;
  }

  private void initSidePanel() {
    mySidePanel = new SidePanel();
  }

  @Override
  public boolean isModified() {
    for (Configurable configurable : myConfigurables) {
      if (configurable.isModified()) {
        return true;
      }
    }
    return super.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    UsageTracker.getInstance().trackEvent(CATEGORY_PROJECT_STRUCTURE_DIALOG, ACTION_PROJECT_STRUCTURE_DIALOG_SAVE, null, null);

    validateState();
    if (myErrorsPanel.hasCriticalErrors()) {
      return;
    }

    boolean dataChanged = false;
    for (Configurable configurable: myConfigurables) {
      if (configurable.isModified()) {
        UsageTracker.getInstance().trackEvent(CATEGORY_PROJECT_STRUCTURE_DIALOG, ACTION_PROJECT_STRUCTURE_DIALOG_LEFT_NAV_SAVE, configurable.getDisplayName(),
                                              null);
        dataChanged = true;
        configurable.apply();
      }
    }

    if (!myProject.isDefault() && (dataChanged || GradleSyncState.getInstance(myProject).isSyncNeeded() == ThreeState.YES)) {
      GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
    }
  }

  @Override
  public void reset() {
    // Need this to ensure VFS operations will not block because of storage flushing and other maintenance IO tasks run in background.
    AccessToken token = HeavyProcessLatch.INSTANCE.processStarted("Resetting project structure");

    try {
      for (Configurable configurable: myConfigurables) {
        configurable.reset();
      }

      if (myUiInitialized) {
        validateState();

        // Prepare module entries but don't add them until after developer services
        ModuleManager moduleManager = ModuleManager.getInstance(myProject);
        Module[] modules = moduleManager.getModules();
        Arrays.sort(modules, ModuleTypeComparator.INSTANCE);

        List<AndroidModuleConfigurable> moduleConfigurables = Lists.newArrayList();
        for (Module module : modules) {
          AndroidModuleConfigurable configurable = addModule(module);
          if (configurable != null) {
            moduleConfigurables.add(configurable);
          }
        }

        // Populate the "Developer Services" section
        removeServices();

        if (Projects.isBuildWithGradle(myProject)) {
          DefaultComboBoxModel moduleList = new DefaultComboBoxModel();
          for (AndroidModuleConfigurable moduleConfigurable : moduleConfigurables) {
            // Collect only Android modules
            if (AndroidFacet.getInstance(moduleConfigurable.getModule()) != null) {
              moduleList.addElement(moduleConfigurable.getModule());
            }
          }

          if (!myProject.isDefault() && moduleList.getSize() > 0) {
            // This may not be our first time opening the developer services dialog. User may have
            // modified developer service values last time but then pressed cancel. To be safe, we
            // restore our old values before reentering.
            // TODO: We really should do this on cancel but it doesn't look like we have any hooks
            // into that event.
            for (int i = 0; i < moduleList.getSize(); i++) {
              Module module = (Module)moduleList.getElementAt(i);
              for (DeveloperService service : DeveloperServices.getAll(module)) {
                service.getContext().restore();
              }
            }

            Module module = (Module)moduleList.getSelectedItem();
            Set<ServiceCategory> categories = Sets.newHashSet();
            for (DeveloperService s : DeveloperServices.getAll(module)) {
              categories.add(s.getCategory());
            }
            ArrayList<ServiceCategory> categoriesSorted = Lists.newArrayList(categories);
            Collections.sort(categoriesSorted);
            for (ServiceCategory category : categoriesSorted) {
              myConfigurables.add(new ServiceCategoryConfigurable(moduleList, category));
            }
          }
        }

        // Populate the "Modules" section.
        removeModules();
        Module toSelect = null;
        for (Module module : modules) {
          AndroidModuleConfigurable configurable = addModule(module);
          if (configurable != null) {
            myConfigurables.add(configurable);
            if (configurable.getDisplayName().equals(myUiState.lastSelectedConfigurable)) {
              toSelect = module;
            }
          }
        }

        if (myUiState.proportion > 0) {
          mySplitter.setProportion(myUiState.proportion);
        }

        mySidePanel.reset();
        if (toSelect != null) {
          mySidePanel.select(toSelect);
        } else {
          mySidePanel.selectSdk();
        }
      }
    }
    finally {
      token.finish();
    }
  }

  private void removeServices() {
    for (Iterator<Configurable> it = myConfigurables.iterator(); it.hasNext(); ) {
      if (it.next() instanceof ServiceCategoryConfigurable) {
        it.remove();
      }
    }
  }

  private void removeModules() {
    for (Iterator<Configurable> it = myConfigurables.iterator(); it.hasNext(); ) {
      if (it.next() instanceof AndroidModuleConfigurable) {
        it.remove();
      }
    }
  }

  @Nullable
  private AndroidModuleConfigurable addModule(@NotNull Module module) {
    String gradlePath = getGradlePath(module);
    AndroidModuleConfigurable configurable = null;
    if (StringUtil.isNotEmpty(gradlePath)) {
      configurable = new AndroidModuleConfigurable(myProject, module, gradlePath);
      configurable.reset();
    }
    return configurable;
  }

  private void validateState() {
    myErrorsPanel.removeAllErrors();
    List<ProjectConfigurationError> errors = mySdksConfigurable.validateState();
    if (!errors.isEmpty()) {
      Runnable navigationTask = new Runnable() {
        @Override
        public void run() {
          selectConfigurable(mySdksConfigurable);
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

  private void selectConfigurable(@NotNull Configurable configurable) {
    UsageTracker.getInstance().trackEvent(CATEGORY_PROJECT_STRUCTURE_DIALOG, ACTION_PROJECT_STRUCTURE_DIALOG_LEFT_NAV_CLICK, configurable.getDisplayName(),
                                          null);
    JComponent content = configurable.createComponent();
    assert content != null;
    myDetails.setContent(content);
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

    for (Configurable configurable : myConfigurables) {
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

  @Override
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return mySidePanel != null ? mySidePanel.myList : null;
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
  public void syncSucceeded(@NotNull Project project) {
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

  @Override
  public void syncSkipped(@NotNull Project project) {
  }

  private static void revalidateAndRepaint(@NotNull JComponent c) {
    c.revalidate();
    c.repaint();
  }

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
      return JBUI.size(800, 600);
    }
  }

  private class SidePanel extends JPanel {
    @NotNull private final JBList myList;
    @NotNull private final DefaultListModel myListModel;
    @NotNull private final Map<Object, String> mySectionHeaderMap = Maps.newHashMap();

    SidePanel() {
      super(new BorderLayout());
      myListModel = new DefaultListModel();
      myList = new JBList(myListModel);
      ListItemDescriptor descriptor = new ListItemDescriptor() {
        @Override
        @Nullable
        public String getTextFor(Object value) {
          if (value instanceof Configurable) {
            return ((Configurable)value).getDisplayName();
          }
          return value != null ? value.toString() : "";
        }

        @Override
        @Nullable
        public String getTooltipFor(Object value) {
          if (value instanceof AndroidModuleConfigurable) {
            Module module = (Module) ((AndroidModuleConfigurable)value).getEditableObject();
            return new File(module.getModuleFilePath()).getAbsolutePath();
          }
          return null;
        }

        @Override
        @Nullable
        public Icon getIconFor(Object value) {
          if (value instanceof AndroidModuleConfigurable) {
            Module module = (Module) ((AndroidModuleConfigurable)value).getEditableObject();
            return module.isDisposed() ? AllIcons.Nodes.Module : GradleUtil.getModuleIcon(module);
          }
          return null;
        }

        @Override
        public boolean hasSeparatorAboveOf(Object value) {
          return mySectionHeaderMap.containsKey(value);
        }

        @Override
        @Nullable
        public String getCaptionAboveOf(Object value) {
          return hasSeparatorAboveOf(value) ? mySectionHeaderMap.get(value) : null;
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
          Object selection = myList.getSelectedValue();
          if (selection instanceof Configurable) {
            selectConfigurable((Configurable)selection);
          }
        }
      });

      final JScrollPane scrollPane = ScrollPaneFactory
        .createScrollPane(myList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      add(scrollPane, BorderLayout.CENTER);

      if (!myProject.isDefault()) {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(createAddAction());
        group.add(new DeleteModuleAction(this));
        JComponent toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
        add(toolbar, BorderLayout.NORTH);
      }
    }

    private void reset() {
      myListModel.clear();
      mySectionHeaderMap.clear();
      Class<? extends Configurable> activeSection = null;
      for (Configurable configurable : myConfigurables) {
        myListModel.addElement(configurable);

        if (activeSection == configurable.getClass()) {
          continue;
        }
        activeSection = configurable.getClass();
        if (configurable instanceof AndroidModuleConfigurable) {
          mySectionHeaderMap.put(configurable, "Modules");
        }
        else if (configurable instanceof ServiceCategoryConfigurable) {
          mySectionHeaderMap.put(configurable, "Developer Services");
        }
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

    private int getModuleCount() {
      int count = 0;
      for (Configurable configurable : myConfigurables) {
        if (configurable instanceof AndroidModuleConfigurable) {
          count++;
        }
      }
      return count;
    }

    @Override
    public Dimension getMinimumSize() {
      Dimension original = super.getMinimumSize();
      return new Dimension(Math.max(original.width, JBUI.scale(100)), original.height);
    }

    @Nullable
    AndroidModuleConfigurable select(@NotNull Module module) {
      for (int i = 0; i < myListModel.size(); i++) {
        Object object = myListModel.elementAt(i);
        if (object instanceof AndroidModuleConfigurable &&
            ((AndroidModuleConfigurable)object).getEditableObject() == module) {
            myList.setSelectedValue(object, true);
            return (AndroidModuleConfigurable)object;
        }
      }
      return null;
    }

    void chooseJdkLocation() {
      selectSdk();
      mySdksConfigurable.chooseJdkLocation();
    }

    void selectSdk() {
      myList.setSelectedValue(mySdksConfigurable, true);
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

  private class DeleteModuleAction extends DumbAwareAction {
    @NotNull private final SidePanel mySidePanel;

    DeleteModuleAction(@NotNull SidePanel sidePanel) {
      super(CommonBundle.message("button.delete"), CommonBundle.message("button.delete"), PlatformIcons.DELETE_ICON);
      mySidePanel = sidePanel;
      registerCustomShortcutSet(CommonShortcuts.DELETE, mySidePanel.myList);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Object selectedValue = mySidePanel.myList.getSelectedValue();
      if (!(selectedValue instanceof AndroidModuleConfigurable)) {
        throw new IllegalStateException("The current selection does not represent a module");
      }
      AndroidModuleConfigurable configurable = (AndroidModuleConfigurable)selectedValue;
      Object editableObject = configurable.getEditableObject();
      if (!(editableObject instanceof Module)) {
        throw new IllegalStateException("Unable to find the module to delete");
      }

      String question;
      if (mySidePanel.getModuleCount() == 1) {
        question = ProjectBundle.message("module.remove.last.confirmation", 1);
      }
      else {
        question = ProjectBundle.message("module.remove.confirmation", configurable.getDisplayName(), 1);
      }
      if (Messages.showYesNoDialog(myProject, question, ProjectBundle.message("module.remove.confirmation.title", 1),
                                   Messages.getQuestionIcon()) != Messages.YES) {
        return;
      }

      final Module module = (Module)editableObject;
      final String gradlePath = getGradlePath(module);
      if (StringUtil.isEmpty(gradlePath)) {
        String msg = String.format("The module '%1$s' does not have a Gradle path", module.getName());
        throw new IllegalStateException(msg);
      }
      RunResult result = new WriteCommandAction.Simple(module.getProject()) {
        @Override
        protected void run() throws Throwable {
          delete(module);
          if (mySettingsFile != null) {
            mySettingsFile.removeModule(gradlePath);
          }
        }
      }.execute();
      Throwable error = result.getThrowable();
      if (error != null) {
        String msg = String.format("Failed to remove module '%1$s'", module.getName());
        LOG.error(msg, error);
        return;
      }

      myConfigurables.remove(configurable);
      mySidePanel.reset();
      GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
    }

    @NotNull
    private String getGradlePath(@NotNull Module module) {
      AndroidGradleFacet facet = AndroidGradleFacet.getInstance(module);
      if (facet == null) {
        String msg = String.format("The module '%1$s' is not a Gradle module", module.getName());
        throw new IllegalStateException(msg);
      }
      String path = facet.getConfiguration().GRADLE_PROJECT_PATH;
      if (StringUtil.isEmpty(path)) {
        String msg = String.format("The module '%1$s' does not have a Gradle path", module.getName());
        throw new IllegalStateException(msg);
      }
      return path;
    }

    private void delete(@NotNull Module module) {
      if (module.isDisposed()) {
        return;
      }
      ModuleManager moduleManager = ModuleManager.getInstance(module.getProject());
      ModifiableModuleModel modifiableModel = moduleManager.getModifiableModel();
      try {
        modifiableModel.disposeModule(module);
      }
      finally {
        modifiableModel.commit();
      }
    }

    @Override
    public void update(AnActionEvent e) {
      Object selectedValue = mySidePanel.myList.getSelectedValue();
      e.getPresentation().setEnabled(selectedValue instanceof AndroidModuleConfigurable);
    }
  }
}
