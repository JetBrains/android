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

import com.android.tools.idea.gradle.dsl.api.FlavorTypeModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValueImpl;
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
 * Common base class for {@link BuildTypeModelImpl} and {@link ProductFlavorModelImpl}.
 */
public abstract class FlavorTypeModelImpl extends GradleDslBlockModel implements FlavorTypeModel {
  @NonNls private static final String CONSUMER_PROGUARD_FILES = "consumerProguardFiles";
  @NonNls private static final String MANIFEST_PLACEHOLDERS = "manifestPlaceholders";
  @NonNls private static final String MULTI_DEX_ENABLED = "multiDexEnabled";
  @NonNls private static final String PROGUARD_FILES = "proguardFiles";
  @NonNls private static final String RES_VALUE = "resValue";
  @NonNls private static final String USE_JACK = "useJack";

  public FlavorTypeModelImpl(@NotNull AbstractFlavorTypeDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> consumerProguardFiles() {
    return myDslElement.getListProperty(CONSUMER_PROGUARD_FILES, String.class);
  }

  @Override
  @NotNull
  public FlavorTypeModel addConsumerProguardFile(@NotNull String consumerProguardFile) {
    myDslElement.addToNewLiteralList(CONSUMER_PROGUARD_FILES, consumerProguardFile);
    return this;
  }

  @Override
  @NotNull
  public FlavorTypeModel removeConsumerProguardFile(@NotNull String consumerProguardFile) {
    myDslElement.removeFromExpressionList(CONSUMER_PROGUARD_FILES, consumerProguardFile);
    return this;
  }

  @Override
  @NotNull
  public FlavorTypeModel removeAllConsumerProguardFiles() {
    myDslElement.removeProperty(CONSUMER_PROGUARD_FILES);
    return this;
  }

  @Override
  @NotNull
  public FlavorTypeModel replaceConsumerProguardFile(@NotNull String oldConsumerProguardFile,
                                                     @NotNull String newConsumerProguardFile) {
    myDslElement.replaceInExpressionList(CONSUMER_PROGUARD_FILES, oldConsumerProguardFile, newConsumerProguardFile);
    return this;
  }

  @Override
  @Nullable
  public Map<String, GradleNotNullValue<Object>> manifestPlaceholders() {
    return myDslElement.getMapProperty(MANIFEST_PLACEHOLDERS, Object.class);
  }

  @Override
  @NotNull
  public FlavorTypeModel setManifestPlaceholder(@NotNull String name, @NotNull String value) {
    myDslElement.setInNewLiteralMap(MANIFEST_PLACEHOLDERS, name, value);
    return this;
  }

  @Override
  @NotNull
  public FlavorTypeModel setManifestPlaceholder(@NotNull String name, int value) {
    myDslElement.setInNewLiteralMap(MANIFEST_PLACEHOLDERS, name, value);
    return this;
  }

  @Override
  @NotNull
  public FlavorTypeModel setManifestPlaceholder(@NotNull String name, boolean value) {
    myDslElement.setInNewLiteralMap(MANIFEST_PLACEHOLDERS, name, value);
    return this;
  }

  @Override
  @NotNull
  public FlavorTypeModel removeManifestPlaceholder(@NotNull String name) {
    myDslElement.removeFromExpressionMap(MANIFEST_PLACEHOLDERS, name);
    return this;
  }

  @Override
  @NotNull
  public FlavorTypeModel removeAllManifestPlaceholders() {
    myDslElement.removeProperty(MANIFEST_PLACEHOLDERS);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> multiDexEnabled() {
    return myDslElement.getLiteralProperty(MULTI_DEX_ENABLED, Boolean.class);
  }

  @Override
  @NotNull
  public FlavorTypeModel setMultiDexEnabled(boolean multiDexEnabled) {
    myDslElement.setNewLiteral(MULTI_DEX_ENABLED, multiDexEnabled);
    return this;
  }

  @Override
  @NotNull
  public FlavorTypeModel removeMultiDexEnabled() {
    myDslElement.removeProperty(MULTI_DEX_ENABLED);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> proguardFiles() {
    return myDslElement.getListProperty(PROGUARD_FILES, String.class);
  }

  @Override
  @NotNull
  public FlavorTypeModel addProguardFile(@NotNull String proguardFile) {
    myDslElement.addToNewLiteralList(PROGUARD_FILES, proguardFile);
    return this;
  }

  @Override
  @NotNull
  public FlavorTypeModel removeProguardFile(@NotNull String proguardFile) {
    myDslElement.removeFromExpressionList(PROGUARD_FILES, proguardFile);
    return this;
  }

  @Override
  @NotNull
  public FlavorTypeModel removeAllProguardFiles() {
    myDslElement.removeProperty(PROGUARD_FILES);
    return this;
  }

  @Override
  @NotNull
  public FlavorTypeModel replaceProguardFile(@NotNull String oldProguardFile, @NotNull String newProguardFile) {
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
                             new TypeNameValueElementImpl(elementName, values.get(0).value(), values.get(1).value(),
                                                          values.get(2).value())));
        }
      }
    }
    return result;
  }

  @Override
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
      resValues.add(new GradleNotNullValueImpl<>(listElement, new ResValueImpl(typeNameValueElement.type(), typeNameValueElement.name(),
                                                                               typeNameValueElement.value())));
    }

    return resValues;
  }

  @NotNull
  protected FlavorTypeModel addTypeNameValueElement(@NotNull TypeNameValueElement typeNameValueElement) {
    GradleDslElementList elementList = myDslElement.getPropertyElement(typeNameValueElement.elementName(), GradleDslElementList.class);
    if (elementList == null) {
      elementList = new GradleDslElementList(myDslElement, typeNameValueElement.elementName());
      myDslElement.setNewElement(typeNameValueElement.elementName(), elementList);
    }
    elementList.addNewElement(TypeNameValueElementImpl.toLiteralListElement(typeNameValueElement, myDslElement));
    return this;
  }

  @Override
  @NotNull
  public FlavorTypeModel addResValue(@NotNull ResValue resValue) {
    return addTypeNameValueElement(resValue);
  }

  @NotNull
  protected FlavorTypeModel removeTypeNameValueElement(@NotNull TypeNameValueElement typeNameValueElement) {
    GradleDslElementList elementList = myDslElement.getPropertyElement(typeNameValueElement.elementName(), GradleDslElementList.class);
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

  @Override
  @NotNull
  public FlavorTypeModel removeResValue(@NotNull ResValue resValue) {
    return removeTypeNameValueElement(resValue);
  }

  @Override
  @NotNull
  public FlavorTypeModel removeAllResValues() {
    myDslElement.removeProperty(RES_VALUE);
    return this;
  }

  @NotNull
  protected FlavorTypeModel replaceTypeNameValueElement(@NotNull TypeNameValueElement oldElement,
                                                        @NotNull TypeNameValueElement newElement) {
    if (oldElement.elementName().equals(newElement.elementName())) {
      GradleDslElementList elementList = myDslElement.getPropertyElement(oldElement.elementName(), GradleDslElementList.class);
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

  @Override
  @NotNull
  public FlavorTypeModel replaceResValue(@NotNull ResValue oldResValue, @NotNull ResValue newResValue) {
    return replaceTypeNameValueElement(oldResValue, newResValue);
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> useJack() {
    return myDslElement.getLiteralProperty(USE_JACK, Boolean.class);
  }

  @Override
  @NotNull
  public FlavorTypeModel setUseJack(boolean useJack) {
    myDslElement.setNewLiteral(USE_JACK, useJack);
    return this;
  }

  @Override
  @NotNull
  public FlavorTypeModel removeUseJack() {
    myDslElement.removeProperty(USE_JACK);
    return this;
  }

  /**
   * Represents a statement like {@code resValue} or {@code buildConfigField} which contains type, name and value parameters.
   */
  public static class TypeNameValueElementImpl implements TypeNameValueElement {
    @NotNull private final String myElementName;
    @NotNull private final String myType;
    @NotNull private final String myName;
    @NotNull private final String myValue;

    public TypeNameValueElementImpl(@NotNull String elementName,
                                    @NotNull String type,
                                    @NotNull String name,
                                    @NotNull String value) {
      myElementName = elementName;
      myType = type;
      myName = name;
      myValue = value;
    }

    @Override
    @NotNull
    public String type() {
      return myType;
    }

    @Override
    @NotNull
    public String name() {
      return myName;
    }

    @Override
    @NotNull
    public String value() {
      return myValue;
    }

    @Override
    @NotNull
    public String elementName() {
      return myElementName;
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
      if (!(o instanceof TypeNameValueElementImpl)) {
        return false;
      }

      TypeNameValueElementImpl other = (TypeNameValueElementImpl)o;
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
    protected static GradleDslExpressionList toLiteralListElement(@NotNull TypeNameValueElement element, @NotNull GradleDslElement parent) {
      GradleDslLiteral typeElement = new GradleDslLiteral(parent, element.elementName());
      typeElement.setValue(element.type());
      GradleDslLiteral nameElement = new GradleDslLiteral(parent, element.elementName());
      nameElement.setValue(element.name());
      GradleDslLiteral valueElement = new GradleDslLiteral(parent, element.elementName());
      valueElement.setValue(element.value());

      GradleDslExpressionList gradleDslExpressionList = new GradleDslExpressionList(parent, element.elementName());
      gradleDslExpressionList.addNewExpression(typeElement);
      gradleDslExpressionList.addNewExpression(nameElement);
      gradleDslExpressionList.addNewExpression(valueElement);
      return gradleDslExpressionList;
    }
  }

  /**
   * Represents a {@code resValue} statement defined in the product flavor block of the Gradle file.
   */
  public static final class ResValueImpl extends TypeNameValueElementImpl implements ResValue {
    public ResValueImpl(@NotNull String type, @NotNull String name, @NotNull String value) {
      super(RES_VALUE, type, name, value);
    }
  }
}
