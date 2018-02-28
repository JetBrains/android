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
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.SigningConfigPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl;
import com.android.tools.idea.gradle.dsl.parser.android.AbstractFlavorTypeDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.LIST_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.NONE;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.FILE_TRANSFORM;

/**
 * Common base class for {@link BuildTypeModelImpl} and {@link ProductFlavorModelImpl}.
 */
public abstract class FlavorTypeModelImpl extends GradleDslBlockModel implements FlavorTypeModel {
  @NonNls private static final String APPLICATION_ID_SUFFIX = "applicationIdSuffix";
  @NonNls private static final String BUILD_CONFIG_FIELD = "buildConfigField";
  @NonNls private static final String CONSUMER_PROGUARD_FILES = "consumerProguardFiles";
  @NonNls private static final String MANIFEST_PLACEHOLDERS = "manifestPlaceholders";
  @NonNls private static final String MULTI_DEX_ENABLED = "multiDexEnabled";
  @NonNls private static final String MULTI_DEX_KEEP_FILE = "multiDexKeepFile";
  @NonNls private static final String MULTI_DEX_KEEP_PROGUARD = "multiDexKeepProguard";
  @NonNls private static final String PROGUARD_FILES = "proguardFiles";
  @NonNls private static final String RES_VALUE = "resValue";
  @NonNls private static final String SIGNING_CONFIG = "signingConfig";
  @NonNls private static final String USE_JACK = "useJack";
  @NonNls private static final String VERSION_NAME_SUFFIX = "versionNameSuffix";

  public FlavorTypeModelImpl(@NotNull AbstractFlavorTypeDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel applicationIdSuffix() {
    return getModelForProperty(APPLICATION_ID_SUFFIX);
  }


  @Override
  @Nullable
  public List<BuildConfigField> buildConfigFields() {
    return getTypeNameValuesElements(BuildConfigFieldImpl::new, BUILD_CONFIG_FIELD);
  }

  @Override
  public BuildConfigField addBuildConfigField(@NotNull String type, @NotNull String name, @NotNull String value) {
    return addNewTypeNameValueElement(BuildConfigFieldImpl::new, BUILD_CONFIG_FIELD, type, name, value);
  }

  @Override
  public void removeBuildConfigField(@NotNull String type, @NotNull String name, @NotNull String value) {
    BuildConfigField model = getTypeNameValueElement(BuildConfigFieldImpl::new, BUILD_CONFIG_FIELD, type, name, value);
    if (model != null) {
      model.remove();
    }
  }

  @Override
  @Nullable
  public BuildConfigField replaceBuildConfigField(@NotNull String oldType,
                                                  @NotNull String oldName,
                                                  @NotNull String oldValue,
                                                  @NotNull String type,
                                                  @NotNull String name,
                                                  @NotNull String value) {
    BuildConfigField field = getTypeNameValueElement(BuildConfigFieldImpl::new, BUILD_CONFIG_FIELD, oldType, oldName, oldValue);
    if (field == null) {
      return null;
    }

    field.type().setValue(type);
    field.name().setValue(name);
    field.value().setValue(value);
    return field;
  }

  @Override
  public void removeAllBuildConfigFields() {
    myDslElement.removeProperty(BUILD_CONFIG_FIELD);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel consumerProguardFiles() {
    return getModelForProperty(CONSUMER_PROGUARD_FILES);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel manifestPlaceholders() {
    return getModelForProperty(MANIFEST_PLACEHOLDERS);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel multiDexEnabled() {
    return getModelForProperty(MULTI_DEX_ENABLED);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel multiDexKeepFile() {
    return GradlePropertyModelBuilder.create(myDslElement, MULTI_DEX_KEEP_FILE).asMethod(true).addTransform(FILE_TRANSFORM).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel multiDexKeepProguard() {
    return GradlePropertyModelBuilder.create(myDslElement, MULTI_DEX_KEEP_PROGUARD).asMethod(true).addTransform(FILE_TRANSFORM)
      .buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel proguardFiles() {
    return getModelForProperty(PROGUARD_FILES, true);
  }

  @Override
  @Nullable
  public List<ResValue> resValues() {
    return getTypeNameValuesElements(ResValueImpl::new, RES_VALUE);
  }

  @Override
  @NotNull
  public ResValue addResValue(@NotNull String type, @NotNull String name, @NotNull String value) {
    return addNewTypeNameValueElement(ResValueImpl::new, RES_VALUE, type, name, value);
  }

  @Override
  public void removeResValue(@NotNull String type, @NotNull String name, @NotNull String value) {
    ResValue model = getTypeNameValueElement(ResValueImpl::new, RES_VALUE, type, name, value);
    if (model != null) {
      model.remove();
    }
  }

  @Override
  public ResValue replaceResValue(@NotNull String oldType,
                                  @NotNull String oldName,
                                  @NotNull String oldValue,
                                  @NotNull String type,
                                  @NotNull String name,
                                  @NotNull String value) {
    ResValue field = getTypeNameValueElement(ResValueImpl::new, RES_VALUE, oldType, oldName, oldValue);
    if (field == null) {
      return null;
    }

    field.type().setValue(type);
    field.name().setValue(name);
    field.value().setValue(value);
    return field;
  }

  @Override
  public void removeAllResValues() {
    myDslElement.removeProperty(RES_VALUE);
  }

  @NotNull
  @Override
  public SigningConfigPropertyModel signingConfig() {
    return GradlePropertyModelBuilder.create(myDslElement, SIGNING_CONFIG).asMethod(true).buildSigningConfig();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel useJack() {
    return getModelForProperty(USE_JACK);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel versionNameSuffix() {
    return getModelForProperty(VERSION_NAME_SUFFIX);
  }

  /**
   * Represents a statement like {@code resValue} or {@code buildConfigField} which contains type, name and value parameters.
   */
  public static class TypeNameValueElementImpl implements TypeNameValueElement {
    @NotNull private final String myElementName;
    @NotNull private final GradlePropertyModelImpl myList;

    public TypeNameValueElementImpl(@NotNull String elementName,
                                    @NotNull GradleDslExpressionList list) {
      myElementName = elementName;
      myList = new GradlePropertyModelImpl(list);
      List<GradlePropertyModel> elements = myList.getValue(LIST_TYPE);
      assert elements != null;
      // Make sure the list has at least 3 elements.
      for (int i = elements.size(); i < 3; i++) {
        myList.addListValue();
      }
    }

    @NotNull
    private ResolvedPropertyModel getModelForIndex(int index) {
      List<GradlePropertyModel> elements = myList.getValue(LIST_TYPE);
      assert elements != null;
      GradlePropertyModel expression = elements.get(index);
      assert expression.getValueType() != NONE;
      return expression.resolve();
    }

    @Override
    @NotNull
    public ResolvedPropertyModel type() {
      return getModelForIndex(0);
    }

    @Override
    @NotNull
    public ResolvedPropertyModel name() {
      return getModelForIndex(1);
    }

    @Override
    @NotNull
    public ResolvedPropertyModel value() {
      return getModelForIndex(2);
    }

    @Override
    @NotNull
    public String elementName() {
      return myElementName;
    }

    @Override
    public void remove() {
      myList.delete();
    }

    @Override
    public GradlePropertyModel getModel() {
      return myList;
    }

    @Override
    public String toString() {
      return String.format("Type: %1$s, Name: %2$s, Value: %3$s", type().getValue(STRING_TYPE), name().getValue(STRING_TYPE),
                           value().getValue(STRING_TYPE));
    }
  }

  protected <T extends TypeNameValueElement> T addNewTypeNameValueElement(@NotNull Function<GradleDslExpressionList, T> producer,
                                                                          @NotNull String elementName,
                                                                          @NotNull String type,
                                                                          @NotNull String name,
                                                                          @NotNull String value) {
    GradleNameElement nameElement = GradleNameElement.create(elementName);
    GradleDslElementList elementList = myDslElement.getPropertyElement(elementName, GradleDslElementList.class);
    if (elementList == null) {
      elementList = new GradleDslElementList(myDslElement, nameElement);
      myDslElement.setNewElement(elementName, elementList);
    }

    GradleDslExpressionList expressionList = new GradleDslExpressionList(myDslElement, nameElement, false);
    T newValue = producer.apply(expressionList);
    assert newValue != null;
    newValue.type().setValue(type);
    newValue.name().setValue(name);
    newValue.value().setValue(value);
    elementList.addNewElement(expressionList);

    return newValue;
  }

  protected <T> List<T> getTypeNameValuesElements(@NotNull Function<GradleDslExpressionList, T> producer, @NotNull String elementName) {
    GradleDslElementList elementList = myDslElement.getPropertyElement(elementName, GradleDslElementList.class);
    if (elementList == null) {
      return null;
    }

    List<T> result = Lists.newArrayList();
    for (GradleDslElement element : elementList.getElements()) {
      if (element instanceof GradleDslExpressionList) {
        GradleDslExpressionList list = (GradleDslExpressionList)element;
        T value = producer.apply(list);
        assert value != null;
        result.add(value);
      }
    }
    return result;
  }

  @Nullable
  protected <T extends TypeNameValueElement> T getTypeNameValueElement(@NotNull Function<GradleDslExpressionList, T> producer,
                                                                       @NotNull String elementName,
                                                                       @NotNull String type,
                                                                       @NotNull String name,
                                                                       @NotNull String value) {
    GradleDslElementList elementList = myDslElement.getPropertyElement(elementName, GradleDslElementList.class);
    if (elementList == null) {
      return null;
    }

    for (GradleDslElement element : elementList.getElements()) {
      if (element instanceof GradleDslExpressionList) {
        GradleDslExpressionList list = (GradleDslExpressionList)element;
        T typeNameValueElement = producer.apply(list);
        assert typeNameValueElement != null;
        String oldType = typeNameValueElement.type().getValue(STRING_TYPE);
        String oldName = typeNameValueElement.name().getValue(STRING_TYPE);
        String oldValue = typeNameValueElement.value().getValue(STRING_TYPE);
        if (oldType != null && oldType.equals(type) &&
            oldName != null && oldName.equals(name) &&
            oldValue != null && oldValue.equals(value)) {
          return typeNameValueElement;
        }
      }
    }
    return null;
  }

  /**
   * Represents a {@code resValue} statement defined in the product flavor block of the Gradle file.
   */
  public static final class ResValueImpl extends TypeNameValueElementImpl implements ResValue {
    public ResValueImpl(@NotNull GradleDslExpressionList list) {
      super(RES_VALUE, list);
    }
  }

  /**
   * Represents a {@code buildConfigField} statement defined in the build type block of the Gradle file.
   */
  public final static class BuildConfigFieldImpl extends TypeNameValueElementImpl implements BuildConfigField {
    public BuildConfigFieldImpl(@NotNull GradleDslExpressionList list) {
      super(BUILD_CONFIG_FIELD, list);
    }
  }
}
