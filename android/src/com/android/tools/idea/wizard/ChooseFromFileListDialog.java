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

import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.ArrayUtil;
import org.apache.commons.io.comparator.NameFileComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChooseFromFileListDialog extends DialogWrapper {
  private final List<File> myFiles;
  private FileListItem myChosenFile;

  public ChooseFromFileListDialog(@Nullable Project project, @NotNull List<File> files) {
    super(project);
    myFiles = files;
    Window window = getWindow();
    // Allow creation in headless mode for tests
    if (window != null) {
      getWindow().setMinimumSize(new Dimension(600, 480));
    } else {
      assert ApplicationManager.getApplication().isUnitTestMode();
    }
    init();
  }

  @Nullable
  public File getChosenFile() {
    return myChosenFile.myFile;
  }

  @Override
  public boolean isOKActionEnabled() {
    return myChosenFile != null;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final JBList list = new JBList();
    list.setModel(getListModel(myFiles));
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        myChosenFile = (FileListItem)list.getSelectedValue();
      }
    });
    return ListWithFilter.wrap(list);
  }

  private static ListModel getListModel(List<File> files) {
    ArrayList<FileListItem> items = Lists.newArrayListWithExpectedSize(files.size());
    for (File f : files) {
      items.add(new FileListItem(f));
    }
    Collections.sort(items);
    return JBList.createDefaultListModel(ArrayUtil.toObjectArray(items));
  }

  private static class FileListItem implements Comparable<FileListItem> {
    private final File myFile;
    public FileListItem(@NotNull File file) {
      myFile = file;
    }

    @Override
    public String toString() {
      return myFile.getName();
    }

    @Override
    public int compareTo(@NotNull FileListItem o) {
      return toString().compareTo(o.toString());
    }
  }
}
