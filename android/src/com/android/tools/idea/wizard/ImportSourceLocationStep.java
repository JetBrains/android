/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.eclipse.AdtImportBuilder;
import com.android.tools.idea.gradle.project.ImportSourceKind;
import com.android.tools.idea.gradle.project.ProjectImportUtil;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.ui.MessageType.ERROR;

/**
 * Wizard page for selecting source location for module import.
 */
public class ImportSourceLocationStep extends ModuleWizardStep implements AndroidStudioWizardStep {
  private static final int VALIDATION_STATUS_DISPLAY_DELAY = 50; //ms
  private final Logger LOG = Logger.getInstance(ImportSourceLocationStep.class);
  private final NewModuleWizardState myState;
  private final ModulesTableModel myModulesModel;
  private final Timer myDelayedValidationProgressDisplay;
  @NotNull private final TemplateWizardStep.UpdateListener myUpdateListener;
  @NotNull private final WizardContext myContext;
  private JPanel myPanel;
  private TextFieldWithBrowseButton mySourceLocation;
  private JBLabel myErrorWarning;
  private JBTable myModulesList;
  private JBLabel myModuleImportLabel;
  private AsyncProcessIcon myValidationProgress;
  private AsyncValidator<?> validator;
  private BackgroundOperationResult myResult;
  private boolean myIsValidating = false;

  public ImportSourceLocationStep(@NotNull WizardContext context,
                                  @NotNull NewModuleWizardState state,
                                  @NotNull Disposable disposable,
                                  @Nullable TemplateWizardStep.UpdateListener listener) {
    myModulesList.setGridColor(UIUtil.getSlightlyDarkerColor(myModulesList.getBackground()));
    myContext = context;
    myUpdateListener = listener == null ? new TemplateWizardStep.UpdateListener() {
      @Override
      public void update() {
        // Do nothing
      }
    } : listener;
    myState = state;
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor();
    descriptor.setTitle("Select Source Location");
    descriptor.setDescription("Select location of your existing ");
    mySourceLocation.addBrowseFolderListener(new TextBrowseFolderListener(descriptor));

    validator = new AsyncValidator<BackgroundOperationResult>(disposable) {
      @Override
      protected void showValidationResult(BackgroundOperationResult result) {
        applyBackgroundOperationResult(result);
      }

      @NotNull
      @Override
      protected BackgroundOperationResult validate() {
        return checkPath(mySourceLocation.getText());
      }
    };
    myModulesModel = new ModulesTableModel();
    myModulesList.setModel(myModulesModel);
    applyBackgroundOperationResult(checkPath(mySourceLocation.getText()));
    myErrorWarning.setText("");
    myErrorWarning.setIcon(null);
    mySourceLocation.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        invalidate();
      }
    });
    myDelayedValidationProgressDisplay = new Timer(VALIDATION_STATUS_DISPLAY_DELAY, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myIsValidating) {
          updateStatusDisplay(PageStatus.VALIDATING);
        }
      }
    });
  }

  /**
   * Returns a relative path string to be shown in the UI. Wizard logic
   * operates with VirtualFile's so these paths are only for user. The paths
   * shown are relative to the file system location user specified, showing
   * relative paths will be easier for the user to read.
   */
  private static String getRelativePath(@NotNull VirtualFile file, @Nullable VirtualFile base) {
    String path = file.getPath();
    if (base == null) {
      return path;
    }
    else if (file.equals(base)) {
      return ".";
    }
    else if (!base.isDirectory()) {
      return getRelativePath(file, base.getParent());
    }
    else {
      String basePath = base.getPath();
      if (path.startsWith(basePath + "/")) {
        return path.substring(basePath.length() + 1);
      }
      else if (file.getFileSystem().equals(base.getFileSystem())) {
        StringBuilder builder = new StringBuilder(basePath.length());
        String prefix = Strings.commonPrefix(path, basePath);
        if (!prefix.endsWith("/")) {
          prefix = prefix.substring(0, prefix.indexOf("/"));
        }
        if (!path.startsWith(basePath)) {
          Iterable<String> segments = Splitter.on("/").split(basePath.substring(prefix.length()));
          Joiner.on("/").appendTo(builder, Iterables.transform(segments, Functions.constant("..")));
          builder.append("/");
        }
        builder.append(path.substring(prefix.length()));
        return builder.toString();
      }
      else {
        return path;
      }
    }
  }

  private void updateStatusDisplay(@NotNull PageStatus status) {
    myValidationProgress.setVisible(status.isSpinnerVisible());
    myErrorWarning.setText(status.message);
    myErrorWarning.setIcon(status.getIcon());
    myModulesList.setEnabled(!status.isSpinnerVisible()); // Grayed out for background op
    myUpdateListener.update();
  }

  private void invalidate() {
    if (!myDelayedValidationProgressDisplay.isRunning()) {
      myDelayedValidationProgressDisplay.start();
    }
    myIsValidating = true;
    validator.invalidate();
  }

  private void applyBackgroundOperationResult(@NotNull BackgroundOperationResult result) {
    assert EventQueue.isDispatchThread();
    Map<String, VirtualFile> modules = null;
    try {
      if (result.myStatus == PageStatus.OK) {
        modules = ProjectImportUtil.findModules(result.myVfile, myContext.getProject());
        AdtImportBuilder builder = (AdtImportBuilder)myContext.getProjectBuilder();
        assert builder != null;
        builder.setSelectedProject(new File(mySourceLocation.getText()));
      }
    }
    catch (IOException e) {
      LOG.error(e);
      result = PageStatus.INTERNAL_ERROR.result();
    }
    myIsValidating = false;
    myResult = result;
    updateStatusDisplay(myResult.myStatus);
    myUpdateListener.update();
    myState.setImportKind(result.myImportKind);
    refreshModulesList(result.myVfile, modules);
    myState.setModulesToImport(modules);
  }

  private void refreshModulesList(@Nullable VirtualFile vfile, @Nullable Map<String, VirtualFile> modules) {
    // No need to show table when importing a single module
    boolean hasModules = modules != null && modules.size() > 1;
    myModulesList.setVisible(hasModules);
    myModuleImportLabel.setVisible(hasModules);
    myModulesModel.setModules(vfile, modules);
  }

  private void createUIComponents() {
    myValidationProgress = new AsyncProcessIcon("validation");
    myValidationProgress.setVisible(false);
  }

  @Override
  public boolean validate() {
    return myResult.myStatus.severity != ERROR && !myIsValidating;
  }

  @Override
  public boolean isValid() {
    return validate();
  }

  @NotNull
  @VisibleForTesting
  protected BackgroundOperationResult checkPath(@NotNull String path) {
    path = path.trim();
    if (Strings.isNullOrEmpty(path)) {
      return PageStatus.EMPTY_PATH.result();
    }
    VirtualFile vfile = VfsUtil.findFileByIoFile(new File(path), false);
    if (vfile == null || !vfile.exists()) {
      return PageStatus.DOES_NOT_EXIST.result();
    }
    else if (isInProject(vfile)) {
      return PageStatus.NESTED_IN_PROJECT.result();
    }
    ImportSourceKind kind = ProjectImportUtil.getImportLocationKind(vfile);
    if (kind != ImportSourceKind.ADT && kind != ImportSourceKind.GRADLE) {
      return PageStatus.NOT_ADT_OR_GRADLE.result();
    }
    return new BackgroundOperationResult(PageStatus.OK, vfile, kind);
  }

  private boolean isInProject(VirtualFile path) {
    Project project = myContext.getProject();
    return project == null || VfsUtilCore.isAncestor(project.getBaseDir(), path, false);
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public boolean isStepVisible() {
    return myState.myIsModuleImport;
  }

  @Override
  public void updateDataModel() {
    // Do nothing?
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySourceLocation.getTextField();
  }

  // TODO: Validate:
  // 1. Not in "workspace"
  // 2. Exists
  // 3. ADT or Gradle
  @VisibleForTesting
  enum PageStatus {
    OK(null, null), EMPTY_PATH("Path is empty", ERROR), DOES_NOT_EXIST("Path does not exist", ERROR),
    NESTED_IN_PROJECT("Path points to a location within your project", ERROR),
    NOT_ADT_OR_GRADLE("Specify location of the Gradle or Android Eclipse project", ERROR),
    INTERNAL_ERROR("Internal error, please check the IDE log", ERROR), VALIDATING("Validating", null);

    @Nullable public final String message;
    @Nullable public final MessageType severity;

    PageStatus(@Nullable String message, @Nullable MessageType severity) {
      this.message = message;
      this.severity = severity;
    }

    public BackgroundOperationResult result() {
      return new BackgroundOperationResult(this, null, null);
    }

    @Nullable
    public Icon getIcon() {
      return severity == null ? null : severity.getDefaultIcon();
    }

    public boolean isSpinnerVisible() {
      return this == VALIDATING;
    }
  }

  @VisibleForTesting
  static final class BackgroundOperationResult {
    @NotNull public final PageStatus myStatus;
    @Nullable public final VirtualFile myVfile;
    @Nullable public final ImportSourceKind myImportKind;

    private BackgroundOperationResult(@NotNull PageStatus status,
                                      @Nullable VirtualFile vfile,
                                      @Nullable ImportSourceKind importKind) {
      myStatus = status;
      myVfile = vfile;
      myImportKind = importKind;
    }
  }

  private static class ModulesTableModel extends AbstractTableModel {
    private @NotNull List<String> mySortedNames = Collections.emptyList();
    private @Nullable Map<String, VirtualFile> myModules = null;
    @Nullable private VirtualFile myBase;


    @Override
    public int getRowCount() {
      return mySortedNames.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex >= mySortedNames.size()) {
        return "<Out of bounds>";
      }

      String name = mySortedNames.get(rowIndex);
      switch (columnIndex) {
        case 0:
          return name;
        case 1:
          return getModulePath(name);
        default:
          return String.format("Column %d", columnIndex);
      }
    }

    @NotNull
    private String getModulePath(@NotNull String name) {
      assert myModules != null;
      VirtualFile vfile = myModules.get(name);
      if (vfile == null) {
        return "";
      }
      else {
        return Files.simplifyPath(getRelativePath(vfile, myBase));
      }
    }

    public void setModules(@Nullable VirtualFile base, @Nullable Map<String, VirtualFile> modules) {
      myBase = base;
      mySortedNames = modules == null ? Collections.<String>emptyList() : new ArrayList<String>(modules.keySet());
      myModules = modules;
      Collections.sort(mySortedNames, Collator.getInstance());
      fireTableDataChanged();
    }
  }
}
