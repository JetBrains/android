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

import com.android.tools.idea.gradle.dsl.model.ext.transforms.PropertyTransform;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType;
import com.android.tools.idea.gradle.dsl.parser.semantics.SemanticsDescription;
import com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.ext.ElementSort;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.followElement;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.isPropertiesElementOrMap;
import static com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference.PROPERTY_PLACEMENT;
import static com.android.tools.idea.gradle.dsl.parser.elements.ElementState.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.RESET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelSemanticsDescription.CREATE_WITH_VALUE;

/**
 * Base class for {@link GradleDslElement}s that represent a closure block or a map element. It provides the functionality to store the
 * data as key value pairs and convenient methods to access the data.
 * <p>
 * TODO: Rename this class to something different as this is confusable with GradlePropertiesModelImpl
 */
public abstract class GradlePropertiesDslElement extends GradleDslElementImpl {
  private static final Logger LOG = Logger.getInstance(GradlePropertiesDslElement.class);

  @NotNull private final static Predicate<ElementList.ElementItem> VARIABLE_FILTER =
    e -> e.myElement.getElementType() == PropertyType.VARIABLE;
  // This filter currently gives us everything that is not a variable.
  @NotNull private final static Predicate<ElementList.ElementItem> PROPERTY_FILTER = VARIABLE_FILTER.negate();
  @NotNull private final static Predicate<ElementList.ElementItem> ANY_FILTER = e -> true;

  @NotNull public final ElementList myProperties = new ElementList();

  protected GradlePropertiesDslElement(@Nullable GradleDslElement parent,
                                       @Nullable PsiElement psiElement,
                                       @NotNull GradleNameElement name) {
    super(parent, psiElement, name);
  }

  /**
   * Adds the given {@code property}. All additions to {@code myProperties} should be made via this function to
   * ensure that {@code myVariables} is also updated.
   *
   * @param element the {@code GradleDslElement} for the property.
   */
  private void addPropertyInternal(@NotNull GradleDslElement element, @NotNull ElementState state) {
    if (this instanceof ExtDslElement && state == TO_BE_ADDED) {
      int index = reorderAndMaybeGetNewIndex(element);
      myProperties.addElementAtIndex(element, state, index, false);
    }
    else {
      myProperties.addElement(element, state, state == EXISTING);
    }

    if (state == TO_BE_ADDED) {
      updateDependenciesOnAddElement(element);
      element.setModified();
    }
  }

  public void addParsedPropertyAsFirstElement(@NotNull GradleDslElement extElement) {
    myProperties.addElementAtIndex(extElement, EXISTING, 0, true);
  }

  private void addPropertyInternal(int index, @NotNull GradleDslElement element, @NotNull ElementState state) {
    myProperties.addElementAtIndex(element, state, index, state == EXISTING);
    if (state == TO_BE_ADDED) {
      updateDependenciesOnAddElement(element);
      element.setModified();
    }
  }

  public void addAppliedProperty(@NotNull GradleDslElement element) {
    element.addHolder(this);
    addPropertyInternal(element, APPLIED);
  }

  public void addDefaultProperty(@NotNull GradleDslElement element) {
    element.setElementType(REGULAR);
    addPropertyInternal(element, DEFAULT);
  }

  private void removePropertyInternal(@NotNull String property) {
    List<GradleDslElement> elements = myProperties.removeAll(e -> e.myElement.getName().equals(property));
    elements.forEach(e -> {
      e.setModified();
      updateDependenciesOnRemoveElement(e);
    });
    // Since we only setModified after the child is removed we need to set us to be modified after.
    setModified();
  }

  /**
   * Removes the property by the given element. Returns the OLD ElementState.
   */
  private ElementState removePropertyInternal(@NotNull GradleDslElement element) {
    element.setModified();
    ElementState state = myProperties.remove(element);
    updateDependenciesOnRemoveElement(element);
    return state;
  }

  private ElementState replacePropertyInternal(@NotNull GradleDslElement element, @NotNull GradleDslElement newElement) {
    element.setModified();
    updateDependenciesOnReplaceElement(element, newElement);
    newElement.setModified();

    ElementState oldState = myProperties.replaceElement(element, newElement);
    reorderAndMaybeGetNewIndex(newElement);
    return oldState;
  }

  private ElementState hidePropertyInternal(@NotNull GradleDslElement element) {
    return myProperties.hide(element);
  }

  public void addAppliedModelProperties(@NotNull GradleDslFile file) {
    // Here we need to merge the properties into from the applied file into this element.
    mergePropertiesFrom(file);
  }

  private void mergePropertiesFrom(@NotNull GradlePropertiesDslElement other) {
    Map<String, GradleDslElement> ourProperties = getPropertyElements();
    for (Map.Entry<String, GradleDslElement> entry : other.getPropertyElements().entrySet()) {
      GradleDslElement newProperty = entry.getValue();

      // Don't merge ApplyDslElements, this can cause stack overflow exceptions while applying changes in
      // complex projects.
      if (newProperty instanceof ApplyDslElement) {
        continue;
      }

      if (ourProperties.containsKey(entry.getKey())) {
        GradleDslElement existingProperty = getElementWhere(entry.getKey(), PROPERTY_FILTER);
        // If they are both block elements, merge them.
        if (newProperty instanceof GradleDslBlockElement && existingProperty instanceof GradleDslBlockElement) {
          ((GradlePropertiesDslElement)existingProperty).mergePropertiesFrom((GradlePropertiesDslElement)newProperty);
          continue;
        }
      }
      else if (isPropertiesElementOrMap(newProperty)) {
        // If the element we are trying to add a GradlePropertiesDslElement that doesn't exist, create it.
        GradlePropertiesDslElement createdElement =
          getDslFile().getParser().getPropertiesElement(Arrays.asList(entry.getKey().split("\\.")), this, null);
        if (createdElement != null) {
          // Merge it with the created element.
          createdElement.mergePropertiesFrom((GradlePropertiesDslElement)newProperty);
          continue;
        }
      }

      // Otherwise just add the new property.
      addAppliedProperty(entry.getValue());
    }
  }

  /**
   * Sets or replaces the given {@code property} value with the give {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an assigned statement.
   */
  public void setParsedElement(@NotNull GradleDslElement element) {
    element.setParent(this);
    addPropertyInternal(element, EXISTING);
  }

  /**
   * Sets or replaces the given {@code property} value with the given {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an application statement. As the application statements
   * can have different meanings like append vs replace for list elements, the sub classes can override this method to do the right thing
   * for any given property.
   */
  public void addParsedElement(@NotNull GradleDslElement element) {
    element.setParent(this);
    addPropertyInternal(element, EXISTING);
  }

  /**
   * Augments a property with the given {@code element}.
   *
   * Subclasses
   * @param element
   */
  public void augmentParsedElement(@NotNull GradleDslElement element) {
    ModelEffectDescription modelEffect = element.getModelEffect();
    if (modelEffect == null) {
      // this is probably not right but let's see what happens.
      addParsedElement(element);
      return;
    }
    ModelPropertyType type = modelEffect.property.type;
    if (type == ModelPropertyType.MUTABLE_LIST || type == ModelPropertyType.MUTABLE_SET) {
      addToParsedExpressionList(modelEffect, element);
      return;
    }
    if (type == ModelPropertyType.MUTABLE_MAP) {
      addToParsedExpressionMap(modelEffect, element);
      return;
    }
    addParsedElement(element);
  }

  protected void addAsParsedDslExpressionList(@NotNull ModelEffectDescription effect, @NotNull GradleDslSimpleExpression expression) {
    String property = effect.property.name;
    PsiElement psiElement = expression.getPsiElement();
    if (psiElement == null) {
      return;
    }
    // Only elements which are added as expression list are the ones which supports both single argument and multiple arguments
    // (ex: flavorDimensions in android block). To support that, we create an expression list where appending to the arguments list is
    // supported even when there is only one element in it. This does not work in many other places like proguardFile elements where
    // only one argument is supported and for this cases we use addToParsedExpressionList method.
    GradleDslExpressionList literalList =
      new GradleDslExpressionList(this, psiElement, GradleNameElement.create(property), true);
    literalList.setModelEffect(effect);
    literalList.setElementType(REGULAR);
    if (expression instanceof GradleDslMethodCall) {
      // Make sure the psi is set to the argument list instead of the whole method call.
      literalList.setPsiElement(((GradleDslMethodCall)expression).getArgumentListPsiElement());
      for (GradleDslElement element : ((GradleDslMethodCall)expression).getArguments()) {
        if (element instanceof GradleDslSimpleExpression) {
          literalList.addParsedExpression((GradleDslSimpleExpression)element);
        }
      }
    }
    else {
      literalList.addParsedExpression(expression);
    }
    addPropertyInternal(literalList, EXISTING);
  }

  @Nullable
  private static PsiElement mungeElementsForAddToParsedExpressionList(@NotNull GradleDslElement dslElement,
                                                                      @NotNull List<GradleDslElement> newDslElements) {
    PsiElement psiElement = dslElement.getPsiElement();
    if (psiElement == null) {
      return null;
    }

    if (dslElement instanceof GradleDslMethodCall) {
      List<GradleDslExpression> args = ((GradleDslMethodCall)dslElement).getArguments();
      if (!args.isEmpty()) {
        if (args.size() == 1 && args.get(0) instanceof GradleDslExpressionList) {
          newDslElements.addAll(((GradleDslExpressionList)args.get(0)).getExpressions());
          PsiElement newElement = args.get(0).getPsiElement();
          return newElement != null ? newElement : psiElement;
        }
        else {
          newDslElements.addAll(args);
          return psiElement;
        }
      }
      else {
        return psiElement;
      }
    }
    else if (dslElement instanceof GradleDslSimpleExpression) {
      newDslElements.add(dslElement);
      return psiElement;
    }
    else if (dslElement instanceof GradleDslExpressionList) {
      newDslElements.addAll(((GradleDslExpressionList)dslElement).getExpressions());
      return psiElement;
    }
    else {
      return null;
    }
  }

  public void addToParsedExpressionList(@NotNull String property, @NotNull GradleDslElement element) {
    List<GradleDslElement> newElements = new ArrayList<>();
    PsiElement psiElement = mungeElementsForAddToParsedExpressionList(element, newElements);
    if (psiElement == null) {
      return;
    }

    GradleDslExpressionList gradleDslExpressionList = getPropertyElement(property, GradleDslExpressionList.class);
    if (gradleDslExpressionList == null) {
      gradleDslExpressionList = new GradleDslExpressionList(this, psiElement, GradleNameElement.create(property), false);
      addPropertyInternal(gradleDslExpressionList, EXISTING);
    }
    else {
      gradleDslExpressionList.setPsiElement(psiElement);
    }
    newElements.forEach(gradleDslExpressionList::addParsedElement);
  }

  public void addToParsedExpressionList(@NotNull ModelEffectDescription effect, @NotNull GradleDslElement element) {
    List<GradleDslElement> newElements = new ArrayList<>();
    PsiElement psiElement = mungeElementsForAddToParsedExpressionList(element, newElements);
    if (psiElement == null) {
      return;
    }

    GradleDslExpressionList gradleDslExpressionList = getPropertyElement(effect.property, GradleDslExpressionList.class);
    if (gradleDslExpressionList == null) {
      gradleDslExpressionList = new GradleDslExpressionList(this, psiElement, GradleNameElement.create(effect.property.name), false);
      // TODO(xof): rewriting the effect to CREATE_WITH_VALUE seems wrong.  It is necessary because we can end up adding to a parsed list
      //  with several kinds of model semantics (e.g. augmented assignment on a VAL or VAR, AUGMENT_LIST from a method call) and we
      //  must be prepared to rewrite on structural change for all these cases.  We preserve the existing effect's versionConstraint, if
      //  any, so that that too can be used to decide whether to rewrite.
      ModelEffectDescription createEffect = new ModelEffectDescription(effect.property, CREATE_WITH_VALUE, effect.versionConstraint);
      gradleDslExpressionList.setModelEffect(createEffect);
      gradleDslExpressionList.setElementType(REGULAR);
      addPropertyInternal(gradleDslExpressionList, EXISTING);
    }
    else {
      gradleDslExpressionList.setPsiElement(psiElement);
    }
    newElements.forEach(gradleDslExpressionList::addParsedElement);
  }

  public void addToParsedExpressionMap(@NotNull ModelEffectDescription effect, @NotNull GradleDslElement element) {
    element = followElement(element);
    if (!(element instanceof GradleDslExpressionMap)) return;

    GradleDslExpressionMap map = getPropertyElement(effect.property, GradleDslExpressionMap.class);
    if (map == null) {
      map = new GradleDslExpressionMap(this, element.getPsiElement(), GradleNameElement.create(effect.property.name), true);
      ModelEffectDescription createEffect = new ModelEffectDescription(effect.property, CREATE_WITH_VALUE, effect.versionConstraint);
      map.setModelEffect(createEffect);
      map.setElementType(REGULAR);
      addPropertyInternal(map, EXISTING);
    }
    else {
      map.setPsiElement(element.getPsiElement());
      SemanticsDescription semantics = CREATE_WITH_VALUE;
      ModelEffectDescription currentEffect = map.getModelEffect();
      if (currentEffect != null) {
        semantics = currentEffect.semantics;
      }
      ModelEffectDescription newEffect = new ModelEffectDescription(effect.property, semantics, effect.versionConstraint);
      map.setModelEffect(newEffect);
    }

    GradleDslExpressionMap newElements = (GradleDslExpressionMap)element;
    for (Map.Entry<String,GradleDslElement> entry : newElements.getPropertyElements().entrySet()) {
      map.setParsedElement(entry.getValue());
    }
  }

  @NotNull
  private static final ImmutableMap<String,PropertiesElementDescription> NO_CHILD_PROPERTIES_ELEMENTS = ImmutableMap.of();

  /**
   * a helper for the default implementation for getChildPropertiesElementDescription: a common implementation will involve a Dsl element
   * maintaining a String-to-Description map, and looking up the given name.  This works for most blocks, but is not suitable for
   * NamedDomainObject containers, where the child properties can have arbitrary user-supplied names.
   *
   * In principle this map could vary by external Dsl language (Groovy, KotlinScript).  In practice at least at present all properties
   * elements have the same name in both.
   *
   * @return a map of external names to descriptions of the corresponding properties element.
   */
  @NotNull
  protected ImmutableMap<String,PropertiesElementDescription> getChildPropertiesElementsDescriptionMap() {
    return NO_CHILD_PROPERTIES_ELEMENTS;
  }

  /**
   * @param name the external name of a potential block.
   * @return the properties element description corresponding to the given name in the context of this block, or null if no such properties
   * element exists in the Dsl.
   */
  @Nullable
  public PropertiesElementDescription getChildPropertiesElementDescription(String name) {
    return getChildPropertiesElementsDescriptionMap().get(name);
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
  public <T extends GradleDslElement> List<T> getPropertyElements(@NotNull String name, @NotNull Class<T> clazz) {
    return myProperties.getElementsWhere(PROPERTY_FILTER).stream()
                       .filter(e -> clazz.isAssignableFrom(e.getClass()) && e.getName().equals(name))
                       .map(e -> clazz.cast(e)).collect(Collectors.toList());
  }

  /**
   * @return all elements that visible now (effective elements)
   */
  @NotNull
  public List<GradleDslElement> getAllPropertyElements() {
    return myProperties.getElementsWhere(PROPERTY_FILTER);
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
  public List<GradleDslElement> getAllElements() {
    return myProperties.getElementsWhere(ANY_FILTER);
  }

  @NotNull
  private Map<String, GradleDslElement> getElementsWhere(@NotNull Predicate<ElementList.ElementItem> predicate) {
    Map<String, GradleDslElement> results = new LinkedHashMap<>();
    List<GradleDslElement> elements = myProperties.getElementsWhere(predicate);
    for (GradleDslElement element : elements) {
      if (element != null) {
        results.put(element.getName(), element);
      }
    }
    return results;
  }

  private GradleDslElement getElementWhere(@NotNull String name, @NotNull Predicate<ElementList.ElementItem> predicate) {
    return getElementWhere(e -> predicate.test(e) && e.myElement.getName().equals(name));
  }

  private GradleDslElement getElementWhere(@NotNull ModelPropertyDescription property, @NotNull Predicate<ElementList.ElementItem> predicate) {
    return getElementWhere(e -> predicate.test(e) && property.equals(e.myElement.getModelProperty()));
  }

  protected GradleDslElement getElementWhere(@NotNull Predicate<ElementList.ElementItem> predicate) {
    return myProperties.getElementWhere(predicate);
  }

  private GradleDslElement getElementBeforeChildWhere(@NotNull String name,
                                                      Predicate<ElementList.ElementItem> predicate,
                                                      @NotNull GradleDslElement element,
                                                      boolean includeSelf) {
    return getElementBeforeChildWhere(e -> predicate.test(e) && e.myElement.getName().equals(name), element, includeSelf);
  }

  protected GradleDslElement getElementBeforeChildWhere(Predicate<ElementList.ElementItem> predicate,
                                                        @NotNull GradleDslElement element,
                                                        boolean includeSelf) {
    return myProperties.getElementBeforeChildWhere(predicate, element, includeSelf);
  }

  @Nullable
  public GradleDslElement getVariableElement(@NotNull String property) {
    return getElementWhere(property, VARIABLE_FILTER);
  }

  /**
   * Returns the {@link GradleDslElement} corresponding to the given {@code property}, or {@code null} if the given {@code property}
   * does not exist in this element.
   */
  @Nullable
  public GradleDslElement getPropertyElement(@NotNull String property) {
    return getElementWhere(property, PROPERTY_FILTER);
  }

  @Nullable
  public GradleDslElement getPropertyElement(@NotNull ModelPropertyDescription property) {
    return getElementWhere(property, PROPERTY_FILTER);
  }

  @Nullable
  public GradleDslElement getElement(@NotNull String property) {
    return getElementWhere(property, ANY_FILTER);
  }

  @Nullable
  public GradleDslElement getPropertyElementBefore(@Nullable GradleDslElement element, @NotNull String property, boolean includeSelf) {
    if (element == null) {
      return getElementWhere(property, PROPERTY_FILTER);
    }
    else {
      return getElementBeforeChildWhere(property, PROPERTY_FILTER, element, includeSelf);
    }
  }

  @Nullable
  GradleDslElement getElementBefore(@Nullable GradleDslElement element, @NotNull String property, boolean includeSelf) {
    if (element == null) {
      return getElementWhere(property, ANY_FILTER);
    }
    else {
      return getElementBeforeChildWhere(property, ANY_FILTER, element, includeSelf);
    }
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

  @Nullable
  public <T extends GradleDslElement> T getPropertyElement(@NotNull ModelPropertyDescription property, @NotNull Class<T> clazz) {
    GradleDslElement propertyElement = getPropertyElement(property);
    return clazz.isInstance(propertyElement) ? clazz.cast(propertyElement) : null;
  }


  @Nullable
  public <T extends GradlePropertiesDslElement> T getPropertyElement(@NotNull PropertiesElementDescription<T> description) {
    assert description.name != null;
    return getPropertyElement(description.name, description.clazz);
  }

  @NotNull
  public <T extends GradlePropertiesDslElement> T ensurePropertyElement(@NotNull PropertiesElementDescription<T> description) {
    return ensurePropertyElementAt(description, null);
  }

  @NotNull
  public <T extends GradlePropertiesDslElement, U> T ensurePropertyElementBefore(
    @NotNull PropertiesElementDescription<T> description,
    Class<U> before
  ) {
    Integer at = null;
    List<GradleDslElement> elements = getCurrentElements();
    for (int i = 0; i < elements.size(); i++) {
      if (before.isInstance(elements.get(i))) {
        at = i;
        break;
      }
    }
    return ensurePropertyElementAt(description, at);
  }

  @NotNull
  public <T extends GradlePropertiesDslElement> T ensureNamedPropertyElement(
    PropertiesElementDescription<T> description,
    GradleNameElement name
  ) {
    T propertyElement = getPropertyElement(name.name(), description.clazz);
    if (propertyElement != null) return propertyElement;
    T newElement;
    assert description.name == null;
    newElement = description.constructor.construct(this, name);
    setNewElement(newElement);
    return newElement;
  }

  @NotNull
  public <T extends GradlePropertiesDslElement> T ensurePropertyElementAt(PropertiesElementDescription<T> description, Integer at) {
    T propertyElement = getPropertyElement(description);
    if (propertyElement != null) return propertyElement;
    T newElement;
    assert description.name != null;
    newElement = description.constructor.construct(this, GradleNameElement.create(description.name));
    if (at != null) {
      addNewElementAt(at, newElement);
    }
    else {
      setNewElement(newElement);
    }
    return newElement;
  }

  @Nullable
  public <T extends GradleDslElement> T getPropertyElement(@NotNull List<String> properties, @NotNull Class<T> clazz) {
    GradleDslElement propertyElement = myProperties.getElementWhere(e -> properties.contains(e.myElement.getName()));
    return clazz.isInstance(propertyElement) ? clazz.cast(propertyElement) : null;
  }

  @NotNull
  public <T extends GradleDslElement> List<T> getPropertyElements(@NotNull Class<T> clazz) {
    return myProperties.getElementsWhere(PROPERTY_FILTER).stream().filter(e -> clazz.isAssignableFrom(e.getClass())).map(e -> clazz.cast(e))
                       .collect(Collectors.toList());
  }

  @NotNull
  public List<GradleDslElement> getPropertyElementsByName(@NotNull String propertyName) {
    return myProperties.getElementsWhere(e -> e.myElement.getName().equals(propertyName) && PROPERTY_FILTER.test(e));
  }

  /**
   * @return All elements that loaded from file
   */
  @NotNull
  public List<GradleDslElement> getOriginalElements() {
    return myProperties.myElements.stream().filter(e -> e.myExistsOnFile).map(e -> e.myElement).collect(Collectors.toList());
  }

  @Nullable
  public GradleDslElement getOriginalElementForNameAndType(@NotNull String name, @NotNull PropertyType type) {
    return myProperties.myElements.stream().filter(
      e -> e.myElement.getName().equals(name) && e.myExistsOnFile && e.myElement.getElementType() == type).map(e -> e.myElement)
                                  .reduce((a, b) -> b).orElse(null);
  }

  /**
   * @return all the elements which represent the current state of the Dsl object, including modifications.
   */
  @NotNull
  public List<GradleDslElement> getCurrentElements() {
    Predicate<ElementList.ElementItem> currentElementFilter = e ->
      e.myElementState == TO_BE_ADDED ||
      e.myElementState == EXISTING ||
      e.myElementState == MOVED ||
      (e.myElementState == DEFAULT && e.myElement instanceof GradlePropertiesDslElement &&
       !(((GradlePropertiesDslElement)e.myElement).getCurrentElements().isEmpty()));
    return myProperties.myElements.stream().filter(currentElementFilter).map(e -> e.myElement).collect(Collectors.toList());
  }

  /**
   * Adds the given element to the to-be-added elements list, which are applied when {@link #apply()} method is invoked
   * or discarded when the {@link #resetState()} method is invoked.
   */
  public void setNewElement(@NotNull GradleDslElement newElement) {
    newElement.setParent(this);
    addPropertyInternal(newElement, TO_BE_ADDED);
    setModified();
  }

  public void addNewElementAt(int index, @NotNull GradleDslElement newElement) {
    newElement.setParent(this);
    addPropertyInternal(index, newElement, TO_BE_ADDED);
    setModified();
  }

  @VisibleForTesting
  public void moveElementTo(int index, @NotNull GradleDslElement newElement) {
    assert newElement.getParent() == this;
    myProperties.moveElementToIndex(newElement, index);
  }

  public void replaceElement(@NotNull GradleDslElement oldElement, @NotNull GradleDslElement newElement) {
    newElement.setParent(this);
    List<GradlePropertiesDslElement> holders = new ArrayList<>();
    holders.add(this);
    holders.addAll(oldElement.getHolders());
    for (GradlePropertiesDslElement holder : holders) {
      holder.replacePropertyInternal(oldElement, newElement);
    }
  }

  /**
   * This method is for postprocessing purposes, in order to be able to alter the raw results of the parser, for example because
   * of an interpretation imposed on top of the tree-like structure of the Dsl which forces a change of representation.  Implementing
   * language semantics should generally use {@link PropertyTransform} and
   * {@link GradlePropertiesDslElement#replaceElement(GradleDslElement, GradleDslElement)}; this is only used for implementing
   * extra-language semantics.
   *
   * @param oldElement the original Dsl element as parsed by the parser
   * @param newElement the element to replace it with, with its psiElement set up appropriately
   */
  public void substituteElement(@NotNull GradleDslElement oldElement, @NotNull GradleDslElement newElement) {
    assert newElement.getParent() == this;
    myProperties.substituteElement(oldElement, newElement);
  }

  @Nullable
  public <T> T getLiteral(@NotNull String property, @NotNull Class<T> clazz) {
    GradleDslSimpleExpression expression = getPropertyElement(property, GradleDslSimpleExpression.class);
    if (expression == null) {
      return null;
    }

    return expression.getValue(clazz);
  }

  @NotNull
  public GradleDslLiteral setNewLiteral(@NotNull String property, @NotNull Object value) {
    return setNewLiteralImpl(property, value);
  }

  @NotNull
  private GradleDslLiteral setNewLiteralImpl(@NotNull String property, @NotNull Object value) {
    GradleDslLiteral literalElement = getPropertyElement(property, GradleDslLiteral.class);
    if (literalElement == null) {
      literalElement = new GradleDslLiteral(this, GradleNameElement.create(property));
      addPropertyInternal(literalElement, TO_BE_ADDED);
    }
    literalElement.setValue(value);
    return literalElement;
  }

  /**
   * Marks the given {@code property} for removal.
   *
   * <p>The actual property will be removed from Gradle file when {@link #apply()} method is invoked.
   *
   * <p>The property will be un-marked for removal when {@link #reset()} method is invoked.
   */
  public void removeProperty(@NotNull String property) {
    removePropertyInternal(property);
  }

  /**
   * Marks the given {@code element} for removal.
   *
   * Note that it removes the element from all its holders, not just the one this is called on (usually, but not always, its parent).
   *
   * @param element the element to remove.
   */
  public void removeProperty(@NotNull GradleDslElement element) {
    removePropertyInternal(element);
    for (GradlePropertiesDslElement holder : element.getHolders()) {
      if (this != holder) {
        holder.removePropertyInternal(element);
      }
    }
    GradleDslElement parent = element.getParent();
    if (this != parent && parent instanceof GradlePropertiesDslElement) {
      ((GradlePropertiesDslElement)parent).removePropertyInternal(element);
    }
  }

  public void hideProperty(@NotNull GradleDslElement element) {
    hidePropertyInternal(element);
    for (GradlePropertiesDslElement holder : element.getHolders()) {
      if (this != holder) {
        holder.removePropertyInternal(element);
      }
    }
    GradleDslElement parent = element.getParent();
    if (this != parent && parent instanceof GradlePropertiesDslElement) {
      ((GradlePropertiesDslElement)parent).hidePropertyInternal(element);
    }
  }

  @Override
  @Nullable
  public GradleDslElement requestAnchor(@NotNull GradleDslElement element) {
    // We need to find the element before `element` in my properties. The last one that has a psiElement, has the same name scheme as
    // the given element (to ensure that they should be placed in the same block) and must have a state of EXISTING, TO_BE_ADDED or MOVED.
    GradleDslElement lastElement = null;
    for (ElementList.ElementItem item : myProperties.myElements) {
      if (item.myElement == element) {
        return lastElement;
      }

      if (item.myElementState.isPhysicalInFile()) {
        GradleDslElement currentElement = item.myElement;
        // Don't count empty ProjectPropertiesModel, this can cause the properties to be added at the top of the file where
        // we require that they be below other properties (e.g project(':lib')... should be after include: 'lib').
        if (currentElement instanceof ProjectPropertiesDslElement &&
            ((ProjectPropertiesDslElement)currentElement).getAllPropertyElements().isEmpty()) {
          continue;
        }
        // this reflects the fact that an ApplyDslElement may contain more than one block or statement, and that (for safety) all
        // properties added should go after the last apply (in case it applies a semantically-important plugin, or includes a file
        // with relevant properties.
        // TODO(xof): there should be something similar for ExtDslElement in KotlinScript
        if (item.myElement instanceof ApplyDslElement) {
          lastElement = item.myElement.requestAnchor(element);
        }
        else {
          lastElement = item.myElement;
        }
      }
    }

    // The element is not in this list, we can't provide an anchor. Default to adding it at the end.
    return lastElement;
  }

  @Override
  @NotNull
  public Collection<GradleDslElement> getChildren() {
    return getAllElements();
  }

  @Override
  @NotNull
  public List<GradleDslElement> getContainedElements(boolean includeProperties) {
    List<GradleDslElement> result = new ArrayList<>();
    if (includeProperties) {
      result.addAll(getElementsWhere(e -> (e.myElementState != APPLIED && !e.isDefaultElement())).values());
    }
    else {
      result.addAll(getVariableElements().values());
    }

    // We don't want to include lists and maps in this.
    List<GradlePropertiesDslElement> holders =
      getPropertyElements(GradlePropertiesDslElement.class).stream().filter(e -> !(e instanceof GradleDslExpression))
                                                           .collect(Collectors.toList());

    holders.forEach(e -> result.addAll(e.getContainedElements(includeProperties)));
    return result;
  }

  @Override
  public void delete() {
    myProperties.forEach(e -> e.myElement.delete());
    super.delete();
  }

  protected void deleteAndRecreate() {
    myProperties.removeElements(GradleDslElement::delete);
    if (!myProperties.isEmpty()) {
      myProperties.forEach(item -> {
        GradleDslElement element = item.myElement;
        GradleDslElement copy = element;
        if (element instanceof GradleDslExpression) {
          copy = ((GradleDslExpression)element).copy();
        }
        element.getDslFile().getWriter().deleteDslElement(element);
        item.myElement = copy;
      });
      getDslFile().getWriter().deleteDslElement(this);
      this.create();
      myProperties.forEach(item -> {
        GradleDslElement element = item.myElement;
        element.create();
        item.myElementState = EXISTING;
      });
    }
  }

  protected boolean isStructurallyModified() {
    Predicate<ElementList.ElementItem> predicate = e -> e.myElementState.isStructuralChange();
    return myProperties.myElements.stream().anyMatch(predicate);
  }

  @Override
  protected void apply() {
    getDslFile().getWriter().applyDslPropertiesElement(this);
    myProperties.removeElements(GradleDslElement::delete);
    myProperties.createElements((e) -> e.create() != null);
    myProperties.applyElements(e -> {
      if (e.isModified()) {
        e.applyChanges();
      }
    });
    myProperties.forEach(item -> {
      if (item.myElementState == MOVED) {
        item.myElement.move();
      }
    });
  }

  @Override
  protected void reset() {
    myProperties.reset();
  }

  protected void clear() {
    myProperties.clear();
  }

  public int reorderAndMaybeGetNewIndex(@NotNull GradleDslElement element) {
    int result = sortElementsAndMaybeGetNewIndex(element);
    element.resolve();
    return result;
  }

  private int sortElementsAndMaybeGetNewIndex(@NotNull GradleDslElement element) {
    List<GradleDslElement> currentElements =
      myProperties.getElementsWhere(e -> e.myElementState == EXISTING || e.myElementState == TO_BE_ADDED);
    List<GradleDslElement> sortedElements = new ArrayList<>();
    boolean result = ElementSort.create(this, element).sort(currentElements, sortedElements);
    int resultIndex = myProperties.myElements.size();

    if (!result) {
      notification(PROPERTY_PLACEMENT);
      return resultIndex;
    }

    int i = 0, j = 0;
    while (i < currentElements.size() && j < sortedElements.size()) {
      if (currentElements.get(i) == sortedElements.get(i)) {
        i++;
        j++;
        continue;
      }

      if (sortedElements.get(i) == element && !currentElements.contains(element)) {
        resultIndex = i;
        j++;
        continue;
      }

      // Move the element into the correct position.
      moveElementTo(i, sortedElements.get(j));
      i++;
      j++;
    }

    return resultIndex;
  }

  @Override
  @NotNull
  public List<GradleReferenceInjection> getDependencies() {
    return myProperties.getElementsWhere(e -> e.myElementState != APPLIED).stream().map(GradleDslElement::getDependencies)
                       .flatMap(Collection::stream).collect(
        Collectors.toList());
  }

  @VisibleForTesting
  public boolean isApplied(@NotNull GradleDslElement element) {
    for (ElementList.ElementItem item : myProperties.myElements) {
      if (item.myElement == element) {
        return item.myElementState == APPLIED;
      }
    }
    // The element must be found.
    throw new IllegalStateException("Element not found in parent"); // OK to throw: test-only
  }

  void updateAppliedState(@NotNull GradleDslElement element) {
    for (ElementList.ElementItem item : myProperties.myElements) {
      if (item.myElement == element) {
        if (item.myElementState == APPLIED) item.myElementState = TO_BE_ADDED;
        return;
      }
    }
    LOG.warn(new IllegalStateException("Element " + element + " not found in parent " + this));
  }
  /**
   * Class to deal with retrieving the correct property for a given context. It manages whether
   * or not variable types should be returned along with coordinating a number of properties
   * with the same name.
   */
  protected static class ElementList {
    /**
     * Wrapper to add state to each element.
     */
    public static class ElementItem {
      @NotNull private GradleDslElement myElement;
      @NotNull private ElementState myElementState;
      // Whether or not this element item exists in THIS DSL file. While element state == EXISTING implies this is true,
      // the reverse doesn't apply.
      private boolean myExistsOnFile;

      private ElementItem(@NotNull GradleDslElement element, @NotNull ElementState state, boolean existsOnFile) {
        myElement = element;
        myElementState = state;
        myExistsOnFile = existsOnFile;
      }
      private boolean isDefaultElement() {
        return myElementState == DEFAULT &&
               myElement.isInsignificantIfEmpty() &&
               myElement instanceof GradlePropertiesDslElement &&
               (((GradlePropertiesDslElement)myElement).getCurrentElements().isEmpty());

      }
    }

    @NotNull private final List<ElementItem> myElements;

    public ElementList() {
      myElements = new ArrayList<>();
    }

    private void forEach(@NotNull Consumer<ElementItem> func) {
      myElements.forEach(func);
    }

    @NotNull
    private List<GradleDslElement> getElementsWhere(@NotNull Predicate<ElementItem> predicate) {
      return myElements.stream().filter(e -> e.myElementState.isSemanticallyRelevant())
                       .filter(predicate).map(e -> e.myElement).collect(Collectors.toList());
    }

    @Nullable
    public GradleDslElement getElementWhere(@NotNull Predicate<ElementItem> predicate) {
      // We reduce to get the last element stored, this will be the one we want as it was added last and therefore must appear
      // later on in the file.
      GradleDslElement last = myElements.stream()
        .filter(e -> e.myElementState.isSemanticallyRelevant())
        .filter(predicate).map(e -> e.myElement).reduce((first, second) -> second).orElse(null);
      if (last != null) {
        ModelEffectDescription effect = last.getModelEffect();
        if (effect != null && effect.semantics == RESET) return null;
      }
      return last;
    }

    /**
     * Return the last element satisfying {@code predicate} that is BEFORE {@code child}. If {@code child} is not a child of
     * this {@link GradlePropertiesDslElement} then every element is checked and the last one (if any) returned.
     */
    @Nullable
    public GradleDslElement getElementBeforeChildWhere(@NotNull Predicate<ElementItem> predicate,
                                                       @NotNull GradleDslElement child,
                                                       boolean includeSelf) {
      // TODO(b/181308123): this is used primarily (maybe exclusively?) for resolution: when the rvalue of a property refers to
      //  another property in the same scope, we must only account for changes to that property before this.  Direct assignment
      //  is handled by having that insert a new element at that point; however, mutations to collections (from AUGMENT_LIST/OTHER
      //  methods or, if we implement .clear(), RESET) are not correctly modelled.
      GradleDslElement lastElement = null;
      for (ElementItem i : myElements) {
        // Skip removed or hidden elements.
        if (!i.myElementState.isSemanticallyRelevant()) {
          continue;
        }

        if (predicate.test(i)) {
          if (includeSelf || child != i.myElement) {
            lastElement = i.myElement;
          }
        }

        if (i.myElement == child) {
          return lastElement;
        }
      }
      return lastElement;
    }

    public void addElement(@NotNull GradleDslElement newElement, @NotNull ElementState state, boolean onFile) {
      myElements.add(new ElementItem(newElement, state, onFile));
    }

    private void addElementAtIndex(@NotNull GradleDslElement newElement, @NotNull ElementState state, int index, boolean onFile) {
      myElements.add(getRealIndex(index, newElement), new ElementItem(newElement, state, onFile));
    }

    // Note: The index position is calculated AFTER the element has been removed from the list.
    private void moveElementToIndex(@NotNull GradleDslElement element, int index) {
      // Find the element.
      ElementItem item = myElements.stream().filter(e -> e.myElement == element).findFirst().orElse(null);
      if (item == null) {
        return;
      }

      // Remove the element.
      myElements.remove(item);
      // Set every EXISTING element in this tree to MOVED.
      moveElementTree(item);
      // Add the element back at the given index.
      myElements.add(getRealIndex(index, element), item);
    }

    /**
     * Converts a given index to a real index that can correctly place elements in myElements. This ignores all elements that should be
     * removed or have been applied.
     */
    private int getRealIndex(int index, @NotNull GradleDslElement element) {
      // If the index is less than zero then clamp it to zero
      if (index <= 0) {
        return 0;
      }

      // Work out the real index
      for (int i = 0; i < myElements.size(); i++) {
        if (index == 0) {
          return i;
        }
        ElementItem item = myElements.get(i);
        if (item.myElementState.isPhysicalInFile()) {
          index--;
        }
      }
      return myElements.size();
    }

    @Nullable
    private ElementState remove(@NotNull GradleDslElement element) {
      ElementItem item = myElements.stream().filter(e -> element == e.myElement).findFirst().orElse(null);
      if (item == null) {
        return null;
      }
      ElementState oldState = item.myElementState;
      item.myElementState = TO_BE_REMOVED;
      return oldState;
    }

    private @Nullable ElementState hide(@NotNull GradleDslElement element) {
      ElementItem item = myElements.stream().filter(e -> element == e.myElement).findFirst().orElse(null);
      if (item == null) {
        return null;
      }
      ElementState oldState = item.myElementState;
      item.myElementState = HIDDEN;
      return oldState;
    }

    @Nullable
    private ElementState replaceElement(@Nullable GradleDslElement oldElement, @NotNull GradleDslElement newElement) {
      for (int i = 0; i < myElements.size(); i++) {
        ElementItem item = myElements.get(i);
        if (oldElement == item.myElement) {
          ElementState oldState = item.myElementState;
          item.myElementState = TO_BE_REMOVED;
          ElementState newState = TO_BE_ADDED;
          if (Arrays.asList(APPLIED, HIDDEN).contains(oldState)) {
            newState = oldState;
          }
          myElements.add(i, new ElementItem(newElement, newState, false));
          return oldState;
        }
      }
      return null;
    }

    @Nullable
    private ElementState substituteElement(@Nullable GradleDslElement oldElement, @NotNull GradleDslElement newElement) {
      for (int i = 0; i < myElements.size(); i++) {
        ElementItem item = myElements.get(i);
        if (oldElement == item.myElement) {
          item.myElement = newElement;
          if (newElement.getPsiElement() == null) {
            item.myElementState = TO_BE_ADDED;
            item.myExistsOnFile = false;
          } else {
            item.myElementState = EXISTING;
            item.myExistsOnFile = true;
          }
          return item.myElementState;
        }
      }
      return null;
    }

    @NotNull
    private List<GradleDslElement> removeAll(@NotNull Predicate<ElementItem> filter) {
      List<ElementItem> toBeRemoved = myElements.stream().filter(filter).collect(Collectors.toList());
      toBeRemoved.forEach(e -> e.myElementState = TO_BE_REMOVED);
      return ContainerUtil.map(toBeRemoved, e -> e.myElement);
    }

    private boolean isEmpty() {
      return myElements.isEmpty();
    }

    private void reset() {
      Set<String> seen = new LinkedHashSet<>();
      for (Iterator<ElementItem> i = myElements.iterator(); i.hasNext(); ) {
        ElementItem item = i.next();
        item.myElement.resetState();
        if (item.myElementState == TO_BE_REMOVED) {
          item.myElementState = EXISTING;
        }
        if (item.myElementState == EXISTING) {
          seen.add(item.myElement.getName());
        }
        if (item.myElementState == TO_BE_ADDED) {
          i.remove();
        }
        if (item.myElementState == DEFAULT && seen.contains(item.myElement.getName())) {
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
        if (item.myElementState == TO_BE_REMOVED) {
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
        if (item.myElementState == DEFAULT && !item.isDefaultElement()) {
          item.myElementState = TO_BE_ADDED;
        }
        if (item.myElementState == TO_BE_ADDED) {
          if (addFunc.test(item.myElement)) {
            item.myElementState = EXISTING;
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
      myElements.stream().filter(e -> e.myElementState != APPLIED).map(e -> e.myElement).forEach(func);
    }

    /**
     * Clears ALL element in this element list. This clears the whole list without affecting state. If you actually want to remove
     * elements from the file use {@link #removeAll(Predicate)}.
     */
    private void clear() {
      myElements.clear();
    }

    /**
     * Moves the element tree represented by item.
     *
     * @param item root of the tree to be moved
     */
    private static void moveElementTree(@NotNull ElementItem item) {
      // Move the current element item, unless it is not on file yet.
      if (item.myElementState != TO_BE_ADDED) {
        item.myElementState = MOVED;
      }
      // Mark it as modified.
      item.myElement.setModified();
    }
  }
}
