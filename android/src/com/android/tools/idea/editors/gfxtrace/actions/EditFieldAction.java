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
package com.android.tools.idea.editors.gfxtrace.actions;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.controllers.AtomController;
import com.android.tools.idea.editors.gfxtrace.renderers.Render;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.snippets.SnippetObject;
import com.android.tools.rpclib.schema.*;
import com.android.tools.swing.util.FloatFilter;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.awt.event.ActionEvent;

public class EditFieldAction extends AbstractAction {

  private @NotNull JComponent myEditor;
  private @NotNull GfxTraceEditor myGfxTraceEditor;
  private @NotNull AtomController.Node myNode;
  private int myFieldIndex;

  private EditFieldAction(@NotNull GfxTraceEditor traceEditor, @NotNull AtomController.Node node, int fieldIndex, @NotNull JComponent editor) {
    super("Edit");
    myEditor = editor;
    myGfxTraceEditor = traceEditor;
    myNode = node;
    myFieldIndex = fieldIndex;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    final Container parent = myGfxTraceEditor.getComponent();
    final String title = "Edit " + myNode.atom.getFieldInfo(myFieldIndex).getName();
    int result = JOptionPane.showOptionDialog(parent, myEditor, title,
                                              JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                                              null, null, null);
    if (result == JOptionPane.OK_OPTION) {
      Object value = getEditorValue(myEditor);
      final Path oldPath = myNode.getFieldPath(myGfxTraceEditor.getAtomStream().getPath(), myFieldIndex);

      Futures.addCallback(myGfxTraceEditor.getClient().set(oldPath, value), new FutureCallback<Path>() {
        @Override
        public void onSuccess(Path newPath) {
          // CommandProcessor#executeCommand NEEDS to be called from UI Thread
          ApplicationManager.getApplication().invokeLater(() -> new ActivatePathCommand(title, oldPath, newPath).execute());
        }

        @Override
        public void onFailure(Throwable t) {
          JOptionPane.showMessageDialog(parent, "Error: " + t);
        }
      });
    }
  }

  class ActivatePathCommand implements Runnable, UndoableAction {

    private String myName;
    private Path myOldPath;
    private Path myNewPath;

    ActivatePathCommand(String name, Path oldPath, Path newPath) {
      myName = name;
      myOldPath = oldPath;
      myNewPath = newPath;
    }

    public void execute() {
      // create an action that can be undone
      CommandProcessor.getInstance()
        .executeCommand(myGfxTraceEditor.getProject(), this, myName, null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);
    }

    @Override
    public void run() {
      // do the action
      myGfxTraceEditor.activatePath(myNewPath, EditFieldAction.this);
      // setup what will happen for undo and redo of this action
      UndoManager.getInstance(myGfxTraceEditor.getProject()).undoableActionPerformed(this);
    }

    @Override
    public void undo() throws UnexpectedUndoException {
      myGfxTraceEditor.activatePath(myOldPath, EditFieldAction.this);
    }

    @Override
    public void redo() throws UnexpectedUndoException {
      myGfxTraceEditor.activatePath(myNewPath, EditFieldAction.this);
    }

    @Override
    public @Nullable DocumentReference[] getAffectedDocuments() {
      return null;
    }

    @Override
    public boolean isGlobal() {
      // if i do not touch any files with a change then we HAVE to mark it as global or it does not show up in the menu
      return true;
    }
  }

  @Nullable
  public static EditFieldAction getEditActionFor(AtomController.Node node, int fieldIndex, GfxTraceEditor traceEditor) {
    SnippetObject snippetObject = SnippetObject.param(node.atom, fieldIndex);
    Object value = node.atom.getFieldValue(fieldIndex);
    JComponent editor = getEditorFor(node.atom.getFieldInfo(fieldIndex).getType(), value, snippetObject);
    return editor == null ? null : new EditFieldAction(traceEditor, node, fieldIndex, editor);
  }

  @Nullable
  private static JComponent getEditorFor(Type type, Object value, @Nullable SnippetObject snippetObject) {
    if (type instanceof Primitive) {
      final Primitive primitive = (Primitive)type;
      if (snippetObject != null) {
        Constant constant = Render.findConstant(snippetObject, primitive);
        if (constant != null) {
          // handle enum
          ConstantSet constants = ConstantSet.lookup(type);
          JComboBox combo = new JComboBox(constants.getEntries());
          combo.setSelectedItem(constant);
          return combo;
        }
      }
      Method method = primitive.getMethod();
      if (method == Method.Bool) {
        // handle boolean
        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(Boolean.parseBoolean(String.valueOf(value)));
        return checkBox;
      }
      else if (method == Method.Float32 || method == Method.Float64) {
        // handle floats/doubles
        JTextField floatBox = new JTextField(String.valueOf(value));
        ((AbstractDocument)floatBox.getDocument()).setDocumentFilter(new FloatFilter());
        return floatBox;
      }
      else if (method == Method.String) {
        return new JTextField(String.valueOf(value));
      }
      else {
        // handle ints
        JSpinner spinner;
        // unsigned
        if (method == Method.Uint8 || method == Method.Uint16 || method == Method.Uint32 || method == Method.Uint64) {

          // we need the class of zero to match the class of the number
          Comparable<? extends Number> zero;
          if (value instanceof Long) {
            zero = Long.valueOf(0);
          }
          else if (value instanceof Integer) {
            zero = Integer.valueOf(0);
          }
          else if (value instanceof Short) {
            zero = Short.valueOf((short)0);
          }
          else {
            zero = Byte.valueOf((byte)0);
          }

          spinner = new JSpinner(new SpinnerNumberModel((Number)value, zero, null, Integer.valueOf(1)));
        }
        else {
          // signed ints
          spinner = new JSpinner();
          spinner.setValue(value);
        }
        return spinner;
      }
    }
    return null;
  }

  // TODO make this not have instanceof checks
  @Nullable
  private static Object getEditorValue(JComponent editor) {
    if (editor instanceof JSpinner) {
      // ints
      return ((JSpinner)editor).getValue();
    }
    if (editor instanceof JTextField) {
      // floats or strings
      JTextField textField = ((JTextField)editor);
      String text = textField.getText();
      if (((AbstractDocument)textField.getDocument()).getDocumentFilter() instanceof FloatFilter) {
        return Double.parseDouble(text);
      }
      return text;
    }
    if (editor instanceof JComboBox) {
      // enums
      Constant value = (Constant)((JComboBox)editor).getSelectedItem();
      return value.getValue();
    }
    if (editor instanceof JCheckBox) {
      //booleans
      return ((JCheckBox)editor).isSelected() ? Boolean.TRUE : Boolean.FALSE;
    }
    throw new IllegalArgumentException();
  }
}
