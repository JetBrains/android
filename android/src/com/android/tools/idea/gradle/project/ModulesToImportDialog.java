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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.CheckBoxListListener;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.util.PlatformIcons;
import com.intellij.util.SystemProperties;
import org.jdesktop.swingx.JXLabel;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.android.tools.idea.gradle.AndroidProjectKeys.IDE_GRADLE_PROJECT;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getChildren;

public class ModulesToImportDialog extends DialogWrapper {
  @NotNull private final List<DataNode<ModuleData>> alwaysIncludedModules = Lists.newArrayList();

  @Nullable private final Project myProject;

  private JPanel myPanel;
  private CheckBoxList<DataNode<ModuleData>> myModulesList;
  private JXLabel myDescriptionLabel;
  private JPanel myContentsPanel;

  private volatile boolean skipValidation;
  private volatile boolean allItemsUnselected;

  public ModulesToImportDialog(@NotNull Collection<DataNode<ModuleData>> modules, @Nullable Project project) {
    super(project, true, IdeModalityType.IDE);
    setTitle("Select Modules to Include");
    myProject = project;

    List<DataNode<ModuleData>> sortedModules = Lists.newArrayList(modules);
    Collections.sort(sortedModules, new Comparator<DataNode<ModuleData>>() {
      @Override
      public int compare(DataNode<ModuleData> m1, DataNode<ModuleData> m2) {
        return getNameOf(m1).compareTo(getNameOf(m2));
      }
    });

    init();
    for (DataNode<ModuleData> module : sortedModules) {
      Collection<DataNode<IdeaGradleProject>> gradleProjects = getChildren(module, IDE_GRADLE_PROJECT);
      if (gradleProjects.isEmpty()) {
        alwaysIncludedModules.add(module);
      }
      else {
        // We only show modules that are recognized in Gradle.
        // For example, in a multi-module project the top-level module is just a folder that contains the rest of
        // modules, which is not defined in settings.gradle.
        myModulesList.addItem(module, getNameOf(module), true);
      }
    }
    myModulesList.setCheckBoxListListener(new CheckBoxListListener() {
      @Override
      public void checkBoxSelectionChanged(int index, boolean value) {
        if (value) {
          allItemsUnselected = false;
        }
        if (!skipValidation) {
          initValidation();
        }
      }
    });
    myModulesList.setBorder(BorderFactory.createEmptyBorder());
    new ListSpeedSearch(myModulesList);
    myDescriptionLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    DefaultActionGroup group = new DefaultActionGroup();
    group.addAll(new SelectAllAction(true), new SelectAllAction(false));
    group.addSeparator();
    group.addAll(new LoadFromFileAction(), new SaveToFileAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("android.gradle.module.selection.dialog.toolbar", group, true);
    myContentsPanel.add(toolbar.getComponent(), BorderLayout.NORTH);
  }

  @NotNull
  private static String getNameOf(@NotNull DataNode<ModuleData> module) {
    return module.getData().getExternalName();
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    if (allItemsUnselected || !hasSelectedModules()) {
      return new ValidationInfo("Please select at least on Module", myModulesList);
    }
    return null;
  }

  private boolean hasSelectedModules() {
    int count = myModulesList.getItemsCount();
    for (int i = 0; i < count; i++) {
      if (myModulesList.isItemSelected(i)) {
        return true;
      }
    }
    return false;
  }

  private void setAllSelected(boolean selected) {
    int count = myModulesList.getItemsCount();
    skipValidation = true;
    for (int i = 0; i < count; i++) {
      DataNode<ModuleData> item = myModulesList.getItemAt(i);
      if (item != null) {
        myModulesList.setItemSelected(item, selected);
      }
    }
    skipValidation = false;
    allItemsUnselected = !selected;
    if (!selected) {
      initValidation();
    }
  }

  @NotNull
  public Collection<DataNode<ModuleData>> getSelectedModules() {
    List<DataNode<ModuleData>> modules = Lists.newArrayList(alwaysIncludedModules);
    modules.addAll(getUserSelectedModules());
    return modules;
  }

  @NotNull
  private Collection<DataNode<ModuleData>> getUserSelectedModules() {
    List<DataNode<ModuleData>> modules = Lists.newArrayList();
    int count = myModulesList.getItemsCount();
    for (int i = 0; i < count; i++) {
      if (myModulesList.isItemSelected(i)) {
        modules.add(myModulesList.getItemAt(i));
      }
    }
    return modules;
  }

  private void select(@NotNull List<String> moduleNames) {
    int count = myModulesList.getItemsCount();
    for (int i = 0; i < count; i++) {
      DataNode<ModuleData> module = myModulesList.getItemAt(i);
      if (module != null) {
        String name = getNameOf(module);
        myModulesList.setItemSelected(module, moduleNames.contains(name));
      }
    }
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myModulesList;
  }

  private class SelectAllAction extends DumbAwareAction {
    private final boolean mySelect;

    SelectAllAction(boolean select) {
      super(select ? "Select All" : "Unselect All", null, select ? PlatformIcons.SELECT_ALL_ICON : PlatformIcons.UNSELECT_ALL_ICON);
      mySelect = select;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myModulesList.getModel().getSize() > 0);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      setAllSelected(mySelect);
    }
  }

  private class LoadFromFileAction extends DumbAwareAction {
    LoadFromFileAction() {
      super("Load Selection from File", null, AllIcons.Actions.Menu_open);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
        @Override
        public boolean isFileSelectable(VirtualFile file) {
          boolean selectable = super.isFileSelectable(file);
          if (selectable) {
            selectable = SdkConstants.EXT_XML.equals(file.getExtension());
          }
          return selectable;
        }
      };
      String title = "Load Module Selection";
      descriptor.setTitle(title);
      FileChooserDialog dialog = FileChooserFactory.getInstance().createFileChooser(descriptor, myProject, getWindow());
      VirtualFile[] allSelected = dialog.choose(null, myProject);
      if (allSelected.length > 0) {
        File file = VfsUtilCore.virtualToIoFile(allSelected[0]);
        try {
          List<String> loadedModuleNames = Selection.load(file);
          select(loadedModuleNames);
        }
        catch (Throwable error) {
          String msg = String.format("Failed to load Module selection from file '%1$s'", file.getPath());
          Messages.showErrorDialog(getWindow(), msg, title);
          String cause = error.getMessage();
          if (StringUtil.isNotEmpty(cause)) {
            msg = msg + ":\n" + cause;
          }
          Logger.getInstance(ModulesToImportDialog.class).info(msg, error);
        }
      }
    }
  }

  private class SaveToFileAction extends DumbAwareAction {
    SaveToFileAction() {
      super("Save Selection As", null, AllIcons.Actions.Menu_saveall);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(hasSelectedModules());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final String title = "Save Module Selection";
      FileSaverDescriptor descriptor = new FileSaverDescriptor(title, "Save the list of selected Modules to a file",
                                                               SdkConstants.EXT_XML);
      FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, getWindow());
      VirtualFile baseDir = myProject != null ? myProject.getBaseDir() : null;
      VirtualFileWrapper result = dialog.save(baseDir, null);
      if (result != null) {
        File file = result.getFile();
        try {
          Selection.save(getUserSelectedModules(), file);
        }
        catch (IOException error) {
          String msg = String.format("Failed to save Module selection to file '%1$s'", file.getPath());
          Messages.showErrorDialog(getWindow(), msg, title);
          String cause = error.getMessage();
          if (StringUtil.isNotEmpty(cause)) {
            msg = msg + ":\n" + cause;
          }
          Logger.getInstance(ModulesToImportDialog.class).info(msg, error);
        }
      }
    }
  }

  // Module selection can be stored in XML files. Sample:
  // <?xml version="1.0" encoding="UTF-8"?>
  // <selectedModules>
  //   <module name="app" />
  //   <module name="mylibrary" />
  //</selectedModules>

  @VisibleForTesting
  static class Selection {
    @NonNls private static final String ROOT_ELEMENT_NAME = "selectedModules";
    @NonNls private static final String MODULE_ELEMENT_NAME = "module";
    @NonNls private static final String MODULE_NAME_ATTRIBUTE_NAME = "name";

    @NotNull
    static List<String> load(@NotNull File file) throws JDOMException, IOException {
      Document document = JDOMUtil.loadDocument(file);
      List<String> modules = Lists.newArrayList();
      Element rootElement = document.getRootElement();
      if (rootElement != null && ROOT_ELEMENT_NAME.equals(rootElement.getName())) {
        for (Element child : rootElement.getChildren(MODULE_ELEMENT_NAME)) {
          String moduleName = child.getAttributeValue(MODULE_NAME_ATTRIBUTE_NAME);
          if (StringUtil.isNotEmpty(moduleName)) {
            modules.add(moduleName);
          }
        }
      }
      return modules;
    }

    static void save(@NotNull Collection<DataNode<ModuleData>> modules, @NotNull File file) throws IOException {
      Document document = new Document(new Element(ROOT_ELEMENT_NAME));
      for (DataNode<ModuleData> module : modules) {
        Element child = new Element(MODULE_ELEMENT_NAME);
        child.setAttribute(MODULE_NAME_ATTRIBUTE_NAME, getNameOf(module));
        document.getRootElement().addContent(child);
      }
      JDOMUtil.writeDocument(document, file, SystemProperties.getLineSeparator());
    }
  }
}
