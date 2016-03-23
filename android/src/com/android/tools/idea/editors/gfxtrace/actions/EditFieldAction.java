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
    int result = JOptionPane.showOptionDialog(parent, myEditor, "Edit " + myNode.atom.getFieldInfo(myFieldIndex).getName(),
                                              JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                                              null, null, null);
    if (result == JOptionPane.OK_OPTION) {
      Object value = getEditorValue(myEditor);
      Path path = myNode.getFieldPath(myGfxTraceEditor.getAtomStream().getPath(), myFieldIndex);
      Futures.addCallback(myGfxTraceEditor.getClient().set(path, value), new FutureCallback<Path>() {
        @Override
        public void onSuccess(Path result) {
          myGfxTraceEditor.activatePath(result, this);
        }

        @Override
        public void onFailure(Throwable t) {
          JOptionPane.showMessageDialog(parent, "Error: " + t);
        }
      });
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
          spinner = new JSpinner(new SpinnerNumberModel((Number)value, Integer.valueOf(0), null, Integer.valueOf(1)));
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
