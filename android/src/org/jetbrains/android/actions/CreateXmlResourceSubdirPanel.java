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
package org.jetbrains.android.actions;

import com.android.resources.ResourceFolderType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * List of subdirectories where a new XML resource should be added.
 */
public class CreateXmlResourceSubdirPanel {

  private JPanel myDirectoriesPanel;
  private final CheckBoxList myDirectoriesList;
  private Map<String, JCheckBox> myCheckBoxes = Collections.emptyMap();
  private String[] myDirNames = ArrayUtil.EMPTY_STRING_ARRAY;

  public interface Parent {
    VirtualFile getResourceDirectory();

    void updateFilesCombo(List<VirtualFile> directories);
  }
  private Project myProject;
  ResourceFolderType myFolderType;
  private Parent myParent;

  public CreateXmlResourceSubdirPanel(Project project, ResourceFolderType folderType,
                                      JPanel parentPanel, Parent parent) {
    myProject = project;
    myFolderType = folderType;
    myParent = parent;
    myDirectoriesPanel = parentPanel;
    myDirectoriesList = new CheckBoxList();
    setupDirectoriesPanel();
  }

  protected void setupDirectoriesPanel() {
    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myDirectoriesList);

    decorator.setEditAction(null);
    decorator.disableUpDownActions();

    decorator.setAddAction(button -> doAddNewDirectory());

    decorator.setRemoveAction(button -> doDeleteDirectory());

    final AnActionButton selectAll = new AnActionButton("Select All", null, PlatformIcons.SELECT_ALL_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        doSelectAllDirs();
      }
    };
    decorator.addExtraAction(selectAll);

    final AnActionButton unselectAll = new AnActionButton("Unselect All", null, PlatformIcons.UNSELECT_ALL_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        doUnselectAllDirs();
      }
    };
    decorator.addExtraAction(unselectAll);

    myDirectoriesPanel.add(decorator.createPanel());

    updateDirectories(true, myParent.getResourceDirectory());
  }

  private void doDeleteDirectory() {
    VirtualFile resourceDir = myParent.getResourceDirectory();
    if (resourceDir == null) {
      return;
    }

    final int selectedIndex = myDirectoriesList.getSelectedIndex();
    if (selectedIndex < 0) {
      return;
    }

    final String selectedDirName = myDirNames[selectedIndex];
    final VirtualFile selectedDir = resourceDir.findChild(selectedDirName);
    if (selectedDir == null) {
      return;
    }

    final VirtualFileDeleteProvider provider = new VirtualFileDeleteProvider();
    provider.deleteElement(dataId -> {
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.getName().equals(dataId)) {
        return new VirtualFile[]{selectedDir};
      }
      else {
        return null;
      }
    });
    updateDirectories(false, resourceDir);
  }

  private void doSelectAllDirs() {
    for (JCheckBox checkBox : myCheckBoxes.values()) {
      checkBox.setSelected(true);
    }
    myDirectoriesList.repaint();
  }

  private void doUnselectAllDirs() {
    for (JCheckBox checkBox : myCheckBoxes.values()) {
      checkBox.setSelected(false);
    }
    myDirectoriesList.repaint();
  }

  private void doAddNewDirectory() {
    VirtualFile resourceDir = myParent.getResourceDirectory();
    if (resourceDir == null) {
      return;
    }
    final PsiDirectory psiResDir = PsiManager.getInstance(myProject).findDirectory(resourceDir);

    if (psiResDir != null) {
      final PsiElement[] createdElements = new CreateResourceDirectoryAction(myFolderType).invokeDialog(myProject, psiResDir);

      if (createdElements.length > 0) {
        updateDirectories(false, resourceDir);
      }
    }
  }

  public void updateDirectories(boolean updateFileCombo, VirtualFile resourceDir) {
    List<VirtualFile> directories = Collections.emptyList();
    if (resourceDir != null) {
      directories = AndroidResourceUtil.getResourceSubdirs(myFolderType, new VirtualFile[]{resourceDir});
    }

    Collections.sort(directories, (f1, f2) -> f1.getName().compareTo(f2.getName()));

    final Map<String, JCheckBox> oldCheckBoxes = myCheckBoxes;
    final int selectedIndex = myDirectoriesList.getSelectedIndex();
    final String selectedDirName = selectedIndex >= 0 ? myDirNames[selectedIndex] : null;

    final List<JCheckBox> checkBoxList = new ArrayList<>();
    myCheckBoxes = new HashMap<>();
    myDirNames = new String[directories.size()];

    int newSelectedIndex = -1;

    int i = 0;

    for (VirtualFile dir : directories) {
      final String dirName = dir.getName();
      final JCheckBox oldCheckBox = oldCheckBoxes.get(dirName);
      final boolean selected = oldCheckBox != null && oldCheckBox.isSelected();
      final JCheckBox checkBox = new JCheckBox(dirName, selected);
      checkBoxList.add(checkBox);
      myCheckBoxes.put(dirName, checkBox);
      myDirNames[i] = dirName;

      if (dirName.equals(selectedDirName)) {
        newSelectedIndex = i;
      }
      i++;
    }

    String defaultFolderName = myFolderType.getName();
    JCheckBox noQualifierCheckBox = myCheckBoxes.get(defaultFolderName);
    if (noQualifierCheckBox == null) {
      noQualifierCheckBox = new JCheckBox(defaultFolderName);

      checkBoxList.add(0, noQualifierCheckBox);
      myCheckBoxes.put(defaultFolderName, noQualifierCheckBox);

      String[] newDirNames = new String[myDirNames.length + 1];
      newDirNames[0] = defaultFolderName;
      System.arraycopy(myDirNames, 0, newDirNames, 1, myDirNames.length);
      myDirNames = newDirNames;
    }
    noQualifierCheckBox.setSelected(true);

    myDirectoriesList.setModel(new CollectionListModel<>(checkBoxList));

    if (newSelectedIndex >= 0) {
      myDirectoriesList.setSelectedIndex(newSelectedIndex);
    }

    if (updateFileCombo) {
      myParent.updateFilesCombo(directories);
    }
  }

  public void resetToDefault() {
    for (JCheckBox checkBox : myCheckBoxes.values()) {
      checkBox.setSelected(false);
    }
    myCheckBoxes.get(myFolderType.getName()).setSelected(true);
    myDirectoriesList.repaint();
  }

  public void resetFromFile(@NotNull VirtualFile directory) {
    final JCheckBox checkBox = myCheckBoxes.get(directory.getName());
    if (checkBox == null) {
      return;
    }

    for (JCheckBox checkBox1 : myCheckBoxes.values()) {
      checkBox1.setSelected(false);
    }
    checkBox.setSelected(true);
  }

  public List<String> getDirNames() {
    List<String> selectedDirs = new ArrayList<>();
    for (Map.Entry<String, JCheckBox> entry : myCheckBoxes.entrySet()) {
      if (entry.getValue().isSelected()) {
        selectedDirs.add(entry.getKey());
      }
    }
    return selectedDirs;
  }
}
