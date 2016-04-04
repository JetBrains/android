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
package com.android.tools.idea.gradle.structure.configurables.ui;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository;
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepositorySearch;
import com.android.tools.idea.gradle.structure.model.repositories.search.SearchRequest;
import com.android.tools.idea.gradle.structure.model.repositories.search.SearchResult;
import com.google.common.collect.Lists;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TableSpeedSearch;
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
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

import static com.android.ide.common.repository.GradleCoordinate.parseCoordinateString;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;

public class ArtifactRepositorySearchForm {
  @NotNull private final ArtifactRepositorySearch mySearch;

  private JBTextField myArtifactNameTextField;
  private JBTextField myGroupIdTextField;
  private JButton mySearchButton;
  private JBScrollPane myResultsScrollPane;
  private JPanel myPanel;

  private TableView<FoundArtifact> myResultsTable;

  public ArtifactRepositorySearchForm(@NotNull List<ArtifactRepository> repositories) {
    mySearch = new ArtifactRepositorySearch(repositories);

    myArtifactNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        mySearchButton.setEnabled(getArtifactName().length() >= 3);
      }
    });

    ActionListener actionListener = e -> {
      if (mySearchButton.isEnabled()) {
        performSearch();
      }
    };

    mySearchButton.addActionListener(actionListener);

    myArtifactNameTextField.addActionListener(actionListener);
    myArtifactNameTextField.getEmptyText().setText("Example: \"guava\"");

    myGroupIdTextField.addActionListener(actionListener);
    myGroupIdTextField.getEmptyText().setText("Example: \"com.google.guava\"");

    myResultsTable = new TableView<>(new ResultsTableModel());
    myResultsTable.setAutoCreateRowSorter(true);
    myResultsTable.setShowGrid(false);
    myResultsTable.getTableHeader().setReorderingAllowed(false);
    myResultsScrollPane.setViewportView(myResultsTable);

    new TableSpeedSearch(myResultsTable);
  }

  private void performSearch() {
    mySearchButton.setEnabled(false);
    myResultsTable.getEmptyText().setText("Searching...");
    myResultsTable.setPaintBusy(true);
    clearResults();

    SearchRequest request = new SearchRequest(getArtifactName(), getGroupId(), 50, 0);
    final ArtifactRepositorySearch.Callback callback = mySearch.start(request);
    callback.doWhenDone(() -> invokeLaterIfNeeded(() -> {
      List<FoundArtifact> foundArtifacts = Lists.newArrayList();

      for (SearchResult result : callback.getSearchResults()) {
        for (String coordinateText : result.getData()) {
          GradleCoordinate coordinate = parseCoordinateString(coordinateText);
          if (coordinate != null) {
            foundArtifacts.add(new FoundArtifact(coordinate, result.getRepositoryName()));
          }
        }
      }

      myResultsTable.getListTableModel().setItems(foundArtifacts);
      myResultsTable.updateColumnSizes();
      myResultsTable.setPaintBusy(false);
      myResultsTable.getEmptyText().setText("Nothing to show");
      if (!foundArtifacts.isEmpty()) {
        myResultsTable.changeSelection(0, 0, false, false);
      }
      myResultsTable.requestFocusInWindow();

      mySearchButton.setEnabled(true);
    }));
  }

  private void clearResults() {
    myResultsTable.getListTableModel().setItems(Collections.emptyList());
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
  public JPanel getPanel() {
    return myPanel;
  }

  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myArtifactNameTextField;
  }

  private static class ResultsTableModel extends ListTableModel<FoundArtifact> {
    ResultsTableModel() {
      createAndSetColumnInfos();
      setSortable(true);
    }

    private void createAndSetColumnInfos() {
      ColumnInfo<FoundArtifact, String> groupId = new ColumnInfo<FoundArtifact, String>("Group ID") {
        @Override
        @Nullable
        public String valueOf(FoundArtifact found) {
          return found.coordinate.getGroupId();
        }

        @Override
        @NonNls
        @NotNull
        public String getPreferredStringValue() {
          // Some text to provide a hint of what column width should be.
          return "abcdefghijklmno";
        }
      };
      ColumnInfo<FoundArtifact, String> artifactName = new ColumnInfo<FoundArtifact, String>("Artifact Name") {
        @Override
        @Nullable
        public String valueOf(FoundArtifact found) {
          return found.coordinate.getArtifactId();
        }

        @Override
        @NonNls
        @NotNull
        public String getPreferredStringValue() {
          // Some text to provide a hint of what column width should be.
          return "abcdefg";
        }
      };
      ColumnInfo<FoundArtifact, String> version = new ColumnInfo<FoundArtifact, String>("Version") {
        @Override
        @Nullable
        public String valueOf(FoundArtifact found) {
          return found.coordinate.getRevision();
        }

        @Override
        @NotNull
        public String getPreferredStringValue() {
          // Some text to provide a hint of what column width should be.
          return "100.100.100";
        }
      };
      ColumnInfo<FoundArtifact, String> repository = new ColumnInfo<FoundArtifact, String>("Repository") {
        @Override
        @Nullable
        public String valueOf(FoundArtifact found) {
          return found.repository;
        }
      };
      setColumnInfos(new ColumnInfo[]{groupId, artifactName, version, repository});
    }
  }

  private static class FoundArtifact {
    @NotNull final GradleCoordinate coordinate;
    @NotNull final String repository;

    FoundArtifact(@NotNull GradleCoordinate coordinate, @NotNull String repository) {
      this.coordinate = coordinate;
      this.repository = repository;
    }
  }
}
