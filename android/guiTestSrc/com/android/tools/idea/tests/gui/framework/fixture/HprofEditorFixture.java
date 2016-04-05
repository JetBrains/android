/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.editors.hprof.HprofEditor;
import com.android.tools.idea.editors.hprof.HprofView;
import com.android.tools.idea.editors.hprof.views.ClassesTreeView;
import com.android.tools.idea.editors.hprof.views.InstanceReferenceTreeView;
import com.android.tools.idea.editors.hprof.views.InstancesTreeView;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.ui.table.JBTable;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HprofEditorFixture extends EditorFixture {
  @NotNull private HprofEditor myHprofEditor;
  @NotNull private ActionToolbarFixture myToolbarFixture;

  public static HprofEditorFixture findByFileName(@NotNull Robot robot,
                                                  @NotNull final IdeFrameFixture frame,
                                                  @NotNull final String hprofFileName) {
    HprofEditor editor = GuiActionRunner.execute(new GuiQuery<HprofEditor>() {
      @Nullable
      @Override
      protected HprofEditor executeInEDT() throws Throwable {
        FileEditor[] openEditors = null;
        FileEditorManagerImpl fileEditorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(frame.getProject());
        for (EditorsSplitters splitters : fileEditorManager.getAllSplitters()) {
          for (EditorWindow window : splitters.getWindows()) {
            for (EditorWithProviderComposite editorWithProviderComposite : window.getEditors()) {
              if (editorWithProviderComposite.getFile().getName().endsWith(hprofFileName)) {
                if (openEditors != null) {
                  throw new ComponentLookupException(String.format("More than one Hprof editor for file '%s' found", hprofFileName));
                }
                openEditors = editorWithProviderComposite.getEditors();
              }
            }
          }
        }
        if (openEditors == null) {
          throw new ComponentLookupException(String.format("Cannot find any open Hprof editors for file '%s'", hprofFileName));
        }

        HprofEditor targetEditor = null;
        for (FileEditor editor : openEditors) {
          if (editor instanceof HprofEditor) {
            if (targetEditor != null) {
              throw new ComponentLookupException(String.format("More than one Hprof editor pane for file '%s' found", hprofFileName));
            }
            else {
              targetEditor = (HprofEditor)editor;
            }
          }
        }
        assertNotNull(targetEditor);
        return targetEditor;
      }
    });

    assertNotNull(editor);
    return new HprofEditorFixture(robot, frame, editor);
  }

  private HprofEditorFixture(@NotNull Robot robot, @NotNull IdeFrameFixture frame, @NotNull HprofEditor targetEditor) {
    super(robot, frame);
    myHprofEditor = targetEditor;

    waitForHprofEditor();

    myToolbarFixture = ActionToolbarFixture.findByName(HprofView.TOOLBAR_NAME, robot, myHprofEditor.getComponent(), true);
  }

  public boolean assertCurrentHeapName(@NotNull String heapName) {
    myToolbarFixture.findComboBoxActionWithText(heapName);
    return true;
  }

  public void selectHeap(@NotNull String oldHeapName, @NotNull String newHeapName) {
    myToolbarFixture.findComboBoxActionWithText(oldHeapName).selectItem(newHeapName);
  }

  public boolean assertCurrentClassesViewMode(@NotNull String viewModeText) {
    myToolbarFixture.findComboBoxActionWithText(viewModeText);
    return true;
  }

  public void selectClassViewMode(@NotNull String oldViewModeName, @NotNull String newViewModeName) {
    myToolbarFixture.findComboBoxActionWithText(oldViewModeName).selectItem(newViewModeName);
  }

  @NotNull
  public JTreeFixture getClassesTree() {
    return new JTreeFixture(robot, ClassesTreeView.TREE_NAME);
  }

  @NotNull
  public JTableFixture getClassesTreeHeader() {
    JTreeFixture tree = getClassesTree();
    return new JTableFixture(robot, robot.finder().findByType(tree.target().getParent(), JBTable.class));
  }

  @NotNull
  public JTreeFixture getInstancesTree() {
    return new JTreeFixture(robot, InstancesTreeView.TREE_NAME);
  }

  @NotNull
  public JTableFixture getInstancesTreeHeader() {
    JTreeFixture tree = getInstancesTree();
    return new JTableFixture(robot, robot.finder().findByType(tree.target().getParent(), JBTable.class));
  }

  @NotNull
  public JTreeFixture getInstanceReferenceTree() {
    return new JTreeFixture(robot, InstanceReferenceTreeView.TREE_NAME);
  }

  @NotNull
  public JTableFixture getInstanceReferenceTreeHeader() {
    JTreeFixture tree = getInstanceReferenceTree();
    return new JTableFixture(robot, robot.finder().findByType(tree.target().getParent(), JBTable.class));
  }

  private void waitForHprofEditor() {
    Wait.minutes(2).expecting("editor to be ready").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        try {
          robot.finder().findByName(myHprofEditor.getComponent(), "HprofClassesTree", true);
          return true;
        }
        catch (ComponentLookupException e) {
          return false;
        }
      }
    });
  }

  protected static class ActionToolbarFixture extends JComponentFixture<ActionToolbarFixture, ActionToolbarImpl> {
    public static ActionToolbarFixture findByName(@NotNull String name,
                                                  @NotNull Robot robot,
                                                  @NotNull Container container,
                                                  boolean showing) {
      return new ActionToolbarFixture(robot, robot.finder().findByName(container, name, ActionToolbarImpl.class, showing));
    }

    @NotNull
    public ComboBoxActionFixture findComboBoxActionWithText(@NotNull String text) {
      return ComboBoxActionFixture.findComboBoxByText(robot(), target(), text);
    }

    @NotNull
    public ComboBoxActionFixture getComboBoxActionAt(int index) {
      java.util.List<AnAction> actions = target().getActions(true);
      if (index >= actions.size()) {
        throw new ArrayIndexOutOfBoundsException("Index '" + index + "' is out of bounds (size: " + actions.size() + ")");
      }
      assertTrue(actions.get(index) instanceof ComboBoxAction);
      Component buttonPanel = target().getComponent().getComponent(index);
      assertTrue(buttonPanel instanceof Container);

      final JButton button = robot().finder().findByType((Container)buttonPanel, JButton.class, false);
      Wait.minutes(2).expecting("button to be visible").until(button::isShowing);
      return ComboBoxActionFixture.findComboBox(robot(), (Container)buttonPanel);
    }

    private ActionToolbarFixture(@NotNull Robot robot, @NotNull ActionToolbarImpl target) {
      super(ActionToolbarFixture.class, robot, target);
    }
  }
}
