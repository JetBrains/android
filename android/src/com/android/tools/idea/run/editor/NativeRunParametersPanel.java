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
package com.android.tools.idea.run.editor;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class NativeRunParametersPanel implements ActionListener {
  private final Project myProject;
  private JPanel myPanel;
  private JBLabel mySymDirsLabel;
  private JPanel mySymDirsPanel;
  private TextFieldWithBrowseButton myWorkingDirField;
  private JTextField myLoggingTargetChannelsField;
  private JCheckBox myHybridDebugCheckBox;

  private final JBList mySymDirsList;
  private CollectionListModel<String> mySymDirsModel;

  public NativeRunParametersPanel(@NotNull Project project) {
    myProject = project;

    mySymDirsModel = new CollectionListModel<String>();
    mySymDirsList = new JBList(mySymDirsModel);
    mySymDirsLabel.setLabelFor(mySymDirsList);

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(mySymDirsList).
      setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final String path = chooseDirectory();
          if (path != null) {
            mySymDirsModel.add(path);
            mySymDirsList.setSelectedValue(path, true);
          }
        }
      });
    mySymDirsPanel.add(decorator.createPanel());

    myWorkingDirField.addBrowseFolderListener(null, null, myProject, FileChooserDescriptorFactory.createSingleFolderDescriptor());
  }

  @Nullable
  private String chooseDirectory() {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    final VirtualFile file = FileChooser.chooseFile(descriptor, mySymDirsList, myProject, myProject.getBaseDir());
    return file != null ? FileUtil.toSystemDependentName(file.getPath()) : null;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
  }

  public boolean isHybridDebug() {
    return myHybridDebugCheckBox.isSelected();
  }

  public void setHybridDebug(boolean hybridDebug) {
    myHybridDebugCheckBox.setSelected(hybridDebug);
  }

  @NotNull
  public List<String> getSymbolDirs() {
    return mySymDirsModel.getItems();
  }

  public void setSymbolDirs(@NotNull List<String> symDirs) {
    mySymDirsModel = new CollectionListModel<String>(symDirs);
    mySymDirsList.setModel(mySymDirsModel);
  }

  @NotNull
  public String getWorkingDir() {
    return myWorkingDirField.getText();
  }

  public void setWorkingDir(@NotNull String workingDir) {
    myWorkingDirField.setText(workingDir);
  }

  @NotNull
  public String getTargetLoggingChannels() {
    // Allow only character symbols, "-", ":" and spaces.
    final String logChannels = myLoggingTargetChannelsField.getText().trim();
    return logChannels.replaceAll("[^a-z\\-\\s:]", "");
  }

  public void setTargetLoggingChannels(@NotNull String targetLoggingChannels) {
    myLoggingTargetChannelsField.setText(targetLoggingChannels);
  }

}
