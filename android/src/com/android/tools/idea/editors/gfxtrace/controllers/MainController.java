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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.execution.ui.layout.impl.ViewImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class MainController extends Controller {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new MainController(editor).myPanel;
  }

  @NotNull private JBPanel myPanel = new JBPanel(new BorderLayout());
  @NotNull private final RunnerLayoutUi myLayoutUi;
  @NotNull private final Content myAtomTab;
  @NotNull private final Content myStateTab;
  @NotNull private final Content myMemoryTab;

  private MainController(@NotNull GfxTraceEditor editor) {
    super(editor);

    JBPanel top = new JBPanel(new GridLayout(2, 1));
    top.add(new JBLabel() {{
      setText("The GPU debugger is experimental software.");
      setIcon(AllIcons.General.BalloonWarning);
      setBackground(new JBColor(0xffee88, 0xa49152));
      setBorder(JBUI.Borders.empty(0, 10));
      setOpaque(true);
    }});
    top.add(ContextController.createUI(editor));
    myPanel.add(top, BorderLayout.NORTH);

    // ThreeComponentsSplitter instead of JBSplitter as it lets us set an exact size for the first component.
    ThreeComponentsSplitter splitter = new ThreeComponentsSplitter(true);
    myPanel.add(splitter, BorderLayout.CENTER);
    splitter.setDividerWidth(5);

    // Add the scrubber view to the top panel.
    splitter.setFirstComponent(ScrubberController.createUI(editor));
    splitter.setFirstSize(150);

    // Configure the image tabs.
    // we use RunnerLayoutUi to allow the user to drag the tabs out of the JBRunnerTabs
    myLayoutUi = RunnerLayoutUi.Factory.getInstance(editor.getProject()).create("gfx-trace-runnerId", editor.getName(), editor.getSessionName(), this);
    myAtomTab = addTab(myLayoutUi, AtomController.createUI(editor), "GPU Commands", PlaceInGrid.left);
    addTab(myLayoutUi, FrameBufferController.createUI(editor), "Framebuffer", PlaceInGrid.center);
    addTab(myLayoutUi, TexturesController.createUI(editor), "Textures", PlaceInGrid.center);
    addTab(myLayoutUi, GeometryController.createUI(editor), "Geometry", PlaceInGrid.center);
    myStateTab = addTab(myLayoutUi, StateController.createUI(editor), "GPU State", PlaceInGrid.center);
    myMemoryTab = addTab(myLayoutUi, MemoryController.createUI(editor), "Memory", PlaceInGrid.center);

    splitter.setLastComponent(myLayoutUi.getComponent());
  }

  private static Content addTab(@NotNull RunnerLayoutUi layoutUi, @NotNull JComponent component, @NotNull String name, @NotNull PlaceInGrid defaultPlace) {
    Content content = layoutUi.createContent(name + "-contentId", component, name, null, null);
    content.setCloseable(false);
    layoutUi.addContent(content, -1, defaultPlace, false);
    return content;
  }

  @Override
  public void notifyPath(PathEvent event) {
    if (event.findTypedMemoryPath() != null || event.findMemoryPath() != null) {
      select(myMemoryTab);
    }
    else if (event.findStatePath() != null) {
      select(myStateTab);
    }
    else if (event.findAtomPath() != null) {
      select(myAtomTab);
    }
  }

  private void select(Content content) {
    restoreIfMinimized(myLayoutUi, content);
    myLayoutUi.selectAndFocus(content, true, true, true);
  }

  /**
   * @see XWatchesViewImpl#showWatchesTab(XDebugSessionImpl)
   */
  public static void restoreIfMinimized(RunnerLayoutUi layoutUi, Content content) {
    JComponent component = layoutUi.getComponent();
    if (component instanceof DataProvider) {
      RunnerContentUi ui = RunnerContentUi.KEY.getData(((DataProvider)component));
      if (ui != null) {
        ui.restoreContent(content.getUserData(ViewImpl.ID));
      }
    }
  }

  @Override
  public void clear() {
    myPanel.removeAll();
  }
}
