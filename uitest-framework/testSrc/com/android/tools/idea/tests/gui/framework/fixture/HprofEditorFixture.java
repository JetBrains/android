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
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.designer.LightToolWindow;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.treeStructure.Tree;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JLabelFixture;
import org.fest.swing.fixture.JPanelFixture;
import org.fest.swing.fixture.JToggleButtonFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.AccessibleContext;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.*;

public class HprofEditorFixture extends EditorFixture {
  @NotNull private HprofEditor myHprofEditor;
  @NotNull private ActionToolbarFixture myToolbarFixture;

  @NotNull
  public static HprofEditorFixture findByFileName(@NotNull Robot robot,
                                                  @NotNull final IdeFrameFixture frame,
                                                  @NotNull final String hprofFileName) {
    HprofEditor hprofEditor = GuiQuery.getNonNull(
      () -> {
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
        return targetEditor;
      });
    return new HprofEditorFixture(robot, frame, hprofEditor);
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

  public boolean assertCurrentClassesViewMode(@NotNull String viewModeText) {
    myToolbarFixture.findComboBoxActionWithText(viewModeText);
    return true;
  }

  @NotNull
  public JTreeFixture getClassesTree() {
    return new JTreeFixture(robot, ClassesTreeView.TREE_NAME);
  }

  @NotNull
  public JTreeFixture getInstancesTree() {
    return new JTreeFixture(robot, InstancesTreeView.TREE_NAME);
  }

  @NotNull
  public JTreeFixture getInstanceReferenceTree() {
    return new JTreeFixture(robot, InstanceReferenceTreeView.TREE_NAME);
  }

  @NotNull
  public HprofEditorFixture openAnalyzerTasks() {
    JToggleButton button = robot.finder().find(new GenericTypeMatcher<JToggleButton>(JToggleButton.class) {
      @Override
      protected boolean isMatching(@NotNull JToggleButton component) {
        AccessibleContext accessibleContext = component.getAccessibleContext();
        if (accessibleContext == null) {
          return false;
        }

        return "Analyzer Tasks".equals(accessibleContext.getAccessibleName());
      }
    });
    new JToggleButtonFixture(robot, button).click();
    Wait.seconds(1).expecting("Analyzer Tasks window to open")
      .until(() ->
        !robot.finder().findAll(new GenericTypeMatcher<JLabel>(JLabel.class) {
          @Override
          protected boolean isMatching(@NotNull JLabel label) {
            return "Analyzer Tasks".equals(label.getText());
          }
        }).isEmpty()
      );
    return this;
  }

  @NotNull
  public JPanelFixture getAnalyzerTasksWindow() {
    Component labelContainer = robot.finder().find(Matchers.byText(JLabel.class, "Analyzer Tasks")).getParent();
    LightToolWindow analyzerWindow = (LightToolWindow) SwingUtilities.getAncestorOfClass(LightToolWindow.class, labelContainer);
    if(analyzerWindow == null) {
      throw new ComponentLookupException("Analyzer window not found");
    }

    return new JPanelFixture(robot, analyzerWindow);
  }

  /**
   * Clicks on the green arrow button to start an hprof file analysis.
   * Expects that the analysis will return results for leaked activities
   * and duplicate strings.
   */
  @NotNull
  public HprofEditorFixture performAnalysis() {
    JPanelFixture analyzerWindow = getAnalyzerTasksWindow();
    InplaceButton analysisButton = robot.finder()
      .find(analyzerWindow.target(), new GenericTypeMatcher<InplaceButton>(InplaceButton.class) {
        @Override
        protected boolean isMatching(@NotNull InplaceButton component) {
          return "Perform Analysis".equals(component.getToolTipText());
        }
      });

    Tree tree = (Tree) analyzerWindow.tree().target();
    Wait.seconds(5)
      .expecting("hprof analysis to finish")
      .until(() -> {
        robot.click(analysisButton);
        return GuiQuery.get(() -> !tree.isEmpty());
      });

    return this;
  }

  @NotNull
  public JTreeFixture getAnalysisResultsTree() {
    JLabelFixture analysisResults = getAnalyzerTasksWindow().label(Matchers.byText(JLabel.class, "Analysis Results"));

    JPanel results = (JPanel) SwingUtilities.getAncestorOfClass(JPanel.class, analysisResults.target());
    JPanelFixture resultsFixture = new JPanelFixture(robot, results);
    return resultsFixture.tree();
  }

  private void waitForHprofEditor() {
    Wait.seconds(10).expecting("editor to be ready")
      .until(() -> {
        try {
          robot.finder().findByName(myHprofEditor.getComponent(), "HprofClassesTree", true);
          return true;
        }
        catch (ComponentLookupException e) {
          return false;
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

    private ActionToolbarFixture(@NotNull Robot robot, @NotNull ActionToolbarImpl target) {
      super(ActionToolbarFixture.class, robot, target);
    }
  }
}
