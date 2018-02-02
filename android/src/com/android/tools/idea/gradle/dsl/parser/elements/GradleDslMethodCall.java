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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Represents a method call expression element.
 */
public final class GradleDslMethodCall extends GradleDslExpression {
  private final
  @NotNull List<GradleDslElement> myArguments = Lists.newArrayList();
  private final
  @NotNull List<GradleDslElement> myToBeRemovedArguments = Lists.newArrayList();

  @Nullable private String myStatementName;

  @Nullable private GradleDslElement myToBeAddedArgument;

  @Nullable private PsiElement myArgumentListPsiElement;

  /**
   * Create a new method call.
   *
   * @param parent        the parent element.
   * @param methodName    method name.
   * @param statementName the statement name this method call need to be added to,  Ex: to create "compile project(':xyz')",
   *                      use "compile" as statement name and "project" as method name, or {@code null} if the method needs to be added
   *                      without any application statement.
   */
  public GradleDslMethodCall(@NotNull GradleDslElement parent, @NotNull GradleNameElement methodName, @Nullable String statementName) {
    super(parent, null, methodName, null);
    myStatementName = statementName;
  }

  public GradleDslMethodCall(@NotNull GradleDslElement parent,
                             @NotNull PsiElement methodCall,
                             @NotNull GradleNameElement name,
                             @NotNull PsiElement argumentListPsiElement) {
    super(parent, methodCall, name, methodCall);
    myArgumentListPsiElement = argumentListPsiElement;
  }

  public void addParsedExpression(@NotNull GradleDslExpression expression) {
    expression.myParent = this;
    myArguments.add(expression);
  }

  public void addParsedExpressionMap(@NotNull GradleDslExpressionMap expressionMap) {
    expressionMap.myParent = this;
    myArguments.add(expressionMap);
  }

  public void addNewArgument(@NotNull GradleDslExpression argument) {
    addNewArgumentInternal(argument);
  }

  public void addNewArgument(@NotNull GradleDslExpressionMap mapArgument) {
    addNewArgumentInternal(mapArgument);
  }

  /**
   * This method should <b>not</b> be called outside of the GradleDslWriter classes.
   * <p>
   * If you need to add an argument to this GradleDslMethodCall please use {@link #addNewArgument(GradleDslExpression) addNewArgument}
   * followed by a call to {@link #apply() apply} to ensure the change is written to the underlying file.
   */
  public void commitNewArgument(@NotNull GradleDslElement element) {
    myArguments.add(element);
  }

  private void addNewArgumentInternal(@NotNull GradleDslElement argument) {
    assert argument instanceof GradleDslExpression || argument instanceof GradleDslExpressionMap;
    // Only adding expression or map arguments to an empty method is supported.
    // The other combinations are not supported as there is no real use case.
    if (getArguments().isEmpty()) {
      myToBeAddedArgument = argument;
      setModified(true);
    }
  }

  @Nullable
  public PsiElement getArgumentListPsiElement() {
    return myArgumentListPsiElement;
  }

  @Nullable
  public GradleDslElement getToBeAddedArgument() {
    return myToBeAddedArgument;
  }

  @NotNull
  public List<GradleDslElement> getArguments() {
    if (myToBeRemovedArguments.isEmpty() && myToBeAddedArgument == null) {
      return ImmutableList.copyOf(myArguments);
    }

    List<GradleDslElement> result = Lists.newArrayList();

    for (GradleDslElement argument : myArguments) {
      if (argument instanceof GradleDslReference) {
        // See if the reference is pointing to a list or map.
        GradleDslExpressionList listValue = ((GradleDslReference)argument).getValue(GradleDslExpressionList.class);
        if (listValue != null) {
          result.addAll(listValue.getExpressions());
          continue;
        }

        GradleDslExpressionMap mapValue = ((GradleDslReference)argument).getValue(GradleDslExpressionMap.class);
        if (mapValue != null) {
          result.add(mapValue);
          continue;
        }
      }
      result.add(argument);
    }

    if (myToBeAddedArgument != null) {
      result.add(myToBeAddedArgument);
    }

    for (GradleDslElement argument : myToBeRemovedArguments) {
      result.remove(argument);
    }

    return result;
  }

  @Override
  @NotNull
  public Collection<GradleDslElement> getChildren() {
    return getArguments();
  }

  @Override
  @Nullable
  public Object getValue() {
    // If we only have one argument then just return its value. This allows us to correctly
    // parse functions that are used to set properties.
    if (myArguments.size() == 1 && myArguments.get(0) instanceof GradleDslExpression) {
      return ((GradleDslExpression)myArguments.get(0)).getValue();
    }

    PsiElement psiElement = getPsiElement();
    return psiElement != null ? psiElement.getText() : null;
  }

  @Override
  @Nullable
  public Object getUnresolvedValue() {
    return getValue();
  }

  @Override
  @Nullable
  public <T> T getValue(@NotNull Class<T> clazz) {
    if (clazz.isAssignableFrom(File.class)) {
      return clazz.cast(getFileValue());
    }
    Object value = getValue();
    if (clazz.isInstance(value)) {
      return clazz.cast(value);
    }
    return null;
  }

  @Override
  @Nullable
  public <T> T getUnresolvedValue(@NotNull Class<T> clazz) {
    return getValue(clazz);
  }

  @Nullable
  private File getFileValue() {
    if (!myName.name().equals("file")) {
      return null;
    }

    List<GradleDslElement> arguments = getArguments();
    if (arguments.isEmpty()) {
      return null;
    }

    GradleDslElement pathArgument = arguments.get(0);
    if (!(pathArgument instanceof GradleDslExpression)) {
      return null;
    }

    String path = ((GradleDslExpression)pathArgument).getValue(String.class);
    if (path == null) {
      return null;
    }

    return new File(path);
  }

  @Override
  public void setValue(@NotNull Object value) {
    if (value instanceof File) {
      setFileValue((File)value);
    }
    // TODO: Add support to set the full method definition as a String.

    valueChanged();
  }

  private void setFileValue(@NotNull File file) {
    if (!myName.name().equals("file")) {
      return;
    }

    List<GradleDslElement> arguments = getArguments();
    if (arguments.isEmpty()) {
      GradleDslLiteral argument = new GradleDslLiteral(this, myName);
      argument.setValue(file.getPath());
      myToBeAddedArgument = argument;
      return;
    }

    GradleDslElement pathArgument = arguments.get(0);
    if (!(pathArgument instanceof GradleDslExpression)) {
      return;
    }

    ((GradleDslExpression)pathArgument).setValue(file.getPath());
  }

  @Nullable
  public String getStatementName() {
    return myStatementName;
  }

  public void remove(GradleDslElement argument) {
    if (myArguments.contains(argument)) {
      myToBeRemovedArguments.add(argument);
      setModified(true);
    }
  }

  @Override
  protected void apply() {
    for (GradleDslElement argument : myToBeRemovedArguments) {
      if (myArguments.remove(argument)) {
        argument.delete();
      }
    }

    getDslFile().getWriter().applyDslMethodCall(this);
  }

  @Override
  protected void reset() {
    myToBeAddedArgument = null;
    myToBeRemovedArguments.clear();
    for (GradleDslElement argument : myArguments) {
      if (argument.isModified()) {
        argument.resetState();
      }
    }
  }

  @Override
  @Nullable
  public PsiElement create() {
    return getDslFile().getWriter().createDslMethodCall(this);
  }
}
