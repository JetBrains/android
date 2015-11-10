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
package com.android.tools.idea.gradle.structure.configurables.editor.dependencies;

import com.android.ide.common.repository.GradleCoordinate;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil.FontSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static com.android.ide.common.repository.GradleCoordinate.parseCoordinateString;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ui.UIUtil.getLabelFont;
import static java.awt.event.KeyEvent.VK_A;

class LibrarySearch {
  @NotNull private final DependenciesPanel myDependenciesPanel;
  @NotNull private final ArtifactRepositorySearch[] myRepositorySearches;

  private JBTextField myArtifactNameTextField;
  private JPanel myPanel;
  private JBTextField myGroupIdTextField;
  private JBTable myResultsTable;
  private JButton mySearchButton;
  private JButton myAddLibraryButton;
  private JBLabel myArtifactNameLabel;
  private JBLabel myGroupIdLabel;
  private JBLabel myRequiredFieldLabel;

  LibrarySearch(@NotNull DependenciesPanel dependenciesPanel, @NotNull ArtifactRepositorySearch[] repositorySearches) {
    myDependenciesPanel = dependenciesPanel;
    myRepositorySearches = repositorySearches;
    myArtifactNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        mySearchButton.setEnabled(getArtifactName().length() >= 3);
      }
    });

    Font labelFont = getLabelFont(FontSize.SMALL);
    myArtifactNameLabel.setFont(labelFont);
    myArtifactNameLabel.setDisplayedMnemonic(VK_A);
    myArtifactNameLabel.setDisplayedMnemonicIndex(0);
    myGroupIdLabel.setFont(labelFont);
    myRequiredFieldLabel.setFont(labelFont);

    myArtifactNameTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (mySearchButton.isEnabled()) {
          performSearch();
        }
      }
    });
    myArtifactNameTextField.getEmptyText().setText("Example: \"guava\"");
    myGroupIdTextField.getEmptyText().setText("Example: \"com.google.guava\"");

    mySearchButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        performSearch();
      }
    });
  }

  private void performSearch() {
    mySearchButton.setEnabled(false);
    myResultsTable.getEmptyText().setText("Loading...");
    myResultsTable.setPaintBusy(true);
    clearResults();

    final ArtifactRepositorySearch.Request request = new ArtifactRepositorySearch.Request(getArtifactName(), getGroupId(), 50, 0);

    List<Future<SearchResult>> jobs = Lists.newArrayListWithExpectedSize(myRepositorySearches.length);
    List<LibraryFound> librariesFound = Lists.newArrayList();

    Application application = ApplicationManager.getApplication();
    for (final ArtifactRepositorySearch search : myRepositorySearches) {
      jobs.add(application.executeOnPooledThread(new Callable<SearchResult>() {
        @Override
        public SearchResult call() throws Exception {
          return search.start(request);
        }
      }));
    }

    try {
      for (Future<SearchResult> job : jobs) {
        SearchResult result = Futures.get(job, Exception.class);
        for (String coordinateText : result.data) {
          GradleCoordinate coordinate = parseCoordinateString(coordinateText);
          if (coordinate != null) {
            librariesFound.add(new LibraryFound(coordinate, result.repository));
          }
        }
      }
    }
    catch (Throwable e) {
      e.printStackTrace();
    }

    myResultsTable.setModel(new ResultsTableModel(librariesFound));
    myResultsTable.setPaintBusy(false);
    myResultsTable.getEmptyText().setText("Nothing to Show");
    mySearchButton.setEnabled(true);
    myResultsTable.requestFocusInWindow();
  }

  @NotNull
  private String getArtifactName() {
    return myArtifactNameTextField.getText().trim();
  }

  @Nullable
  private String getGroupId() {
    String groupId = myGroupIdTextField.getText().trim();
    return isNotEmpty(groupId) ? groupId : null;
  }

  @NotNull
  JPanel getPanel() {
    return myPanel;
  }

  private static class LibraryFound {
    @NotNull final GradleCoordinate coordinate;
    @NotNull final String repository;

    LibraryFound(@NotNull GradleCoordinate coordinate, @NotNull String repository) {
      this.coordinate = coordinate;
      this.repository = repository;
    }
  }

  private void createUIComponents() {
    myResultsTable = new JBTable();
    new TableSpeedSearch(myResultsTable);
    clearResults();
  }

  private void clearResults() {
    myResultsTable.setModel(new ResultsTableModel(Collections.<LibraryFound>emptyList()));
  }

  private static class ResultsTableModel extends AbstractTableModel {
    private static final String[] TABLE_COLUMN_NAMES = {"Group ID", "Artifact Name", "Version", "Repository"};
    private static final int GROUP_ID_COLUMN = 0;
    private static final int ARTIFACT_NAME_COLUMN = 1;
    private static final int VERSION_COLUMN = 2;
    private static final int REPOSITORY_COLUMN = 3;

    @NotNull private final List<LibraryFound> myLibrariesFound;

    ResultsTableModel(@NotNull List<LibraryFound> librariesFound) {
      myLibrariesFound = librariesFound;
    }

    @Override
    public int getRowCount() {
      return myLibrariesFound.size();
    }

    @Override
    public int getColumnCount() {
      return TABLE_COLUMN_NAMES.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      LibraryFound libraryFound = myLibrariesFound.get(rowIndex);
      switch (columnIndex) {
        case GROUP_ID_COLUMN:
          return libraryFound.coordinate.getGroupId();
        case ARTIFACT_NAME_COLUMN:
          return libraryFound.coordinate.getArtifactId();
        case VERSION_COLUMN:
          return libraryFound.coordinate.getRevision();
        case REPOSITORY_COLUMN:
          return libraryFound.repository;
      }
      throw new IllegalArgumentException(String.format("'%1$d' is not a valid column index", columnIndex));
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return false;
    }

    @Override
    public String getColumnName(int column) {
      return TABLE_COLUMN_NAMES[column];
    }
  }
}
