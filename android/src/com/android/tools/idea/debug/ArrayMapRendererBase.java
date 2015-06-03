/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.debug;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.descriptors.data.UserExpressionData;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.*;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * ArrayMap's in Android implement the Map interface, but throw an UnsupportedOperationException when
 * the expression "entrySet().toArray()" is evaluated on them. As a result users would see the exception
 * when inspecting an ArrayMap in a debugger. This class provides a custom renderer for ArrayMaps.
 *
 * Similar to {@link ArrayRenderer}, it iterates over all of its children, and displays the first {@link #MAX_CHILDREN}
 * mappings. Each mapping is displayed as an array containing the key and the value.
 */
public class ArrayMapRendererBase extends NodeRendererImpl {
  private static final int MAX_CHILDREN = 10;
  @NonNls private final static String MORE_ELEMENTS = "...";

  private final String myFqn;
  private final LabelRenderer myLabelRenderer = new LabelRenderer();
  private final MyArrayMapSizeEvaluator mySizeEvaluator = new MyArrayMapSizeEvaluator();

  public ArrayMapRendererBase(@NotNull String mapFqn) {
    myProperties.setEnabled(true);
    myProperties.setName(mapFqn);

    myFqn = mapFqn;
  }

  /** Builds a list of {@link DebuggerTreeNode}'s that are the children of this node. */
  @Override
  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    List<DebuggerTreeNode> children = new ArrayList<DebuggerTreeNode>();
    NodeManagerImpl nodeManager = (NodeManagerImpl)builder.getNodeManager();
    NodeDescriptorFactory descriptorFactory = builder.getDescriptorManager();

    int size;
    try {
      size = getArrayMapSize(value, evaluationContext);
    } catch (Exception e) {
      size = 0;
    }

    for (int i = 0, n = Math.min(size, MAX_CHILDREN); i < n; i++) {
      // For each entry, display the value at that entry. TODO: we need to show the key corresponding to this as well.
      // We used to show the key and value by using the following expression:
      // String expression = String.format("new Object[] {this.keyAt(%1$d), this.valueAt(%2$d)}", i, i);
      // But it turns out that this throws "java.lang.ClassNotFoundException: [LObject;"
      // Until we find an alternate scheme, just show the value.
      String expression = String.format("this.valueAt(%1$d)", i);
      UserExpressionData descriptorData =
        new UserExpressionData((ValueDescriptorImpl)builder.getParentDescriptor(), myFqn, String.format("value[%1$d]", i),
                               new TextWithImportsImpl(CodeFragmentKind.EXPRESSION,
                                                       expression,
                                                       "",
                                                       StdFileTypes.JAVA));
      UserExpressionDescriptor userExpressionDescriptor =
        descriptorFactory.getUserExpressionDescriptor(builder.getParentDescriptor(), descriptorData);
      DebuggerTreeNode arrayMapItemNode = nodeManager.createNode(userExpressionDescriptor, evaluationContext);
      children.add(arrayMapItemNode);
    }

    if (size > MAX_CHILDREN) {
      children.add(nodeManager.createMessageNode(new MessageDescriptor(MORE_ELEMENTS, MessageDescriptor.SPECIAL)));
    }

    builder.setChildren(children);
  }

  @Override
  public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    return null;
  }

  @Override
  public boolean isExpandable(Value value, EvaluationContext context, NodeDescriptor parentDescriptor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return getArrayMapIsEmpty(value, context);
  }

  @Override
  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
    throws EvaluateException {
    if (!(descriptor.getValue() instanceof ObjectReference)) {
      return DebuggerBundle.message("label.undefined");
    }

    ObjectReference ref = (ObjectReference)descriptor.getValue();
    myLabelRenderer.setLabelExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "size()", "", StdFileTypes.JAVA));
    return ValueDescriptorImpl.getIdLabel(ref) + ", size = " + myLabelRenderer.calcLabel(descriptor, evaluationContext, listener);
  }

  @Override
  public String getUniqueId() {
    return myFqn;
  }

  @Override
  public boolean isApplicable(@Nullable Type type) {
    return type != null && myFqn.equals(type.name());
  }

  private boolean getArrayMapIsEmpty(Value arrayMapValue, EvaluationContext context) {
    try {
      return getArrayMapSize(arrayMapValue, context) > 0;
    }
    catch (Exception e) {
      return false;
    }
  }

  private int getArrayMapSize(Value arrayMapValue, EvaluationContext context) throws InvalidTypeException, EvaluateException {
    EvaluationContext evaluationContext = context.createEvaluationContext(arrayMapValue);
    Value v = mySizeEvaluator.evaluate(evaluationContext.getProject(), evaluationContext);
    if (v instanceof IntegerValue) {
      return ((IntegerValue)v).intValue();
    }
    else {
      throw new InvalidTypeException();
    }
  }

  private class MyArrayMapSizeEvaluator extends CachedEvaluator {
    public MyArrayMapSizeEvaluator() {
      setReferenceExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "size()", "", StdFileTypes.JAVA));
    }

    @Override
    protected String getClassName() {
      return myFqn;
    }

    public Value evaluate(Project p, EvaluationContext context) throws EvaluateException {
      return getEvaluator(p).evaluate(context);
    }
  }
}
