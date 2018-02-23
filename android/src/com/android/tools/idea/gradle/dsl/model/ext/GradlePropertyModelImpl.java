// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.TypeReference;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.PropertyTransform;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.*;

public class GradlePropertyModelImpl implements GradlePropertyModel {
  @Nullable protected GradleDslElement myElement;
  @NotNull private GradleDslElement myPropertyHolder;
  // Indicates whether this property represents a method call or an assignment. This is needed to remove the braces when creating
  // properties for example "android.defaultConfig.proguardFiles" requires "proguardFiles "file.txt", "file.pro"" whereas
  // assignments require "prop = ["file.txt", "file.pro"]". If the method syntax is required #markAsMethodCall should be used.
  private boolean myIsMethodCall;

  // The list of transforms to be checked for this property model. Only the first transform that has its PropertyTransform#condition
  // return true will be used.
  @NotNull
  private List<PropertyTransform> myTransforms = new ArrayList<>();

  // The following properties should always be kept up to date with the values given by myElement.getElementType() and myElement.getName().
  @NotNull private final PropertyType myPropertyType;
  @NotNull protected String myName;

  public GradlePropertyModelImpl(@NotNull GradleDslElement element) {
    myElement = element;
    myTransforms.add(DEFAULT_TRANSFORM);

    GradleDslElement parent = element.getParent();
    assert parent != null &&
           (parent instanceof GradlePropertiesDslElement ||
            parent instanceof GradleDslExpressionList ||
            parent instanceof GradleDslElementList) : "Property found to be invalid, this should never happen!";
    myPropertyHolder = parent;

    myPropertyType = myElement.getElementType();
    myName = myElement.getName();

    myIsMethodCall = false;
  }

  // Used to create an empty property with no backing element.
  public GradlePropertyModelImpl(@NotNull GradleDslElement element, @NotNull PropertyType type, @NotNull String name) {
    myPropertyHolder = element;
    myPropertyType = type;
    myName = name;
    myTransforms.add(DEFAULT_TRANSFORM);

    myIsMethodCall = false;
  }

  public void markAsMethodCall() {
    myIsMethodCall = true;
  }

  public void addTransform(@NotNull PropertyTransform transform) {
    myTransforms.add(0, transform);
  }

  @Override
  @NotNull
  public ValueType getValueType() {
    return extractAndGetValueType(getElement());
  }

  @Override
  @NotNull
  public PropertyType getPropertyType() {
    return myPropertyType;
  }

  @Override
  @Nullable
  public <T> T getValue(@NotNull TypeReference<T> typeReference) {
    return extractValue(typeReference, true);
  }

  @Override
  public <T> T getRawValue(@NotNull TypeReference<T> typeReference) {
    return extractValue(typeReference, false);
  }

  @Nullable
  private GradleDslElement maybeGetInnerReferenceModel() {
    if (extractAndGetValueType(getElement()) == LIST && myElement instanceof GradleDslExpressionList) {
      GradleDslExpressionList list = (GradleDslExpressionList)myElement;
      if (list.getExpressions().size() == 1) {
        GradleDslExpression expression = list.getElementAt(0);
        if (expression instanceof GradleDslReference) {
          GradleDslReference reference = (GradleDslReference)expression;
          GradleReferenceInjection injection = reference.getReferenceInjection();
          if (injection != null) {
            return injection.getToBeInjected();
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private Map<String, GradlePropertyModel> getMap(boolean resolved) {
    GradleDslExpressionMap map;
    GradleDslElement innerElement = maybeGetInnerReferenceModel();
    if (resolved && innerElement instanceof GradleDslExpressionMap) {
      map = (GradleDslExpressionMap)innerElement;
    }
    else if (extractAndGetValueType(getElement()) != MAP || !(myElement instanceof GradleDslExpressionMap)) {
      return ImmutableMap.of();
    }
    else {
      map = (GradleDslExpressionMap)myElement;
    }

    // If we have a single reference it will be parsed as a list with one element.
    // we need to make sure that this actually gets resolved to the correct map.


    return map.getPropertyElements().entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, e -> new GradlePropertyModelImpl(e.getValue())));
  }

  @NotNull
  private List<GradlePropertyModel> getList(boolean resolved) {
    if (extractAndGetValueType(getElement()) != LIST || !(myElement instanceof GradleDslExpressionList)) {
      return ImmutableList.of();
    }

    GradleDslExpressionList list = (GradleDslExpressionList)myElement;
    // If the list contains a single reference, that is also to a list. Follow it and return the
    // resulting list. Only do this if the resolved value is requested.
    if (resolved) {
      GradleDslElement innerElement = maybeGetInnerReferenceModel();
      if (innerElement instanceof GradleDslExpressionList) {
        list = (GradleDslExpressionList)innerElement;
      }
    }

    return list.getExpressions().stream().map(e -> new GradlePropertyModelImpl(e)).collect(Collectors.toList());
  }

  @Override
  @NotNull
  public String getName() {
    if (myElement != null && myPropertyHolder instanceof GradleDslExpressionList) {
      GradleDslExpressionList list = (GradleDslExpressionList)myPropertyHolder;
      return String.valueOf(list.findIndexOf(myElement));
    }

    return myName;
  }

  @Override
  @NotNull
  public List<GradlePropertyModel> getDependencies() {
    if (myElement == null) {
      return Collections.emptyList();
    }

    return myElement.getResolvedVariables().stream()
      .map(injection -> new GradlePropertyModelImpl(injection.getToBeInjected())).collect(
        Collectors.toList());
  }

  @Override
  @NotNull
  public String getFullyQualifiedName() {
    if (myElement != null && myPropertyHolder instanceof GradleDslExpressionList) {
      GradleDslExpressionList list = (GradleDslExpressionList)myPropertyHolder;
      return myPropertyHolder.getQualifiedName() + "[" + String.valueOf(list.findIndexOf(myElement)) + "]";
    }

    return myPropertyHolder.getQualifiedName() + "." + getName();
  }

  @Override
  @NotNull
  public VirtualFile getGradleFile() {
    return myPropertyHolder.getDslFile().getFile();
  }

  @Override
  public void setValue(@NotNull Object value) {
    GradleDslElement newElement = getTransform().bind(myPropertyHolder, myElement, value, myName);
    bindToNewElement(newElement);
  }

  @Override
  @NotNull
  public GradlePropertyModel convertToEmptyMap() {
    makeEmptyMap();
    return this;
  }

  @Override
  @NotNull
  public GradlePropertyModel getMapValue(@NotNull String key) {
    ValueType valueType = getValueType();
    if (valueType != MAP && valueType != NONE) {
      throw new IllegalStateException("Can't add map value to type: " + valueType + ". " +
                                      "Please call GradlePropertyModel#convertToMap before trying to add values");
    }

    if (valueType == NONE) {
      makeEmptyMap();
    }

    assert myElement instanceof GradleDslExpressionMap;

    // Does the element already exist?
    GradleDslExpressionMap map = (GradleDslExpressionMap)myElement;
    GradleDslElement element = map.getPropertyElement(key);

    return element == null ? new GradlePropertyModelImpl(myElement, PropertyType.DERIVED, key) : new GradlePropertyModelImpl(element);
  }

  @Override
  @NotNull
  public GradlePropertyModel convertToEmptyList() {
    makeEmptyList();
    return this;
  }

  @Override
  @NotNull
  public GradlePropertyModel addListValue() {
    ValueType valueType = getValueType();
    if (valueType != LIST && valueType != NONE) {
      throw new IllegalStateException("Can't add list value to type: " + valueType + ". " +
                                      "Please call GradlePropertyModel#convertToList before trying to add values");
    }

    if (valueType == NONE) {
      makeEmptyList();
    }

    assert myElement instanceof GradleDslExpressionList;

    return addListValueAt(((GradleDslExpressionList)myElement).getExpressions().size());
  }

  @Override
  @NotNull
  public GradlePropertyModel addListValueAt(int index) {
    ValueType valueType = getValueType();
    if (valueType != LIST && valueType != NONE) {
      throw new IllegalStateException("Can't add list value to type: " + valueType + ". " +
                                      "Please call GradlePropertyModel#convertToList before trying to add values");
    }

    if (valueType == NONE) {
      makeEmptyList();
    }

    assert myElement instanceof GradleDslExpressionList;

    // Unlike maps, we don't create a placeholder element. This is since we need to retain and update order in the list.
    // This would be hard to create an intuitive api to do this, so instead we always create an empty string as the new item.
    GradleDslLiteral literal = new GradleDslLiteral(myElement, GradleNameElement.fake("listItem"));
    literal.setValue("");

    GradleDslExpressionList list = (GradleDslExpressionList)myElement;
    list.addNewExpression(literal, index);

    return new GradlePropertyModelImpl(literal);
  }

  @Override
  @Nullable
  public GradlePropertyModel getListValue(@NotNull Object value) {
    ValueType valueType = getValueType();
    if (valueType != LIST && valueType != NONE) {
      throw new IllegalStateException("Can't get list value on type: " + valueType + ". " +
                                      "Please call GradlePropertyModel#convertToList before trying to get values");
    }

    List<GradlePropertyModel> list = getValue(LIST_TYPE);
    if (list == null) {
      return null;
    }
    return list.stream().filter(e -> {
      Object v = e.getValue(OBJECT_TYPE);
      return v != null && v.equals(value);
    }).findFirst().orElseGet(null);
  }

  @Override
  public void delete() {
    removeElement(myElement);
    myElement = null;
  }

  @Override
  @NotNull
  public ResolvedPropertyModel resolve() {
    return new ResolvedPropertyModelImpl(this);
  }

  @NotNull
  @Override
  public GradlePropertyModel getUnresolvedModel() {
    return this;
  }

  @Nullable
  @Override
  public PsiElement getPsiElement() {
    if (myElement == null) {
      return null;
    }
    return myElement.getPsiElement();
  }

  @Override
  public void rename(@NotNull String name) {
    // If we have no backing element then just alter the name that we will change.
    if (myElement == null) {
      myName = name;
      return;
    }

    // Check that the element should actually be renamed.
    if (myPropertyHolder instanceof GradleDslExpressionList) {
      throw new IllegalStateException("Can't rename list values!");
    }

    myElement.rename(name);
  }

  @Override
  public String toString() {
    return getValue(STRING_TYPE);
  }

  @Nullable
  @Override
  public Integer toInt() {
    return getValue(INTEGER_TYPE);
  }

  @Nullable
  @Override
  public BigDecimal toBigDecimal() {
    return getValue(BIG_DECIMAL_TYPE);
  }

  @Nullable
  @Override
  public Boolean toBoolean() {
    return getValue(BOOLEAN_TYPE);
  }

  @Nullable
  @Override
  public List<GradlePropertyModel> toList() {
    return getValue(LIST_TYPE);
  }

  @Nullable
  @Override
  public Map<String, GradlePropertyModel> toMap() {
    return getValue(MAP_TYPE);
  }

  private static ValueType extractAndGetValueType(@Nullable GradleDslElement element) {
    if (element == null) {
      return NONE;
    }

    if (element instanceof GradleDslExpressionMap) {
      return MAP;
    }
    else if (element instanceof GradleDslExpressionList) {
      return LIST;
    }
    else if (element instanceof GradleDslReference) {
      return REFERENCE;
    }
    else if ((element instanceof GradleDslMethodCall &&
              (element.shouldUseAssignment() || element.getElementType() == PropertyType.DERIVED)) ||
             element instanceof GradleDslUnknownElement) {
      // This check ensures that methods we care about, i.e targetSdkVersion(12) are not classed as unknown.
      return UNKNOWN;
    }
    else if (element instanceof GradleDslExpression) {
      GradleDslExpression expression = (GradleDslExpression)element;
      Object value = expression.getValue();
      if (value instanceof Boolean) {
        return BOOLEAN;
      }
      else if (value instanceof Integer) {
        return INTEGER;
      }
      else if (value instanceof String) {
        return STRING;
      }
      else if (value instanceof BigDecimal) {
        return BIG_DECIMAL;
      }
      else {
        return UNKNOWN;
      }
    }
    else {
      // We should not be trying to create properties based of other elements.
      throw new IllegalArgumentException("Can't create property model from given GradleDslElement: " + element);
    }
  }

  @Nullable
  private <T> T extractValue(@NotNull TypeReference<T> typeReference, boolean resolved) {
    GradleDslElement element = getElement();
    // If we don't have an element, no value have yet been set.
    if (element == null) {
      return null;
    }

    ValueType valueType = getValueType();
    Object value;
    if (valueType == MAP) {
      value = getMap(resolved);
    }
    else if (valueType == LIST) {
      value = getList(resolved);
    }
    else if (valueType == REFERENCE) {
      // For references only display the reference text for both resolved and unresolved values.
      // Users should follow the reference to obtain the value.
      GradleDslReference ref = (GradleDslReference)element;
      String refText = ref.getReferenceText();
      value = refText == null ? null : typeReference.castTo(refText);
    }
    else if (valueType == UNKNOWN) {
      if (element.getPsiElement() == null) {
        return null;
      }
      value = element.getPsiElement().getText();
    }
    else {
      GradleDslExpression expression = (GradleDslExpression)element;

      value = resolved ? expression.getValue() : expression.getUnresolvedValue();
    }

    if (value == null) {
      return null;
    }

    T result = typeReference.castTo(value);
    // Attempt to cast to a string if requested. But only do this for unresolved values,
    // or when my type is BOOLEAN, STRING or INTEGER.
    if (result == null && typeReference.getType().equals(String.class)) {
      result = typeReference.castTo(value.toString());
    }

    return result;
  }

  private void makeEmptyMap() {
    GradleNameElement nameElement = GradleNameElement.create(myName);
    bindToNewElement(new GradleDslExpressionMap(myPropertyHolder, nameElement, !myIsMethodCall));
  }

  private void makeEmptyList() {
    GradleNameElement nameElement = GradleNameElement.create(myName);
    bindToNewElement(new GradleDslExpressionList(myPropertyHolder, nameElement, !myIsMethodCall));
  }

  private void bindToNewElement(@NotNull GradleDslElement newElement) {
    if (newElement == myElement) {
      // No need to bind
      return;
    }

    replaceElement(myPropertyHolder, myElement, newElement, myName);
    newElement.setElementType(myPropertyType);
    newElement.setUseAssignment(!myIsMethodCall);
    myElement = newElement;
  }

  @Nullable
  private GradleDslElement getElement() {
    if (myElement == null) {
      return null;
    }
    return getTransform().transform(myElement);
  }

  @NotNull
  protected PropertyTransform getTransform() {
    for (PropertyTransform transform : myTransforms) {
      if (transform.test(myElement)) {
        return transform;
      }
    }
    throw new IllegalStateException("No transforms found for this property model!");
  }
}
