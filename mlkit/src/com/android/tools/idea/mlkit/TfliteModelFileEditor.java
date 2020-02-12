/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener;
import com.android.tools.idea.projectsystem.ProjectSystemSyncUtil;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.android.tools.mlkit.MetadataExtractor;
import com.android.tools.mlkit.ModelData;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Editor for the TFLite mode file.
 */
// TODO(b/148866418): complete this based on the UX spec.
public class TfliteModelFileEditor extends UserDataHolderBase implements FileEditor {
  private static final String NAME = "TFLite Model File";
  private static final String HTML_TABLE_STYLE = "<style>\n" +
                                                 "table {\n" +
                                                 "  font-family: arial, sans-serif;\n" +
                                                 "  border-collapse: collapse;\n" +
                                                 "  width: 60%;\n" +
                                                 "}\n" +
                                                 "td, th {\n" +
                                                 "  border: 0;\n" +
                                                 "  text-align: left;\n" +
                                                 "  padding: 8px;\n" +
                                                 "}\n" +
                                                 "</style>";

  private final Project myProject;
  private final Module myModule;
  private final VirtualFile myFile;
  private final JBScrollPane myRootPane;

  public TfliteModelFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myProject = project;
    myFile = file;
    myModule = ModuleUtilCore.findModuleForFile(file, project);

    JPanel contentPanel = new JPanel();
    contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
    contentPanel.setBackground(UIUtil.getTextFieldBackground());
    contentPanel.setBorder(JBUI.Borders.empty(20));

    // TODO(149115468): revisit.
    JButton addDepButton = new JButton("Add Missing Dependencies");
    addDepButton.setAlignmentX(Component.LEFT_ALIGNMENT);
    addDepButton.setVisible(shouldShowAddDepButton());
    addDepButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        List<GradleCoordinate> depsToAdd = MlkitUtils.getMissingDependencies(myModule, myFile);
        // TODO(b/149224613): switch to use DependencyManagementUtil#addDependencies.
        AndroidModuleSystem moduleSystem = ProjectSystemUtil.getModuleSystem(myModule);
        if (DependencyManagementUtil.userWantsToAdd(myModule.getProject(), depsToAdd, "")) {
          for (GradleCoordinate dep : depsToAdd) {
            moduleSystem.registerDependency(dep);
          }
          ProjectSystemUtil.getSyncManager(myProject).syncProject(SyncReason.PROJECT_MODIFIED);
          addDepButton.setVisible(false);
        }
      }
    });
    contentPanel.add(addDepButton);

    try {
      ModelData modelData = ModelData.buildFrom(new MetadataExtractor(ByteBuffer.wrap(file.contentsToByteArray())));
      addModelSummarySection(contentPanel, modelData);
    }
    catch (IOException e) {
      Logger.getInstance(TfliteModelFileEditor.class).error(e);
    }

    myRootPane = new JBScrollPane(contentPanel);

    project.getMessageBus().connect(project).subscribe(ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC, new SyncResultListener() {
      @Override
      public void syncEnded(@NotNull ProjectSystemSyncManager.SyncResult result) {
        addDepButton.setVisible(shouldShowAddDepButton());
      }
    });
  }

  private boolean shouldShowAddDepButton() {
    return MlkitUtils.isMlModelFileInAssetsFolder(myFile) &&
           myModule != null &&
           !MlkitUtils.getMissingDependencies(myModule, myFile).isEmpty();
  }

  private static void addModelSummarySection(@NotNull JPanel contentPanel, @NotNull ModelData modelData) {
    // TODO(b/148866418): make table collapsible.
    String modelHtml = "<h2>Model</h2>\n" +
                       "<table>\n" +
                       "<tr>\n" +
                       "<td>Name</td>\n" +
                       "<td>" + modelData.getModelName() + "</td>\n" +
                       "</tr>\n" +
                       "<tr>\n" +
                       "<td>Description</td>\n" +
                       "<td>" + modelData.getModelDescription() + "</td>\n" +
                       "</tr>\n" +
                       "<tr>\n" +
                       "<td>Version</td>\n" +
                       "<td>" + modelData.getModelVersion() + "</td>\n" +
                       "</tr>\n" +
                       "<tr>\n" +
                       "<td>Author</td>\n" +
                       "<td>" + modelData.getModelAuthor() + "</td>\n" +
                       "</tr>\n" +
                       "<tr>\n" +
                       "<td>License</td>\n" +
                       "<td>" + modelData.getModelLicense() + "</td>\n" +
                       "</tr>\n" +
                       "</table>";

    JTextPane modelPane = new JTextPane();
    modelPane.setAlignmentX(Component.LEFT_ALIGNMENT);
    setHtml(modelPane, modelHtml);
    contentPanel.add(modelPane);
  }

  private static void setHtml(@NotNull JEditorPane pane, @NotNull String bodyContent) {
    String html = "<html><head>" + HTML_TABLE_STYLE + "</head><body>" + bodyContent + "</body></html>";
    pane.setContentType("text/html");
    pane.setEditable(false);
    pane.setText(html);
    pane.setBackground(UIUtil.getTextFieldBackground());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myRootPane;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public void selectNotify() {
  }

  @Override
  public void deselectNotify() {
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public void dispose() {
  }
}
