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
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static com.android.ide.common.repository.GradleCoordinate.parseCoordinateString;
import static com.intellij.icons.AllIcons.General.WarningDecorator;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ui.UIUtil.ComponentStyle.SMALL;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;
import static java.awt.event.KeyEvent.VK_N;

class LibrarySearch {
  @NotNull private final DependenciesPanel myDependenciesPanel;
  @NotNull private final List<ArtifactRepositorySearch> myRepositorySearches;

  private JBTextField myArtifactNameTextField;
  private JPanel myPanel;
  private JBTextField myGroupIdTextField;
  private TableView<LibraryFound> myResultsTable;
  private JButton mySearchButton;
  private JButton myAddLibraryButton;
  private JBLabel myArtifactNameLabel;
  private JBLabel myRequiredFieldLabel;
  private JBScrollPane myResultsScrollPane;

  LibrarySearch(@NotNull DependenciesPanel dependenciesPanel, @NotNull List<ArtifactRepositorySearch> repositorySearches) {
    myDependenciesPanel = dependenciesPanel;
    myRepositorySearches = repositorySearches;
    myArtifactNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        mySearchButton.setEnabled(getArtifactName().length() >= 3);
      }
    });

    myArtifactNameLabel.setComponentStyle(SMALL);
    myArtifactNameLabel.setDisplayedMnemonic(VK_N);
    myArtifactNameLabel.setIcon(WarningDecorator);

    myRequiredFieldLabel.setIcon(WarningDecorator);

    myArtifactNameTextField.addActionListener(new SearchActionListener());
    myArtifactNameTextField.getEmptyText().setText("Example: \"guava\"");

    myGroupIdTextField.addActionListener(new SearchActionListener());
    myGroupIdTextField.getEmptyText().setText("Example: \"com.google.guava\"");

    mySearchButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        performSearch();
      }
    });

    myAddLibraryButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Collection<LibraryFound> selection = myResultsTable.getSelection();
        for (LibraryFound libraryFound : selection) {
          myDependenciesPanel.addLibraryDependency(libraryFound.coordinate.toString());
        }
      }
    });

    myResultsTable = new TableView<LibraryFound>(new ResultsTableModel());
    myResultsTable.setAutoCreateRowSorter(true);
    myResultsTable.setShowGrid(false);
    myResultsTable.getTableHeader().setReorderingAllowed(false);
    myResultsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        myAddLibraryButton.setEnabled(!myResultsTable.getSelection().isEmpty());
      }
    });
    myResultsScrollPane.setViewportView(myResultsTable);
    List<? extends RowSorter.SortKey> sortKeys = myResultsTable.getRowSorter().getSortKeys();
    new TableSpeedSearch(myResultsTable);
    clearResults();
  }

  private void performSearch() {
    mySearchButton.setEnabled(false);
    myResultsTable.getEmptyText().setText("Searching...");
    myResultsTable.setPaintBusy(true);
    clearResults();

    final ArtifactRepositorySearch.Request request = new ArtifactRepositorySearch.Request(getArtifactName(), getGroupId(), 50, 0);

    final ActionCallback callback = new ActionCallback();
    final List<Future<SearchResult>> jobs = Lists.newArrayListWithExpectedSize(myRepositorySearches.size());
    final List<LibraryFound> librariesFound = Lists.newArrayList();

    final Application application = ApplicationManager.getApplication();

    application.executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        for (final ArtifactRepositorySearch search : myRepositorySearches) {
          jobs.add(application.executeOnPooledThread(new Callable<SearchResult>() {
            @Override
            public SearchResult call() throws Exception {
              return search.start(request);
            }
          }));
        }

        for (Future<SearchResult> job : jobs) {
          SearchResult result;
          try {
            result = Futures.get(job, Exception.class);
            for (String coordinateText : result.data) {
              GradleCoordinate coordinate = parseCoordinateString(coordinateText);
              if (coordinate != null) {
                librariesFound.add(new LibraryFound(coordinate, result.repository));
              }
            }
          }
          catch (Exception e) {
            // TODO show error message in search panel
            e.printStackTrace();
          }
        }

        callback.setDone();
      }
    });

    callback.doWhenDone(new Runnable() {
      @Override
      public void run() {
        invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            myResultsTable.getListTableModel().setItems(librariesFound);
            myResultsTable.updateColumnSizes();
            myResultsTable.setPaintBusy(false);
            myResultsTable.getEmptyText().setText("Nothing to show");
            if (librariesFound.isEmpty()) {
              myAddLibraryButton.setEnabled(false);
            }
            else {
              myResultsTable.changeSelection(0, 0, false, false);
            }
            myResultsTable.requestFocusInWindow();

            mySearchButton.setEnabled(true);
          }
        });
      }
    });
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

  private void clearResults() {
    myResultsTable.getListTableModel().setItems(Collections.<LibraryFound>emptyList());
  }

  private class SearchActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (mySearchButton.isEnabled()) {
        performSearch();
      }
    }
  }

  private static class ResultsTableModel extends ListTableModel<LibraryFound> {
    ResultsTableModel() {
      createAndSetColumnInfos();
      setSortable(true);
    }

    private void createAndSetColumnInfos() {
      ColumnInfo<LibraryFound, String> groupId = new ColumnInfo<LibraryFound, String>("Group ID") {
        @Override
        @Nullable
        public String valueOf(LibraryFound libraryFound) {
          return libraryFound.coordinate.getGroupId();
        }

        @Override
        @NonNls
        @NotNull
        public String getPreferredStringValue() {
          return "abcdefghijklmno";
        }
      };
      ColumnInfo<LibraryFound, String> artifactName = new ColumnInfo<LibraryFound, String>("Artifact Name") {
        @Override
        @Nullable
        public String valueOf(LibraryFound libraryFound) {
          return libraryFound.coordinate.getArtifactId();
        }

        @Override
        @NonNls
        @NotNull
        public String getPreferredStringValue() {
          return "abcdefg";
        }
      };
      ColumnInfo<LibraryFound, String> version = new ColumnInfo<LibraryFound, String>("Version") {
        @Override
        @Nullable
        public String valueOf(LibraryFound libraryFound) {
          return libraryFound.coordinate.getRevision();
        }

        @Override
        @NotNull
        public String getPreferredStringValue() {
          return "100.100.100";
        }
      };
      ColumnInfo<LibraryFound, String> repository = new ColumnInfo<LibraryFound, String>("Repository") {
        @Override
        @Nullable
        public String valueOf(LibraryFound libraryFound) {
          return libraryFound.repository;
        }
      };
      setColumnInfos(new ColumnInfo[]{groupId, artifactName, version, repository});
    }
  }

  private static class LibraryFound {
    @NotNull final GradleCoordinate coordinate;
    @NotNull final String repository;

    LibraryFound(@NotNull GradleCoordinate coordinate, @NotNull String repository) {
      this.coordinate = coordinate;
      this.repository = repository;
    }
  }
}
