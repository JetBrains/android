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
import com.android.tools.idea.gradle.dsl.api.util.TypeReference;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*;

public class GradlePropertyModelImpl implements GradlePropertyModel {
  @NotNull private ValueType myValueType;
  @NotNull private final GradleDslElement myElement;
  private boolean myIsValid = true;

  public GradlePropertyModelImpl(@NotNull GradleDslElement element) {
    myElement = element;

    myValueType = extractAndGetValueType(myElement);
  }

  @Override
  @NotNull
  public ValueType getValueType() {
    return myValueType;
  }

  @Override
  @NotNull
  public PropertyType getPropertyType() {
    return myElement.getElementType();
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
    return myElement.getName();
  }

  @Override
  @NotNull
  public List<GradlePropertyModel> getDependencies() {
    return myElement.getResolvedVariables().stream()
      .map(injection -> new GradlePropertyModelImpl(injection.getToBeInjected())).collect(
        Collectors.toList());
  }

  @Override
  @NotNull
  public String getFullyQualifiedName() {
    return myElement.getQualifiedName();
  }

  @Override
  @NotNull
  public VirtualFile getGradleFile() {
    return myElement.getDslFile().getFile();
  }

  @Override
  public void setValue(@NotNull Object value) {
    ensureValid();
    if (myValueType == MAP || myValueType == LIST) {
      throw new UnsupportedOperationException("Setting map and list values are not supported!");
    }

    GradleDslExpression expression = (GradleDslExpression)myElement;
    expression.setValue(value);

    // Update the current value type
    myValueType = extractAndGetValueType(myElement);
  }

  @Override
  @NotNull
  public EmptyPropertyModel delete() {
    ensureValid();
    GradleDslElement parent = myElement.getParent();

    assert parent != null && parent instanceof GradlePropertiesDslElement : "Property found to be invalid, this should never happen!";

    GradlePropertiesDslElement propertyHolder = ((GradlePropertiesDslElement)parent);
    propertyHolder.removeProperty(myElement.getName());

    // Invalidate this property.
    this.myIsValid = false;

    return new EmptyPropertyModel(propertyHolder, myElement.getElementType(), myElement.getName(), false);
  }

  @Override
  public String toString() {
    return String.format("[Element: %1$s, Type: %2$s, ValueType: %3$s]@%4$s",
                         myElement.toString(), myElement.getElementType(), myValueType.toString(), Integer.toHexString(hashCode()));
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

  private void ensureValid() {
    if (!myIsValid) {
      throw new IllegalStateException("Attempted to change an invalid GradlePropertyModel " + this);
    }
  }
}
