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
import com.android.tools.idea.editors.gfxtrace.renderers.RenderUtils;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.snippets.Labels;
import com.android.tools.idea.editors.gfxtrace.service.snippets.SnippetObject;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.android.tools.rpclib.schema.*;
import com.android.tools.swing.util.FloatFilter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
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
      String typeString = myEditors[i].myType instanceof Primitive ? " ("  + ((Primitive)myEditors[i].myType).getMethod() + ")" : "";
      fields.add(new JBLabel(myNode.atom.getFieldInfo(myEditors[i].myFieldIndex).getName() + typeString), bag.nextLine().next());
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
    @Nullable
    public DocumentReference[] getAffectedDocuments() {
      return null;
    }

    @Override
    public boolean isGlobal() {
      // if i do not touch any files with a change then we HAVE to mark it as global or it does not show up in the menu
      return true;
    }
  }

  @Nullable
  public static EditAtomParametersAction getEditActionFor(@NotNull AtomController.Node node, @NotNull GfxTraceEditor traceEditor) {
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
    public static Editor getFor(@NotNull Type type, @Nullable Object value, @NotNull SnippetObject snippetObject, int fieldIndex) {
      if (type instanceof Primitive) {
        final Primitive primitive = (Primitive)type;

        Collection<Constant> constant = Render.findConstant(snippetObject, primitive);
        if (constant.size() == 1) {
          // handle enum
          List<Constant> constants = Arrays.asList(ConstantSet.lookup(type).getEntries());
          // if we have a set of preferred constants, use them.
          Labels labels = Labels.fromSnippets(snippetObject.getSnippets());
          if (labels != null) {
            List<Constant> preferred = labels.preferred(constants);
            if (preferred.containsAll(constant)) {
              constants = preferred;
            }
          }

          JComboBox combo = new ComboBox(new DefaultComboBoxModel<>(constants.toArray()));
          combo.setSelectedItem(Iterables.get(constant, 0));
          return new Editor(snippetObject, type, combo, fieldIndex);
        }
        if (constant.size() >= 1) {
          // TODO add editing of bit flags
          return new Editor(snippetObject, type, null, fieldIndex);
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

            Number javaValue = RenderUtils.toJavaIntType(method, (Number)value);
            // we need the class of zero to match the class of the number
            Comparable<? extends Number> zero = getZero(javaValue.getClass());
            Comparable<? extends Number> max = getUnsignedMax(method);

            spinner = new JSpinner(new SpinnerNumberModel(javaValue, zero, max, Integer.valueOf(1)));
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

    @NotNull
    private static Comparable<? extends Number> getZero(@NotNull Class<? extends Number> numberClass) {
      if (numberClass == Byte.class) {
        return Byte.valueOf((byte)0);
      }
      if (numberClass == Short.class) {
        return Short.valueOf((short)0);
      }
      if (numberClass == Integer.class) {
        return Integer.valueOf(0);
      }
      if (numberClass == Long.class) {
        return Long.valueOf(0);
      }
      if (numberClass == BigInteger.class) {
        return BigInteger.ZERO;
      }
      throw new IllegalArgumentException("unknown number class " + numberClass);
    }

    @NotNull
    private static Comparable<? extends Number> getUnsignedMax(@NotNull Method type) {
      if (type == Method.Uint8) {
        return (short)255;
      }
      if (type == Method.Uint16) {
        return 65535;
      }
      if (type == Method.Uint32) {
        return 4294967295L;
      }
      if (type == Method.Uint64) {
        return new BigInteger("18446744073709551615");
      }
      throw new IllegalArgumentException("not unsigned type" + type);
    }

    @NotNull
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
        // we don't need to convert it back to the overflown java type as the encoder will encode it correctly anyway
        // {@link Primitive#encodeValue(Encoder, Object)}
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
