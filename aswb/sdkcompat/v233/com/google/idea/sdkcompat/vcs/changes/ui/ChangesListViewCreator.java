package com.google.idea.sdkcompat.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import javax.swing.tree.DefaultTreeModel;

/** Compat class to initialize ChangesListView. */
public final class ChangesListViewCreator {
  public static ChangesListView create(Project project, boolean showCheckboxes) {
    return new ChangesListView(project, showCheckboxes) {};
  }

  public static void update(ChangesListView changesListView, DefaultTreeModel treeModel) {
    changesListView.setModel(treeModel);
  }

  private ChangesListViewCreator() {}
}
