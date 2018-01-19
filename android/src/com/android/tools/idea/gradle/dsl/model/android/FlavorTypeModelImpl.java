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
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.ResolvedPropertyModelImpl;
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

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;

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
  public void addConsumerProguardFile(@NotNull String consumerProguardFile) {
    myDslElement.addToNewLiteralList(CONSUMER_PROGUARD_FILES, consumerProguardFile);
  }

  @Override
  public void removeConsumerProguardFile(@NotNull String consumerProguardFile) {
    myDslElement.removeFromExpressionList(CONSUMER_PROGUARD_FILES, consumerProguardFile);
  }

  @Override
  public void removeAllConsumerProguardFiles() {
    myDslElement.removeProperty(CONSUMER_PROGUARD_FILES);
  }

  @Override
  public void replaceConsumerProguardFile(@NotNull String oldConsumerProguardFile,
                                          @NotNull String newConsumerProguardFile) {
    myDslElement.replaceInExpressionList(CONSUMER_PROGUARD_FILES, oldConsumerProguardFile, newConsumerProguardFile);
  }

  @Override
  @Nullable
  public Map<String, GradleNotNullValue<Object>> manifestPlaceholders() {
    return myDslElement.getMapProperty(MANIFEST_PLACEHOLDERS, Object.class);
  }

  @Override
  public void setManifestPlaceholder(@NotNull String name, @NotNull String value) {
    myDslElement.setInNewLiteralMap(MANIFEST_PLACEHOLDERS, name, value);
  }

  @Override
  public void setManifestPlaceholder(@NotNull String name, int value) {
    myDslElement.setInNewLiteralMap(MANIFEST_PLACEHOLDERS, name, value);
  }

  @Override
  public void setManifestPlaceholder(@NotNull String name, boolean value) {
    myDslElement.setInNewLiteralMap(MANIFEST_PLACEHOLDERS, name, value);
  }

  @Override
  public void removeManifestPlaceholder(@NotNull String name) {
    myDslElement.removeFromExpressionMap(MANIFEST_PLACEHOLDERS, name);
  }

  @Override
  public void removeAllManifestPlaceholders() {
    myDslElement.removeProperty(MANIFEST_PLACEHOLDERS);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel multiDexEnabled() {
    return getModelForProperty(MULTI_DEX_ENABLED);
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> proguardFiles() {
    return myDslElement.getListProperty(PROGUARD_FILES, String.class);
  }

  @Override
  public void addProguardFile(@NotNull String proguardFile) {
    myDslElement.addToNewLiteralList(PROGUARD_FILES, proguardFile);
  }

  @Override
  public void removeProguardFile(@NotNull String proguardFile) {
    myDslElement.removeFromExpressionList(PROGUARD_FILES, proguardFile);
  }

  @Override
  public void removeAllProguardFiles() {
    myDslElement.removeProperty(PROGUARD_FILES);
  }

  @Override
  public void replaceProguardFile(@NotNull String oldProguardFile, @NotNull String newProguardFile) {
    myDslElement.replaceInExpressionList(PROGUARD_FILES, oldProguardFile, newProguardFile);
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

  protected void addTypeNameValueElement(@NotNull TypeNameValueElement typeNameValueElement) {
    GradleDslElementList elementList = myDslElement.getPropertyElement(typeNameValueElement.elementName(), GradleDslElementList.class);
    if (elementList == null) {
      elementList = new GradleDslElementList(myDslElement, typeNameValueElement.elementName());
      myDslElement.setNewElement(typeNameValueElement.elementName(), elementList);
    }
    elementList.addNewElement(TypeNameValueElementImpl.toLiteralListElement(typeNameValueElement, myDslElement));
  }

  @Override
  public void addResValue(@NotNull ResValue resValue) {
    addTypeNameValueElement(resValue);
  }

  protected void removeTypeNameValueElement(@NotNull TypeNameValueElement typeNameValueElement) {
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
  }

  @Override
  public void removeResValue(@NotNull ResValue resValue) {
    removeTypeNameValueElement(resValue);
  }

  @Override
  public void removeAllResValues() {
    myDslElement.removeProperty(RES_VALUE);
  }

  protected void replaceTypeNameValueElement(@NotNull TypeNameValueElement oldElement,
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
  }

  @Override
  public void replaceResValue(@NotNull ResValue oldResValue, @NotNull ResValue newResValue) {
    replaceTypeNameValueElement(oldResValue, newResValue);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel useJack() {
    return getModelForProperty(USE_JACK);
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

      GradleDslExpressionList gradleDslExpressionList = new GradleDslExpressionList(parent, element.elementName(), false);
      gradleDslExpressionList.addNewExpression(typeElement);
      gradleDslExpressionList.addNewExpression(nameElement);
      gradleDslExpressionList.addNewExpression(valueElement);
      return gradleDslExpressionList;
    }
  }

  @NotNull
  protected ResolvedPropertyModel getModelForProperty(@NotNull String property) {
    GradleDslElement element = myDslElement.getPropertyElement(property);
    return new ResolvedPropertyModelImpl(element == null
                                         ? new GradlePropertyModelImpl(myDslElement, REGULAR, property)
                                         : new GradlePropertyModelImpl(element));
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
