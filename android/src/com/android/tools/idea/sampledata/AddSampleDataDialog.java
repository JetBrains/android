/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.sampledata;

import com.android.tools.idea.sampledata.datasource.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.function.Function;

import static com.android.tools.idea.res.SampleDataResourceRepository.getSampleDataDir;

public class AddSampleDataDialog extends DialogWrapper {
  private static Logger LOG = Logger.getInstance(AddSampleDataDialog.class);
  private VirtualFile myCreatedFile;

  private static class SampleDataSource {
    private final Function<OutputStream, Exception> myRunnable;
    private final String myDisplayName;
    private final String myFileNameRoot;

    SampleDataSource(@NotNull String displayName, @NotNull String fileNameRoot, @NotNull Function<OutputStream, Exception> runnable) {
      myDisplayName = displayName;
      myFileNameRoot = fileNameRoot;
      myRunnable = runnable;
    }

    @Override
    public String toString() {
      return myDisplayName;
    }
  }

  private final AndroidFacet myFacet;
  private JBList<SampleDataSource> mySourcesList;
  private JPanel myPanel;

  protected AddSampleDataDialog(@NotNull AndroidFacet facet) {
    super(facet.getModule().getProject());

    myFacet = facet;

    mySourcesList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (index == 0) {
          setBorder(JBUI.Borders.customLine(JBColor.DARK_GRAY, 0, 0, 1, 0));
          setFont(getFont().deriveFont(Font.ITALIC));
        }
        return this;
      }
    });
    DefaultListModel<SampleDataSource> model = new DefaultListModel<>();
    ClassLoader loader = AddSampleDataDialog.class.getClassLoader();
    model.addElement(new SampleDataSource("New empty file...", "sample",
                                          new HardcodedContent("Replace this content with the sample data you want to use")));

    model.addElement(new SampleDataSource("Full names", "full_names",
                                          new CombinerDataSource(
                                            loader.getResourceAsStream("sampleData/names.txt"),
                                            loader.getResourceAsStream("sampleData/surnames.txt"))));
    model.addElement(new SampleDataSource("First names", "names",
                                          ResourceContent.fromInputStream(
                                            loader.getResourceAsStream("sampleData/names.txt"))));
    model.addElement(new SampleDataSource("Last names", "surnames",
                                          ResourceContent.fromInputStream((
                                            loader.getResourceAsStream("sampleData/surnames.txt")))));
    model.addElement(new SampleDataSource("Cities of the world", "cities",
                                          ResourceContent.fromInputStream(
                                            loader.getResourceAsStream("sampleData/cities.txt"))));
    model.addElement(new SampleDataSource("US Postcode", "postcodes",
                                          new NumberGenerator("%05d", 20000, 99999)));
    model.addElement(new SampleDataSource("US Phone numbers", "phones",
                                          new NumberGenerator("555-%04d", 0, 9999)));
    model.addElement(new SampleDataSource("Lorem Ipsum", "lorem",
                                          new LoremIpsumGenerator(false)));
    mySourcesList.setModel(model);

    setTitle("Add Sample Data File");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    int selectedIndex = mySourcesList.getSelectedIndex();
    if (selectedIndex == -1) {
      return;
    }

    SampleDataSource dataSource = mySourcesList.getModel().getElementAt(selectedIndex);

    ProgressManager.getInstance()
      .run(new Task.Backgroundable(myFacet.getModule().getProject(), "Adding Sample Data File", false,
                                   PerformInBackgroundOption.ALWAYS_BACKGROUND) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);

          doDataGeneration(myProject, dataSource);
        }
      });
  }

  /**
   * Runs the data generation. This method might do IO tasks that take a while to complete.
   */
  private void doDataGeneration(@NotNull Project project, @NotNull SampleDataSource dataSource) {
    VirtualFile contentRoot = AndroidRootUtil.getMainContentRoot(myFacet);
    if (contentRoot == null) {
      return;
    }

    final String rootFileName = dataSource.myFileNameRoot;
    VirtualFile sampleDataDir;
    try {
      sampleDataDir = getSampleDataDir(myFacet, true);
    }
    catch (IOException e) {
      LOG.error("Unable to find sample data directory", e);
      return;
    }

    if (sampleDataDir == null) {
      LOG.error("Unable to find sample data directory");
      return;
    }

    myCreatedFile =
      WriteCommandAction.runWriteCommandAction(myFacet.getModule().getProject(), (Computable<VirtualFile>)() -> {
        VirtualFile newFile = null;
        try {
          // Find a filename that doesn't exist already
          String fileName = rootFileName;
          int i = 1;
          while (sampleDataDir.findChild(fileName) != null || i > 50) {
            fileName = rootFileName + (i++);
          }

          newFile = sampleDataDir.createChildData(this, fileName);
          try (OutputStream outputStream = newFile.getOutputStream(this)) {
            // TODO: If this becomes a long-running operation (like an HTTP download), we can extract it before the write action.
            Exception e = dataSource.myRunnable.apply(outputStream);

            if (e != null) {
              LOG.error("Unable to create sample data file", e);
            }
          }
        }
        catch (IOException e) {
          LOG.error("Unable to create sample data file", e);
        }

        return newFile;
      });

    if (myCreatedFile != null) {
      OpenFileDescriptor fileDesc = new OpenFileDescriptor(project,
                                                           myCreatedFile);
      ApplicationManager.getApplication().invokeLater(() -> FileEditorManager.getInstance(project).openTextEditor(fileDesc, true));
    }
  }

  /**
   * Returns the name of the created sample data file or null if it wasn't created
   */
  @SuppressWarnings("unused")
  @Nullable
  public String getCreatedFileName() {
    return myCreatedFile != null ? myCreatedFile.getName() : null;
  }
}
