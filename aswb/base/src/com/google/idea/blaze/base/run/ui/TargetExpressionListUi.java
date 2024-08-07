/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.ui;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.TableUtil;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletion.StringsCompletionProvider;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.EventObject;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.TableCellEditor;

/** UI component for a list of {@link TargetExpression}s with autocomplete. */
public class TargetExpressionListUi extends JPanel {

  private final Project project;
  private final ListTableModel<TargetItem> listModel;
  private final TableView<TargetItem> tableView;

  public TargetExpressionListUi(Project project) {
    this.project = project;

    listModel = new ListTableModel<>(new TargetColumn());
    tableView = new TableView<>(listModel);
    tableView.getEmptyText().setText("Choose some targets");
    tableView.setPreferredScrollableViewportSize(new Dimension(200, tableView.getRowHeight() * 4));

    setLayout(new BorderLayout());
    add(
        ToolbarDecorator.createDecorator(tableView)
            .setAddAction(button -> addTarget())
            .setRemoveAction(button -> removeTarget())
            .disableUpDownActions()
            .createPanel(),
        BorderLayout.CENTER);
  }

  /** Returns the non-empty target patterns presented in the UI component. */
  public ImmutableList<String> getTargetExpressions() {
    return listModel.getItems().stream()
        .map(t -> t.expression.trim())
        .filter(s -> !s.isEmpty())
        .collect(toImmutableList());
  }

  public void setTargetExpressions(List<String> targets) {
    listModel.setItems(
        targets.stream()
            .filter(s -> s != null && !s.isEmpty())
            .map(TargetItem::new)
            .collect(toList()));
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    tableView.setEnabled(enabled);
    tableView.setRowSelectionAllowed(enabled);
  }

  private void addTarget() {
    TableUtil.stopEditing(tableView); // save any partially-filled state

    listModel.addRow(new TargetItem(""));
    int index = listModel.getRowCount() - 1;
    tableView.getSelectionModel().setSelectionInterval(index, index);
    tableView.scrollRectToVisible(
        tableView.getCellRect(index, /* column= */ 0, /* includeSpacing= */ true));
    TableUtil.editCellAt(tableView, index, /* column= */ 0);
  }

  private void removeTarget() {
    TableUtil.removeSelectedItems(tableView);
  }

  private class TargetColumn extends ColumnInfo<TargetItem, String> {
    TargetColumn() {
      super(/* name= */ ""); // empty column name; separate label for table title
    }

    @Override
    public String valueOf(TargetItem targetItem) {
      return targetItem.expression;
    }

    @Override
    public void setValue(TargetItem targetItem, String value) {
      targetItem.expression = value;
    }

    @Override
    public TableCellEditor getEditor(TargetItem targetItem) {
      return new TargetListCellEditor(project);
    }

    @Override
    public boolean isCellEditable(TargetItem targetItem) {
      return true;
    }
  }

  private static class TargetItem {
    private String expression;

    TargetItem(String expression) {
      this.expression = expression;
    }
  }

  private static class TargetListCellEditor extends AbstractCellEditor implements TableCellEditor {

    private final Project project;
    private volatile TextFieldWithAutoCompletion<String> textField = null;

    TargetListCellEditor(Project project) {
      this.project = project;
    }

    @Override
    public Component getTableCellEditorComponent(
        JTable table, Object value, boolean isSelected, int row, int column) {
      textField =
          new TextFieldWithAutoCompletion<String>(
              project,
              new TargetCompletionProvider(project),
              /* showCompletionHint= */ true,
              /* text= */ (String) value);
      textField.addSettingsProvider(
          editorEx -> {
            // base class ignores 'enter' keypress events, causing entire dialog to close without
            // committing changes... fix copied from upstream PsiClassTableCellEditor
            JComponent c = editorEx.getContentComponent();
            c.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ENTER");
            c.getActionMap()
                .put(
                    "ENTER",
                    new AbstractAction() {
                      @Override
                      public void actionPerformed(ActionEvent e) {
                        stopCellEditing();
                      }
                    });
          });
      textField.setBorder(BorderFactory.createLineBorder(JBColor.BLACK));
      return textField;
    }

    @Override
    public boolean isCellEditable(EventObject e) {
      if (!(e instanceof MouseEvent)) {
        return true;
      }
      return ((MouseEvent) e).getClickCount() >= 2;
    }

    @Override
    public String getCellEditorValue() {
      TextFieldWithAutoCompletion<String> field = textField;
      return field != null ? field.getText() : "";
    }
  }

  private static class TargetCompletionProvider extends StringsCompletionProvider {
    TargetCompletionProvider(Project project) {
      super(getTargets(project), null);
    }

    private static Collection<String> getTargets(Project project) {
      BlazeProjectData projectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      BlazeImportSettings importSettings =
          BlazeImportSettingsManager.getInstance(project).getImportSettings();
      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      if (projectData == null || importSettings == null || projectViewSet == null) {
        return ImmutableList.of();
      }
      ImportRoots importRoots =
          ImportRoots.builder(
                  WorkspaceRoot.fromImportSettings(importSettings), importSettings.getBuildSystem())
              .add(projectViewSet)
              .build();

      if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
        return projectData.targets().stream()
            .map(TargetInfo::getLabel)
            .filter(importRoots::importAsSource)
            .map(TargetExpression::toString)
            .collect(toImmutableList());
      }

      return projectData.getTargetMap().targets().stream()
          .filter(TargetIdeInfo::isPlainTarget)
          .map(TargetIdeInfo::getKey)
          .map(TargetKey::getLabel)
          .filter(importRoots::importAsSource)
          .map(TargetExpression::toString)
          .collect(toImmutableList());
    }
  }
}
