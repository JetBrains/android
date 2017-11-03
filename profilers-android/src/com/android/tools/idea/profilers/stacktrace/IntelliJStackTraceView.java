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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerLayout;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.StackTraceModel;
import com.android.tools.profilers.stacktrace.StackTraceView;
import com.android.tools.profilers.stacktrace.ThreadId;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.intellij.ui.SimpleTextAttributes.*;

public class IntelliJStackTraceView extends AspectObserver implements StackTraceView {
  @NotNull
  private final Project myProject;

  @NotNull
  private final StackTraceModel myModel;

  @NotNull
  private final BiFunction<Project, CodeLocation, CodeElement> myGenerator;

  @NotNull
  private final JBScrollPane myScrollPane;

  @NotNull
  private final DefaultListModel<StackElement> myListModel;

  @NotNull
  private final JBList myListView;

  public IntelliJStackTraceView(@NotNull Project project,
                                @NotNull StackTraceModel model) {
    this(project, model, IntelliJCodeElement::new);
  }

  @VisibleForTesting
  IntelliJStackTraceView(@NotNull Project project,
                         @NotNull StackTraceModel model,
                         @NotNull BiFunction<Project, CodeLocation, CodeElement> stackNavigationGenerator) {
    myProject = project;
    myModel = model;
    myGenerator = stackNavigationGenerator;
    myListModel = new DefaultListModel<>();
    myListView = new JBList(myListModel);
    myListView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myListView.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    myListView.setCellRenderer(new StackElementRenderer());
    myScrollPane = new JBScrollPane(myListView);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

    myListView.addListSelectionListener(e -> {
      if (myListView.getSelectedValue() == null) {
        myModel.clearSelection();
      }
    });

    Supplier<Boolean> navigationHandler = () -> {
      int index = myListView.getSelectedIndex();
      if (index >= 0 && index < myListView.getItemsCount()) {
        myModel.setSelectedIndex(index);
        return true;
      }
      else {
        return false;
      }
    };

    myListView.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        // On Windows we don't get a KeyCode so checking the getKeyCode doesn't work. Instead we get the code from the char
        // we are given.
        int keyCode = KeyEvent.getExtendedKeyCodeForChar(e.getKeyChar());
        if (keyCode == KeyEvent.VK_ENTER) {
          if (navigationHandler.get()) {
            e.consume();
          }
        }
      }
    });
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        return navigationHandler.get();
      }
    }.installOn(myListView);

    myModel.addDependency(this).
      onChange(StackTraceModel.Aspect.STACK_FRAMES, () -> {
        List<CodeLocation> stackFrames = myModel.getCodeLocations();
        myListModel.removeAllElements();
        myListView.clearSelection();
        stackFrames.forEach(stackFrame -> myListModel.addElement(myGenerator.apply(myProject, stackFrame)));

        ThreadId threadId = myModel.getThreadId();
        if (!threadId.equals(ThreadId.INVALID_THREAD_ID)) {
          myListModel.addElement(new ThreadElement(threadId));
        }
      })
      .onChange(StackTraceModel.Aspect.SELECTED_LOCATION, () -> {
        int index = myModel.getSelectedIndex();
        if (myModel.getSelectedType() == StackTraceModel.Type.INVALID) {
          if (myListView.getSelectedIndex() != -1) {
            myListView.clearSelection();
          }
        }
        else if (index >= 0 && index < myListView.getItemsCount()) {
          if (myListView.getSelectedIndex() != index) {
            myListView.setSelectedIndex(index);
          }
        }
        else {
          throw new IndexOutOfBoundsException(
            "View has " + myListView.getItemsCount() + " elements while aspect is changing to index " + index);
        }
      });
  }

  @NotNull
  @Override
  public StackTraceModel getModel() {
    return myModel;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myScrollPane;
  }

  @VisibleForTesting
  @NotNull
  JBList getListView() {
    return myListView;
  }

  /**
   * Renderer for a JList of {@link StackElement} instances.
   */
  private static final class StackElementRenderer extends ColoredListCellRenderer<StackElement> {
    @Override
    protected void customizeCellRenderer(@NotNull JList list,
                                         StackElement value,
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

      setIpad(ProfilerLayout.LIST_ROW_INSETS);
      if (value instanceof CodeElement) {
        renderStackNavigation((CodeElement)value, selected);
      }
      else if (value instanceof ThreadElement) {
        renderThreadElement((ThreadElement)value, selected);
      }
      else {
        append(value.toString(), ERROR_ATTRIBUTES);
      }
    }

    private void renderStackNavigation(@NotNull CodeElement codeElement, boolean selected) {
      setIcon(PlatformIcons.METHOD_ICON);
      SimpleTextAttributes textAttribute = selected || codeElement.isInUserCode() ? REGULAR_ATTRIBUTES : GRAY_ATTRIBUTES;
      CodeLocation location = codeElement.getCodeLocation();
      StringBuilder methodBuilder = new StringBuilder(codeElement.getMethodName());
      if (location.getLineNumber() != CodeLocation.INVALID_LINE_NUMBER) {
        methodBuilder.append(":");
        methodBuilder.append(location.getLineNumber() + 1);
      }
      methodBuilder.append(", ");
      methodBuilder.append(codeElement.getSimpleClassName());
      String methodName = methodBuilder.toString();
      append(methodName, textAttribute, methodName);
      String packageName = " (" + codeElement.getPackageName() + ")";
      append(packageName, selected ? REGULAR_ITALIC_ATTRIBUTES : GRAYED_ITALIC_ATTRIBUTES, packageName);
    }

    private void renderThreadElement(@NotNull ThreadElement threadElement, boolean selected) {
      setIcon(AllIcons.Debugger.ThreadSuspended);
      String text = threadElement.getThreadId().toString();
      append(text, selected ? REGULAR_ATTRIBUTES : GRAY_ATTRIBUTES, text);
    }
  }
}
