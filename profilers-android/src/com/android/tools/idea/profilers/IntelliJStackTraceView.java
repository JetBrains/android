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
package com.android.tools.idea.profilers;

import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.common.CodeLocation;
import com.android.tools.profilers.common.StackFrameParser;
import com.android.tools.profilers.common.StackTraceView;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class IntelliJStackTraceView implements StackTraceView {
  @NotNull
  private final Project myProject;

  @NotNull
  private final FileColorManager myFileColorManager;

  @NotNull
  private final BiFunction<Project, CodeLocation, StackNavigation> myGenerator;

  @NotNull
  private final JBScrollPane myScrollPane;

  @NotNull
  private final DefaultListModel<StackNavigation> myListModel;

  @NotNull
  private final JBList myListView;

  public IntelliJStackTraceView(@NotNull Project project, @Nullable Runnable preNavigate) {
    this(project, preNavigate, FileColorManager.getInstance(project), IntelliJStackNavigation::new);
  }

  @VisibleForTesting
  IntelliJStackTraceView(@NotNull Project project,
                         @Nullable Runnable preNavigate,
                         @NotNull FileColorManager fileColorManager,
                         @NotNull BiFunction<Project, CodeLocation, StackNavigation> stackNavigationGenerator) {
    myProject = project;
    myFileColorManager = fileColorManager;
    myGenerator = stackNavigationGenerator;
    myListModel = new DefaultListModel<>();
    myListView = new JBList(myListModel);
    myListView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myListView.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    myListView.setCellRenderer(new StackFrameRenderer());
    myScrollPane = new JBScrollPane(myListView);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

    myListView.addListSelectionListener(e -> {
      int index = myListView.getLeadSelectionIndex();
      if (index < 0 || index >= myListView.getItemsCount() || myListView.getItemsCount() == 0) {
        return;
      }

      StackNavigation navigation = myListModel.getElementAt(index);
      Navigatable[] navigatables = navigation.getNavigatable(preNavigate);
      if (navigatables != null) {
        for (Navigatable navigatable : navigatables) {
          if (navigatable.canNavigate()) {
            navigatable.navigate(false);
          }
        }
      }
    });
  }

  @Override
  public void clearStackFrames() {
    myListModel.removeAllElements();
  }

  @Override
  public void setStackFrames(@NotNull String stackString) {
    setStackFrames(Arrays.stream(stackString.split("\\n")).map(
      stackFrame -> {
        StackFrameParser parser = new StackFrameParser(stackFrame);
        return new CodeLocation(parser.getClassName(), parser.getFileName(), parser.getMethodName(), parser.getLineNumber() - 1);
      }).collect(Collectors.toList()));
  }

  @Override
  public void setStackFrames(@Nullable List<CodeLocation> stackFrames) {
    clearStackFrames();

    if (stackFrames == null || stackFrames.isEmpty()) {
      return;
    }

    stackFrames.forEach(stackFrame -> myListModel.addElement(myGenerator.apply(myProject, stackFrame)));
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myScrollPane;
  }

  @Nullable
  @Override
  public CodeLocation getSelectedLocation() {
    int index = myListView.getSelectedIndex();
    if (index < 0 || index >= myListModel.size()) {
      return null;
    }
    return myListModel.get(index).getCodeLocation();
  }

  @Override
  public boolean selectCodeLocation(@Nullable CodeLocation location) {
    if (location == null) {
      myListView.clearSelection();
      return false;
    }

    for (int i = 0; i < myListModel.size(); ++i) {
      if (myListModel.getElementAt(i).getCodeLocation().equals(location)) {
        myListView.setSelectedIndex(i);
        return true;
      }
    }
    myListView.clearSelection();
    return false;
  }

  @NotNull
  @Override
  public List<CodeLocation> getCodeLocations() {
    List<CodeLocation> locations = new ArrayList<>(myListModel.getSize());
    for (int i = 0; i < myListModel.size(); i++) {
      locations.add(myListModel.get(i).getCodeLocation());
    }
    return locations;
  }

  @VisibleForTesting
  @NotNull
  JBList getListView() {
    return myListView;
  }

  private class StackFrameRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(@NotNull JList list,
                                         Object value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      // Fix GTK background
      if (UIUtil.isUnderGTKLookAndFeel()) {
        final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        UIUtil.changeBackGround(this, background);
      }

      if (value == null) {
        return;
      }

      if (value instanceof StackNavigation) {
        StackNavigation navigation = (StackNavigation)value;
        VirtualFile classFile = navigation.findClassFile();

        if (!selected) {
          Color c;
          if (classFile != null && classFile.isValid()) {
            c = myFileColorManager.getFileColor(classFile);
          }
          else {
            c = myFileColorManager.getScopeColor(NonProjectFilesScope.NAME);
          }

          if (c != null) {
            setBackground(c);
          }
        }

        SimpleTextAttributes textAttribute =
          selected || (classFile != null && ProjectFileIndex.SERVICE.getInstance(myProject).isInSource(classFile))
          ? SimpleTextAttributes.REGULAR_ATTRIBUTES
          : SimpleTextAttributes.GRAY_ATTRIBUTES;
        CodeLocation location = navigation.getCodeLocation();
        append(navigation.getMethodName(), textAttribute);
        append(":" + Integer.toString(location.getLineNumber() + 1) + ", ", textAttribute);
        append(navigation.getSimpleClassName(), textAttribute);
        append(" (" + navigation.getPackageName() + ")",
               selected ? SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES : SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
      }
      else {
        append(value.toString(), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }
  }
}
