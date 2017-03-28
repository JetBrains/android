/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.parser.android.AbstractFlavorTypeDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.utils.Pair;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Common base class for {@link BuildTypeModel} and {@link ProductFlavorModel}.
 */
public abstract class AbstractFlavorTypeModel extends GradleDslBlockModel {
  @NonNls private static final String CONSUMER_PROGUARD_FILES = "consumerProguardFiles";
  @NonNls private static final String MANIFEST_PLACEHOLDERS = "manifestPlaceholders";
  @NonNls private static final String MULTI_DEX_ENABLED = "multiDexEnabled";
  @NonNls private static final String PROGUARD_FILES = "proguardFiles";
  @NonNls private static final String RES_VALUE = "resValue";
  @NonNls private static final String USE_JACK = "useJack";

  public AbstractFlavorTypeModel(@NotNull AbstractFlavorTypeDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @Nullable
  public List<GradleNotNullValue<String>> consumerProguardFiles() {
    return myDslElement.getListProperty(CONSUMER_PROGUARD_FILES, String.class);
  }

  @NotNull
  public AbstractFlavorTypeModel addConsumerProguardFile(@NotNull String consumerProguardFile) {
    myDslElement.addToNewLiteralList(CONSUMER_PROGUARD_FILES, consumerProguardFile);
    return this;
  }

  @NotNull
  public AbstractFlavorTypeModel removeConsumerProguardFile(@NotNull String consumerProguardFile) {
    myDslElement.removeFromExpressionList(CONSUMER_PROGUARD_FILES, consumerProguardFile);
    return this;
  }

  @NotNull
  public AbstractFlavorTypeModel removeAllConsumerProguardFiles() {
    myDslElement.removeProperty(CONSUMER_PROGUARD_FILES);
    return this;
  }

  @NotNull
  public AbstractFlavorTypeModel replaceConsumerProguardFile(@NotNull String oldConsumerProguardFile,
                                                             @NotNull String newConsumerProguardFile) {
    myDslElement.replaceInExpressionList(CONSUMER_PROGUARD_FILES, oldConsumerProguardFile, newConsumerProguardFile);
    return this;
  }

  @Nullable
  public Map<String, GradleNotNullValue<Object>> manifestPlaceholders() {
    return myDslElement.getMapProperty(MANIFEST_PLACEHOLDERS, Object.class);
  }

  @NotNull
  public AbstractFlavorTypeModel setManifestPlaceholder(@NotNull String name, @NotNull String value) {
    myDslElement.setInNewLiteralMap(MANIFEST_PLACEHOLDERS, name, value);
    return this;
  }

  @NotNull
  public AbstractFlavorTypeModel setManifestPlaceholder(@NotNull String name, int value) {
    myDslElement.setInNewLiteralMap(MANIFEST_PLACEHOLDERS, name, value);
    return this;
  }

  @NotNull
  public AbstractFlavorTypeModel setManifestPlaceholder(@NotNull String name, boolean value) {
    myDslElement.setInNewLiteralMap(MANIFEST_PLACEHOLDERS, name, value);
    return this;
  }

  @NotNull
  public AbstractFlavorTypeModel removeManifestPlaceholder(@NotNull String name) {
    myDslElement.removeFromExpressionMap(MANIFEST_PLACEHOLDERS, name);
    return this;
  }

  @NotNull
  public AbstractFlavorTypeModel removeAllManifestPlaceholders() {
    myDslElement.removeProperty(MANIFEST_PLACEHOLDERS);
    return this;
  }

  @NotNull
  public GradleNullableValue<Boolean> multiDexEnabled() {
    return myDslElement.getLiteralProperty(MULTI_DEX_ENABLED, Boolean.class);
  }

  @NotNull
  public AbstractFlavorTypeModel setMultiDexEnabled(boolean multiDexEnabled) {
    myDslElement.setNewLiteral(MULTI_DEX_ENABLED, multiDexEnabled);
    return this;
  }

  @NotNull
  public AbstractFlavorTypeModel removeMultiDexEnabled() {
    myDslElement.removeProperty(MULTI_DEX_ENABLED);
    return this;
  }

  @Nullable
  public List<GradleNotNullValue<String>> proguardFiles() {
    return myDslElement.getListProperty(PROGUARD_FILES, String.class);
  }

  @NotNull
  public AbstractFlavorTypeModel addProguardFile(@NotNull String proguardFile) {
    myDslElement.addToNewLiteralList(PROGUARD_FILES, proguardFile);
    return this;
  }

  @NotNull
  public AbstractFlavorTypeModel removeProguardFile(@NotNull String proguardFile) {
    myDslElement.removeFromExpressionList(PROGUARD_FILES, proguardFile);
    return this;
  }

  @NotNull
  public AbstractFlavorTypeModel removeAllProguardFiles() {
    myDslElement.removeProperty(PROGUARD_FILES);
    return this;
  }

  @NotNull
  public AbstractFlavorTypeModel replaceProguardFile(@NotNull String oldProguardFile, @NotNull String newProguardFile) {
    myDslElement.replaceInExpressionList(PROGUARD_FILES, oldProguardFile, newProguardFile);
    return this;
  }

  @Nullable
  protected List<Pair<GradleDslExpressionList, TypeNameValueElement>> getTypeNameValueElements(@NotNull String elementName) {
    GradleDslElementList typeNameValueElements = myDslElement.getPropertyElement(elementName, GradleDslElementList.class);
    if (typeNameValueElements == null) {
      return null;
    }

    List<Pair<GradleDslExpressionList, TypeNameValueElement>> result = Lists.newArrayList();
    for (GradleDslElement typeNameValue : typeNameValueElements.getElements()) {
      if (typeNameValue instanceof GradleDslExpressionList) {
        GradleDslExpressionList listElement = (GradleDslExpressionList)typeNameValue;
        List<GradleNotNullValue<String>> values = listElement.getValues(String.class);
        if (values.size() == 3) {
          result.add(Pair.of(listElement,
                             new TypeNameValueElement(elementName, values.get(0).value(), values.get(1).value(), values.get(2).value())));
        }
      }
    }
    return result;
  }

  @Nullable
  public List<GradleNotNullValue<ResValue>> resValues() {
    List<Pair<GradleDslExpressionList, TypeNameValueElement>> typeNameValueElements = getTypeNameValueElements(RES_VALUE);
    if (typeNameValueElements == null) {
      return null;
    }

    List<GradleNotNullValue<ResValue>> resValues = Lists.newArrayListWithCapacity(typeNameValueElements.size());
    for (Pair<GradleDslExpressionList, TypeNameValueElement> elementPair : typeNameValueElements) {
      GradleDslExpressionList listElement = elementPair.getFirst();
      TypeNameValueElement typeNameValueElement = elementPair.getSecond();
      resValues.add(new GradleNotNullValue<>(listElement, new ResValue(typeNameValueElement.type(), typeNameValueElement.name(),
                                                                       typeNameValueElement.value())));
    }

    return resValues;
  }

  @NotNull
  protected AbstractFlavorTypeModel addTypeNameValueElement(@NotNull TypeNameValueElement typeNameValueElement) {
    GradleDslElementList elementList = myDslElement.getPropertyElement(typeNameValueElement.myElementName, GradleDslElementList.class);
    if (elementList == null) {
      elementList = new GradleDslElementList(myDslElement, typeNameValueElement.myElementName);
      myDslElement.setNewElement(typeNameValueElement.myElementName, elementList);
    }
    elementList.addNewElement(typeNameValueElement.toLiteralListElement(myDslElement));
    return this;
  }


  @NotNull
  public AbstractFlavorTypeModel addResValue(@NotNull ResValue resValue) {
    return addTypeNameValueElement(resValue);
  }

  @NotNull
  protected AbstractFlavorTypeModel removeTypeNameValueElement(@NotNull TypeNameValueElement typeNameValueElement) {
    GradleDslElementList elementList = myDslElement.getPropertyElement(typeNameValueElement.myElementName, GradleDslElementList.class);
    if (elementList != null) {
      for (GradleDslExpressionList element : elementList.getElements(GradleDslExpressionList.class)) {
        List<GradleNotNullValue<String>> values = element.getValues(String.class);
        if (values.size() == 3
            && typeNameValueElement.type().equals(values.get(0).value())
            && typeNameValueElement.name().equals(values.get(1).value())
            && typeNameValueElement.value().equals(values.get(2).value())) {
          elementList.removeElement(element);
        }
      }
    }
    return this;
  }

  @NotNull
  public AbstractFlavorTypeModel removeResValue(@NotNull ResValue resValue) {
    return removeTypeNameValueElement(resValue);
  }

  @NotNull
  public AbstractFlavorTypeModel removeAllResValues() {
    myDslElement.removeProperty(RES_VALUE);
    return this;
  }

  @NotNull
  protected AbstractFlavorTypeModel replaceTypeNameValueElement(@NotNull TypeNameValueElement oldElement,
                                                                @NotNull TypeNameValueElement newElement) {
    if (oldElement.myElementName.equals(newElement.myElementName)) {
      GradleDslElementList elementList = myDslElement.getPropertyElement(oldElement.myElementName, GradleDslElementList.class);
      if (elementList != null) {
        for (GradleDslExpressionList element : elementList.getElements(GradleDslExpressionList.class)) {
          List<GradleDslExpression> gradleDslLiterals = element.getExpressions();
          if (gradleDslLiterals.size() == 3
              && oldElement.type().equals(gradleDslLiterals.get(0).getValue())
              && oldElement.name().equals(gradleDslLiterals.get(1).getValue())
              && oldElement.value().equals(gradleDslLiterals.get(2).getValue())) {
            gradleDslLiterals.get(0).setValue(newElement.type());
            gradleDslLiterals.get(1).setValue(newElement.name());
            gradleDslLiterals.get(2).setValue(newElement.value());
          }
        }
      }
    }
    return this;
  }

  @NotNull
  public AbstractFlavorTypeModel replaceResValue(@NotNull ResValue oldResValue, @NotNull ResValue newResValue) {
    return replaceTypeNameValueElement(oldResValue, newResValue);
  }

  @NotNull
  public GradleNullableValue<Boolean> useJack() {
    return myDslElement.getLiteralProperty(USE_JACK, Boolean.class);
  }

  @NotNull
  public AbstractFlavorTypeModel setUseJack(boolean useJack) {
    myDslElement.setNewLiteral(USE_JACK, useJack);
    return this;
  }

  @NotNull
  public AbstractFlavorTypeModel removeUseJack() {
    myDslElement.removeProperty(USE_JACK);
    return this;
  }

  /**
   * Represents a statement like {@code resValue} or {@code buildConfigField} which contains type, name and value parameters.
   */
  public static class TypeNameValueElement {
    @NotNull private final String myElementName;
    @NotNull private final String myType;
    @NotNull private final String myName;
    @NotNull private final String myValue;

    public TypeNameValueElement(@NotNull String elementName,
                                @NotNull String type,
                                @NotNull String name,
                                @NotNull String value) {
      myElementName = elementName;
      myType = type;
      myName = name;
      myValue = value;
    }

    @NotNull
    public String type() {
      return myType;
    }

    @NotNull
    public String name() {
      return myName;
    }

    @NotNull
    public String value() {
      return myValue;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myType, myName, myValue);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof TypeNameValueElement)) {
        return false;
      }

      TypeNameValueElement other = (TypeNameValueElement)o;
      return myElementName.equals(other.myElementName)
             && myType.equals(other.myType)
             && myName.equals(other.myName)
             && myValue.equals(other.myValue);
    }

    @Override
    public String toString() {
      return String.format("Type: %1$s, Name: %2$s, Value: %3$s", myType, myName, myValue);
    }

    @NotNull
    protected GradleDslExpressionList toLiteralListElement(@NotNull GradleDslElement parent) {
      GradleDslLiteral typeElement = new GradleDslLiteral(parent, myElementName);
      typeElement.setValue(myType);
      GradleDslLiteral nameElement = new GradleDslLiteral(parent, myElementName);
      nameElement.setValue(myName);
      GradleDslLiteral valueElement = new GradleDslLiteral(parent, myElementName);
      valueElement.setValue(myValue);

      GradleDslExpressionList gradleDslExpressionList = new GradleDslExpressionList(parent, myElementName);
      gradleDslExpressionList.addNewExpression(typeElement);
      gradleDslExpressionList.addNewExpression(nameElement);
      gradleDslExpressionList.addNewExpression(valueElement);
      return gradleDslExpressionList;
    }
  }

  /**
   * Represents a {@code resValue} statement defined in the product flavor block of the Gradle file.
   */
  public static final class ResValue extends TypeNameValueElement {
    public ResValue(@NotNull String type, @NotNull String name, @NotNull String value) {
      super(RES_VALUE, type, name, value);
    }
  }
}
