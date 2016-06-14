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
import com.intellij.ui.CheckBoxList;
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
import java.util.Vector;
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
                                              JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
                                              null, null, null);
    if (result == JOptionPane.OK_OPTION) {
      Dynamic newAtom = ((Dynamic)myNode.atom.unwrap()).copy();
      for (Editor editor : myEditors) {
        if (editor.hasEditComponent()) {
          newAtom.setFieldValue(editor.myFieldIndex, editor.getValue());
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

  @NotNull
  private JComponent buildDialog() {
    JPanel fields = new JPanel(new GridBagLayout());
    GridBag bag = new GridBag()
      .setDefaultAnchor(GridBagConstraints.EAST)
      .setDefaultFill(GridBagConstraints.HORIZONTAL)
      .setDefaultWeightX(1, 1)
      .setDefaultPaddingX(5);

    for (Editor editor : myEditors) {
      String typeString = editor.myType instanceof Primitive ? " (" + ((Primitive)editor.myType).getMethod() + ")" : "";
      fields.add(new JBLabel(myNode.atom.getFieldInfo(editor.myFieldIndex).getName() + typeString), bag.nextLine().next());
      fields.add(editor.getComponent(), bag.next());
    }
    return fields;
  }

  class ActivatePathCommand implements Runnable, UndoableAction {

    @NotNull private final String myName;
    @NotNull private final Path myOldPath;
    @NotNull private final Path myNewPath;

    ActivatePathCommand(@NotNull String name, @NotNull Path oldPath, @NotNull Path newPath) {
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

  private abstract static class Editor {
    public final SnippetObject myCurrentValue;
    public final Type myType;
    public final int myFieldIndex;

    Editor(@NotNull SnippetObject currentValue, @NotNull Type type, int fieldIndex) {
      myCurrentValue = currentValue;
      myType = type;
      myFieldIndex = fieldIndex;
    }

    @NotNull
    public static Editor getFor(@NotNull Type type, @Nullable Object value, @NotNull SnippetObject snippetObject, int fieldIndex) {
      if (type instanceof Primitive) {
        final Primitive primitive = (Primitive)type;

        Collection<Constant> constant = Render.findConstant(snippetObject, primitive);
        if (constant.size() >= 1) {
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

          // TODO change check to actually check if this ConstantSet is flags
          if (constant.size() == 1) {
            return new EnumEditor(snippetObject, type, fieldIndex, constants, Iterables.get(constant, 0));
          }
          else {
            return new FlagEditor(snippetObject, type, fieldIndex, constants, constant);
          }
        }

        Method method = primitive.getMethod();
        if (method == Method.Bool) {
          // handle boolean
          return new BooleanEditor(snippetObject, type, fieldIndex, value);
        }
        else if (method == Method.Float32 || method == Method.Float64) {
          // handle floats/doubles
          assert value != null;
          return new FloatEditor(snippetObject, type, fieldIndex, value);
        }
        else if (method == Method.String) {
          return new StringEditor(snippetObject, type, fieldIndex, value);
        }
        else {
          // handle ints
          assert value != null;
          return new IntEditor(snippetObject, type, fieldIndex, value);
        }
      }
      return new NoEditor(snippetObject, type, fieldIndex);
    }

    public boolean hasEditComponent() {
      return true;
    }

    @NotNull
    abstract JComponent getComponent();

    @Nullable
    abstract Object getValue();
  }

  private static class NoEditor extends Editor {
    NoEditor(@NotNull SnippetObject currentValue, @NotNull Type type, int fieldIndex) {
      super(currentValue, type, fieldIndex);
    }

    @Override
    public boolean hasEditComponent() {
      return false;
    }

    @Override
    @NotNull
    public JComponent getComponent() {
      SimpleColoredComponent result = new SimpleColoredComponent();
      Render.render(myCurrentValue, myType, result, SimpleTextAttributes.REGULAR_ATTRIBUTES, Render.NO_TAG);
      return result;
    }

    @Override
    Object getValue() {
      throw new IllegalStateException();
    }
  }

  private static class BooleanEditor extends Editor {
    private final JCheckBox myCheckBox;

    BooleanEditor(@NotNull SnippetObject currentValue, @NotNull Type type, int fieldIndex, @Nullable Object value) {
      super(currentValue, type, fieldIndex);

      myCheckBox = new JCheckBox();
      myCheckBox.setSelected(Boolean.parseBoolean(String.valueOf(value)));
    }

    @Override
    @NotNull
    JCheckBox getComponent() {
      return myCheckBox;
    }

    @Override
    @Nullable
    Boolean getValue() {
      return myCheckBox.isSelected();
    }
  }

  private static class IntEditor extends Editor {
    private final JSpinner mySpinner;

    IntEditor(@NotNull SnippetObject currentValue, @NotNull Type type, int fieldIndex, @NotNull Object value) {
      super(currentValue, type, fieldIndex);
      Method method = ((Primitive)type).getMethod();

      // unsigned
      if (method == Method.Uint8 || method == Method.Uint16 || method == Method.Uint32 || method == Method.Uint64) {

        Number javaValue = RenderUtils.toJavaIntType(method, (Number)value);
        // we need the class of zero to match the class of the number
        Comparable<? extends Number> zero = getZero(javaValue.getClass());
        Comparable<? extends Number> max = getUnsignedMax(method);

        mySpinner = new JSpinner(new SpinnerNumberModel(javaValue, zero, max, Integer.valueOf(1)));
      }
      else {
        // signed ints
        mySpinner = new JSpinner();
        mySpinner.setValue(value);
      }
    }

    @Override
    @NotNull
    JSpinner getComponent() {
      return mySpinner;
    }

    @Override
    @Nullable
    Object getValue() {
      return mySpinner.getValue();
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
  }

  private static class FloatEditor extends Editor {
    private final JTextField myFloatBox;

    FloatEditor(@NotNull SnippetObject currentValue, @NotNull Type type, int fieldIndex, @NotNull Object value) {
      super(currentValue, type, fieldIndex);

      myFloatBox = new JTextField(String.valueOf(value));
      ((AbstractDocument)myFloatBox.getDocument()).setDocumentFilter(new FloatFilter());
    }

    @Override
    @NotNull
    JTextField getComponent() {
      return myFloatBox;
    }

    @Override
    @Nullable
    Number getValue() {
      String text = myFloatBox.getText();
      return Double.parseDouble(text);
    }
  }

  private static class StringEditor extends Editor {
    private final JTextField textField;

    StringEditor(@NotNull SnippetObject currentValue, @NotNull Type type, int fieldIndex, @Nullable Object value) {
      super(currentValue, type, fieldIndex);

      textField = new JTextField(String.valueOf(value));
    }

    @Override
    @NotNull
    JTextField getComponent() {
      return textField;
    }

    @Override
    @Nullable
    String getValue() {
      return textField.getText();
    }
  }

  private static class EnumEditor extends Editor {
    private final JComboBox<Constant> myCombo;

    EnumEditor(@NotNull SnippetObject currentValue,
               @NotNull Type type,
               int fieldIndex,
               @NotNull List<Constant> constants,
               @NotNull Constant constant) {
      super(currentValue, type, fieldIndex);

      //noinspection unchecked,UseOfObsoleteCollectionType
      myCombo = new ComboBox(new DefaultComboBoxModel<>(new Vector<>(constants)));
      myCombo.setSelectedItem(constant);
    }

    @Override
    @NotNull
    JComboBox<Constant> getComponent() {
      return myCombo;
    }

    @Override
    @Nullable
    Object getValue() {
      Constant value = (Constant)myCombo.getSelectedItem();
      return value.getValue();
    }
  }

  private static class FlagEditor extends Editor {
    private final CheckBoxList<Constant> myFlagList;

    FlagEditor(@NotNull SnippetObject currentValue,
               @NotNull Type type,
               int fieldIndex,
               @NotNull List<Constant> constants,
               @NotNull Collection<Constant> constant) {
      super(currentValue, type, fieldIndex);

      myFlagList = new CheckBoxList<>();
      myFlagList.setItems(constants, null);
      for (Constant con : constant) {
        myFlagList.setItemSelected(con, true);
      }
    }

    @Override
    @NotNull
    CheckBoxList<Constant> getComponent() {
      return myFlagList;
    }

    @Override
    @Nullable
    Number getValue() {
      long result = 0;
      for (int c = 0; c < myFlagList.getItemsCount(); c++) {
        if (myFlagList.isItemSelected(c)) {
          Constant flag = myFlagList.getItemAt(c);
          assert flag != null;
          result |= ((Number)flag.getValue()).longValue();
        }
      }
      return result;
    }
  }
}
