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
import com.android.tools.idea.editors.gfxtrace.UiCallback;
import com.android.tools.idea.editors.gfxtrace.controllers.AtomController;
import com.android.tools.idea.editors.gfxtrace.renderers.Render;
import com.android.tools.idea.editors.gfxtrace.service.path.FieldPath;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.snippets.SnippetObject;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.android.tools.rpclib.schema.*;
import com.android.tools.swing.util.FloatFilter;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class EditAtomParametersAction extends AbstractAction {
  private static final @NotNull Logger LOG = Logger.getInstance(EditAtomParametersAction.class);

  private @NotNull Editor[] myEditors;
  private @NotNull GfxTraceEditor myGfxTraceEditor;
  private @NotNull AtomController.Node myNode;

  private EditAtomParametersAction(@NotNull GfxTraceEditor traceEditor, @NotNull AtomController.Node node, @NotNull Editor[] editors) {
    super("Edit");
    myEditors = editors;
    myGfxTraceEditor = traceEditor;
    myNode = node;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    final Container parent = myGfxTraceEditor.getComponent();
    final String title = "Edit " + myNode.atom.getName();
    int result = JOptionPane.showOptionDialog(parent, buildDialog(), title,
                                              JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                                              null, null, null);
    if (result == JOptionPane.OK_OPTION) {
      Dynamic newAtom = ((Dynamic)myNode.atom.unwrap()).copy();
      for (int i = 0; i < myEditors.length; i++) {
        if (myEditors[i].hasEditComponent()) {
          newAtom.setFieldValue(myEditors[i].myFieldIndex, myEditors[i].getValue());
        }
      }

      final Path oldPath = myGfxTraceEditor.getAtomStream().getPath().index(myNode.index);
      Rpc.listen(myGfxTraceEditor.getClient().set(oldPath, newAtom), LOG, new UiCallback<Path, Path>() {
        @Override
        protected Path onRpcThread(Rpc.Result<Path> result) throws RpcException, ExecutionException {
          return result.get();
        }

        @Override
        protected void onUiThread(Path newPath) {
          new ActivatePathCommand(title, oldPath, newPath).execute();
        }
      });
    }
  }

  private JComponent buildDialog() {
    JPanel fields = new JPanel(new GridBagLayout());
    GridBag bag = new GridBag()
      .setDefaultAnchor(GridBagConstraints.EAST)
      .setDefaultFill(GridBagConstraints.HORIZONTAL)
      .setDefaultWeightX(1, 1)
      .setDefaultPaddingX(5);

    for (int i = 0; i < myEditors.length; i++) {
      fields.add(new JBLabel(myNode.atom.getFieldInfo(myEditors[i].myFieldIndex).getName()), bag.nextLine().next());
      if (myEditors[i].hasEditComponent()) {
        fields.add(myEditors[i].myComponent, bag.next());
      }
      else {
        fields.add(myEditors[i].getReadOnlyComponent(), bag.next());
      }
    }
    return fields;
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
      myGfxTraceEditor.activatePath(myNewPath, EditAtomParametersAction.this);
      // setup what will happen for undo and redo of this action
      UndoManager.getInstance(myGfxTraceEditor.getProject()).undoableActionPerformed(this);
    }

    @Override
    public void undo() throws UnexpectedUndoException {
      myGfxTraceEditor.activatePath(myOldPath, EditAtomParametersAction.this);
    }

    @Override
    public void redo() throws UnexpectedUndoException {
      myGfxTraceEditor.activatePath(myNewPath, EditAtomParametersAction.this);
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
  public static EditAtomParametersAction getEditActionFor(AtomController.Node node, GfxTraceEditor traceEditor) {
    List<Editor> editors = Lists.newArrayList();
    int resultIndex = node.atom.getResultIndex();
    int extrasIndex = node.atom.getExtrasIndex();
    boolean found = false;
    for (int i = 0; i < node.atom.getFieldCount(); i++) {
      if (i == resultIndex || i == extrasIndex) continue;
      SnippetObject snippetObject = SnippetObject.param(node.atom, i);
      Object value = node.atom.getFieldValue(i);
      Editor editor = Editor.getFor(node.atom.getFieldInfo(i).getType(), value, snippetObject, i);
      editors.add(editor);
      found |= editor.hasEditComponent();
    }
    return found ? new EditAtomParametersAction(traceEditor, node, editors.toArray(new Editor[editors.size()])) : null;
  }

  private static class Editor {
    public final SnippetObject myCurrentValue;
    public final Type myType;
    public final @Nullable JComponent myComponent;
    public final int myFieldIndex;

    private Editor(@NotNull SnippetObject currentValue, @NotNull Type type, @Nullable JComponent component, int fieldIndex) {
      myCurrentValue = currentValue;
      myType = type;
      myComponent = component;
      myFieldIndex = fieldIndex;
    }

    @NotNull
    public static Editor getFor(@NotNull Type type, Object value, @NotNull SnippetObject snippetObject, int fieldIndex) {
      if (type instanceof Primitive) {
        final Primitive primitive = (Primitive)type;
        if (snippetObject != null) {
          Constant constant = Render.findConstant(snippetObject, primitive);
          if (constant != null) {
            // handle enum
            ConstantSet constants = ConstantSet.lookup(type);
            JComboBox combo = new JComboBox(constants.getEntries());
            combo.setSelectedItem(constant);
            return new Editor(snippetObject, type, combo, fieldIndex);
          }
        }
        Method method = primitive.getMethod();
        if (method == Method.Bool) {
          // handle boolean
          JCheckBox checkBox = new JCheckBox();
          checkBox.setSelected(Boolean.parseBoolean(String.valueOf(value)));
          return new Editor(snippetObject, type, checkBox, fieldIndex);
        }
        else if (method == Method.Float32 || method == Method.Float64) {
          // handle floats/doubles
          JTextField floatBox = new JTextField(String.valueOf(value));
          ((AbstractDocument)floatBox.getDocument()).setDocumentFilter(new FloatFilter());
          return new Editor(snippetObject, type, floatBox, fieldIndex);
        }
        else if (method == Method.String) {
          return new Editor(snippetObject, type, new JTextField(String.valueOf(value)), fieldIndex);
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
          return new Editor(snippetObject, type, spinner, fieldIndex);
        }
      }
      return new Editor(snippetObject, type, null, fieldIndex);
    }

    public JComponent getReadOnlyComponent() {
      SimpleColoredComponent result = new SimpleColoredComponent();
      Render.render(myCurrentValue, myType, result, SimpleTextAttributes.REGULAR_ATTRIBUTES, -1);
      return result;
    }

    public boolean hasEditComponent() {
      return myComponent != null;
    }

    // TODO make this not have instanceof checks
    @Nullable
    public Object getValue() {
      if (myComponent instanceof JSpinner) {
        // ints
        return ((JSpinner)myComponent).getValue();
      }
      if (myComponent instanceof JTextField) {
        // floats or strings
        JTextField textField = ((JTextField)myComponent);
        String text = textField.getText();
        if (((AbstractDocument)textField.getDocument()).getDocumentFilter() instanceof FloatFilter) {
          return Double.parseDouble(text);
        }
        return text;
      }
      if (myComponent instanceof JComboBox) {
        // enums
        Constant value = (Constant)((JComboBox)myComponent).getSelectedItem();
        return value.getValue();
      }
      if (myComponent instanceof JCheckBox) {
        //booleans
        return ((JCheckBox)myComponent).isSelected() ? Boolean.TRUE : Boolean.FALSE;
      }
      throw new IllegalStateException();
    }
  }
}
