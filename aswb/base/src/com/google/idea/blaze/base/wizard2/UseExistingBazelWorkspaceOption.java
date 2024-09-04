/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.wizard2;

import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.settings.ui.ProjectViewUi;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI.Borders;
import icons.BlazeIcons;
import java.awt.Dimension;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

/** Allows importing an existing bazel workspace */
public class UseExistingBazelWorkspaceOption implements TopLevelSelectWorkspaceOption {

  private final JComponent component;
  private final TextFieldWithHistory directoryField;

  public UseExistingBazelWorkspaceOption(BlazeNewProjectBuilder builder) {
    directoryField = new TextFieldWithHistory();
    directoryField.setName("workspace-directory-field");
    directoryField.setHistory(builder.getWorkspaceHistory(BuildSystemName.Bazel));
    directoryField.setHistorySize(BlazeNewProjectBuilder.HISTORY_SIZE);
    directoryField.setText(builder.getLastImportedWorkspace(BuildSystemName.Bazel));
    directoryField.setMinimumAndPreferredWidth(MINIMUM_FIELD_WIDTH);

    JButton button = new JButton("...");
    button.addActionListener(action -> this.chooseDirectory());
    int buttonSize = this.directoryField.getPreferredSize().height;
    button.setPreferredSize(new Dimension(buttonSize, buttonSize));

    JPanel canvas = new JPanel(new VerticalLayout(4));
    canvas.setPreferredSize(ProjectViewUi.getContainerSize());
    canvas.add(new JLabel("Select an existing Bazel workspace"));
    canvas.add(new JSeparator());

    // Explicitly specify alignment, so preferred widths of children are honored.
    JPanel content =
        new JPanel(new VerticalLayout(/* gap= */ 12, /* alignment= */ SwingConstants.LEFT));
    content.setBorder(Borders.empty(20, 20, 0, 0));

    JComponent box =
        UiUtil.createHorizontalBox(
            BlazeWizardOption.HORIZONTAL_LAYOUT_GAP,
            getIconComponent(),
            new JLabel("Workspace:"),
            this.directoryField,
            button);
    UiUtil.setPreferredWidth(box, BlazeWizardOption.PREFERRED_COMPONENT_WIDTH);
    content.add(box);
    canvas.add(content);
    this.component = canvas;
  }

  private String getDirectory() {
    return directoryField.getText().trim();
  }

  @Override
  public String getOptionName() {
    return "use-existing-bazel-workspace";
  }

  @Override
  public String getTitle() {
    return "Bazel";
  }

  @Override
  public String getDescription() {
    return "Use existing Bazel workspace";
  }

  @Override
  public JComponent getUiComponent() {
    return component;
  }

  @Override
  public WorkspaceTypeData getWorkspaceData() throws ConfigurationException {
    String directory = getDirectory();
    if (directory.isEmpty()) {
      throw new ConfigurationException("Please select a workspace");
    }
    File workspaceRootFile = new File(directory);
    if (!workspaceRootFile.exists()) {
      throw new ConfigurationException("Workspace does not exist");
    }
    if (!isWorkspaceRoot(workspaceRootFile)) {
      throw new ConfigurationException(
          "Invalid workspace root: choose a bazel workspace directory "
              + "(containing a WORKSPACE file)");
    }
    WorkspaceRoot root = new WorkspaceRoot(workspaceRootFile);
    return WorkspaceTypeData.builder()
        .setWorkspaceName(workspaceRootFile.getName())
        .setWorkspaceRoot(root)
        .setFileBrowserRoot(workspaceRootFile)
        .setWorkspacePathResolver(new WorkspacePathResolverImpl(root))
        .setBuildSystem(BuildSystemName.Bazel)
        .build();
  }

  @Override
  public void commit() {}

  private void chooseDirectory() {
    FileChooserDescriptor descriptor =
        new FileChooserDescriptor(false, true, false, false, false, false) {
          @Override
          public boolean isFileSelectable(VirtualFile file) {
            // Default implementation doesn't filter directories,
            // we want to make sure only workspace roots are selectable
            return super.isFileSelectable(file) && isWorkspaceRoot(file);
          }
        }.withHideIgnored(false)
            .withTitle("Select Workspace Root")
            .withDescription("Select the directory of the workspace you want to use.")
            .withFileFilter(UseExistingBazelWorkspaceOption::isWorkspaceRoot);
    // File filters are broken for the native Mac file chooser.
    descriptor.setForcedToUseIdeaFileChooser(true);
    FileChooserDialog chooser =
        FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);

    final VirtualFile[] files;
    File existingLocation = new File(getDirectory());
    if (existingLocation.exists()) {
      VirtualFile toSelect =
          LocalFileSystem.getInstance().refreshAndFindFileByPath(existingLocation.getPath());
      files = chooser.choose(null, toSelect);
    } else {
      files = chooser.choose(null);
    }
    if (files.length == 0) {
      return;
    }
    VirtualFile file = files[0];
    directoryField.setText(file.getPath());
  }

  private static JComponent getIconComponent() {
    JLabel iconPanel =
        new JLabel(IconLoader.getIconSnapshot(BlazeIcons.BazelLogo)) {
          @Override
          public boolean isEnabled() {
            return true;
          }
        };
    UiUtil.setPreferredWidth(iconPanel, 16);
    return iconPanel;
  }

  private static boolean isWorkspaceRoot(File file) {
    return BuildSystemProvider.getWorkspaceRootProvider(BuildSystemName.Bazel)
        .isWorkspaceRoot(file);
  }

  private static boolean isWorkspaceRoot(VirtualFile file) {
    return isWorkspaceRoot(new File(file.getPath()));
  }
}
