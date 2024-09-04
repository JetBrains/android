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
package com.google.idea.blaze.base.run.exporter;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewSet.ProjectViewFile;
import com.google.idea.blaze.base.run.BlazeRunConfiguration;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.BooleanTableCellEditor;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.DefaultCellEditor;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableColumn;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/** UI for exporting run configurations. */
public class ExportRunConfigurationDialog extends DialogWrapper {

  // show blaze run configurations first, otherwise sort by name
  private static final Comparator<RunConfiguration> COMPARATOR =
      (o1, o2) -> {
        if (o1 instanceof BlazeRunConfiguration != o2 instanceof BlazeRunConfiguration) {
          return o1 instanceof BlazeRunConfiguration ? -1 : 1;
        }
        return o1.getName().compareTo(o2.getName());
      };

  private final ImmutableList<RunConfiguration> configurations;
  private final ExportRunConfigurationTableModel tableModel;
  private final JBTable table;
  private final FieldPanel outputDirectoryPanel;

  ExportRunConfigurationDialog(Project project) {
    super(project, true);
    configurations =
        ImmutableList.copyOf(
            RunManager.getInstance(project)
                .getAllConfigurationsList()
                .stream()
                .sorted(COMPARATOR)
                .collect(Collectors.toList()));
    tableModel = new ExportRunConfigurationTableModel(configurations);
    table = new JBTable(tableModel);

    TableColumn booleanColumn = table.getColumnModel().getColumn(0);
    booleanColumn.setCellRenderer(new BooleanTableCellRenderer());
    booleanColumn.setCellEditor(new BooleanTableCellEditor());
    int width = table.getFontMetrics(table.getFont()).stringWidth(table.getColumnName(0)) + 10;
    booleanColumn.setPreferredWidth(width);
    booleanColumn.setMinWidth(width);
    booleanColumn.setMaxWidth(width);

    table
        .getColumnModel()
        .getColumn(2)
        .setCellEditor(new DefaultCellEditor(GuiUtils.createUndoableTextField()));

    TableColumn nameColumn = table.getColumnModel().getColumn(1);
    nameColumn.setCellRenderer(
        new ColoredTableCellRenderer() {
          @Override
          protected void customizeCellRenderer(
              JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            RunConfiguration config = configurations.get(row);
            setIcon(config.getType().getIcon());
            append(config.getName());
          }
        });

    table.setPreferredSize(new Dimension(700, 700));
    table.setShowColumns(true);

    final ActionListener browseAction = e -> chooseDirectory();
    outputDirectoryPanel =
        new FieldPanel("Export configurations to directory:", null, browseAction, null);
    File defaultExportDirectory = defaultExportDirectory(project);
    if (defaultExportDirectory != null) {
      outputDirectoryPanel.setText(defaultExportDirectory.getPath());
    }

    String buildSystem = Blaze.buildSystemName(project);
    setTitle(String.format("Export %s Run Configurations", buildSystem));
    init();
  }

  /** Try to find a checked-in project view file. Otherwise, fall back to the workspace root. */
  @Nullable
  private static File defaultExportDirectory(Project project) {
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProjectSafe(project);
    if (workspaceRoot == null) {
      return null;
    }
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet != null) {
      for (ProjectViewFile projectViewFile : projectViewSet.getProjectViewFiles()) {
        File file = projectViewFile.projectViewFile;
        if (file != null && FileUtil.isAncestor(workspaceRoot.directory(), file, false)) {
          return file.getParentFile();
        }
      }
    }
    return workspaceRoot.directory();
  }

  private String getOutputDirectoryPath() {
    return Strings.nullToEmpty(outputDirectoryPanel.getText()).trim();
  }

  private void chooseDirectory() {
    FileChooserDescriptor descriptor =
        FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Export Directory Location")
            .withDescription("Choose directory to export run configurations to")
            .withHideIgnored(false);
    FileChooserDialog chooser =
        FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);

    final VirtualFile[] files;
    File existingLocation = new File(getOutputDirectoryPath());
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
    outputDirectoryPanel.setText(file.getPath());
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    String outputDir = getOutputDirectoryPath();
    if (outputDir.isEmpty()) {
      return new ValidationInfo("Choose an output directory");
    }
    if (!FileOperationProvider.getInstance().exists(new File(outputDir))) {
      return new ValidationInfo("Invalid output directory");
    }
    Set<String> names = new HashSet<>();
    for (int i = 0; i < configurations.size(); i++) {
      if (!tableModel.enabled[i]) {
        continue;
      }
      if (!names.add(tableModel.paths[i])) {
        return new ValidationInfo("Duplicate output file name '" + tableModel.paths[i] + "'");
      }
    }
    return null;
  }

  @Override
  protected void doOKAction() {
    File outputDir = new File(getOutputDirectoryPath());
    List<File> outputFiles = new ArrayList<>();
    for (int i = 0; i < configurations.size(); i++) {
      if (!tableModel.enabled[i]) {
        continue;
      }
      File outputFile = new File(outputDir, tableModel.paths[i]);
      writeConfiguration(configurations.get(i), outputFile);
      outputFiles.add(outputFile);
    }
    LocalFileSystem.getInstance().refreshIoFiles(outputFiles);
    super.doOKAction();
  }

  private static void writeConfiguration(RunConfiguration configuration, File outputFile) {
    try (FileOutputStream writer = new FileOutputStream(outputFile, false)) {
      XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());
      xmlOutputter.output(RunConfigurationSerializer.writeToXml(configuration), writer);
    } catch (IOException e) {
      throw new RuntimeException("Error exporting run configuration to file: " + outputFile);
    }
  }

  @Override
  protected JComponent createNorthPanel() {
    return outputDirectoryPanel;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder("Run Configurations", false));
    panel.add(
        ToolbarDecorator.createDecorator(table).addExtraAction(new SelectAllButton()).createPanel(),
        BorderLayout.CENTER);
    return panel;
  }

  private class SelectAllButton extends AnActionButton {

    boolean allSelected = false;

    private SelectAllButton() {
      super("Select All", AllIcons.Actions.Selectall);
    }

    @Override
    public synchronized void actionPerformed(AnActionEvent anActionEvent) {
      boolean newState = !allSelected;
      for (int i = 0; i < tableModel.enabled.length; i++) {
        table.setValueAt(newState, i, 0);
      }
      allSelected = newState;
      Presentation presentation = anActionEvent.getPresentation();
      if (allSelected) {
        presentation.setText("Deselect All");
        presentation.setIcon(AllIcons.Actions.Unselectall);
      } else {
        presentation.setText("Select All");
        presentation.setIcon(AllIcons.Actions.Selectall);
      }
      tableModel.fireTableDataChanged();
      table.revalidate();
      table.repaint();
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }
}
