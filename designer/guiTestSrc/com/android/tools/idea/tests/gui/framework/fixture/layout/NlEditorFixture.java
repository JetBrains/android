/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.editor.NlEditorPanel;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.*;

/**
 * Fixture wrapping the the layout editor for a particular file
 */
public class NlEditorFixture extends ComponentFixture<NlEditorFixture, Component> {
  private final NlEditorPanel myPanel;
  private final JPanel myProgressPanel;

  @Nullable
  public static NlEditorFixture getNlEditor(@NotNull final EditorFixture editor, @NotNull final IdeFrameFixture frame,
                                            boolean switchToTabIfNecessary) {
    VirtualFile currentFile = editor.getCurrentFile();
    if (ResourceHelper.getFolderType(currentFile) != ResourceFolderType.LAYOUT) {
      return null;
    }

    if (switchToTabIfNecessary) {
      editor.selectEditorTab(EditorFixture.Tab.DESIGN);
    }

    return execute(new GuiQuery<NlEditorFixture>() {
      @Override
      @Nullable
      protected NlEditorFixture executeInEDT() throws Throwable {
        FileEditorManager manager = FileEditorManager.getInstance(frame.getProject());
        FileEditor[] editors = manager.getSelectedEditors();
        if (editors.length == 0) {
          return null;
        }
        FileEditor selected = editors[0];
        if (!(selected instanceof NlEditor)) {
          return null;
        }

        return new NlEditorFixture(frame.robot(), (NlEditor)selected);
      }
    });
  }

  private NlEditorFixture(@NotNull Robot robot, @NotNull NlEditor editor) {
    super(NlEditorFixture.class, robot, editor.getComponent());
    myPanel = editor.getComponent();
    myProgressPanel = robot.finder().findByName(myPanel, "Layout Editor Progress Panel", JPanel.class, false);
  }

  public void waitForRenderToFinish() {
    Wait.minutes(2).expecting("render to finish").until(() -> !myProgressPanel.isVisible());
    final ScreenView screenView = myPanel.getSurface().getCurrentScreenView();
    assertNotNull(screenView);
    Wait.minutes(2).expecting("render to finish").until(() -> screenView.getResult() != null);
  }

  /**
   * Searches for the nth occurrence of a given view in the layout. The ordering of widgets of the same
   * type is by visual order, first vertically, then horizontally (and finally by XML source offset, if they exactly overlap
   * as for example would happen in a {@code <merge>}
   *
   * @param tag the view tag to search for, e.g. "Button" or "TextView"
   * @param occurrence the index of the occurrence of the tag, e.g. 0 for the first TextView in the layout
   */
  @NotNull
  public NlComponentFixture findView(@NotNull final String tag, int occurrence) {
    waitForRenderToFinish();
    ScreenView screenView = myPanel.getSurface().getCurrentScreenView();
    assertNotNull(screenView);
    final NlModel model = screenView.getModel();
    final List<NlComponent> components = Lists.newArrayList();

    model.getComponents().forEach(component -> addComponents(tag, component, components));
    // Sort by visual order
    components.sort((component1, component2) -> {
      int delta = component1.y - component2.y;
      if (delta != -1) {
        return delta;
      }
      delta = component1.x - component2.x;
      if (delta != -1) {
        return delta;
      }
      // Unlikely
      return component1.getTag().getTextOffset() - component2.getTag().getTextOffset();
    });

    assertTrue("Only " + components.size() + " found, not enough for occurrence #" + occurrence, components.size() > occurrence);

    NlComponent component = components.get(occurrence);
    return createComponentFixture(component);
  }

  private NlComponentFixture createComponentFixture(@NotNull NlComponent component) {
    return new NlComponentFixture(robot(), component, myPanel.getSurface());
  }

  /** Requires the selection to have the given number of selected widgets */
  public NlEditorFixture requireSelection(@NotNull List<NlComponentFixture> components) {
    assertEquals(components, getSelection());
    return this;
  }

  /** Returns a list of the selected views */
  @NotNull
  public List<NlComponentFixture> getSelection() {
    List<NlComponentFixture> selection = Lists.newArrayList();
    ScreenView screenView = myPanel.getSurface().getCurrentScreenView();
    if (screenView != null) {
      for (NlComponent component : screenView.getSelectionModel().getSelection()) {
        selection.add(createComponentFixture(component));
      }
    }
    return selection;
  }

  private static void addComponents(@NotNull String tag, @NotNull NlComponent component, @NotNull List<NlComponent> components) {
    if (tag.equals(component.getTagName())) {
      components.add(component);
    }

    for (NlComponent child : component.getChildren()) {
      addComponents(tag, child, components);
    }
  }

  @NotNull
  public RenderErrorPanelFixture getRenderErrors() {
    ScreenView screenView = myPanel.getSurface().getCurrentScreenView();
    assertNotNull(screenView);
    return new RenderErrorPanelFixture(robot(), screenView);
  }
}
