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
package com.google.idea.blaze.base.settings.ui;

import com.google.idea.blaze.base.lang.projectview.language.ProjectViewFileType;
import com.google.idea.blaze.base.lang.projectview.language.ProjectViewLanguage;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverProvider;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.LanguageTextField.SimpleDocumentCreator;
import com.intellij.ui.components.JBLabel;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** UI for changing the ProjectView. */
public class ProjectViewUi {

  private static final String USE_SHARED_PROJECT_VIEW = "Use shared project view file";

  private final Disposable parentDisposable;
  private EditorEx projectViewEditor;
  private JCheckBox useShared;

  private WorkspacePathResolver workspacePathResolver;
  private boolean useSharedProjectView;
  private boolean allowEditShared;
  private String sharedProjectViewText;

  public ProjectViewUi(Disposable parentDisposable) {
    this.parentDisposable = parentDisposable;
  }

  /**
   * To support the custom language features, we need a ProjectImpl, and it's not desirable to
   * create one from scratch.<br>
   *
   * @return the current, non-default project, if one exists, else the default project.
   */
  public static Project getProject() {
    Project project = (Project) DataManager.getInstance().getDataContext().getData("project");
    if (project != null && project instanceof ProjectImpl) {
      return project;
    }
    return ProjectManager.getInstance().getDefaultProject();
  }

  private static Dimension getEditorSize() {
    return new Dimension(1000, 550);
  }

  public static Dimension getContainerSize() {
    // Add pixels so we have room for our extra fields
    Dimension dimension = getEditorSize();
    return new Dimension(dimension.width, dimension.height + 200);
  }

  private static EditorEx createEditor(String tooltip) {
    Project project = getProject();
    Document document =
        LanguageTextField.createDocument(
            /* value= */ "", ProjectViewLanguage.INSTANCE, project, new SimpleDocumentCreator());
    EditorEx editor =
        (EditorEx)
            EditorFactory.getInstance()
                .createEditor(document, project, ProjectViewFileType.INSTANCE, false);
    final EditorSettings settings = editor.getSettings();
    settings.setLineNumbersShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setRightMarginShown(false);
    settings.setAdditionalPageAtBottom(false);
    editor.getComponent().setMinimumSize(getEditorSize());
    editor.getComponent().setPreferredSize(getEditorSize());
    editor.getComponent().setToolTipText(tooltip);
    editor.getComponent().setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
    editor.getComponent().setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
    return editor;
  }

  public void fillUi(JPanel canvas) {
    String tooltip =
        "Enter a project view descriptor file."
            + (Blaze.defaultBuildSystem() == BuildSystemName.Blaze
                ? " See 'go/intellij/docs/project-views.md' for more information."
                : "");

    projectViewEditor = createEditor(tooltip);
    projectViewEditor
        .getColorsScheme()
        .setColor(EditorColors.READONLY_BACKGROUND_COLOR, UIManager.getColor("Label.background"));
    Disposer.register(
        parentDisposable, () -> EditorFactory.getInstance().releaseEditor(projectViewEditor));

    JBLabel labelsLabel = new JBLabel("Project View");
    labelsLabel.setToolTipText(tooltip);
    canvas.add(labelsLabel, UiUtil.getFillLineConstraints(0));

    canvas.add(projectViewEditor.getComponent(), UiUtil.getFillLineConstraints(0));

    useShared = new JCheckBox(USE_SHARED_PROJECT_VIEW);
    useShared.addActionListener(
        e -> {
          useSharedProjectView = useShared.isSelected();
          if (useSharedProjectView) {
            setProjectViewText(sharedProjectViewText);
          }
          updateTextAreasEnabled();
        });
    canvas.add(useShared, UiUtil.getFillLineConstraints(0));
  }

  public void init(
      WorkspacePathResolver workspacePathResolver,
      String projectViewText,
      @Nullable String sharedProjectViewText,
      @Nullable WorkspacePath sharedProjectViewWorkspacePath,
      boolean useSharedProjectView,
      boolean allowEditShared) {
    this.workspacePathResolver = workspacePathResolver;
    this.useSharedProjectView = useSharedProjectView;
    this.allowEditShared = allowEditShared;
    this.sharedProjectViewText = sharedProjectViewText;

    assert !(useSharedProjectView && sharedProjectViewText == null);

    if (sharedProjectViewWorkspacePath != null) {
      useShared.setText(
          USE_SHARED_PROJECT_VIEW + ": " + sharedProjectViewWorkspacePath.relativePath());
    }

    useShared.setSelected(useSharedProjectView);
    useShared.setEnabled(sharedProjectViewText != null);

    setDummyWorkspacePathResolverProvider(this.workspacePathResolver);
    setProjectViewText(projectViewText);
  }

  private void setDummyWorkspacePathResolverProvider(WorkspacePathResolver workspacePathResolver) {
    WorkspacePathResolverProvider.getInstance(getProject())
        .setTemporaryOverride(workspacePathResolver, parentDisposable);
  }

  private void setProjectViewText(String projectViewText) {
    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              projectViewEditor.getDocument().setReadOnly(false);
              projectViewEditor.getDocument().setText(projectViewText);
            });
    updateTextAreasEnabled();
  }

  private void updateTextAreasEnabled() {
    boolean editEnabled = allowEditShared || !useSharedProjectView;
    projectViewEditor.setViewer(!editEnabled);
    projectViewEditor.getDocument().setReadOnly(!editEnabled);
    projectViewEditor.reinitSettings();
  }

  public ProjectViewSet parseProjectView(final List<IssueOutput> issues) {
    final String projectViewText = projectViewEditor.getDocument().getText();
    final OutputSink<IssueOutput> issueCollector =
        output -> {
          issues.add(output);
          return OutputSink.Propagation.Continue;
        };
    return Scope.root(
        context -> {
          context.addOutputSink(IssueOutput.class, issueCollector);
          ProjectViewParser projectViewParser =
              new ProjectViewParser(context, workspacePathResolver);
          projectViewParser.parseProjectView(projectViewText);
          return projectViewParser.getResult();
        });
  }

  public boolean getUseSharedProjectView() {
    return this.useSharedProjectView;
  }
}
