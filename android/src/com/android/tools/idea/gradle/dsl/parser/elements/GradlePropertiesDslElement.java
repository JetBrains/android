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

import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
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
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Base class for {@link GradleDslElement}s that represent a closure block or a map element. It provides the functionality to store the
 * data as key value pairs and convenient methods to access the data.
 *
 * TODO: Rename this class to something different as this will be conflicting with GradlePropertiesModel
 */
public abstract class GradlePropertiesDslElement extends GradleDslElement {
  @NotNull private final static Predicate<GradleDslElement> VARIABLE_FILTER = e -> e.getElementType() == PropertyType.VARIABLE;
  // This filter currently gives us everything that is not a variable.
  @NotNull private final static Predicate<GradleDslElement> PROPERTY_FILTER = VARIABLE_FILTER.negate();
  @NotNull private final static Predicate<GradleDslElement> ANY_FILTER = e -> true;

  @NotNull private final Map<String, ElementList> myProperties = new LinkedHashMap<>();

  /**
   * Represents the state of an element.
   */
  private enum ElementState {
    TO_BE_ADDED, // Does not exist on file, should be added.
    TO_BE_REMOVED, // Exists on file but should be deleted.
    EXISTING, // Exists on file and should stay there.
    HIDDEN, // Exists on file but invisible to the model.
  }

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
  private void addPropertyInternal(@NotNull String property, @NotNull GradleDslElement element, @NotNull ElementState state) {
    if (myProperties.containsKey(property)) {
      myProperties.get(property).addElement(element, state);
    }
    else {
      ElementList elementList = new ElementList();
      elementList.addElement(element, state);
      myProperties.put(property, elementList);
    }
  }

  private void removePropertyInternal(@NotNull String property) {
    if (myProperties.containsKey(property)) {
      myProperties.get(property).removeAll();
    }
  }

  private void removePropertyInternal(@NotNull GradleDslElement element) {
    if (myProperties.containsKey(element.getName())) {
      myProperties.get(element.getName()).remove(element);
    }
  }

  private void hidePropertyInternal(@NotNull String property) {
    myProperties.get(property).hideAll();
  }

  /**
   * Sets or replaces the given {@code property} value with the give {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an assigned statement.
   */
  public void setParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    element.myParent = this;
    addPropertyInternal(property, element, ElementState.EXISTING);
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
    addPropertyInternal(property, element, ElementState.EXISTING);
  }

  /**
   * Sets or replaces the given {@code property} value with the given {@code element}.
   *
   * <p>This method should be used when the given {@code property} would reset the effect of the other property. Ex: {@code reset()} method
   * in android.splits.abi block will reset the effect of the previously defined {@code includes} element.
   */
  protected void addParsedResettingElement(@NotNull String property, @NotNull GradleDslElement element, @NotNull String propertyToReset) {
    element.myParent = this;
    addPropertyInternal(property, element, ElementState.EXISTING);
    hidePropertyInternal(propertyToReset);
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
    }
    else {
      literalList.addParsedExpression(expression);
    }
    addPropertyInternal(property, literalList, ElementState.EXISTING);
  }

  public void addToParsedExpressionList(@NotNull String property, @NotNull GradleDslElement element) {
    PsiElement psiElement = element.getPsiElement();
    if (psiElement == null) {
      return;
    }

    GradleDslExpressionList gradleDslExpressionList = getPropertyElement(property, GradleDslExpressionList.class);
    if (gradleDslExpressionList == null) {
      gradleDslExpressionList = new GradleDslExpressionList(this, psiElement, property, false);
      addPropertyInternal(property, gradleDslExpressionList, ElementState.EXISTING);
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

  /**
   * Note: This function does NOT guarantee that only elements belonging to properties are returned, since this class is also used
   * for maps it is also possible for the resulting elements to be of {@link PropertyType#DERIVED}.
   */
  @NotNull
  public Map<String, GradleDslElement> getPropertyElements() {
    return getElementsWhere(PROPERTY_FILTER);
  }

  @NotNull
  public Map<String, GradleDslElement> getVariableElements() {
    return getElementsWhere(VARIABLE_FILTER);
  }

  @NotNull
  public Map<String, GradleDslElement> getElements() {
    return getElementsWhere(ANY_FILTER);
  }

  @NotNull
  private Map<String, GradleDslElement> getElementsWhere(@NotNull Predicate<GradleDslElement> predicate) {
    Map<String, GradleDslElement> results = new LinkedHashMap<>();
    for (Map.Entry<String, ElementList> item : myProperties.entrySet()) {
      GradleDslElement element = item.getValue().getElementWhere(predicate);
      if (element != null) {
        results.put(item.getKey(), element);
      }
    }
    return results;
  }

  private GradleDslElement getElementWhere(@NotNull String name, @NotNull Predicate<GradleDslElement> predicate) {
    return getElementsWhere(predicate).get(name);
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
      return getElementWhere(property, VARIABLE_FILTER);
    }
    return searchForNestedProperty(property, GradlePropertiesDslElement::getVariableElement);
  }

  /**
   * Returns the {@link GradleDslElement} corresponding to the given {@code property}, or {@code null} if the given {@code property}
   * does not exist in this element.
   */
  @Nullable
  public GradleDslElement getPropertyElement(@NotNull String property) {
    if (!isPropertyNested(property)) {
      return getElementWhere(property, PROPERTY_FILTER);
    }
    return searchForNestedProperty(property, GradlePropertiesDslElement::getPropertyElement);
  }

  @Nullable
  public GradleDslElement getElement(@NotNull String property) {
    if (!isPropertyNested(property)) {
      return getElementWhere(property, ANY_FILTER);
    }
    return searchForNestedProperty(property, GradlePropertiesDslElement::getElement);
  }

  /**
   * Searches for a nested {@code property}.
   */
  @Nullable
  private GradleDslElement searchForNestedProperty(@NotNull String property,
                                                   @NotNull BiFunction<GradlePropertiesDslElement, String, GradleDslElement> func) {
    List<String> propertyNameSegments = Splitter.on('.').splitToList(property);
    GradlePropertiesDslElement nestedElement = this;
    for (int i = 0; i < propertyNameSegments.size() - 1; i++) {
      GradleDslElement element = nestedElement.getElement(propertyNameSegments.get(i).trim());
      if (element instanceof GradlePropertiesDslElement) {
        nestedElement = (GradlePropertiesDslElement)element;
      }
      else {
        return null;
      }
    }
    return func.apply(nestedElement, propertyNameSegments.get(propertyNameSegments.size() - 1));
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
    addPropertyInternal(property, newElement, ElementState.TO_BE_ADDED);
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
      addPropertyInternal(property, literalElement, ElementState.TO_BE_ADDED);
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
      gradleDslExpressionList = new GradleDslExpressionList(this, property, false);
      addPropertyInternal(property, gradleDslExpressionList, ElementState.TO_BE_ADDED);
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

  /**
   * Marks the given {@code property} for removal.
   *
   * <p>The actual property will be removed from Gradle file when {@link #apply()} method is invoked.
   *
   * <p>The property will be un-marked for removal when {@link #reset()} method is invoked.
   */
  public GradlePropertiesDslElement removeProperty(@NotNull String property) {
    removePropertyInternal(property);
    setModified(true);
    return this;
  }

  public GradlePropertiesDslElement removeProperty(@NotNull GradleDslElement element) {
    removePropertyInternal(element);
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

  @Override
  @NotNull
  public Collection<GradleDslElement> getChildren() {
    return getPropertyElements().values();
  }

  @Override
  protected void apply() {
    for (Iterator<Map.Entry<String, ElementList>> i = myProperties.entrySet().iterator(); i.hasNext(); ) {
      Map.Entry<String, ElementList> entry = i.next();
      entry.getValue().removeElements(GradleDslElement::delete);
      entry.getValue().createElements(e -> e.create() != null);
      if (entry.getValue().isEmpty()) {
        i.remove();
      }
    }

    for (ElementList list : myProperties.values()) {
      list.applyElements(e -> {
        if (e.isModified()) {
          e.apply();
        }
      });
    }
  }

  @Override
  protected void reset() {
    for (Iterator<Map.Entry<String, ElementList>> i = myProperties.entrySet().iterator(); i.hasNext(); ) {
      Map.Entry<String, ElementList> entry = i.next();
      entry.getValue().reset();
      if (entry.getValue().isEmpty()) {
        i.remove();
      }
    }
  }

  protected void clear() {
    myProperties.clear();
  }

  /**
   * Class to deal with retrieving the correct property for a given context. It manages whether
   * or not variable types should be returned along with coordinating a number of properties
   * with the same name.
   */
  private static class ElementList {
    /**
     * Wrapper to add state to each element.
     */
    private static class ElementItem {
      @NotNull private GradleDslElement myElement;
      @NotNull private ElementState myElementState;

      private ElementItem(@NotNull GradleDslElement element, @NotNull ElementState state) {
        myElement = element;
        myElementState = state;
      }
    }

    @NotNull private final Deque<ElementItem> myElements;

    private ElementList() {
      myElements = new ArrayDeque<>();
    }

    @Nullable
    private GradleDslElement getElementWhere(@NotNull Predicate<GradleDslElement> predicate) {
      return myElements.stream().filter(e -> e.myElementState != ElementState.TO_BE_REMOVED && e.myElementState != ElementState.HIDDEN)
        .map(e -> e.myElement).filter(predicate)
        .findFirst().orElse(null);
    }

    private void addElement(@NotNull GradleDslElement newElement, @NotNull ElementState state) {
      myElements.push(new ElementItem(newElement, state));
    }

    private void remove(@NotNull GradleDslElement element) {
      myElements.stream().filter(e -> element == e.myElement).findFirst().ifPresent(e -> e.myElementState = ElementState.TO_BE_REMOVED);
    }

    private void removeAll() {
      myElements.forEach(e -> e.myElementState = ElementState.TO_BE_REMOVED);
    }

    private void hideAll() {
      myElements.forEach(e -> e.myElementState = ElementState.HIDDEN);
    }

    private boolean isEmpty() {
      return myElements.isEmpty();
    }

    private void reset() {
      for (Iterator<ElementItem> i = myElements.iterator(); i.hasNext(); ) {
        ElementItem item = i.next();
        item.myElement.reset();
        if (item.myElementState == ElementState.TO_BE_REMOVED) {
          item.myElementState = ElementState.EXISTING;
        }
        if (item.myElementState == ElementState.TO_BE_ADDED) {
          i.remove();
        }
      }
    }

    /**
     * Runs {@code removeFunc} across all of the elements with {@link ElementState#TO_BE_REMOVED} stored in this list.
     * Once {@code removeFunc} has been run, the element is removed from the list.
     */
    private void removeElements(@NotNull Consumer<GradleDslElement> removeFunc) {
      for (Iterator<ElementItem> i = myElements.iterator(); i.hasNext(); ) {
        ElementItem item = i.next();
        if (item.myElementState == ElementState.TO_BE_REMOVED) {
          removeFunc.accept(item.myElement);
          i.remove();
        }
      }
    }

    /**
     * Runs {@code addFunc} across all of the elements with {@link ElementState#TO_BE_ADDED} stored in this list.
     * If {@code addFunc} returns true then the state is changed to {@link ElementState#EXISTING} else the element
     * is removed.
     */
    private void createElements(@NotNull Predicate<GradleDslElement> addFunc) {
      for (Iterator<ElementItem> i = myElements.iterator(); i.hasNext(); ) {
        ElementItem item = i.next();
        if (item.myElementState == ElementState.TO_BE_ADDED) {
          if (addFunc.test(item.myElement)) {
            item.myElementState = ElementState.EXISTING;
          }
          else {
            i.remove();
          }
        }
      }
    }

    /**
     * Runs {@code func} across all of the elements stored in this list.
     */
    private void applyElements(@NotNull Consumer<GradleDslElement> func) {
      myElements.stream().map(e -> e.myElement).forEach(func);
    }
  }
}
