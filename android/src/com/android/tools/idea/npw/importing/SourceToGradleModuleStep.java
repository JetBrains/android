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
package com.android.tools.idea.npw.importing;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.project.ModuleImporter;
import com.android.tools.idea.gradle.project.ModuleToImport;
import com.android.tools.idea.ui.ExpensiveTask;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.ui.properties.swing.VisibleProperty;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.ui.validation.validators.FalseValidator;
import com.android.tools.idea.wizard.model.ModelWizard.Facade;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import static com.android.tools.idea.ui.properties.expressions.bool.BooleanExpressions.not;

/**
 * Wizard Step that allows the user to point to an existing source directory (ADT or Gradle) to import as a new Android Gradle module.
 * Also allows selection of sub-modules to import. Most functionality is contained within existing {@link ModulesTable} class.
 */
public final class SourceToGradleModuleStep extends SkippableWizardStep<SourceToGradleModuleModel> {

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(SourceToGradleModuleStep.class);
  }

  private final ListenerManager myListeners = new ListenerManager();
  private final BindingsManager myBindings = new BindingsManager();

  private final BoolProperty myIsValidating = new BoolValueProperty();
  private final ObjectProperty<Validator.Result> myValidationResult = new ObjectValueProperty<>(Validator.Result.OK);
  private final OptionalProperty<ModuleImporter> myModuleImporter = new OptionalValueProperty<>();
  private final ExpensiveTask.Runner myTaskRunner = new ExpensiveTask.Runner();

  private ValidatorPanel myValidatorPanel;
  private JPanel myPanel;
  private TextFieldWithBrowseButton mySourceLocation;
  private AsyncProcessIcon myValidationProgress;
  private JBScrollPane myModulesScroller;
  private ModulesTable myModulesPanel;
  private JLabel myRequiredModulesLabel;
  private JLabel myModuleNameLabel;
  private JTextField myModuleNameField;
  private JLabel myPrimaryModuleState;

  public SourceToGradleModuleStep(@NotNull SourceToGradleModuleModel model) {
    super(model, AndroidBundle.message("android.wizard.module.import.source.title"));

    myValidatorPanel = new ValidatorPanel(this, myPanel);
  }

  @Override
  protected void onWizardStarting(@NotNull Facade wizard) {
    //noinspection DialogTitleCapitalization - incorrectly detects "Gradle" as incorrectly capitalised
    mySourceLocation.addBrowseFolderListener(AndroidBundle.message("android.wizard.module.import.source.browse.title"),
                                             AndroidBundle.message("android.wizard.module.import.source.browse.description"),
                                             getModel().getProject(),
                                             FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor());

    myBindings.bindTwoWay(new TextProperty(mySourceLocation.getTextField()), getModel().sourceLocation());

    myBindings.bind(new VisibleProperty(myValidationProgress), myIsValidating);

    myPanel.setBorder(new EmptyBorder(UIUtil.PANEL_REGULAR_INSETS));

    myModulesPanel.bindPrimaryModuleEntryComponents(new PrimaryModuleImportSettings(), myRequiredModulesLabel);
    myModulesPanel.addPropertyChangeListener(ModulesTable.PROPERTY_SELECTED_MODULES, event -> {
      if (ModulesTable.PROPERTY_SELECTED_MODULES.equals(event.getPropertyName())) {
        verifyAtLeaseOneModuleIsSelected();
      }
    });

    myValidatorPanel.registerValidator(myIsValidating,
                                       new FalseValidator(Validator.Severity.INFO,
                                                          AndroidBundle.message("android.wizard.module.import.source.browse.validating")));
    // myValidationResult is set externally. Just pass the result on.
    myValidatorPanel.registerValidator(myValidationResult, result -> result);

    myListeners.receiveAndFire(getModel().sourceLocation(), sourcePath -> {
      ApplicationManager.getApplication().assertIsDispatchThread();
      myTaskRunner.setTask(new FindSubmoduleTask(sourcePath));
    });

    myListeners.receive(myModuleImporter, importer -> {
      ModuleImporter.setImporter(getModel().getContext(), importer.orElse(null));
      wizard.updateNavigationProperties(); // Updating importer may affect later step visibility
    });
  }

  @Override
  protected void onProceeding() {
    getModel().setModulesToImport(myModulesPanel.getSelectedModulesMap());
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return not(myValidatorPanel.hasErrors().or(myIsValidating));
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    WizardContext context = getModel().getContext();
    ArrayList<ModelWizardStep> wrappedSteps = new ArrayList<>();

    for (ModuleImporter importer : ModuleImporter.getAllImporters(context)) {
      for (ModuleWizardStep inputStep : importer.createWizardSteps()) {
        wrappedSteps.add(new ModuleWizardStepAdapter(context, inputStep));
      }
    }

    return wrappedSteps;
  }

  private void verifyAtLeaseOneModuleIsSelected() {
    // Validation of import location can be superseded by lack of modules selected for import
    if (!myIsValidating.get() &&
        myValidationResult.get().getSeverity() != Validator.Severity.ERROR &&
        myModulesPanel.getSelectedModules().isEmpty()) {
      myValidationResult.set(
        new Validator.Result(Validator.Severity.ERROR, AndroidBundle.message("android.wizard.module.import.source.browse.no.modules")));
    }
  }

  private void createUIComponents() {
    myValidationProgress = new AsyncProcessIcon("validation");
  }

  /**
   * A worker which runs a potentially expensive {@link SubmoduleFinder} operation on a background
   * thread. Only one should run at a time. Before starting and when finished, it updates the UI.
   */
  private final class FindSubmoduleTask extends ExpensiveTask {
    @NotNull private final String myRootPath;
    @Nullable private SubmoduleFinder.SearchResult mySearchResult;
    @Nullable private String myErrorMessage;

    public FindSubmoduleTask(@NotNull String rootPath) {
      myRootPath = rootPath;
    }

    @Override
    public void onStarting() {
      myValidationResult.set(Validator.Result.OK);
      myIsValidating.set(true);
    }

    @Override
    public void doBackgroundWork() throws Exception {
      SubmoduleFinder finder = new SubmoduleFinder(getModel());
      try {
        mySearchResult = finder.search(myRootPath);
      }
      catch (SubmoduleFinder.SearchException e) {
        myErrorMessage = e.getMessage();
      }
    }

    @Override
    public void onFinished() {
      updateSearchResults(mySearchResult);
      if (myErrorMessage != null) {
        myValidationResult.set(new Validator.Result(Validator.Severity.ERROR, myErrorMessage));
      }
      else {
        assert (mySearchResult != null); // A null myErrorMessage -> not-null mySearchResult
        Set<String> missingSourceModuleNames = Sets.newTreeSet();
        for (ModuleToImport module : mySearchResult.modules) {
          if (module.location == null || !module.location.exists()) {
            missingSourceModuleNames.add(module.name);
          }
        }
        if (!missingSourceModuleNames.isEmpty()) {
          final String formattedMessage = ImportUIUtil.formatElementListString(
            missingSourceModuleNames,
            AndroidBundle.message("android.wizard.module.import.source.browse.bad.modules.1"),
            AndroidBundle.message("android.wizard.module.import.source.browse.bad.modules.2"),
            AndroidBundle.message("android.wizard.module.import.source.browse.bad.modules.more"));
          myValidationResult.set(new Validator.Result(Validator.Severity.WARNING, formattedMessage));
        }
        else {
          myValidationResult.set(Validator.Result.OK);
        }
      }
      myIsValidating.set(false);
    }

    private void updateSearchResults(@Nullable SubmoduleFinder.SearchResult searchResult) {
      VirtualFile path = null;
      Collection<ModuleToImport> modules = null;
      ModuleImporter importer = null;
      if (searchResult != null) {
        path = searchResult.rootPath;
        modules = searchResult.modules;
        importer = searchResult.importer;
      }

      myModulesPanel.setModules(getModel().getProject(), path, modules);
      myModulesScroller.setVisible(myModulesPanel.getComponentCount() > 0);
      myModuleImporter.setNullableValue(importer);

      verifyAtLeaseOneModuleIsSelected();
    }
  }

  /**
   * A class responsible for searching for all Gradle submodules given a starting path. This may
   * take a long time if a project has a lot of files in it, so {@link #search(String)} should be
   * called on the background thread.
   *
   * If the search fails, it will throw a {@link SearchException} with an error message that should
   * be shown to the user.
   */
  @VisibleForTesting
  static final class SubmoduleFinder {
    @NotNull private final SourceToGradleModuleModel myModel;

    public SubmoduleFinder(@NotNull SourceToGradleModuleModel model) {
      myModel = model;
    }

    public static final class SearchResult {
      public final VirtualFile rootPath;
      public final ModuleImporter importer;
      public final Collection<ModuleToImport> modules;
      public SearchResult(VirtualFile rootPath,
                          ModuleImporter importer,
                          Collection<ModuleToImport> modules) {
        this.rootPath = rootPath;
        this.importer = importer;
        this.modules = modules;
      }
    }

    public static final class SearchException extends Exception {
      public SearchException(String message) {
        super(message);
      }
    }

    @NotNull
    public SearchResult search(@NotNull String path) throws SearchException {
      if (Strings.isNullOrEmpty(path)) {
        throw new SearchException(AndroidBundle.message("android.wizard.module.import.source.browse.no.location"));
      }

      VirtualFile vFile = VfsUtil.findFileByIoFile(new File(path), false);

      if (vFile == null || !vFile.exists()) {
        throw new SearchException(AndroidBundle.message("android.wizard.module.import.source.browse.invalid.location"));
      }
      else if (isProjectOrModule(vFile)) {
        throw new SearchException(AndroidBundle.message("android.wizard.module.import.source.browse.taken.location"));
      }

      ModuleImporter importer = ModuleImporter.importerForLocation(myModel.getContext(), vFile);
      if (!importer.isValid()) {
        throw new SearchException(AndroidBundle.message("android.wizard.module.import.source.browse.cant.import"));
      }

      Collection<ModuleToImport> modules = ApplicationManager.getApplication().runReadAction((Computable<Collection<ModuleToImport>>)() -> {
        try {
          return importer.findModules(vFile);
        }
        catch (IOException e) {
          getLog().error(e);
          return null;
        }
      });

      if (modules == null) {
        throw new SearchException(AndroidBundle.message("android.wizard.module.import.source.browse.error"));
      }

      return new SearchResult(vFile, importer, modules);
    }

    private boolean isProjectOrModule(@NotNull VirtualFile dir) {
      Project project = myModel.getProject();
      if (dir.equals(project.getBaseDir())) {
        return true;
      }

      for (Module module : ModuleManager.getInstance(project).getModules()) {
        if (ModuleUtilCore.isModuleDir(module, dir)) {
          return true;
        }
      }

      return false;
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
    @NotNull
    public String getModuleName() {
      return myModuleNameField.getText();
    }

    @Override
    public void setModuleName(@NotNull String moduleName) {
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
    public void addActionListener(@NotNull ActionListener actionListener) {
      myModuleNameField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          actionListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "changed"));
        }
      });
    }
  }
}
