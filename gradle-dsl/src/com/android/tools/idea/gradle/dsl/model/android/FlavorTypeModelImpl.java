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

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.LIST_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.NONE;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.FILE_TRANSFORM;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_MAP;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.UNSPECIFIED_FOR_NOW;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelSemanticsDescription.CREATE_WITH_VALUE;

import com.android.tools.idea.gradle.dsl.api.android.FlavorTypeModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.SigningConfigPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.ListOrVarargsTransform;
import com.android.tools.idea.gradle.dsl.parser.android.AbstractFlavorTypeDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.google.common.base.Function;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common base class for {@link BuildTypeModelImpl} and {@link ProductFlavorModelImpl}.
 */
public abstract class FlavorTypeModelImpl extends GradleDslBlockModel implements FlavorTypeModel {
  @NonNls public static final String APPLICATION_ID_SUFFIX = "mApplicationIdSuffix";
  @NonNls public static final ModelPropertyDescription BUILD_CONFIG_FIELD =
    new ModelPropertyDescription("mBuildConfigField", UNSPECIFIED_FOR_NOW);
  @NonNls public static final ModelPropertyDescription CONSUMER_PROGUARD_FILES =
    // see comment by specification of PROGUARD_FILES
    new ModelPropertyDescription("mConsumerProguardFiles", UNSPECIFIED_FOR_NOW);
  @NonNls public static final String INIT_WITH = "mInitWith";
  @NonNls public static final ModelPropertyDescription MANIFEST_PLACEHOLDERS =
    new ModelPropertyDescription("mManifestPlaceholders", MUTABLE_MAP);
  @NonNls public static final ModelPropertyDescription MATCHING_FALLBACKS =
    new ModelPropertyDescription("mMatchingFallbacks", MUTABLE_LIST);
  @NonNls public static final String MULTI_DEX_ENABLED = "mMultiDexEnabled";
  @NonNls public static final String MULTI_DEX_KEEP_FILE = "mMultiDexKeepFile";
  @NonNls public static final String MULTI_DEX_KEEP_PROGUARD = "mMultiDexKeepProguard";
  @NonNls public static final ModelPropertyDescription PROGUARD_FILES =
    // UNSPECIFIED_FOR_NOW because MUTABLE_LIST actually indicates a list of Strings, whereas proguardFiles is a list of Files
    new ModelPropertyDescription("mProguardFiles", UNSPECIFIED_FOR_NOW);
  @NonNls public static final ModelPropertyDescription RES_VALUE = new ModelPropertyDescription("mResValue", UNSPECIFIED_FOR_NOW);
  @NonNls public static final String SIGNING_CONFIG = "mSigningConfig";
  @NonNls public static final String USE_JACK = "mUseJack";
  @NonNls public static final String VERSION_NAME_SUFFIX = "mVersionNameSuffix";

  public FlavorTypeModelImpl(@NotNull AbstractFlavorTypeDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @Override
  public void rename(@NotNull String newName) {
    myDslElement.getNameElement().rename(newName);
    myDslElement.setModified();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel applicationIdSuffix() {
    return getModelForProperty(APPLICATION_ID_SUFFIX);
  }


  @Override
  @NotNull
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
    myDslElement.removeProperty(BUILD_CONFIG_FIELD.name);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel consumerProguardFiles() {
    return GradlePropertyModelBuilder.create(myDslElement, CONSUMER_PROGUARD_FILES)
      .addTransform(new ListOrVarargsTransform(CONSUMER_PROGUARD_FILES, "setConsumerProguardFiles", "consumerProguardFiles"))
      .buildResolved();
  }

  @NotNull
  public ResolvedPropertyModel initWith() {
    return getModelForProperty(INIT_WITH);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel manifestPlaceholders() {
    GradleDslExpressionMap manifestPlaceholders = myDslElement.getPropertyElement(GradleDslExpressionMap.MANIFEST_PLACEHOLDERS);
    if (manifestPlaceholders == null) {
      manifestPlaceholders = new GradleDslExpressionMap(myDslElement, GradleNameElement.fake(MANIFEST_PLACEHOLDERS.name));
      ModelEffectDescription effect = new ModelEffectDescription(MANIFEST_PLACEHOLDERS, CREATE_WITH_VALUE);
      manifestPlaceholders.setModelEffect(effect);
      manifestPlaceholders.setElementType(REGULAR);
      myDslElement.addDefaultProperty(manifestPlaceholders);
    }
    return getModelForProperty(MANIFEST_PLACEHOLDERS);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel matchingFallbacks() {
    return getModelForProperty(MATCHING_FALLBACKS);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel multiDexEnabled() {
    return getModelForProperty(MULTI_DEX_ENABLED);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel multiDexKeepFile() {
    return GradlePropertyModelBuilder.create(myDslElement, MULTI_DEX_KEEP_FILE).addTransform(FILE_TRANSFORM).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel multiDexKeepProguard() {
    return GradlePropertyModelBuilder.create(myDslElement, MULTI_DEX_KEEP_PROGUARD).addTransform(FILE_TRANSFORM)
                                     .buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel proguardFiles() {
    return GradlePropertyModelBuilder.create(myDslElement, PROGUARD_FILES)
      .addTransform(new ListOrVarargsTransform(PROGUARD_FILES, "setProguardFiles", "proguardFiles"))
      .buildResolved();
  }

  @Override
  @NotNull
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
    myDslElement.removeProperty(RES_VALUE.name);
  }

  @NotNull
  @Override
  public SigningConfigPropertyModel signingConfig() {
    return GradlePropertyModelBuilder.create(myDslElement, SIGNING_CONFIG).buildSigningConfig();
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
    @NotNull private final ModelPropertyDescription myPropertyDescription;
    @NotNull private final GradlePropertyModelImpl myList;

    public TypeNameValueElementImpl(@NotNull ModelPropertyDescription propertyDescription,
                                    @NotNull GradleDslExpressionList list) {
      myPropertyDescription = propertyDescription;
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
      return myPropertyDescription.name;
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
                                                                          @NotNull ModelPropertyDescription property,
                                                                          @NotNull String type,
                                                                          @NotNull String name,
                                                                          @NotNull String value) {
    String elementName = property.name;
    GradleDslMethodCall expressionCall = new GradleDslMethodCall(myDslElement, GradleNameElement.empty(), elementName);
    ModelEffectDescription effect = new ModelEffectDescription(property, OTHER);
    expressionCall.setModelEffect(effect);
    GradleDslExpressionList expressionList = new GradleDslExpressionList(expressionCall, GradleNameElement.empty(), false);
    expressionCall.setParsedArgumentList(expressionList);
    T newValue = producer.apply(expressionList);
    assert newValue != null;
    newValue.type().setValue(type);
    newValue.name().setValue(name);
    newValue.value().setValue(value);
    myDslElement.setNewElement(expressionCall);
    return newValue;
  }

  protected <T> List<T> getTypeNameValuesElements(@NotNull Function<GradleDslExpressionList, T> producer,
                                                  @NotNull ModelPropertyDescription property) {
    String elementName = property.name;
    List<T> result = new ArrayList<>();
    for (GradleDslElement element : myDslElement.getPropertyElementsByName(elementName)) {
      if (element instanceof GradleDslMethodCall) {
        element = ((GradleDslMethodCall)element).getArgumentsElement();
      }
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
                                                                       @NotNull ModelPropertyDescription property,
                                                                       @NotNull String type,
                                                                       @NotNull String name,
                                                                       @NotNull String value) {
    for (GradleDslElement element : myDslElement.getPropertyElementsByName(property.name)) {
      GradleDslExpressionList list = null;
      if (element instanceof GradleDslMethodCall) {
        list = ((GradleDslMethodCall)element).getArgumentsElement();
      }
      else if (element instanceof GradleDslExpressionList) {
        list = (GradleDslExpressionList)element;
      }
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
