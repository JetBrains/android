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
package com.android.tools.idea.updater.configure;

import com.android.repository.api.RepoManager;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SchemaModule;
import com.android.repository.api.SimpleRepositorySource;
import com.android.repository.impl.sources.LocalSourceProvider;
import com.android.tools.idea.sdk.AndroidAuthenticator;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.progress.RepoProgressIndicatorAdapter;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.icons.AllIcons;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.table.IconTableCellRenderer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Table model representing the currently available {@link RepositorySource}s. Sources can be added, deleted, enabled, and disabled.
 */
class SourcesTableModel extends ListTableModel<SourcesTableModel.Row> implements EditableModel {
  /**
   * Sources originally loaded. Used to check whether any have been added or deleted.
   */
  private Set<RepositorySource> myInitialItems;

  /**
   * Callback to run when sources have changed, so other panels can update accordingly.
   */
  private Runnable myRefreshCallback;

  /**
   * Callback to run when we've started loading, to update the UI.
   */
  private Runnable myLoadingStartedCallback;

  /**
   * Callback to run when we're done loading, to update the UI.
   */
  private Runnable myLoadingFinishedCallback;

  /**
   * Logger for repository actions.
   */
  private static final com.android.repository.api.ProgressIndicator myLogger = new StudioLoggerProgressIndicator(SourcesTableModel.class);

  /**
   * Reference to the {@link Configurable} that created us, for retrieving sdk state.
   */
  private SdkUpdaterConfigurable myConfigurable;

  private final ModalityState myModalityState;

  SourcesTableModel(@NotNull Runnable startLoading, @NotNull Runnable finishLoading, @NotNull ModalityState modalityState) {
    super();
    myModalityState = modalityState;
    setColumnInfos(new ColumnInfo[]{new ColumnInfo<Row, Boolean>("Enabled") {
      @Nullable
      @Override
      public Boolean valueOf(Row row) {
        return row.mySource.isEnabled();
      }

      @Override
      public int getWidth(JTable table) {
        return 60;
      }

      @Override
      public Class getColumnClass() {
        return Boolean.class;
      }

      @Override
      public boolean isCellEditable(Row row) {
        return isEditable(indexOf(row)) && Strings.isNullOrEmpty(row.mySource.getFetchError());
      }

      @Override
      public void setValue(Row row, Boolean value) {
        row.mySource.setEnabled(value);  // source enablement changes are saved right away so they can be reflected in the other tabs
        myRefreshCallback.run();
      }

      @Nullable
      @Override
      public TableCellRenderer getRenderer(final Row row) {
        String error = row.mySource.getFetchError();
        if (!Strings.isNullOrEmpty(error)) {
          return new IconTableCellRenderer<Boolean>() {
            @Nullable
            @Override
            protected Icon getIcon(@NotNull Boolean value, JTable table, int row) {
              return AllIcons.General.Error;
            }

            @Override
            public String getText() {
              return null;
            }

            @Override
            public String getToolTipText() {
              return row.mySource.getFetchError();
            }

            @Override
            public int getHorizontalAlignment() {
              return SwingConstants.CENTER;
            }
          };
        }
        return super.getRenderer(row);
      }
    }, new ColumnInfo<Row, String>("Name") {
      @Nullable
      @Override
      public String valueOf(Row r) {
        return r.mySource.getDisplayName();
      }
    }, new ColumnInfo<Row, String>("URL") {
      @Nullable
      @Override
      public String valueOf(Row r) {
        return r.mySource.getUrl();
      }
    }});
    setLoadingFinishedCallback(finishLoading);
    myLoadingStartedCallback = startLoading;
  }

  /**
   * Sets all our sources to be either enabled or disabled.
   */
  public void setAllEnabled(boolean enabled) {
    for (Row r : getItems()) {
      r.mySource.setEnabled(enabled);
    }
    fireTableRowsUpdated(0, getRowCount() - 1);
    myRefreshCallback.run();
  }

  /**
   * Sets the {@link SdkUpdaterConfigurable} that we're part of. Note that this must be a separate method since the model may be created in
   * initializeUiComponents(), which runs before the constructor and thus before the Configurable might be available to pass in.
   */
  public void setConfigurable(SdkUpdaterConfigurable configurable) {
    myConfigurable = configurable;
  }

  /**
   * Reinitializes the UI from the sources provided by our {@link RepoManager}. Does not force the repo manager to refresh its cache of
   * sources.
   *
   * @param force Forces the {@link RepoManager} to reload any cached sources.
   */
  private void refreshUi(boolean force) {
    myLoadingStartedCallback.run();
    Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(() -> {
      final ArrayList<Row> items = new ArrayList<>();
      final Set<RepositorySource> initial = Sets.newHashSet();
      for (RepositorySource source : myConfigurable.getRepoManager().getSources(new StudioDownloader(), myLogger, force)) {
        items.add(new Row(source));
        initial.add(source);
      }
      Collections.sort(items);
      application.invokeAndWait(() -> {
        // only want to do it the first time
        if (myInitialItems == null) {
          myInitialItems = initial;
        }
        setItems(items);
        myLoadingFinishedCallback.run();
        if (force) {
          myRefreshCallback.run();
        }
      }, myModalityState);
    });
  }

  /**
   * Allow the user to edit a source.
   */
  public void editRow(int index) {
    Row r = getRowValue(index);
    EditSourceDialog input = showEditDialog(r.mySource);
    if (input.isOK()) {
      // Actual source names and URLs are immutable; we have to recreate here.
      removeRow(index);
      createSource(input.getUrl(), input.getUiName(), input.getCredentials());
    }
  }

  /**
   * Allow the user to add a new source.
   */
  @Override
  public void addRow() {
    EditSourceDialog input = showEditDialog(null);
    if (input.isOK()) {
      createSource(input.getUrl(), input.getUiName(), input.getCredentials());
    }
  }

  /**
   * Creates a dialog for adding or editing a source.
   *
   * @param source The source to edit, or {@code null} if we're creating a new source.
   */
  @NotNull
  private EditSourceDialog showEditDialog(@Nullable RepositorySource source) {
    RepositorySourceProvider userSourceProvider = getUserSourceProvider();
    // we know it won't be null since otherwise we shouldn't have been editable
    assert userSourceProvider != null;
    EditSourceDialog input = new EditSourceDialog(userSourceProvider, source);
    input.show();
    return input;
  }

  /**
   * Creates a new source with the given URL and display name.
   */
  @VisibleForTesting
  void createSource(@NotNull String url, @Nullable String uiName, @Nullable Credentials credentials) {
    LocalSourceProvider userSourceProvider = getUserSourceProvider();
    // we know it won't be null since otherwise we shouldn't have been editable
    assert userSourceProvider != null;

    Collection<SchemaModule<?>> allowedModules = userSourceProvider.getAllowedModulesBasedOnUrl(url);

    RepositorySource newSource =
      new SimpleRepositorySource(url, uiName, true, Collections.unmodifiableCollection(allowedModules), userSourceProvider);
    userSourceProvider.addSource(newSource);
    PasswordSafe.getInstance().set(new CredentialAttributes(AndroidAuthenticator.getCredentialServiceName(url)), credentials);
    try {
      PasswordSafe.getInstance().set(
        new CredentialAttributes(AndroidAuthenticator.getCredentialServiceName(new URL(url).getHost())), credentials);
    }
    catch (MalformedURLException e) {
      // shouldn't happen: validation is done in the dialog
      throw new AssertionError(e);
    }
    refreshUi(false);
  }

  /**
   * Removes the source at the specified row index.
   */
  @Override
  public void removeRow(int idx) {
    RepositorySourceProvider userSourceProvider = getUserSourceProvider();
    // we know it won't be null since otherwise we shouldn't have been editable
    assert userSourceProvider != null;
    userSourceProvider.removeSource(getRowValue(idx).mySource);
    refreshUi(false);
  }

  @Override
  public void exchangeRows(int oldIndex, int newIndex) {
  }

  @Override
  public boolean canExchangeRows(int oldIndex, int newIndex) {
    return false;
  }

  /**
   * Whether a row is editable (that is, whether the edit/delete buttons should be enabled when this row is selected).
   */
  public boolean isEditable(int row) {
    return isEditable() && getRowValue(row).mySource.getProvider().isModifiable();
  }

  public boolean hasEditableRows() {
    for (int i = 0, rowCount = getRowCount(); i < rowCount; ++i) {
      if (isEditable(i)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Reset the sources to the state they were before the user made any changes.
   */
  public void reset() {
    if (!isSourcesModified()) {
      // We don't have any changes, but if we haven't initialized at all, do it now.
      if (myInitialItems == null) {
        refreshUi(false);
      }
      return;
    }
    // Force refresh so the file is reloaded.
    myInitialItems = null;
    refreshUi(true);
  }

  /**
   * Determine whether any modifications have been made by the user since we last saved.
   */
  public boolean isSourcesModified() {
    if (myInitialItems == null) {
      // This will only happen when we've just reset or saved, and thus there can not be any changes anyway.
      return false;
    }
    if (getItems().size() != myInitialItems.size()) {
      return true;
    }
    for (Row row : getItems()) {
      if (row.isModified()) {
        return true;
      }
      if (!myInitialItems.contains(row.mySource)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Save any changes the user has made.
   */
  public void save(@NotNull ProgressIndicator progress) {
    if (isSourcesModified()) {
      RepositorySourceProvider userSourceProvider = getUserSourceProvider();
      // we know it won't be null since otherwise we shouldn't have been editable
      assert userSourceProvider != null;
      userSourceProvider.save(new RepoProgressIndicatorAdapter(progress));
      reset();
    }
  }

  /**
   * Gets the editable {@link LocalSourceProvider}.
   */
  @Nullable
  private LocalSourceProvider getUserSourceProvider() {
    return myConfigurable == null ? null : myConfigurable.getSdkHandler().getUserSourceProvider(myLogger);
  }

  /**
   * Sets the callback to be called when we've made any changes to the sources, so other panels can update appropriately.
   * @param refreshCallback
   */
  public void setRefreshCallback(@NotNull Runnable refreshCallback) {
    myRefreshCallback = refreshCallback;
  }

  @VisibleForTesting
  void setLoadingFinishedCallback(@NotNull Runnable loadingFinishedCallback) {
    myLoadingFinishedCallback = loadingFinishedCallback;
  }

  public boolean isEditable() {
    return getUserSourceProvider() != null;
  }

  /**
   * A row in our table.
   */
  @VisibleForTesting
  class Row implements SdkUpdaterConfigPanel.MultiStateRow, Comparable<Row> {
    RepositorySource mySource;
    boolean myOriginalEnabled;
    String myOriginalName;

    Row(RepositorySource source) {
      mySource = source;
      myOriginalEnabled = mySource.isEnabled();
      myOriginalName = mySource.getDisplayName();
    }

    /**
     * These rows have only two states, so just invert.
     */
    @Override
    public void cycleState() {
      if (isEditable(indexOf(this))) {
        mySource.setEnabled(!mySource.isEnabled());
      }
    }

    /**
     * Determine whether this source has been modified (and we should show apply/reset).
     */
    public boolean isModified() {
      return myOriginalEnabled != mySource.isEnabled() || !Objects.equal(myOriginalName, mySource.getDisplayName());
    }

    /**
     * User-added sources go at the top; otherwise sort alphabetically by original name (so they don't get reordered within a session).
     */
    @Override
    public int compareTo(Row o) {
      boolean isLocal = mySource.getProvider() instanceof LocalSourceProvider;
      if (isLocal != (o.mySource.getProvider() instanceof LocalSourceProvider)) {
        return isLocal ? -1 : 1;
      }
      if (myOriginalName == null || o.myOriginalName == null) {
        return myOriginalName == null ? (o.myOriginalName == null ? 0 : 1) : -1;
      }
      return myOriginalName.compareTo(o.myOriginalName);
    }
  }
}
