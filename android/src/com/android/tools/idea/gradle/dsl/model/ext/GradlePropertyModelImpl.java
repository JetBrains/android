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
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.vfs.VirtualFile;
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

  // The following properties should always be kept up to date with the values given by myElement.getElementType() and myElement.getName().
  @NotNull private final PropertyType myPropertyType;
  @NotNull private String myName;

  public GradlePropertyModelImpl(@NotNull GradleDslElement element) {
    myElement = element;

    GradleDslElement parent = element.getParent();
    assert parent != null &&
           (parent instanceof GradlePropertiesDslElement ||
            parent instanceof GradleDslExpressionList) : "Property found to be invalid, this should never happen!";
    myPropertyHolder = parent;

    myPropertyType = myElement.getElementType();
    myName = myElement.getName();

    myValueType = extractAndGetValueType(myElement);
  }

  // Used to create an empty property with no backing element.
  public GradlePropertyModelImpl(@NotNull GradleDslElement element, @NotNull PropertyType type, @NotNull String name) {
    myPropertyHolder = element;
    myPropertyType = type;
    myName = name;

    myValueType = NONE;
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

  @NotNull
  private Map<String, GradlePropertyModel> getMap() {
    if (myValueType != MAP || !(myElement instanceof GradleDslExpressionMap)) {
      return ImmutableMap.of();
    }

    GradleDslExpressionMap map = (GradleDslExpressionMap)myElement;
    return map.getPropertyElements().entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, e -> new GradlePropertyModelImpl(e.getValue())));
  }

  @NotNull
  private List<GradlePropertyModel> getList() {
    if (myValueType != LIST || !(myElement instanceof GradleDslExpressionList)) {
      return ImmutableList.of();
    }

    GradleDslExpressionList list = (GradleDslExpressionList)myElement;
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
    boolean isReference = value instanceof ReferenceTo;

    // Check if we can reuse the element.
    if (!isReference && myElement instanceof GradleDslLiteral ||
        isReference && myElement instanceof GradleDslReference) {
      GradleDslExpression expression = (GradleDslExpression)myElement;
      expression.setValue(value);
      // Set the value type for the new value.
      myValueType = extractAndGetValueType(myElement);
    } else {
      // We can't reuse, need to delete and create a new one.
      int index = deleteInternal();

      GradleDslExpression newElement;
      if (!isReference) {
        newElement = new GradleDslLiteral(myPropertyHolder, myName);
      }
      else {
        newElement = new GradleDslReference(myPropertyHolder, myName);
      }
      newElement.setValue(value);
      bindToNewElement(newElement, index);
    }
  }

  @Override
  @NotNull
  public GradlePropertyModel convertToEmptyMap() {
    makeEmptyMap();
    return this;
  }

  @Override
  public GradlePropertyModel addMapValue(@NotNull String key) {
    if (myValueType != MAP) {
      throw new IllegalStateException("Please call GradlePropertyModel#convertToMap before trying to add values");
    }

    assert myElement instanceof GradleDslExpressionMap;

    return new GradlePropertyModelImpl(myElement, PropertyType.DERIVED, key);
  }

  @Override
  public GradlePropertyModel convertToEmptyList() {
    makeEmptyList();
    return this;
  }

  @Override
  public GradlePropertyModel addListValue() {
    if (myValueType != LIST) {
      throw new IllegalStateException("Please call GradlePropertyModel#convertToList before trying to add values");
    }

    assert myElement instanceof GradleDslExpressionList;

    return addListValueAt(((GradleDslExpressionList)myElement).getExpressions().size());
  }

  @Override
  public GradlePropertyModel addListValueAt(int index) {
    if (myValueType != LIST) {
      throw new IllegalStateException("Please call GradlePropertyModel#convertToList before trying to add values");
    }

    assert myElement instanceof GradleDslExpressionList;

    // Unlike maps, we don't create a placeholder element. This is since we need to retain and update order in the list.
    // This would be hard to create an intuitive api to do this, so instead we always create an empty string as the new item.
    GradleDslLiteral literal = new GradleDslLiteral(myElement, "listItem");
    literal.setValue("");

    GradleDslExpressionList list = (GradleDslExpressionList) myElement;
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

  @Override
  public String toString() {
    return String.format("[Element: %1$s, Type: %2$s, ValueType: %3$s]@%4$s",
                         myElement, myPropertyType, myValueType.toString(), Integer.toHexString(hashCode()));
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

    if (myValueType == MAP) {
      Object value = getMap();
      return typeReference.castTo(value);
    }
    else if (myValueType == LIST) {
      Object value = getList();
      return typeReference.castTo(value);
    }
    else if (myValueType == REFERENCE) {
      // For references only display the reference text for both resolved and unresolved values.
      // Users should follow the reference to obtain the value.
      GradleDslReference ref = (GradleDslReference)myElement;
      String refText = ref.getReferenceText();
      return refText == null ? null : typeReference.castTo(refText);
    }

    GradleDslExpression expression = (GradleDslExpression)myElement;

    Object value = resolved ? expression.getValue() : expression.getUnresolvedValue();
    if (value == null) {
      return null;
    }

    return typeReference.castTo(value);
  }

  private void makeEmptyMap() {
    // Makes this property a map, first remove the old property.
    int index = deleteInternal();

    bindToNewElement(new GradleDslExpressionMap(myPropertyHolder, myName, true), index);
  }

  private void makeEmptyList() {
    // Remove the old property.
    int index = deleteInternal();

    bindToNewElement(new GradleDslExpressionList(myPropertyHolder, myName, true), index);
  }

  private void bindToNewElement(@NotNull GradleDslElement element, int index) {
    if (myPropertyHolder instanceof GradlePropertiesDslElement) {
      element.setElementType(myPropertyType);
      myElement = ((GradlePropertiesDslElement)myPropertyHolder).setNewElement(myName, element);
      myValueType = extractAndGetValueType(myElement);
    }
    else if (myPropertyHolder instanceof GradleDslExpressionList) {
      GradleDslExpressionList list = (GradleDslExpressionList)myPropertyHolder;
      assert index != -1; // Can't bind with an invalid index.
      // TODO: Remove this assertion
      assert element instanceof GradleDslExpression;
      list.addNewExpression((GradleDslExpression)element, index);
    }
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
      ((GradlePropertiesDslElement)myPropertyHolder).removeProperty(myElement.getName());
    }
    else {
      assert myPropertyHolder instanceof GradleDslExpressionList;
      GradleDslExpressionList list = (GradleDslExpressionList)myPropertyHolder;
      index = list.findIndexOf(myElement);
      ((GradleDslExpressionList)myPropertyHolder).removeElement(myElement);
    }

    myElement = null;
    myValueType = NONE;
    return index;
  }
}
