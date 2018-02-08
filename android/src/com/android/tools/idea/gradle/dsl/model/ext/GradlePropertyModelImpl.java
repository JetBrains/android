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
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.TypeReference;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*;

public class GradlePropertyModelImpl implements GradlePropertyModel {
  @NotNull private ValueType myValueType;
  @Nullable private GradleDslElement myElement;
  @NotNull private GradleDslElement myPropertyHolder;
  // Indicates whether this property represents a method call or an assignment. This is needed to remove the braces when creating
  // properties for example "android.defaultConfig.proguardFiles" requires "proguardFiles "file.txt", "file.pro"" whereas
  // assignments require "prop = ["file.txt", "file.pro"]". If the method syntax is required #markAsMethodCall should be used.
  private boolean myIsMethodCall;

  // The following properties should always be kept up to date with the values given by myElement.getElementType() and myElement.getName().
  @NotNull private final PropertyType myPropertyType;
  @NotNull private String myName;

  public GradlePropertyModelImpl(@NotNull GradleDslElement element) {
    myElement = element;

    GradleDslElement parent = element.getParent();
    assert parent != null &&
           (parent instanceof GradlePropertiesDslElement ||
            parent instanceof GradleDslExpressionList ||
            parent instanceof GradleDslElementList) : "Property found to be invalid, this should never happen!";
    myPropertyHolder = parent;

    myPropertyType = myElement.getElementType();
    myName = myElement.getName();

    myValueType = extractAndGetValueType(myElement);
    myIsMethodCall = false;
  }

  // Used to create an empty property with no backing element.
  public GradlePropertyModelImpl(@NotNull GradleDslElement element, @NotNull PropertyType type, @NotNull String name) {
    myPropertyHolder = element;
    myPropertyType = type;
    myName = name;

    myValueType = NONE;
    myIsMethodCall = false;
  }

  public void markAsMethodCall() {
    myIsMethodCall = true;
  }

  @Override
  @NotNull
  public ValueType getValueType() {
    return myValueType;
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
    if (myValueType == LIST && myElement instanceof GradleDslExpressionList) {
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
    else if (myValueType != MAP || !(myElement instanceof GradleDslExpressionMap)) {
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
    if (myValueType != LIST || !(myElement instanceof GradleDslExpressionList)) {
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
    else if (myElement != null && myPropertyHolder instanceof GradleDslElementList) {
      // Elements contained within a GradleDslElementList should not have their own names.
      return myPropertyHolder.getQualifiedName();
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
    boolean isReference = value instanceof ReferenceTo;

    // Check if we can reuse the element.
    if (!isReference && myElement instanceof GradleDslLiteral ||
        isReference && myElement instanceof GradleDslReference) {
      GradleDslExpression expression = (GradleDslExpression)myElement;
      expression.setValue(value);
      // Set the value type for the new value.
      myValueType = extractAndGetValueType(myElement);
    }
    else {
      GradleDslExpression newElement;
      if (!isReference) {
        newElement = new GradleDslLiteral(myPropertyHolder, myName);
      }
      else {
        newElement = new GradleDslReference(myPropertyHolder, myName);
      }
      newElement.setValue(value);
      bindToNewElement(newElement);
    }
  }

  @Override
  @NotNull
  public GradlePropertyModel convertToEmptyMap() {
    makeEmptyMap();
    return this;
  }

  @Override
  public GradlePropertyModel getMapValue(@NotNull String key) {
    if (myValueType != MAP && myValueType != NONE) {
      throw new IllegalStateException("Can't add map value to type: " + myValueType + ". " +
                                      "Please call GradlePropertyModel#convertToMap before trying to add values");
    }

    if (myValueType == NONE) {
      makeEmptyMap();
    }

    assert myElement instanceof GradleDslExpressionMap;

    // Does the element already exist?
    GradleDslExpressionMap map = (GradleDslExpressionMap)myElement;
    GradleDslElement element = map.getPropertyElement(key);

    return element == null ? new GradlePropertyModelImpl(myElement, PropertyType.DERIVED, key) : new GradlePropertyModelImpl(element);
  }

  @Override
  public GradlePropertyModel convertToEmptyList() {
    makeEmptyList();
    return this;
  }

  @Override
  public GradlePropertyModel addListValue() {
    if (myValueType != LIST && myValueType != NONE) {
      throw new IllegalStateException("Can't add list value to type: " + myValueType + ". " +
                                      "Please call GradlePropertyModel#convertToList before trying to add values");
    }

    if (myValueType == NONE) {
      makeEmptyList();
    }

    assert myElement instanceof GradleDslExpressionList;

    return addListValueAt(((GradleDslExpressionList)myElement).getExpressions().size());
  }

  @Override
  public GradlePropertyModel addListValueAt(int index) {
    if (myValueType != LIST && myValueType != NONE) {
      throw new IllegalStateException("Can't add list value to type: " + myValueType + ". " +
                                      "Please call GradlePropertyModel#convertToList before trying to add values");
    }

    if (myValueType == NONE) {
      makeEmptyList();
    }

    assert myElement instanceof GradleDslExpressionList;

    // Unlike maps, we don't create a placeholder element. This is since we need to retain and update order in the list.
    // This would be hard to create an intuitive api to do this, so instead we always create an empty string as the new item.
    GradleDslLiteral literal = new GradleDslLiteral(myElement, "listItem");
    literal.setValue("");

    GradleDslExpressionList list = (GradleDslExpressionList)myElement;
    list.addNewExpression(literal, index);

    return new GradlePropertyModelImpl(literal);
  }

  @Override
  public void delete() {
    deleteInternal();
  }

  @Override
  public ResolvedPropertyModel resolve() {
    return new ResolvedPropertyModelImpl(this);
  }

  @Nullable
  @Override
  public PsiElement getPsiElement() {
    if (myElement == null) {
      return null;
    }
    return myElement.getPsiElement();
  }

  @Nullable
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

  private static ValueType extractAndGetValueType(@NotNull GradleDslElement element) {
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
    // If we don't have an element, no value have yet been set.
    if (myElement == null) {
      return null;
    }

    Object value;
    if (myValueType == MAP) {
      value = getMap(resolved);
    }
    else if (myValueType == LIST) {
      value = getList(resolved);
    }
    else if (myValueType == REFERENCE) {
      // For references only display the reference text for both resolved and unresolved values.
      // Users should follow the reference to obtain the value.
      GradleDslReference ref = (GradleDslReference)myElement;
      String refText = ref.getReferenceText();
      value = refText == null ? null : typeReference.castTo(refText);
    }
    else if (myValueType == UNKNOWN) {
      if (myElement.getPsiElement() == null) {
        return null;
      }
      value = myElement.getPsiElement().getText();
    } else {
      GradleDslExpression expression = (GradleDslExpression)myElement;

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
    bindToNewElement(new GradleDslExpressionMap(myPropertyHolder, myName, !myIsMethodCall));
  }

  private void makeEmptyList() {
    bindToNewElement(new GradleDslExpressionList(myPropertyHolder, myName, !myIsMethodCall));
  }

  private void bindToNewElement(@NotNull GradleDslElement element) {
    if (myPropertyHolder instanceof GradlePropertiesDslElement) {
      if (myElement != null) {
        myElement = ((GradlePropertiesDslElement)myPropertyHolder).replaceElement(myName, myElement, element);
      }
      else {
        myElement = ((GradlePropertiesDslElement)myPropertyHolder).setNewElement(myName, element);
      }
    }
    else if (myPropertyHolder instanceof GradleDslExpressionList) {
      int index = deleteInternal();
      GradleDslExpressionList list = (GradleDslExpressionList)myPropertyHolder;
      assert index != -1; // Can't bind with an invalid index.
      // TODO: Remove this assertion
      assert element instanceof GradleDslExpression;
      list.addNewExpression((GradleDslExpression)element, index);
      myElement = element;
    }
    else if (myPropertyHolder instanceof GradleDslElementList) {
      deleteInternal();
      GradleDslElementList list = (GradleDslElementList)myPropertyHolder;
      list.addNewElement(element);
      myElement = element;
    }
    else {
      throw new IllegalStateException("Property holder has unknown type, " + myPropertyHolder);
    }
    element.setElementType(myPropertyType);
    myValueType = extractAndGetValueType(myElement);
  }

  /**
   * Returns the index of the deleted element if myPropertyHolder is a list, -1 otherwise.
   */
  private int deleteInternal() {
    int index = -1;
    // This model doesn't have a backing element, so there is nothing to delete.
    if (myElement == null) {
      return index;
    }

    if (myPropertyHolder instanceof GradlePropertiesDslElement) {
      ((GradlePropertiesDslElement)myPropertyHolder).removeProperty(myElement);
    }
    else if (myPropertyHolder instanceof GradleDslExpressionList) {
      GradleDslExpressionList list = (GradleDslExpressionList)myPropertyHolder;
      index = list.findIndexOf(myElement);
      ((GradleDslExpressionList)myPropertyHolder).removeElement(myElement);
    }
    else {
      assert myPropertyHolder instanceof GradleDslElementList;
      GradleDslElementList elementList = (GradleDslElementList)myPropertyHolder;
      elementList.removeElement(myElement);
    }

    myElement = null;
    myValueType = NONE;
    return index;
  }
}
