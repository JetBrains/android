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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.editors.manifest.ManifestPanel;
import com.android.utils.SdkUtils;
import com.google.common.truth.Truth;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.ui.treeStructure.Tree;
import org.fest.swing.annotation.RunsInEDT;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.tree.TreePath;
import java.awt.*;

import static org.junit.Assert.*;

public class MergedManifestFixture extends ComponentFixture<MergedManifestFixture, ManifestPanel>{

  private @NotNull JTreeFixture myTree;
  private @NotNull JTextComponentFixture myInfoPane;

  public MergedManifestFixture(@NotNull Robot robot, @NotNull ManifestPanel manifestComponent) {
    super(MergedManifestFixture.class, robot, manifestComponent);
    myTree = new JTreeFixture(robot(), robot().finder()
      .findByType(this.target(), Tree.class, true));
    myInfoPane = new JTextComponentFixture(robot(), robot().finder()
      .findByType(this.target(), JEditorPane.class, true));
  }

  @NotNull
  public JTreeFixture getTree() {
    return myTree;
  }

  public void requireText(@NotNull String expected, boolean wrap) {
    try {
      Document document = myInfoPane.target().getDocument();
      String text = document.getText(0, document.getLength()).trim();
      if (wrap) {
        text = SdkUtils.wrap(text, 80, null);
      }
      assertEquals(expected, text);
    } catch (BadLocationException ignore) {
    }
  }

  public void clickLinkText(String linkText) {
    try {
      // Can't use myInfoPane.text() -- which gives us the HTML markup. We
      // want the rendered text instead
      Document document = myInfoPane.target().getDocument();
      String text = document.getText(0, document.getLength());
      int index = text.indexOf(linkText);
      Truth.assertThat(index).isAtLeast(0);
      Rectangle rect = myInfoPane.target().modelToView(index);
      Point location = rect.getLocation();
      location.translate(2, 0);
      robot().click(myInfoPane.target(), location, MouseButton.LEFT_BUTTON, 1);
    }
    catch (Exception ex) {
      throw new AssertionError(ex);
    }
  }

  public Color getDefaultBackgroundColor() {
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = colorsManager.getGlobalScheme();
    return scheme.getDefaultBackground();
  }

  @Nullable
  public Color getSelectedNodeColor() {
    Tree tree = (Tree)myTree.target();
    return tree.isFileColorsEnabled() ? tree.getFileColorForPath(tree.getSelectionPath()) : null;
  }

  @RunsInEDT
  public void checkAllRowsColored() {
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() {
        Tree tree = (Tree)myTree.target();
        for (int row = 0; row < tree.getRowCount(); row++) {
          TreePath path = tree.getPathForRow(row);
          Color color = tree.getFileColorForPath(path);
          assertNotNull(color);
          assertNotEquals(0, color.getAlpha());
        }
      }
    });
  }
}
