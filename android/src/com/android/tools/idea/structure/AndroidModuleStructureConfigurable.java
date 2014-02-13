/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.structure;

import com.android.tools.idea.actions.AndroidNewModuleAction;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.BaseStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.navigation.Place;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * A Project Structure {@linkplain com.intellij.openapi.options.Configurable} that lets users add/remove/configure modules.
 */
public class AndroidModuleStructureConfigurable extends BaseStructureConfigurable implements Place.Navigator {
  private static final Comparator<MyNode> NODE_COMPARATOR = new Comparator<MyNode>() {
    @Override
    public int compare(final MyNode o1, final MyNode o2) {
      return StringUtil.naturalCompare(o1.getConfigurable().getDisplayName(), o2.getConfigurable().getDisplayName());
    }
  };

  @NonNls public static final String CATEGORY = "category";

  private final GradleSettingsFile mySettingsFile;

  public AndroidModuleStructureConfigurable(Project project) {
    super(project);
    mySettingsFile = GradleSettingsFile.get(project);
  }

  @Override
  protected String getComponentStateKey() {
    return "ModuleStructureConfigurable.UI";
  }

  @Override
  protected void initTree() {
    super.initTree();
  }

  @Override
  protected void loadTree() {
    if (mySettingsFile == null) {
      return;
    }
    // Our module names will be Gradle-style paths delimited by colons. We need to turn this into a tree structure, so for each
    // path we break it apart into leaves and turn the leaves into tree nodes if there isn't already a node in the tree for that leaf.
    // We walk these tree nodes so that we add the module to the correct parent node in the tree. We can be called to reload an existing
    // tree, so we have to be careful not to create any duplicates.
    for (final String path : mySettingsFile.getModules()) {
      MyNode parentNode = myRoot;
      List<String> segments = GradleUtil.getPathSegments(path);
      if (segments.isEmpty()) {
        // This must be a single-module project with a settings.gradle file that includes ':'. Create a single empty-named module so we
        // can edit the build.gradle file for it.
        segments = Lists.newArrayList("");
      }
      String moduleName = segments.remove(segments.size() - 1);
      for (String segment : segments) {
        MyNode node = getNode(parentNode, segment);
        if (node == null) {
          node = new MyNode(new FolderConfigurable(segment));
          addNode(node, parentNode);
        }
        parentNode = node;
      }
      if (getNode(parentNode, moduleName) == null) {
        final MyNode moduleNode = new MyNode(new AndroidModuleConfigurable(myProject, path));
        addNode(moduleNode, parentNode);
      }
    }
    myTree.setShowsRootHandles(true);
    ((DefaultTreeModel)myTree.getModel()).reload();
    myUiDisposed = false;
  }

  @Nullable
  private static MyNode getNode(MyNode parentNode, String leaf) {
    for (int i = 0; i < parentNode.getChildCount(); i++) {
      MyNode child = (MyNode)parentNode.getChildAt(i);
      if (child.getDisplayName().equals(leaf)) {
        return child;
      }
    }
    return null;
  }

  @NotNull
  @Override
  protected Collection<? extends ProjectStructureElement> getProjectStructureElements() {
    return ImmutableList.of();
  }

  @Override
  protected Comparator<MyNode> getNodeComparator() {
    return NODE_COMPARATOR;
  }

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();
    myContext.myModulesConfigurator.disposeUIResources();
  }

  @Override
  public void dispose() {
  }

  @Override
  protected void processRemovedItems() {
  }

  @Override
  protected boolean wasObjectStored(Object editableObject) {
    return false;
  }

  @Override
  public String getDisplayName() {
    return ProjectBundle.message("project.roots.display.name");
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  @Override
  @NotNull
  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    final ArrayList<AnAction> result = new ArrayList<AnAction>(2);
    result.add(createAddAction());
    result.add(new MyRemoveAction());
    return result;
  }

  @Override
  protected boolean canBeRemoved(Object[] editableObjects) {
    for (Object editableObject : editableObjects) {
      if (editableObject != null && editableObject instanceof String) {
        String moduleName = (String)editableObject;
        if (Iterables.contains(mySettingsFile.getModules(), moduleName)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  protected boolean removeObject(Object editableObject) {
    if (editableObject != null && editableObject instanceof String) {
      final String moduleName = (String)editableObject;
      if (Iterables.contains(mySettingsFile.getModules(), moduleName)) {

        // TODO: This applies changes immediately. We need to save up changes and not apply them until the user confirms the dialog.
        // In the old dialog this is handled by the fact that we have a ModifiableRootModel that queues changes.

        // TODO: If removing a module, remove any dependencies on that module in other modules. Perhaps we should show this to the user
        // in the confirmation dialog, and ask if dependencies should be cleaned up?

        String question;
        if (Iterables.size(mySettingsFile.getModules()) == 1) {
          question = ProjectBundle.message("module.remove.last.confirmation");
        }
        else {
          question = ProjectBundle.message("module.remove.confirmation", moduleName);
        }
        int result = Messages.showYesNoDialog(myProject, question, ProjectBundle.message("module.remove.confirmation.title"),
                                              Messages.getQuestionIcon());
        if (result != Messages.YES) {
          return false;
        }
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            mySettingsFile.removeModule(moduleName);
          }
        });
        return true;
      }
    }
    return false;
  }

  public static AndroidModuleStructureConfigurable getInstance(final Project project) {
    return ServiceManager.getService(project, AndroidModuleStructureConfigurable.class);
  }

  /**
   * Opens a Project Settings dialog and selects the Gradle module editor, with the given module and editor pane active.
   */
  public static boolean showDialog(final Project project, @Nullable final String moduleToSelect, @Nullable final String editorToSelect) {
    ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(project);
    long timeInMillis = System.currentTimeMillis();
    boolean result = ShowSettingsUtil.getInstance().editConfigurable(project, config, new Runnable() {
      @Override
      public void run() {
        getInstance(project).select(moduleToSelect, editorToSelect, true);
      }
    });
    if (Projects.isGradleSyncNeeded(project, timeInMillis)) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          try {
            GradleProjectImporter.getInstance().reImportProject(project, null);
          }
          catch (ConfigurationException e) {
            Messages.showErrorDialog(project, e.getMessage(), e.getTitle());
          }
        }
      });
    }
    return result;
  }

  private ActionCallback select(@Nullable final String moduleToSelect, @Nullable String editorNameToSelect, final boolean requestFocus) {
    Place place = new Place().putPath(CATEGORY, this);
    if (moduleToSelect != null) {
      final Module module = ModuleManager.getInstance(myProject).findModuleByName(moduleToSelect);
      assert module != null;
      place = place.putPath(MasterDetailsComponent.TREE_OBJECT, module).putPath(ModuleEditor.SELECTED_EDITOR_NAME, editorNameToSelect);
    }
    return navigateTo(place, requestFocus);
  }

  private void addModule() {
    if (AndroidStudioSpecificInitializer.isAndroidStudio()) {
      AndroidNewModuleAction.createModule(myProject, false);
    } else {
      myContext.myModulesConfigurator.addModule(myTree, false);
    }

    // Template instantiation is already being run via invokeLater. When it's done, pick up the changes to settings.gradle and reload
    // the tree.
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            mySettingsFile.reload();
            loadTree();
          }
        });
      }
    });
  }

  @Override
  @NotNull
  @NonNls
  public String getId() {
    return "project.structure";
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  protected AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(ProjectBundle.message("add.new.header.text")) {
      @Override
      @NotNull
      public AnAction[] getChildren(@Nullable final AnActionEvent e) {
        AnAction addModuleAction = new AddModuleAction();
        addModuleAction.getTemplatePresentation().setText("New Module");
        return new AnAction[] {
          addModuleAction
        };
      }
    };
  }

  @Override
  @Nullable
  protected String getEmptySelectionString() {
    return ProjectBundle.message("empty.module.selection.string");
  }

  private class AddModuleAction extends AnAction implements DumbAware {
    public AddModuleAction() {
      super(ProjectBundle.message("add.new.module.text.full"), null, AllIcons.Actions.Module);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      addModule();
    }
  }

  private static class FolderConfigurable extends NamedConfigurable {
    private final String myName;

    public FolderConfigurable(String name) {
      myName = name;
    }

    @Override
    public void setDisplayName(String name) {
    }

    @Override
    @Nullable
    public Object getEditableObject() {
      return null;
    }

    @Override
    public String getBannerSlogan() {
      return "Folder '" + myName + "'";
    }

    @Override
    public JComponent createOptionsPanel() {
      return new JPanel();
    }

    @Nls
    @Override
    public String getDisplayName() {
      return myName;
    }

    @Nullable
    @Override
    public String getHelpTopic() {
      return null;
    }

    @Override
    public boolean isModified() {
      return false;
    }

    @Override
    public void apply() throws ConfigurationException {
    }

    @Override
    public void reset() {
    }

    @Override
    public void disposeUIResources() {
    }

    @Nullable
    @Override
    public Icon getIcon(boolean expanded) {
      return PlatformIcons.FOLDER_ICON;
    }
  }
}
