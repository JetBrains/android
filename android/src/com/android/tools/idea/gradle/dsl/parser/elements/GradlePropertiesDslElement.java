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

import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValueImpl;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValueImpl;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Base class for {@link GradleDslElement}s that represent a closure block or a map element. It provides the functionality to store the
 * data as key value pairs and convenient methods to access the data.
 *
 * TODO: Rename this class to something different as this will be conflicting with GradlePropertiesModel
 */
public abstract class GradlePropertiesDslElement extends GradleDslElement {
  @NotNull private Map<String, GradleDslElement> myProperties = new LinkedHashMap<>();

  // Stores the properties that are to be added and removed, in the order they were added or removed.
  // Removed properties map to a value of null.
  @NotNull private Map<String, GradleDslElement> myAdjustedProperties = new LinkedHashMap<>();

  // This contains the most recent mapping from variable name to value. It contains all the items in
  // myProperties in addition to any extra variables defined with 'def'.
  @NotNull private Map<String, GradleDslElement> myVariables = new LinkedHashMap<>();

  protected GradlePropertiesDslElement(@Nullable GradleDslElement parent, @Nullable PsiElement psiElement, @NotNull String name) {
    super(parent, psiElement, name);
  }

  /**
   * Adds the given {@code property}. All additions to {@code myProperties} should be made via this function to
   * ensure that {@code myVariables} is also updated.
   *
   * @param property the name of the property to add
   * @param element  the {@code GradleDslElement} for the property.
   */
  private void addPropertyInternal(@NotNull String property, @NotNull GradleDslElement element) {
    myVariables.put(property, element);
    myProperties.put(property, element);
  }

  /**
   * Removes the give {@code property}. All deletions from {@code myProperties} should be made via this function
   * to ensure that {@code myVariables} is also updated.
   */
  @Nullable
  private GradleDslElement removePropertyInternal(@NotNull String property) {
    myProperties.remove(property);
    return myVariables.remove(property);
  }

  private LinkedHashMap<String, GradleDslElement> replayPropertyAdjustmentsOnto(@NotNull Map<String, GradleDslElement> map) {
    LinkedHashMap<String, GradleDslElement> newMap = new LinkedHashMap<>();
    newMap.putAll(map);
    myAdjustedProperties.forEach((k, v) -> {
      if (v == null) {
        newMap.remove(k);
      } else {
        newMap.put(k, v);
      }
    });
    return newMap;
  }

  /**
   * Sets or replaces the given {@code variable} value with the given {@code element}
   */
  public void setParsedVariable(@NotNull String variable, @NotNull GradleDslElement element) {
    element.myParent = this;
    myVariables.put(variable, element);
  }

  /**
   * Sets or replaces the given {@code property} value with the give {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an assigned statement.
   */
  public void setParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    element.myParent = this;
    addPropertyInternal(property, element);
  }

  /**
   * Sets or replaces the given {@code property} value with the given {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an application statement. As the application statements
   * can have different meanings like append vs replace for list elements, the sub classes can override this method to do the right thing
   * for any given property.
   */
  public void addParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    element.myParent = this;
    addPropertyInternal(property, element);
  }

  /**
   * Sets or replaces the given {@code property} value with the given {@code element}.
   *
   * <p>This method should be used when the given {@code property} would reset the effect of the other property. Ex: {@code reset()} method
   * in android.splits.abi block will reset the effect of the previously defined {@code includes} element.
   */
  protected void addParsedResettingElement(@NotNull String property, @NotNull GradleDslElement element, @NotNull String propertyToReset) {
    element.myParent = this;
    addPropertyInternal(property, element);
    removePropertyInternal(propertyToReset);
  }

  protected void addAsParsedDslExpressionList(@NotNull String property, GradleDslExpression expression) {
    PsiElement psiElement = expression.getPsiElement();
    if (psiElement == null) {
      return;
    }
    // Only elements which are added as expression list are the ones which supports both single argument and multiple arguments
    // (ex: flavorDimensions in android block). To support that, we create an expression list where appending to the arguments list is
    // supported even when there is only one element in it. This does not work in many other places like proguardFile elements where
    // only one argument is supported and for this cases we use addToParsedExpressionList method.
    GradleDslExpressionList literalList = new GradleDslExpressionList(this, psiElement, property, true);
    if (expression instanceof GradleDslMethodCall) {
      for (GradleDslElement element : ((GradleDslMethodCall)expression).getArguments()) {
        if (element instanceof GradleDslExpression) {
          literalList.addParsedExpression((GradleDslExpression)element);
        }
      }
    } else {
      literalList.addParsedExpression(expression);
    }
    addPropertyInternal(property, literalList);
  }

  public void addToParsedExpressionList(@NotNull String property, @NotNull GradleDslElement element) {
    PsiElement psiElement = element.getPsiElement();
    if (psiElement == null) {
      return;
    }

    GradleDslExpressionList gradleDslExpressionList = getPropertyElement(property, GradleDslExpressionList.class);
    if (gradleDslExpressionList == null) {
      gradleDslExpressionList = new GradleDslExpressionList(this, psiElement, property);
      addPropertyInternal(property, gradleDslExpressionList);
    }
    else {
      gradleDslExpressionList.setPsiElement(psiElement);
    }

    if (element instanceof GradleDslExpression) {
      gradleDslExpressionList.addParsedExpression((GradleDslExpression)element);
    }
    else if (element instanceof GradleDslExpressionList) {
      List<GradleDslExpression> gradleExpressions = ((GradleDslExpressionList)element).getExpressions();
      for (GradleDslExpression expression : gradleExpressions) {
        gradleDslExpressionList.addParsedExpression(expression);
      }
    }
  }

  @NotNull
  public Set<String> getProperties() {
    return getPropertyElements().keySet();
  }

  @NotNull
  public Map<String, GradleDslElement> getPropertyElements() {
    return replayPropertyAdjustmentsOnto(myProperties);
  }

  /**
   * Method to check whether a given property string is nested. A property counts as nested if it has move than one component
   * seperated dots ('.').
   */
  private static boolean isPropertyNested(@NotNull String property) {
    return property.contains(".");
  }

  @Nullable
  public GradleDslElement getVariableElement(@NotNull String property) {
    if (!isPropertyNested(property)) {
      Map<String, GradleDslElement> map = replayPropertyAdjustmentsOnto(myVariables);
      return map.get(property);
    }
    return searchForNestedProperty(property);
  }

  /**
   * Returns the {@link GradleDslElement} corresponding to the given {@code property}, or {@code null} if the given {@code property}
   * does not exist in this element.
   */
  @Nullable
  public GradleDslElement getPropertyElement(@NotNull String property) {
    if (!isPropertyNested(property)) {
      Map<String, GradleDslElement> map = replayPropertyAdjustmentsOnto(myProperties);
      return map.get(property);
    }
    return searchForNestedProperty(property);
  }

  /**
   * Searches for a nested {@code property}.
   */
  @Nullable
  private GradleDslElement searchForNestedProperty(@NotNull String property) {
    List<String> propertyNameSegments = Splitter.on('.').splitToList(property);
    GradlePropertiesDslElement nestedElement = this;
    for (int i = 0; i < propertyNameSegments.size() - 1; i++) {
      GradleDslElement element = nestedElement.getPropertyElement(propertyNameSegments.get(i).trim());
      if (element instanceof GradlePropertiesDslElement) {
        nestedElement = (GradlePropertiesDslElement)element;
      }
      else {
        return null;
      }
    }
    return nestedElement.getPropertyElement(propertyNameSegments.get(propertyNameSegments.size() - 1));
  }

  /**
   * Returns the dsl element of the given {@code property} of the type {@code clazz}, or {@code null} if the given {@code property}
   * does not exist in this element.
   */
  @Nullable
  public <T extends GradleDslElement> T getPropertyElement(@NotNull String property, @NotNull Class<T> clazz) {
    GradleDslElement propertyElement = getPropertyElement(property);
    return clazz.isInstance(propertyElement) ? clazz.cast(propertyElement) : null;
  }

  private static <T> GradleNullableValue<T> createAndWrapDslValue(@Nullable GradleDslElement element, @NotNull Class<T> clazz) {
    if (element == null) {
      return new GradleNullableValueImpl<>(null, null);
    }

    T resultValue = null;
    if (clazz.isInstance(element)) {
      resultValue = clazz.cast(element);
    }
    else if (element instanceof GradleDslExpression) {
      resultValue = ((GradleDslExpression)element).getValue(clazz);
    }

    if (resultValue != null) {
      return new GradleNotNullValueImpl<>(element, resultValue);
    }
    else {
      return new GradleNullableValueImpl<>(element, null);
    }
  }

  /**
   * Returns the literal value of the given {@code property} of the type {@code clazz} along with the variable resolution history.
   *
   * <p>The returned {@link GradleNullableValueImpl} may contain a {@code null} value when either the given {@code property} does not exists in
   * this element or the given {@code property} value is not of the type {@code clazz}.
   */
  @NotNull
  public <T> GradleNullableValue<T> getLiteralProperty(@NotNull String property, @NotNull Class<T> clazz) {
    Preconditions.checkArgument(clazz == String.class || clazz == Integer.class || clazz == Boolean.class);

    return createAndWrapDslValue(getPropertyElement(property), clazz);
  }

  /**
   * Adds the given element to the to-be added elements list, which are applied when {@link #apply()} method is invoked
   * or discarded when the {@lik #resetState()} method is invoked.
   */
  @NotNull
  public GradleDslElement setNewElement(@NotNull String property, @NotNull GradleDslElement newElement) {
    newElement.myParent = this;
    myAdjustedProperties.put(property, newElement);
    setModified(true);
    return newElement;
  }

  @NotNull
  public GradleDslElement setNewLiteral(@NotNull String property, @NotNull Object value) {
    return setNewLiteralImpl(property, value);
  }

  @NotNull
  private GradleDslElement setNewLiteralImpl(@NotNull String property, @NotNull Object value) {
    GradleDslLiteral literalElement = getPropertyElement(property, GradleDslLiteral.class);
    if (literalElement == null) {
      literalElement = new GradleDslLiteral(this, property);
      myAdjustedProperties.put(property, literalElement);
    }
    literalElement.setValue(value);
    return literalElement;
  }

  @NotNull
  public GradlePropertiesDslElement addToNewLiteralList(@NotNull String property, @NotNull String value) {
    return addToNewLiteralListImpl(property, value);
  }

  @NotNull
  private GradlePropertiesDslElement addToNewLiteralListImpl(@NotNull String property, @NotNull Object value) {
    GradleDslExpressionList gradleDslExpressionList = getPropertyElement(property, GradleDslExpressionList.class);
    if (gradleDslExpressionList == null) {
      gradleDslExpressionList = new GradleDslExpressionList(this, property);
      myAdjustedProperties.put(property, gradleDslExpressionList);
    }
    gradleDslExpressionList.addNewLiteral(value);
    return this;
  }

  @NotNull
  public GradlePropertiesDslElement removeFromExpressionList(@NotNull String property, @NotNull String value) {
    return removeFromExpressionListImpl(property, value);
  }

  @NotNull
  private GradlePropertiesDslElement removeFromExpressionListImpl(@NotNull String property, @NotNull Object value) {
    GradleDslExpressionList gradleDslExpressionList = getPropertyElement(property, GradleDslExpressionList.class);
    if (gradleDslExpressionList != null) {
      gradleDslExpressionList.removeExpression(value);
    }
    return this;
  }

  @NotNull
  public GradlePropertiesDslElement replaceInExpressionList(@NotNull String property, @NotNull String oldValue, @NotNull String newValue) {
    return replaceInExpressionListImpl(property, oldValue, newValue);
  }

  @NotNull
  private GradlePropertiesDslElement replaceInExpressionListImpl(@NotNull String property,
                                                                 @NotNull Object oldValue,
                                                                 @NotNull Object newValue) {
    GradleDslExpressionList gradleDslExpressionList = getPropertyElement(property, GradleDslExpressionList.class);
    if (gradleDslExpressionList != null) {
      gradleDslExpressionList.replaceExpression(oldValue, newValue);
    }
    return this;
  }

  @NotNull
  public GradlePropertiesDslElement setInNewLiteralMap(@NotNull String property, @NotNull String name, @NotNull String value) {
    return setInNewLiteralMapImpl(property, name, value);
  }

  @NotNull
  public GradlePropertiesDslElement setInNewLiteralMap(@NotNull String property, @NotNull String name, @NotNull Integer value) {
    return setInNewLiteralMapImpl(property, name, value);
  }

  @NotNull
  public GradlePropertiesDslElement setInNewLiteralMap(@NotNull String property, @NotNull String name, @NotNull Boolean value) {
    return setInNewLiteralMapImpl(property, name, value);
  }

  @NotNull
  private GradlePropertiesDslElement setInNewLiteralMapImpl(@NotNull String property, @NotNull String name, @NotNull Object value) {
    GradleDslExpressionMap gradleDslExpressionMap = getPropertyElement(property, GradleDslExpressionMap.class);
    if (gradleDslExpressionMap == null) {
      gradleDslExpressionMap = new GradleDslExpressionMap(this, property);
      myAdjustedProperties.put(property, gradleDslExpressionMap);
    }
    gradleDslExpressionMap.addNewLiteral(name, value);
    return this;
  }

  @NotNull
  public GradlePropertiesDslElement removeFromExpressionMap(@NotNull String property, @NotNull String name) {
    GradleDslExpressionMap gradleDslExpressionMap = getPropertyElement(property, GradleDslExpressionMap.class);
    if (gradleDslExpressionMap != null) {
      gradleDslExpressionMap.removeProperty(name);
    }
    return this;
  }

  /**
   * Marks the given {@code property} for removal.
   *
   * <p>The actual property will be removed from Gradle file when {@link #apply()} method is invoked.
   *
   * <p>The property will be un-marked for removal when {@link #reset()} method is invoked.
   */
  public GradlePropertiesDslElement removeProperty(@NotNull String property) {
    myAdjustedProperties.put(property, null);
    setModified(true);
    return this;
  }

  /**
   * Returns the list of values of type {@code clazz} when the given {@code property} corresponds to a {@link GradleDslExpressionList}.
   *
   * <p>Returns {@code null} when either the given {@code property} does not exists in this element or does not corresponds to a
   * {@link GradleDslExpressionList}.
   *
   * <p>Returns an empty list when the given {@code property} exists in this element and corresponds to a {@link GradleDslExpressionList}, but either
   * that list is empty or does not contain any element of type {@code clazz}.
   */
  @Nullable
  public <E> List<GradleNotNullValue<E>> getListProperty(@NotNull String property, @NotNull Class<E> clazz) {
    GradleDslExpressionList gradleDslExpressionList = getPropertyElement(property, GradleDslExpressionList.class);
    if (gradleDslExpressionList != null) {
      return gradleDslExpressionList.getValues(clazz);
    }
    return null;
  }

  /**
   * Returns the map from properties of the type {@link String} to the values of the type {@code clazz} when the given {@code property}
   * corresponds to a {@link GradleDslExpressionMap}.
   *
   * <p>Returns {@code null} when either the given {@code property} does not exists in this element or does not corresponds to a
   * {@link GradleDslExpressionMap}.
   *
   * <p>Returns an empty map when the given {@code property} exists in this element and corresponds to a {@link GradleDslExpressionMap}, but either that
   * map is empty or does not contain any values of type {@code clazz}.
   */
  @Nullable
  public <V> Map<String, GradleNotNullValue<V>> getMapProperty(@NotNull String property, @NotNull Class<V> clazz) {
    GradleDslExpressionMap gradleDslExpressionMap = getPropertyElement(property, GradleDslExpressionMap.class);
    if (gradleDslExpressionMap != null) {
      return gradleDslExpressionMap.getValues(clazz);
    }
    return null;
  }

  @Override
  @NotNull
  public Collection<GradleDslElement> getChildren() {
    return getPropertyElements().values();
  }

  @Override
  protected void apply() {
    myAdjustedProperties.forEach((property, element) -> {
      // Delete the old element
      GradleDslElement oldElement = removePropertyInternal(property);
      if (oldElement != null) {
        oldElement.delete();
      }

      // Add the new one if we need to.
      if (element != null) {
        if (element.create() != null) {
          setParsedElement(property, element);
        }
      }
    });

    myAdjustedProperties.clear();

    for (GradleDslElement element : myVariables.values()) {
      if (element.isModified()) {
        element.applyChanges();
      }
    }
  }

  @Override
  protected void reset() {
    for (GradleDslElement element : getPropertyElements().values()) {
      if (element.isModified()) {
        element.resetState();
      }
    }
    myAdjustedProperties.clear();
  }

  protected void clear() {
    myAdjustedProperties.clear();
    myProperties.clear();
    myVariables.clear();
  }
}
