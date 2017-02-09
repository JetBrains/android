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
package com.android.tools.idea.profilers.stacktrace;

import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.common.CodeLocation;
import com.android.tools.profilers.common.StackFrameParser;
import com.android.tools.profilers.common.StackTraceView;
import com.android.tools.profilers.common.ThreadId;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.PlatformIcons;
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

import static com.android.tools.profilers.common.ThreadId.INVALID_THREAD_ID;
import static com.intellij.ui.SimpleTextAttributes.*;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public class IntelliJStackTraceView implements StackTraceView {
  @NotNull
  private final Project myProject;

  @NotNull
  private final BiFunction<Project, CodeLocation, StackNavigation> myGenerator;

  @NotNull
  private final JBScrollPane myScrollPane;

  @NotNull
  private final DefaultListModel<StackElement> myListModel;

  @NotNull
  private final JBList myListView;

  public IntelliJStackTraceView(@NotNull Project project, @Nullable Runnable preNavigate) {
    this(project, preNavigate, IntelliJStackNavigation::new);
  }

  @VisibleForTesting
  IntelliJStackTraceView(@NotNull Project project,
                         @Nullable Runnable preNavigate,
                         @NotNull BiFunction<Project, CodeLocation, StackNavigation> stackNavigationGenerator) {
    myProject = project;
    myGenerator = stackNavigationGenerator;
    myListModel = new DefaultListModel<>();
    myListView = new JBList(myListModel);
    myListView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myListView.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    myListView.setCellRenderer(new StackElementRenderer());
    myScrollPane = new JBScrollPane(myListView);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

    myListView.addListSelectionListener(e -> {
      int index = myListView.getLeadSelectionIndex();
      if (index < 0 || index >= myListView.getItemsCount() || myListView.getItemsCount() == 0) {
        return;
      }

      StackElement element = myListModel.getElementAt(index);
      element.navigate(preNavigate);
    });
  }

  @Override
  public void clearStackFrames() {
    myListModel.removeAllElements();
  }

  @Override
  public void setStackFrames(@NotNull String stackString) {
    setStackFrames(INVALID_THREAD_ID, Arrays.stream(stackString.split("\\n")).map(
      stackFrame -> {
        StackFrameParser parser = new StackFrameParser(stackFrame);
        return new CodeLocation(parser.getClassName(), parser.getFileName(), parser.getMethodName(), parser.getLineNumber() - 1);
      }).collect(Collectors.toList()));
  }

  @Override
  public void setStackFrames(@NotNull ThreadId threadId, @Nullable List<CodeLocation> stackFrames) {
    clearStackFrames();

    if (stackFrames == null || stackFrames.isEmpty()) {
      return;
    }

    stackFrames.forEach(stackFrame -> myListModel.addElement(myGenerator.apply(myProject, stackFrame)));
    if (threadId != INVALID_THREAD_ID) {
      myListModel.addElement(new ThreadElement(threadId));
    }
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
    StackElement renderable = myListModel.get(index);
    return renderable instanceof StackNavigation ? ((StackNavigation)renderable).getCodeLocation() : null;
  }

  @Override
  public boolean selectCodeLocation(@Nullable CodeLocation location) {
    if (location == null) {
      myListView.clearSelection();
      return false;
    }

    for (int i = 0; i < myListModel.size(); ++i) {
      StackElement renderable = myListModel.getElementAt(i);
      if (renderable instanceof StackNavigation && ((StackNavigation)renderable).getCodeLocation().equals(location)) {
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
      Object element = myListModel.get(i);
      if (element instanceof StackNavigation) {
        locations.add(((StackNavigation)element).getCodeLocation());
      }
    }
    return locations;
  }

  @VisibleForTesting
  @NotNull
  JBList getListView() {
    return myListView;
  }

  private static class StackElementRenderer extends ColoredListCellRenderer {
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
        renderStackNavigation((StackNavigation)value, selected);
      }
      else if (value instanceof ThreadElement) {
        renderThreadElement((ThreadElement)value, selected);
      }
      else {
        append(value.toString(), ERROR_ATTRIBUTES);
      }
    }

    private void renderStackNavigation(@NotNull StackNavigation navigation, boolean selected) {
      setIcon(PlatformIcons.METHOD_ICON);
      SimpleTextAttributes textAttribute = selected || navigation.isInContext() ? REGULAR_ATTRIBUTES : GRAY_ATTRIBUTES;
      CodeLocation location = navigation.getCodeLocation();
      append(navigation.getMethodName(), textAttribute, navigation.getMethodName());
      String lineNumberText = ":" + Integer.toString(location.getLineNumber() + 1) + ", ";
      append(lineNumberText, textAttribute, lineNumberText);
      append(navigation.getSimpleClassName(), textAttribute, navigation.getSimpleClassName());
      String packageName = " (" + navigation.getPackageName() + ")";
      append(packageName, selected ? REGULAR_ITALIC_ATTRIBUTES : GRAYED_ITALIC_ATTRIBUTES, packageName);
    }

    private void renderThreadElement(@NotNull ThreadElement threadElement, boolean selected) {
      setIcon(AllIcons.Debugger.ThreadSuspended);
      String text = "<Thread " + threadElement.getThreadId().getThreadId() + ">";
      append(text, selected ? REGULAR_ATTRIBUTES : GRAY_ATTRIBUTES, text);
    }
  }
}
