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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.structure.model.repositories.search.*;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;
import static javax.swing.ListSelectionModel.SINGLE_SELECTION;

public class ArtifactRepositorySearchForm {
  private static final String SEARCHING_EMPTY_TEXT = "Searching...";
  private static final String NOTHING_TO_SHOW_EMPTY_TEXT = "Nothing to show";

  @NotNull private final ArtifactRepositorySearch mySearch;

  private JBLabel myArtifactNameLabel;
  private JBTextField myArtifactNameTextField;

  private JBLabel myGroupIdLabel;
  private JBTextField myGroupIdTextField;

  private JButton mySearchButton;

  private JPanel myResultsPanel;
  private TableView<FoundArtifact> myResultsTable;
  private AvailableVersionsPanel myVersionsPanel;

  private JPanel myPanel;

  @NotNull private final SelectionChangeEventDispatcher<String> myEventDispatcher = new SelectionChangeEventDispatcher<>();
  @NotNull private final List<Exception> mySearchErrors = Lists.newArrayList();

  public ArtifactRepositorySearchForm(@NotNull List<ArtifactRepository> repositories) {
    mySearch = new ArtifactRepositorySearch(repositories);

    myArtifactNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        clearResults();
        showSearchStopped();
      }
    });

    ActionListener actionListener = e -> {
      if (mySearchButton.isEnabled()) {
        performSearch();
      }
    };

    mySearchButton.addActionListener(actionListener);

    myArtifactNameLabel.setLabelFor(myArtifactNameTextField);
    myArtifactNameTextField.addActionListener(actionListener);
    myArtifactNameTextField.getEmptyText().setText("Example: \"guava\"");

    myGroupIdLabel.setLabelFor(myGroupIdTextField);
    myGroupIdTextField.addActionListener(actionListener);
    myGroupIdTextField.getEmptyText().setText("Example: \"com.google.guava\"");

    myResultsTable = new TableView<>(new ResultsTableModel());
    myResultsTable.setPreferredSize(new Dimension(520, 320));

    myResultsTable.setSelectionMode(SINGLE_SELECTION);
    myResultsTable.setAutoCreateRowSorter(true);
    myResultsTable.setShowGrid(false);
    myResultsTable.getTableHeader().setReorderingAllowed(false);

    myResultsTable.getSelectionModel().addListSelectionListener(e -> {
      FoundArtifact artifact = getSelection();

      if (artifact != null) {
        myVersionsPanel.setVersions(artifact.getVersions());
      }
      else {
        notifySelectionChanged(null, null);
      }
    });

    myVersionsPanel = new AvailableVersionsPanel();

    JBSplitter splitter = new OnePixelSplitter(false, 0.7f);
    splitter.setFirstComponent(createScrollPane(myResultsTable));
    splitter.setSecondComponent(myVersionsPanel);

    myResultsPanel.add(splitter, BorderLayout.CENTER);

    new TableSpeedSearch(myResultsTable);
  }

  @Nullable
  private FoundArtifact getSelection() {
    Collection<FoundArtifact> selection = myResultsTable.getSelection();
    FoundArtifact artifact = null;
    if (selection.size() == 1) {
      artifact = getFirstItem(selection);
    }
    return artifact;
  }

  private void notifySelectionChanged(@Nullable FoundArtifact artifact, @Nullable String version) {
    String selected = null;
    if (artifact != null) {
      String groupId = artifact.getGroupId();
      String name = artifact.getName();

      if (version == null) {
        List<GradleVersion> versions = artifact.getVersions();
        version = versions.isEmpty() ? "" : versions.get(0).toString();
      }

      selected = groupId + GRADLE_PATH_SEPARATOR + name;
      if (isNotEmpty(version)) {
        selected += (GRADLE_PATH_SEPARATOR + version);
      }
    }
    myEventDispatcher.selectionChanged(selected);
  }

  private void performSearch() {
    mySearchButton.setEnabled(false);
    myVersionsPanel.setEmptyText(SEARCHING_EMPTY_TEXT);
    myResultsTable.getEmptyText().setText(SEARCHING_EMPTY_TEXT);
    myResultsTable.setPaintBusy(true);
    clearResults();

    SearchRequest request = new SearchRequest(getArtifactName(), getGroupId(), 50, 0);

    ArtifactRepositorySearch.Callback callback = mySearch.start(request);
    callback.doWhenDone(() -> invokeLaterIfNeeded(() -> {
      List<FoundArtifact> foundArtifacts = Lists.newArrayList();

      List<Exception> errors = callback.getErrors();
      if (!errors.isEmpty()) {
        showSearchStopped();
        mySearchErrors.addAll(errors);
        return;
      }

      for (SearchResult result : callback.getSearchResults()) {
        foundArtifacts.addAll(result.getArtifacts());
      }

      if (foundArtifacts.size() > 1) {
        Collections.sort(foundArtifacts);
      }

      myResultsTable.getListTableModel().setItems(foundArtifacts);
      myResultsTable.updateColumnSizes();
      showSearchStopped();
      if (!foundArtifacts.isEmpty()) {
        myResultsTable.changeSelection(0, 0, false, false);
      }
      myResultsTable.requestFocusInWindow();
    }));
  }

  private void clearResults() {
    myResultsTable.getListTableModel().setItems(Collections.emptyList());
    mySearchErrors.clear();
    myVersionsPanel.clear();
  }

  private void showSearchStopped() {
    mySearchButton.setEnabled(getArtifactName().length() >= 3);

    myResultsTable.setPaintBusy(false);
    myResultsTable.getEmptyText().setText(NOTHING_TO_SHOW_EMPTY_TEXT);

    myVersionsPanel.clear();
    myVersionsPanel.setEmptyText(NOTHING_TO_SHOW_EMPTY_TEXT);
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

  public void add(@NotNull SelectionChangeListener<String> listener, @NotNull Disposable parentDisposable) {
    myEventDispatcher.addListener(listener, parentDisposable);
  }

  @NotNull
  public JPanel getPanel() {
    return myPanel;
  }

  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myArtifactNameTextField;
  }

  @NotNull
  public List<Exception> getSearchErrors() {
    return mySearchErrors;
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
          return found.getGroupId();
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
          return found.getName();
        }

        @Override
        @NonNls
        @NotNull
        public String getPreferredStringValue() {
          // Some text to provide a hint of what column width should be.
          return "abcdefg";
        }
      };
      ColumnInfo<FoundArtifact, String> repository = new ColumnInfo<FoundArtifact, String>("Repository") {
        @Override
        @Nullable
        public String valueOf(FoundArtifact found) {
          return found.getRepositoryName();
        }
      };
      setColumnInfos(new ColumnInfo[]{groupId, artifactName, repository});
    }
  }

  private class AvailableVersionsPanel extends JPanel {
    private final TableView<GradleVersion> myVersionsTable;

    AvailableVersionsPanel() {
      super(new BorderLayout());
      myVersionsTable = new TableView<>();
      myVersionsTable.setShowGrid(false);
      myVersionsTable.setSelectionMode(SINGLE_SELECTION);
      myVersionsTable.getListTableModel().setColumnInfos(new ColumnInfo[] {
        new ColumnInfo<GradleVersion, String>("Versions") {
          @Override
          @Nullable
          public String valueOf(GradleVersion version) {
            return version.toString();
          }
        }
      });

      myVersionsTable.getSelectionModel().addListSelectionListener(e -> {
        FoundArtifact artifact = getSelection();
        GradleVersion version = myVersionsTable.getSelectedObject();
        notifySelectionChanged(artifact, version != null ? version.toString() : null);
      });
      JScrollPane scrollPane = createScrollPane(myVersionsTable);
      add(scrollPane, BorderLayout.CENTER);
    }

    void setVersions(@NotNull List<GradleVersion> versions) {
      clear();
      myVersionsTable.getListTableModel().setItems(versions);
      if (!versions.isEmpty()) {
        myVersionsTable.getSelectionModel().setSelectionInterval(0, 0);
      }
    }

    void setEmptyText(@NotNull String text) {
      myVersionsTable.getEmptyText().setText(text);
    }

    void clear() {
      myVersionsTable.getListTableModel().setItems(Collections.emptyList());
    }
  }
}
