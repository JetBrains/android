/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.wizard2.ui;

import static java.util.stream.Collectors.toList;

import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewSet.ProjectViewFile;
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.projectview.ProjectViewVerifier;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ProjectViewDefaultValueProvider;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.ImportSection;
import com.google.idea.blaze.base.projectview.section.sections.Sections;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.OutputSink.Propagation;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.IssueOutput.Category;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.settings.ui.JPanelProvidingProject;
import com.google.idea.blaze.base.settings.ui.ProjectViewUi;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.ui.BlazeValidationError;
import com.google.idea.blaze.base.ui.BlazeValidationResult;
import com.google.idea.blaze.base.ui.UiUtil;
import com.google.idea.blaze.base.wizard2.BlazeNewProjectBuilder;
import com.google.idea.blaze.base.wizard2.BlazeSelectProjectViewOption;
import com.google.idea.blaze.base.wizard2.ProjectDataDirectoryValidator;
import com.google.idea.blaze.base.wizard2.WorkspaceTypeData;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.icons.AllIcons.General;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagLayout;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/** The UI control to collect project settings when importing a Blaze project. */
public final class BlazeEditProjectViewControl {

  private static final FileChooserDescriptor PROJECT_FOLDER_DESCRIPTOR =
      new FileChooserDescriptor(false, true, false, false, false, false);
  private static final Logger logger = Logger.getInstance(BlazeEditProjectViewControl.class);

  private static final BoolExperiment allowAddprojectViewDefaultValues =
      new BoolExperiment("allow.add.project.view.default.values", true);
  private static final String LAST_WORKSPACE_MODE_PROPERTY =
      "blaze.edit.project.view.control.last.workspace.mode";
  private static final String LAST_PROJECT_LOCATION_PROPERTY =
      "blaze.edit.project.view.control.last.project.location";

  private static final String EMPTY_DIRECTORIES_QUESTION =
      "Are you sure you want to create a project with no source directories?";

  private final JPanel component;
  private final String buildSystemName;
  private final ProjectViewUi projectViewUi;

  private TextFieldWithBrowseButton projectDataDirField;
  private JTextField projectNameField;
  private JRadioButton workspaceDefaultNameOption;
  private JRadioButton branchDefaultNameOption;
  private JRadioButton importDirectoryDefaultNameOption;
  private HashCode paramsHash;
  private WorkspacePathResolver workspacePathResolver;
  private WorkspaceTypeData workspaceData;
  private BlazeSelectProjectViewOption projectViewOption;
  private boolean isInitialising;
  private boolean defaultWorkspaceNameModeExplicitlySet;

  private enum InferDefaultNameMode {
    FromWorkspace,
    FromBranch,
    FromImportDirectory,
  }

  public BlazeEditProjectViewControl(BlazeNewProjectBuilder builder, Disposable parentDisposable) {
    this.projectViewUi = new ProjectViewUi(parentDisposable);
    JPanel content = new JPanel(new GridBagLayout());
    fillUi(content);
    update(builder);
    UiUtil.fillBottom(content);
    JScrollPane scrollPane = new JScrollPane(content);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    JPanel component = new JPanelProvidingProject(ProjectViewUi.getProject(), new BorderLayout());
    component.add(scrollPane);
    this.component = component;
    this.buildSystemName = builder.getBuildSystemName();
  }

  public Component getUiComponent() {
    return component;
  }

  private void fillUi(JPanel canvas) {
    JLabel projectDataDirLabel = new JBLabel("Project data directory:");

    canvas.setPreferredSize(ProjectViewUi.getContainerSize());

    projectDataDirField = new TextFieldWithBrowseButton();
    projectDataDirField.setName("project-data-dir-field");
    projectDataDirField.addBrowseFolderListener(
        "",
        buildSystemName + " project data directory",
        null,
        PROJECT_FOLDER_DESCRIPTOR,
        TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
        false);
    final String dataDirToolTipText = "Directory in which to store the project's metadata.";
    projectDataDirField.setToolTipText(dataDirToolTipText);
    projectDataDirLabel.setToolTipText(dataDirToolTipText);

    canvas.add(projectDataDirLabel, UiUtil.getLabelConstraints(0));
    canvas.add(projectDataDirField, UiUtil.getFillLineConstraints(0));

    JLabel projectNameLabel = new JLabel("Project name:");
    projectNameField = new JTextField();
    final String projectNameToolTipText = "Project display name.";
    projectNameField.setToolTipText(projectNameToolTipText);
    projectNameField.setName("project-name-field");
    projectNameLabel.setToolTipText(projectNameToolTipText);
    canvas.add(projectNameLabel, UiUtil.getLabelConstraints(0));
    canvas.add(projectNameField, UiUtil.getFillLineConstraints(0));

    JLabel defaultNameLabel = new JLabel("Infer name from:");
    workspaceDefaultNameOption = new JRadioButton("Workspace");
    branchDefaultNameOption = new JRadioButton("Branch");
    importDirectoryDefaultNameOption = new JRadioButton("Import Directory");

    workspaceDefaultNameOption.setToolTipText("Infer default name from the workspace name");
    branchDefaultNameOption.setToolTipText(
        "Infer default name from the current branch of your workspace");
    importDirectoryDefaultNameOption.setToolTipText(
        "Infer default name from the directory used to import your project view");

    workspaceDefaultNameOption.addItemListener(e -> inferDefaultNameModeSelectionChanged());
    branchDefaultNameOption.addItemListener(e -> inferDefaultNameModeSelectionChanged());
    importDirectoryDefaultNameOption.addItemListener(e -> inferDefaultNameModeSelectionChanged());
    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(workspaceDefaultNameOption);
    buttonGroup.add(branchDefaultNameOption);
    buttonGroup.add(importDirectoryDefaultNameOption);
    canvas.add(defaultNameLabel, UiUtil.getLabelConstraints(0));
    canvas.add(workspaceDefaultNameOption, UiUtil.getLabelConstraints(0));
    canvas.add(branchDefaultNameOption, UiUtil.getLabelConstraints(0));
    canvas.add(importDirectoryDefaultNameOption, UiUtil.getLabelConstraints(0));
    canvas.add(new JPanel(), UiUtil.getFillLineConstraints(0));

    projectViewUi.fillUi(canvas);
  }

  public void update(BlazeNewProjectBuilder builder) {
    this.workspaceData = builder.getWorkspaceData();
    this.projectViewOption = builder.getProjectViewOption();
    WorkspaceRoot workspaceRoot = workspaceData.workspaceRoot();
    WorkspacePath workspacePath = projectViewOption.getSharedProjectView();
    String initialProjectViewText = projectViewOption.getInitialProjectViewText();
    boolean allowAddDefaultValues =
        projectViewOption.allowAddDefaultProjectViewValues()
            && allowAddprojectViewDefaultValues.getValue();
    WorkspacePathResolver workspacePathResolver = workspaceData.workspacePathResolver();

    HashCode hashCode =
        Hashing.md5()
            .newHasher()
            .putUnencodedChars(workspaceRoot.toString())
            .putUnencodedChars(workspacePath != null ? workspacePath.toString() : "")
            .putUnencodedChars(initialProjectViewText != null ? initialProjectViewText : "")
            .putBoolean(allowAddDefaultValues)
            .hash();

    // If any params have changed, reinit the control
    if (!hashCode.equals(paramsHash)) {
      this.paramsHash = hashCode;
      this.isInitialising = true;
      init(
          workspaceData.buildSystem(),
          workspacePathResolver,
          workspacePath,
          initialProjectViewText,
          allowAddDefaultValues);
      this.isInitialising = false;
    }
  }

  private static String modifyInitialProjectView(
      BuildSystemName buildSystemName,
      String initialProjectViewText,
      WorkspacePathResolver workspacePathResolver) {
    BlazeContext context = BlazeContext.create();
    ProjectViewParser projectViewParser = new ProjectViewParser(context, workspacePathResolver);
    projectViewParser.parseProjectView(initialProjectViewText);
    ProjectViewSet projectViewSet = projectViewParser.getResult();
    ProjectViewFile projectViewFile = projectViewSet.getTopLevelProjectViewFile();
    if (projectViewFile == null) {
      return initialProjectViewText;
    }
    ProjectView projectView = projectViewFile.projectView;

    // Sort default value providers to match the section order
    List<SectionKey<?, ?>> sectionKeys =
        Sections.getParsers().stream().map(SectionParser::getSectionKey).collect(toList());
    List<ProjectViewDefaultValueProvider> defaultValueProviders =
        Lists.newArrayList(ProjectViewDefaultValueProvider.EP_NAME.getExtensions());
    defaultValueProviders.sort(
        Comparator.comparingInt(val -> sectionKeys.indexOf(val.getSectionKey())));
    for (ProjectViewDefaultValueProvider defaultValueProvider : defaultValueProviders) {
      projectView =
          defaultValueProvider.addProjectViewDefaultValue(
              buildSystemName, projectViewSet, projectView);
    }
    return ProjectViewParser.projectViewToString(projectView);
  }

  private void init(
      BuildSystemName buildSystemName,
      WorkspacePathResolver workspacePathResolver,
      @Nullable WorkspacePath sharedProjectView,
      @Nullable String initialProjectViewText,
      boolean allowAddDefaultValues) {
    if (allowAddDefaultValues && initialProjectViewText != null) {
      initialProjectViewText =
          modifyInitialProjectView(buildSystemName, initialProjectViewText, workspacePathResolver);
    }
    this.workspacePathResolver = workspacePathResolver;

    updateDefaultProjectNameUiState();
    updateDefaultProjectName();

    String projectViewText = "";
    File sharedProjectViewFile;

    if (sharedProjectView != null) {
      sharedProjectViewFile = workspacePathResolver.resolveToFile(sharedProjectView);

      try {
        projectViewText =
            ProjectViewStorageManager.getInstance().loadProjectView(sharedProjectViewFile);
        if (projectViewText == null) {
          logger.error("Could not load project view: " + sharedProjectViewFile);
          projectViewText = "";
        }
      } catch (IOException e) {
        logger.error(e);
      }
    } else {
      projectViewText = initialProjectViewText;
      logger.assertTrue(projectViewText != null);
    }

    projectViewUi.init(
        workspacePathResolver,
        projectViewText,
        sharedProjectView != null ? projectViewText : null,
        sharedProjectView,
        sharedProjectView != null,
        false /* allowEditShared - not allowed during import */);
  }

  private void updateDefaultProjectNameUiState() {
    workspaceDefaultNameOption.setEnabled(true);
    branchDefaultNameOption.setEnabled(workspaceData.branchName() != null);
    importDirectoryDefaultNameOption.setEnabled(projectViewOption.getImportDirectory() != null);

    InferDefaultNameMode inferDefaultNameMode = InferDefaultNameMode.FromImportDirectory;
    try {
      String lastModeString =
          PropertiesComponent.getInstance().getValue(LAST_WORKSPACE_MODE_PROPERTY);
      if (lastModeString != null) {
        inferDefaultNameMode = InferDefaultNameMode.valueOf(lastModeString);
      }
    } catch (IllegalArgumentException e) {
      // Ignore
    }
    switch (inferDefaultNameMode) {
      case FromWorkspace:
        workspaceDefaultNameOption.setSelected(true);
        break;
      case FromBranch:
        if (workspaceData.branchName() != null) {
          branchDefaultNameOption.setSelected(true);
        } else {
          workspaceDefaultNameOption.setSelected(true);
        }
        break;
      case FromImportDirectory:
        if (projectViewOption.getImportDirectory() != null) {
          importDirectoryDefaultNameOption.setSelected(true);
        } else {
          workspaceDefaultNameOption.setSelected(true);
        }
        break;
      default:
        throw new AssertionError("Illegal workspace name mode");
    }
  }

  private InferDefaultNameMode getInferDefaultNameMode() {
    if (workspaceDefaultNameOption.isSelected()) {
      return InferDefaultNameMode.FromWorkspace;
    } else if (branchDefaultNameOption.isSelected()) {
      return InferDefaultNameMode.FromBranch;
    } else if (importDirectoryDefaultNameOption.isSelected()) {
      return InferDefaultNameMode.FromImportDirectory;
    }
    return InferDefaultNameMode.FromWorkspace;
  }

  private void inferDefaultNameModeSelectionChanged() {
    if (!isInitialising) {
      updateDefaultProjectName();
      this.defaultWorkspaceNameModeExplicitlySet = true;
    }
  }

  private void updateDefaultProjectName() {
    String defaultProjectName = getDefaultName(getInferDefaultNameMode());
    projectNameField.setText(defaultProjectName);
    String defaultDataDir = getDefaultProjectDataDirectory(defaultProjectName);
    projectDataDirField.setText(defaultDataDir);
  }

  private String getDefaultName(InferDefaultNameMode inferDefaultNameMode) {
    switch (inferDefaultNameMode) {
      case FromWorkspace:
        return workspaceData.workspaceName();
      case FromBranch:
        return workspaceData.branchName();
      case FromImportDirectory:
        return projectViewOption.getImportDirectory();
      default:
        throw new AssertionError("Invalid workspace name mode.");
    }
  }

  private String getDefaultProjectDataDirectory(String projectName) {
    File canonicalProjectDataLocation = workspaceData.canonicalProjectDataLocation();
    if (canonicalProjectDataLocation != null) {
      return canonicalProjectDataLocation.getPath();
    }
    String lastProjectLocation =
        PropertiesComponent.getInstance().getValue(LAST_PROJECT_LOCATION_PROPERTY);
    if (lastProjectLocation == null) {
      // TODO(brendandouglas): remove this temporary fall-back once LAST_PROJECT_LOCATION_PROPERTY
      // is populated
      lastProjectLocation = RecentProjectsManager.getInstance().getLastProjectCreationLocation();
    }
    if (lastProjectLocation == null) {
      return newUniquePath(new File(getDefaultProjectsDirectory(), projectName));
    }
    // Because RecentProjectsManager uses PathUtil.toSystemIndependentName.
    lastProjectLocation = lastProjectLocation.replace('/', File.separatorChar);
    File lastProjectParent = new File(lastProjectLocation);
    if (lastProjectParent.getName().equals(BlazeDataStorage.PROJECT_DATA_SUBDIRECTORY)) {
      lastProjectParent = lastProjectParent.getParentFile();
    }
    return newUniquePath(new File(lastProjectParent, projectName));
  }

  private static File getDefaultProjectsDirectory() {
    final String userHome = SystemProperties.getUserHome();
    String productName = ApplicationNamesInfo.getInstance().getLowercaseProductName();
    return new File(userHome, productName.replace(" ", "") + "Projects");
  }

  /** Returns a unique file path by appending numbers until a non-collision is found. */
  private static String newUniquePath(File location) {
    if (!location.exists()) {
      return location.getAbsolutePath();
    }

    String name = location.getName();
    File directory = location.getParentFile();
    int tries = 0;
    while (true) {
      String candidateName = String.format("%s-%02d", name, tries);
      File candidateFile = new File(directory, candidateName);
      if (!candidateFile.exists()) {
        return candidateFile.getAbsolutePath();
      }
      tries++;
    }
  }

  @Nullable
  private File getProjectDataDir() {
    String projectDataDir = projectDataDirField.getText().trim();
    if (StringUtil.isEmpty(projectDataDir)) {
      return null;
    }
    if (workspaceData.canonicalProjectDataLocation() == null) {
      return new File(projectDataDir);
    }
    return new File(projectDataDir, BlazeDataStorage.PROJECT_DATA_SUBDIRECTORY);
  }

  public BlazeValidationResult validate() {
    if (projectViewOption.getOptionName().equals("generate-from-project-proto")) {
      // nothing to validate in this instance.
      // TODO(mathewi) this is a workaround, ideally we would not even show the "edit project view"
      // step in this case.
      return BlazeValidationResult.success();
    }
    // Validate project settings fields
    String projectName = projectNameField.getText().trim();
    if (StringUtil.isEmpty(projectName)) {
      return BlazeValidationResult.failure(
          new BlazeValidationError("Project name is not specified"));
    }
    File projectDataDir = getProjectDataDir();
    if (projectDataDir == null || !projectDataDir.isAbsolute()) {
      return BlazeValidationResult.failure(
          new BlazeValidationError("Project data directory is not valid"));
    }
    if (projectDataDir.exists()) {
      return BlazeValidationResult.failure(
          new BlazeValidationError(projectDataDir + " already exists"));
    }
    for (ProjectDataDirectoryValidator validator :
        ProjectDataDirectoryValidator.EP_NAME.getExtensions()) {
      BlazeValidationResult result = validator.validateDataDirectory(projectDataDir);
      if (!result.isSuccess()) {
        return result;
      }
    }
    List<IssueOutput> issues = Lists.newArrayList();

    ProjectViewSet projectViewSet = projectViewUi.parseProjectView(issues);
    BlazeValidationError projectViewParseError = validationErrorFromIssueList(issues);
    if (projectViewParseError != null) {
      return BlazeValidationResult.failure(projectViewParseError);
    }

    ProjectViewValidator projectViewValidator =
        new ProjectViewValidator(workspacePathResolver, projectViewSet);
    ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(
            projectViewValidator, "Validating Project", false, null);

    if (!projectViewValidator.success) {
      if (!projectViewValidator.errors.isEmpty()) {
        return BlazeValidationResult.failure(
            validationErrorFromIssueList(projectViewValidator.errors));
      }
      return BlazeValidationResult.failure(
          "Project view validation failed, but we couldn't find an error message. "
              + "Please report a bug.");
    }

    List<DirectoryEntry> directories = projectViewSet.listItems(DirectorySection.KEY);
    if (directories.isEmpty()) {
      if (workspaceData.buildSystem().equals(BuildSystemName.Blaze)) {
        int result =
            Messages.showOkCancelDialog(
                getUiComponent(),
                EMPTY_DIRECTORIES_QUESTION,
                "Use Empty Project?",
                General.QuestionDialog);
        if (result == Messages.CANCEL) {
          return BlazeValidationResult.cancelled();
        }
      } else {
        String msg = "Add some directories to index in the 'directories' section.";
        if (projectViewSet.listItems(TargetSection.KEY).isEmpty()) {
          msg += "\nTargets are also generally required to resolve sources.";
        }
        return BlazeValidationResult.failure(msg);
      }
    }

    return BlazeValidationResult.success();
  }

  private static class ProjectViewValidator implements Runnable {
    private final WorkspacePathResolver workspacePathResolver;
    private final ProjectViewSet projectViewSet;

    private boolean success;
    List<IssueOutput> errors = Lists.newArrayList();

    ProjectViewValidator(
        WorkspacePathResolver workspacePathResolver, ProjectViewSet projectViewSet) {
      this.workspacePathResolver = workspacePathResolver;
      this.projectViewSet = projectViewSet;
    }

    @Override
    public void run() {
      success = Scope.root(this::validateProjectView);
    }

    private Boolean validateProjectView(BlazeContext context) {
      context.addOutputSink(
          IssueOutput.class,
          output -> {
            if (output.getCategory() == Category.ERROR) {
              errors.add(output);
            }
            return Propagation.Continue;
          });
      WorkspaceLanguageSettings workspaceLanguageSettings =
          LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
      return ProjectViewVerifier.verifyProjectView(
          null, context, workspacePathResolver, projectViewSet, workspaceLanguageSettings);
    }
  }

  @Nullable
  private static BlazeValidationError validationErrorFromIssueList(List<IssueOutput> issues) {
    List<IssueOutput> errors =
        issues.stream()
            .filter(issue -> issue.getCategory() == IssueOutput.Category.ERROR)
            .collect(toList());

    if (!errors.isEmpty()) {
      StringBuilder errorMessage = new StringBuilder();
      errorMessage.append("The following issues were found:\n\n");
      for (IssueOutput issue : errors) {
        errorMessage.append(issue.getMessage());
        errorMessage.append('\n');
      }
      return new BlazeValidationError(errorMessage.toString());
    }
    return null;
  }

  public void updateBuilder(BlazeNewProjectBuilder builder) {
    String projectName = projectNameField.getText().trim();
    File projectDataDir = getProjectDataDir();
    String projectDataDirectory = projectDataDir.getPath();
    File localProjectViewFile =
        ProjectViewStorageManager.getLocalProjectViewFileName(
            builder.getBuildSystem(), new File(projectDataDirectory));

    BlazeSelectProjectViewOption selectProjectViewOption = builder.getProjectViewOption();
    boolean useSharedProjectView = projectViewUi.getUseSharedProjectView();

    // If we're using a shared project view, synthesize a local one that imports the shared one
    ProjectViewSet parseResult = projectViewUi.parseProjectView(Lists.newArrayList());

    final ProjectView projectView;
    final ProjectViewSet projectViewSet;
    if (useSharedProjectView && selectProjectViewOption.getSharedProjectView() != null) {
      projectView =
          ProjectView.builder()
              .add(
                  ScalarSection.builder(ImportSection.KEY)
                      .set(selectProjectViewOption.getSharedProjectView()))
              .build();
      projectViewSet =
          ProjectViewSet.builder()
              .addAll(parseResult.getProjectViewFiles())
              .add(localProjectViewFile, projectView)
              .build();
    } else {
      ProjectViewSet.ProjectViewFile projectViewFile = parseResult.getTopLevelProjectViewFile();
      assert projectViewFile != null;
      projectView = projectViewFile.projectView;
      projectViewSet = parseResult;
    }

    builder
        .setProjectView(projectView)
        .setProjectViewFile(localProjectViewFile)
        .setProjectViewSet(projectViewSet)
        .setProjectName(projectName)
        .setProjectDataDirectory(projectDataDirectory);
  }

  public void commit() {
    if (defaultWorkspaceNameModeExplicitlySet) {
      InferDefaultNameMode inferDefaultNameMode = getInferDefaultNameMode();
      PropertiesComponent.getInstance()
          .setValue(LAST_WORKSPACE_MODE_PROPERTY, inferDefaultNameMode.toString());
    }
    PropertiesComponent.getInstance()
        .setValue(
            LAST_PROJECT_LOCATION_PROPERTY,
            PathUtil.getParentPath(projectDataDirField.getText().trim()));
  }
}
