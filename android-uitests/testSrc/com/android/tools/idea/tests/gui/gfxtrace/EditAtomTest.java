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
package com.android.tools.idea.tests.gui.gfxtrace;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.CapturesToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gfxtrace.GfxTraceFixture;
import com.android.tools.rpclib.schema.Method;
import org.fest.swing.core.TypeMatcher;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JOptionPaneFixture;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRunner.class)
public class EditAtomTest {

  private static final String CAPTURES_APPLICATION = "CapturesApplication";
  private static final String SAMPLE_SNAPSHOT_NAME = "domination-launch.gfxtrace";

  Method[] TYPES = new Method[] {Method.Bool, Method.Int8, Method.Uint8, Method.Int16, Method.Uint16, Method.Int32, Method.Uint32,
    Method.Int64, Method.Uint64, Method.Float32, Method.Float64, Method.String, GfxTraceFixture.ENUM, GfxTraceFixture.FLAG, null};

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testEditAtom() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish(CAPTURES_APPLICATION);

    CapturesToolWindowFixture myCapturesToolWindowFixture = guiTest.ideFrame().getCapturesToolWindow();
    myCapturesToolWindowFixture.openFile(SAMPLE_SNAPSHOT_NAME);

    GfxTraceFixture gfxTraceFixture = guiTest.ideFrame().getEditor().getGfxTraceEditor();
    gfxTraceFixture.waitForLoadingToFinish();

    JComboBoxFixture contextComboBox = gfxTraceFixture.getContextComboBox();
    contextComboBox.selectItem(2);

    JTreeFixture tree = gfxTraceFixture.getAtomTree();

    int found = 0;
    for (Method type : TYPES) {
      try {
        int row = gfxTraceFixture.findAndSelectAtomWithField(tree, type);
        if (row > 0) {
          found++;
          if (type == null) {
            // test we do not open a edit popup if there is nothing to edit
            tree.rightClickRow(row);
            assertThat(guiTest.robot().finder().findAll(new TypeMatcher(JPopupMenu.class, true))).isEmpty();
          }
          else {
            JPopupMenuFixture popup = tree.showPopupMenuAt(row);
            popup.menuItemWithPath("Edit").click();
            JOptionPaneFixture dialog = new JOptionPaneFixture(guiTest.robot());
            if (dialog.target().getLocationOnScreen().equals(new Point())) {
              // dirty hack to get around a bug in X/AWT where in rare cases a timing issue causes a event from X to misinform AWT about the location of the dialog
              SwingUtilities.getAncestorOfClass(JDialog.class, dialog.target()).setLocation(10, 10);
            }
            dialog.cancelButton().click();
            Wait.minutes(1).expecting("dialog to disappear").until(() -> !dialog.target().isShowing());
          }
        }
        else {
          System.out.println("no atom found to test " + type);
        }
      }
      catch (Throwable ex) {
        throw new RuntimeException("error with " + type, ex);
      }
    }
    assertThat(found).isAtLeast(1);
  }
}
