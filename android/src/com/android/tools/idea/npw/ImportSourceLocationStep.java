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
package com.android.tools.idea.npw;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.project.ModuleImporter;
import com.android.tools.idea.gradle.project.ModuleToImport;
import com.android.tools.idea.wizard.dynamic.AndroidStudioWizardStep;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.ui.MessageType.ERROR;
import static com.intellij.openapi.ui.MessageType.WARNING;

/**
 * Wizard page for selecting source location for module import.
 */
public class ImportSourceLocationStep extends ModuleWizardStep implements AndroidStudioWizardStep {
  private static final int VALIDATION_STATUS_DISPLAY_DELAY = 50; //ms
  private final Logger LOG = Logger.getInstance(ImportSourceLocationStep.class);
  private final NewModuleWizardState myState;
  private final Timer myDelayedValidationProgressDisplay;
  @NotNull private final TemplateWizardStep.UpdateListener myUpdateListener;
  @NotNull private final WizardContext myContext;
  private JPanel myPanel;
  private TextFieldWithBrowseButton mySourceLocation;
  private JBLabel myErrorWarning;
  private AsyncProcessIcon myValidationProgress;
  private JBScrollPane myModulesScroller;
  private ModulesTable myModulesPanel;
  private JLabel myRequiredModulesLabel;
  private JLabel myModuleNameLabel;
  private JTextField myModuleNameField;
  private JLabel myPrimaryModuleState;
  private AsyncValidator<?> validator;
  private PathValidationResult myPageValidationResult;
  private boolean myValidating = false;
  private PageStatus myStatus;
  private Icon mySidePanelIcon;

  public ImportSourceLocationStep(@NotNull WizardContext context,
                                  @NotNull NewModuleWizardState state,
                                  @Nullable Icon sidePanelIcon,
                                  @Nullable TemplateWizardStep.UpdateListener listener) {
    myErrorWarning.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
    myContext = context;
    mySidePanelIcon = sidePanelIcon;
    myUpdateListener = listener == null ? new TemplateWizardStep.UpdateListener() {
      @Override
      public void update() {
        // Do nothing
      }
    } : listener;
    myState = state;
    myPanel.setBorder(new EmptyBorder(UIUtil.PANEL_REGULAR_INSETS));
    myModulesScroller.setVisible(false);
    myModulesPanel.bindPrimaryModuleEntryComponents(new PrimaryModuleImportSettings(), myRequiredModulesLabel);
    PropertyChangeListener modulesListener = new PropertyChangeListener() {
      @SuppressWarnings("unchecked")
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (ModulesTable.PROPERTY_SELECTED_MODULES.equals(evt.getPropertyName())) {
          updateStepStatus(myPageValidationResult);
        }
      }
    };
    myModulesPanel.addPropertyChangeListener(ModulesTable.PROPERTY_SELECTED_MODULES, modulesListener);

    validator = new AsyncValidator<PathValidationResult>(ApplicationManager.getApplication()) {
      @Override
      protected void showValidationResult(PathValidationResult result) {
        applyBackgroundOperationResult(result);
      }

      @NotNull
      @Override
      protected PathValidationResult validate() {
        return checkPath(mySourceLocation.getText());
      }
    };
    setupSourceLocationControls();
    myDelayedValidationProgressDisplay = new Timer(VALIDATION_STATUS_DISPLAY_DELAY, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myValidating) {
          updateStatusDisplay(PageStatus.VALIDATING, null);
        }
      }
    });
  }

  private static String multiLineJLabelText(String... messages) {
    StringBuilder builder = new StringBuilder("<html><body><p>");
    Joiner.on("<br>").appendTo(builder, messages);
    builder.append("</p></body></html>");
    return builder.toString();
  }

  @Override
  public Icon getIcon() {
    return mySidePanelIcon;
  }

  private void setupSourceLocationControls() {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor();
    descriptor.setTitle("Select Source Location");
    descriptor.setDescription("Select existing ADT or Gradle project to import as a new subproject");
    mySourceLocation.addBrowseFolderListener(new TextBrowseFolderListener(descriptor));
    mySourceLocation.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        invalidate();
      }
    });

    applyBackgroundOperationResult(checkPath(mySourceLocation.getText()));
    myErrorWarning.setIcon(null);
    myErrorWarning.setText(null);
  }

  private void updateStatusDisplay(@NotNull PageStatus status, @Nullable Object details) {
    myValidationProgress.setVisible(status.isSpinnerVisible());
    myErrorWarning.setText(status.getMessage(details));
    myErrorWarning.setIcon(status.getIcon());
    myUpdateListener.update();
  }

  private void invalidate() {
    if (!myDelayedValidationProgressDisplay.isRunning()) {
      myDelayedValidationProgressDisplay.start();
    }
    myValidating = true;
    validator.invalidate();
  }

  private void applyBackgroundOperationResult(@NotNull PathValidationResult result) {
    assert EventQueue.isDispatchThread();
    Collection<ModuleToImport> modules = null;
    Project project = myContext.getProject();
    try {
      if (result.myStatus == PageStatus.OK) {
        assert result.myVfile != null && result.myImporter != null;
        modules = result.myImporter.findModules(result.myVfile);
        Set<String> missingSourceModuleNames = Sets.newTreeSet();
        for (ModuleToImport module : modules) {
          if (module.location == null || !module.location.exists()) {
            missingSourceModuleNames.add(module.name);
          }
        }
        if (!missingSourceModuleNames.isEmpty()) {
          result = new PathValidationResult(PageStatus.MISSING_SUBPROJECTS,
                                            result.myVfile, result.myImporter, missingSourceModuleNames);
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
      result = PageStatus.INTERNAL_ERROR.result();
    }
    myValidating = false;
    myModulesPanel.setModules(project, result.myVfile, modules);
    myModulesScroller.setVisible(myModulesPanel.getComponentCount() > 0);
    ModuleImporter.setImporter(myContext, result.myImporter);
    updateStepStatus(result);
  }

  private void updateStepStatus(PathValidationResult result) {
    Object validationDetails = result.myDetails;
    PageStatus status = result.myStatus;
    Map<String, VirtualFile> selectedModules = Collections.emptyMap();
    if (!MessageType.ERROR.equals(status.severity)) {
      final Collection<ModuleToImport> modules = myModulesPanel.getSelectedModules();
      if (modules.isEmpty()) {
        status = PageStatus.NO_MODULES_SELECTED;
        validationDetails = null;
      }
      else {
        selectedModules = Maps.newHashMap();
        for (ModuleToImport module : modules) {
          selectedModules.put(myModulesPanel.getModuleName(module), module.location);
        }
      }
    }
    myPageValidationResult = result;
    myState.setModulesToImport(selectedModules);
    updateStatusDisplay(status, validationDetails);
    myStatus = status;
    myUpdateListener.update();
  }

  private void createUIComponents() {
    myValidationProgress = new AsyncProcessIcon("validation");
    myValidationProgress.setVisible(false);
  }

  @Override
  public boolean validate() {
    return myStatus.severity != ERROR && !myValidating && myModulesPanel.canImport();
  }

  @Override
  public boolean isValid() {
    return validate();
  }

  @NotNull
  @VisibleForTesting
  protected PathValidationResult checkPath(@NotNull String path) {
    path = path.trim();
    if (Strings.isNullOrEmpty(path)) {
      return PageStatus.EMPTY_PATH.result();
    }
    VirtualFile vfile = VfsUtil.findFileByIoFile(new File(path), false);
    if (vfile == null || !vfile.exists()) {
      return PageStatus.DOES_NOT_EXIST.result();
    }
    else if (isProjectOrModule(vfile)) {
      return PageStatus.IS_PROJECT_OR_MODULE.result();
    }
    ModuleImporter kind = ModuleImporter.importerForLocation(myContext, vfile);
    if (!kind.isValid()) {
      return PageStatus.NOT_ADT_OR_GRADLE.result();
    }
    return new PathValidationResult(PageStatus.OK, vfile, kind, null);
  }

  private boolean isProjectOrModule(@NotNull VirtualFile dir) {
    Project project = myContext.getProject();
    if (project != null) {
      if (dir.equals(project.getBaseDir())) {
        return true;
      }
      else {
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          if (ModuleUtilCore.isModuleDir(module, dir)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateDataModel() {
    // Do nothing?
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySourceLocation.getTextField();
  }

  @VisibleForTesting
  enum PageStatus {
    OK(null, null), EMPTY_PATH("Path is empty", ERROR), DOES_NOT_EXIST("Path does not exist", ERROR),
    IS_PROJECT_OR_MODULE("This location is already imported", ERROR),
    MISSING_SUBPROJECTS("Some projects were not found", WARNING),
    NO_MODULES_SELECTED("Select modules to import", ERROR),
    NOT_ADT_OR_GRADLE("Specify location of the Gradle or Android Eclipse project", ERROR),
    INTERNAL_ERROR("Internal error, please check the IDE log", ERROR), VALIDATING("Validating", null);

    @Nullable public final MessageType severity;
    @Nullable private final String message;

    PageStatus(@Nullable String message, @Nullable MessageType severity) {
      this.message = message;
      this.severity = severity;
    }

    public PathValidationResult result() {
      return new PathValidationResult(this, null, null, null);
    }

    @Nullable
    public Icon getIcon() {
      return severity == null ? null : severity.getDefaultIcon();
    }

    public boolean isSpinnerVisible() {
      return this == VALIDATING;
    }

    @SuppressWarnings("unchecked")
    public String getMessage(@Nullable Object details) {
      if (this == MISSING_SUBPROJECTS && details instanceof Collection) {
        final String message = ImportUIUtil
          .formatElementListString((Collection<String>)details, "Unable to find sources for subproject %1$s.",
                                   "Unable to find sources for subprojects %1$s and %2$s.",
                                   "Unable to find sources for %1$s and %2$d more subprojects.");
        return multiLineJLabelText(message, "This may result in missing dependencies.");
      }
      else {
        return Strings.nullToEmpty(message);
      }
    }

  }

  @VisibleForTesting
  static final class PathValidationResult {
    @NotNull public final PageStatus myStatus;
    @Nullable public final VirtualFile myVfile;
    @Nullable public final ModuleImporter myImporter;
    @Nullable public final Object myDetails;

    private PathValidationResult(@NotNull PageStatus status,
                                 @Nullable VirtualFile vfile,
                                 @Nullable ModuleImporter importer,
                                 @Nullable Object details) {
      myStatus = status;
      myVfile = vfile;
      myImporter = importer;
      myDetails = details;
    }
  }

  private final class PrimaryModuleImportSettings implements ModuleImportSettings {
    @Override
    public boolean isModuleSelected() {
      return true;
    }

    @Override
    public void setModuleSelected(boolean selected) {
      // Do nothing - primary module
    }

    @Override
    public String getModuleName() {
      return myModuleNameField.getText();
    }

    @Override
    public void setModuleName(String moduleName) {
      if (!Objects.equal(moduleName, myModuleNameField.getText())) {
        myModuleNameField.setText(moduleName);
      }
    }

    @Override
    public void setModuleSourcePath(String relativePath) {
      // Nothing
    }

    @Override
    public void setCanToggleModuleSelection(boolean b) {
      // Nothing
    }

    @Override
    public void setCanRenameModule(boolean canRenameModule) {
      myModuleNameField.setEnabled(canRenameModule);
    }

    @Override
    public void setValidationStatus(@Nullable MessageType statusSeverity, @Nullable String statusDescription) {
      myPrimaryModuleState.setIcon(statusSeverity == null ? null : statusSeverity.getDefaultIcon());
      myPrimaryModuleState.setText(Strings.nullToEmpty(statusDescription));
    }

    @Override
    public void setVisible(boolean visible) {
      myPrimaryModuleState.setVisible(visible);
      myModuleNameField.setVisible(visible);
      myModuleNameLabel.setVisible(visible);
    }

    @Override
    public void addActionListener(final ActionListener actionListener) {
      myModuleNameField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          actionListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "changed"));
        }
      });
    }
  }
}
