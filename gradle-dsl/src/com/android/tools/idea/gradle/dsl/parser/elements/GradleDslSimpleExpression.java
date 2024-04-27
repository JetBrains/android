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

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.model.CachedValue;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.followElement;

/**
 * Represents an expression element.
 */
public abstract class GradleDslSimpleExpression extends GradleDslElementImpl implements GradleDslExpression {
  private boolean myIsInterpolated;
  private boolean myIsReference;
  @Nullable private PsiElement myUnsavedConfigBlock;

  @Nullable protected PsiElement myExpression;
  // Whether or not this value is part of a cycle. If UNSURE, needs to be computed.
  @Nullable protected ThreeState myHasCycle;

  @NotNull private final CachedValue<GradleDslSimpleExpression> myResolvedCachedValue;
  @NotNull private final CachedValue<GradleDslSimpleExpression> myUnresolvedCachedValue;
  @NotNull private final CachedValue<GradleDslSimpleExpression> myRawCachedValue;

  protected GradleDslSimpleExpression(@Nullable GradleDslElement parent,
                                      @Nullable PsiElement psiElement,
                                      @NotNull GradleNameElement name,
                                      @Nullable PsiElement expression) {
    super(parent, psiElement, name);
    myExpression = expression;
    myHasCycle = ThreeState.UNSURE;
    resolve();
    // Resolved values must be created after resolve() is called. If the debugger calls toString to trigger
    // any of the producers they will be stuck with the wrong value as dependencies have not been computed.
    myResolvedCachedValue = new CachedValue<>(this, GradleDslSimpleExpression::produceValue);
    myUnresolvedCachedValue = new CachedValue<>(this, GradleDslSimpleExpression::produceUnresolvedValue);
    myRawCachedValue = new CachedValue<>(this, GradleDslSimpleExpression::produceRawValue);
  }

  @Override
  @Nullable
  public PsiElement getExpression() {
    return myExpression;
  }

  public void setExpression(@NotNull PsiElement expression) {
    myExpression = expression;
  }

  @Nullable
  public final Object getValue() {
    return myResolvedCachedValue.getValue();
  }

  @Nullable
  protected abstract Object produceValue();

  @Nullable
  public final Object getUnresolvedValue() {
    return myUnresolvedCachedValue.getValue();
  }

  @Nullable
  protected abstract Object produceUnresolvedValue();

  @Nullable
  public <T> T getValue(@NotNull Class<T> clazz) {
    Object value = getValue();
    if (value != null && clazz.isAssignableFrom(value.getClass())) {
      return clazz.cast(value);
    }
    return null;
  }

  @Nullable
  public <T> T getUnresolvedValue(@NotNull Class<T> clazz) {
    Object value = getUnresolvedValue();
    if (value != null && clazz.isAssignableFrom(value.getClass())) {
      return clazz.cast(value);
    }
    return null;
  }

  public abstract void setValue(@NotNull Object value);

  @Nullable
  public final Object getRawValue() {
    return myRawCachedValue.getValue();
  }

  /**
   * @return an object representing the raw value, this can be passed into {@link GradlePropertyModel#setValue(Object)} to set the value
   * correctly. That is, this method will return a {@link ReferenceTo} if needed where as {@link #getUnresolvedValue()} will not.
   * It will also correctly wrap any string that should be interpolated with double quotes.
   */
  @Nullable
  protected abstract Object produceRawValue();

  /**
   * @return a new object that is based on this one but has no backing PsiElement or parent.
   * This means that it can be used to duplicate the element and use it elsewhere in the tree without the danger that the PsiElement will
   * be deleted from use elsewhere.
   */
  @Override
  @NotNull
  public abstract GradleDslSimpleExpression copy();

  /**
   * This should be overridden by subclasses if they require different behaviour, such as getting the dependencies of
   * un-applied expressions.
   */
  @Override
  @NotNull
  public List<GradleReferenceInjection> getResolvedVariables() {
    return myDependencies.stream().filter(e -> e.isResolved()).collect(Collectors.toList());
  }

  public boolean isInterpolated() {
    return myIsInterpolated;
  }

  public void setInterpolated(boolean isInterpolated) {
    myIsInterpolated = isInterpolated;
  }

  public boolean isReference() {
    return myIsReference;
  }

  public void setReference(boolean isReference) {
    myIsReference = isReference;
  }

  @Nullable
  public String getReferenceText() {
    return null; // Overridden by subclasses.
  }

  @Override
  protected void reset() {
    myRawCachedValue.clear();
    myUnresolvedCachedValue.clear();
    myResolvedCachedValue.clear();
  }

  /**
   * This is like plain {@link #dereference(GradleDslElement, String)} but with the opposite handling of references from DslLiterals: its
   * input must already be resolved to a PropertiesDslElement, and it follows references from DslLiterals to return a PropertiesDslElement,
   * which is particularly useful when we require the return value itself to be dereferenceable (e.g. for assignments)
   *
   * @param element
   * @param index
   * @return
   */
  @Nullable
  public static GradlePropertiesDslElement dereferencePropertiesElement(@NotNull GradlePropertiesDslElement element, @NotNull String index) {
    GradleDslElement result = dereference(element, index);
    result = followElement(result);
    if (result instanceof GradlePropertiesDslElement) {
      return (GradlePropertiesDslElement)result;
    }
    else {
      return null;
    }
  }

  /**
   * Tells the expression that the value has changed, this sets this element to modified and resets the cycle detection state.
   */
  protected void valueChanged() {
    myHasCycle = ThreeState.UNSURE;
    setModified();
  }

  /**
   * Works out whether or not this GradleDslSimpleExpression has a cycle.
   */
  public boolean hasCycle() {
    if (myHasCycle != ThreeState.UNSURE) {
      return myHasCycle == ThreeState.YES;
    }
    return hasCycle(this, new HashSet<>(), new HashSet<>());
  }

  private static boolean hasCycle(@NotNull GradleDslSimpleExpression element,
                                  @NotNull Set<GradleDslSimpleExpression> seen,
                                  @NotNull Set<GradleDslSimpleExpression> cycleFree) {
    if (element.myHasCycle != ThreeState.UNSURE) {
      return element.myHasCycle == ThreeState.YES;
    }

    boolean hasCycle = checkCycle(element, seen, cycleFree);
    element.myHasCycle = hasCycle ? ThreeState.YES : ThreeState.NO;
    return hasCycle;
  }

  private static boolean checkCycle(@NotNull GradleDslSimpleExpression element,
                                    @NotNull Set<GradleDslSimpleExpression> seen,
                                    @NotNull Set<GradleDslSimpleExpression> cycleFree) {
    if (cycleFree.contains(element) || element.getExpression() == null) {
      return false;
    }

    if (seen.contains(element)) {
      return true;
    }

    seen.add(element);

    Collection<GradleReferenceInjection> injections = element.getResolvedVariables();

    for (GradleReferenceInjection injection : injections) {
      if (injection.getToBeInjectedExpression() == null) {
        continue;
      }

      boolean hasCycle = hasCycle(injection.getToBeInjectedExpression(), seen, cycleFree);
      if (hasCycle) {
        seen.remove(element);
        return true;
      }
    }

    seen.remove(element);
    cycleFree.add(element);
    return false;
  }

  @Override
  public void resolve() {
    setupDependencies(myExpression);
  }

  @NotNull
  protected List<GradleReferenceInjection> fetchDependencies(@Nullable PsiElement element) {
    if (element == null) {
      return ImmutableList.of();
    }
    return ApplicationManager.getApplication()
                             .runReadAction(
                               (Computable<List<GradleReferenceInjection>>)() -> getDslFile().getParser().getInjections(this, element));
  }

  protected void setupDependencies(@Nullable PsiElement element) {
    // Unregister any registered dependencies.
    myDependencies.stream().filter(e -> e.getToBeInjected() != null).forEach(e -> e.getToBeInjected().unregisterDependent(e));
    myDependencies.stream().filter(e -> e.getToBeInjected() == null)
                  .forEach(e -> getDslFile().getContext().getDependencyManager().unregisterUnresolvedReference(e));
    myDependencies.clear();
    myDependencies.addAll(fetchDependencies(element));
    // Register any resolved dependencies with the elements they depend on.
    myDependencies.stream().filter(e -> e.getToBeInjected() != null).forEach(e -> e.getToBeInjected().registerDependent(e));
    myDependencies.stream().filter(e -> e.getToBeInjected() == null)
                  .forEach(e -> getDslFile().getContext().getDependencyManager().registerUnresolvedReference(e));
  }
}
