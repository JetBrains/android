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
package com.android.tools.idea.tests.gui.framework.fixture.gfxtrace;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.controllers.AtomController;
import com.android.tools.idea.editors.gfxtrace.models.AtomStream;
import com.android.tools.idea.editors.gfxtrace.renderers.Render;
import com.android.tools.idea.editors.gfxtrace.service.Context;
import com.android.tools.idea.editors.gfxtrace.service.atom.Atom;
import com.android.tools.idea.editors.gfxtrace.service.snippets.SnippetObject;
import com.android.tools.idea.editors.gfxtrace.widgets.LoadablePanel;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.rpclib.schema.Constant;
import com.android.tools.rpclib.schema.Method;
import com.android.tools.rpclib.schema.Primitive;
import com.android.tools.rpclib.schema.Type;
import com.google.common.base.Verify;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.JTreeDriver;
import org.fest.swing.fixture.*;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Collection;

public class GfxTraceFixture extends ComponentFixture<GfxTraceFixture, LoadablePanel> {

  // fake method types we use for finding enum and flag type Primitive types.
  public static final Method ENUM = Method.findOrCreate((byte)-1);
  public static final Method FLAG = Method.findOrCreate((byte)-2);

  private final GfxTraceEditor myEditor;

  public GfxTraceFixture(@NotNull Robot robot, @NotNull GfxTraceEditor editor) {
    super(GfxTraceFixture.class, robot, (LoadablePanel)editor.getComponent());
    myEditor = editor;
    myEditor.setFetchReplayDeviceRetrySettings(0, 0);
  }

  public GfxTraceFixture waitForLoadingToFinish() {
    Wait.seconds(10).expecting("open to finish").until(() -> target().getContentLayer().getComponentCount() > 0 || target().isShowingError());
    Wait.seconds(10).expecting("data").until(myEditor.getAtomStream()::isLoaded);
    return this;
  }

  public JComboBoxFixture getContextComboBox() {
    return new JComboBoxFixture(robot(), "ContextCombo");
  }

  public int findAndSelectAtomWithField(@NotNull JTreeFixture tree, @Nullable("no Primitive fields") Method method) {
    AtomStream atoms = myEditor.getAtomStream();
    long atomIndex = findAtomWithField(atoms.getAtoms().getAtoms(), atoms.getSelectedContext(), method);
    if (atomIndex < 0) {
      // could not find an atom with this method type
      return -1;
    }
    int row = selectAtom(tree, atomIndex);
    Verify.verify(row > 0, "atom %s returned by findAtomWithField not found in tree", atomIndex);
    return row;
  }

  private static long findAtomWithField(Atom[] atoms, Context context, @Nullable("no Primitive fields") Method lookingFor) {
    nextAtom: for (int atomIndex = 0; atomIndex < atoms.length; atomIndex++) {
      if (context.contains(atomIndex)) {
        Atom atom = atoms[atomIndex];
        int resultIndex = atom.getResultIndex();
        int extrasIndex = atom.getExtrasIndex();
        for (int i = 0; i < atom.getFieldCount(); i++) {
          if (i == resultIndex || i == extrasIndex) continue;
          Type type = atom.getFieldInfo(i).getType();
          if (type instanceof Primitive) {
            final Primitive primitive = (Primitive)type;
            if (lookingFor == ENUM || lookingFor == FLAG) {
              SnippetObject snippetObject = SnippetObject.param(atom, i);
              Collection<Constant> constant = Render.findConstant(snippetObject, primitive);
              if ((lookingFor == ENUM && constant.size() == 1) ||
                  (lookingFor == FLAG && constant.size() > 1)) {
                return atomIndex;
              }
            }
            else if (lookingFor == primitive.getMethod()) {
              return atomIndex;
            }
            else if (lookingFor == null) {
              // we are looking for a atom with no Primitive fields, but this one has one, so skip to next
              continue nextAtom;
            }
          }
        }
        // we have looked in all the fields and there are no Primitive ones.
        if (lookingFor == null) {
          return atomIndex;
        }
      }
    }
    return -1;
  }

  public static int selectAtom(@NotNull JTreeFixture tree, long atomIndex) {
    JTree jTree = tree.target();
    for (int row = 0; row < tree.target().getRowCount(); row++) {
      Object obj = jTree.getPathForRow(row).getLastPathComponent();
      if (obj instanceof DefaultMutableTreeNode) {
        obj = ((DefaultMutableTreeNode)obj).getUserObject();
      }

      if (obj instanceof AtomController.Node) {
        if (((AtomController.Node)obj).index == atomIndex) {
          tree.selectRow(row);
          return row;
        }
      }
      if (obj instanceof AtomController.Group) {
        if (((AtomController.Group)obj).group.getRange().contains(atomIndex)) {
          tree.expandRow(row);
        }
      }
    }
    return -1;
  }

  @NotNull
  public JTreeFixture getAtomTree() {
    return new JTreeFixture(robot(), "AtomTree") {
      @Nonnull
      @Override
      protected JTreeDriver createDriver(@Nonnull Robot robot) {
        return new ScrollAwareJTreeDriver(robot);
      }
    };
  }
}
