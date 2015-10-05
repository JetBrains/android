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
package com.android.tools.idea.gradle.dsl;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.List;
import java.util.Map;

public final class ProductFlavorElement extends GradleDslPropertiesElement {
  private static final String APPLICATION_ID = "applicationId";
  private static final String CONSUMER_PROGUARD_FILES = "consumerProguardFiles";
  private static final String DIMENSION = "dimension";
  private static final String MANIFEST_PLACEHOLDERS = "manifestPlaceholders";
  private static final String MAX_SDK_VERSION = "maxSdkVersion";
  private static final String MIN_SDK_VERSION = "minSdkVersion";
  private static final String MULTI_DEX_ENABLED = "multiDexEnabled";
  private static final String PROGUARD_FILES = "proguardFiles";
  private static final String RES_CONFIGS = "resConfigs";
  private static final String RES_VALUES = "resValues";
  private static final String TARGET_SDK_VERSION = "targetSdkVersion";
  private static final String TEST_APPLICATION_ID = "testApplicationId";
  private static final String TEST_FUNCTIONAL_TEST = "testFunctionalTest";
  private static final String TEST_HANDLE_PROFILING = "testHandleProfiling";
  private static final String TEST_INSTRUMENTATION_RUNNER = "testInstrumentationRunner";
  private static final String TEST_INSTRUMENTATION_RUNNER_ARGUMENTS = "testInstrumentationRunnerArguments";
  private static final String USE_JACK = "useJack";
  private static final String VERSION_CODE = "versionCode";
  private static final String VERSION_NAME = "versionName";

  @NotNull
  private final String myName;

  ProductFlavorElement(@Nullable GradleDslElement parent, @NotNull String name) {
    super(parent);
    myName = name;
  }

  @NotNull
  public String name() {
    return myName;
  }

  @Nullable
  public String applicationId() {
    return getProperty(APPLICATION_ID, String.class);
  }

  @NotNull
  public ProductFlavorElement setApplicationId(@NotNull String applicationId) {
    return (ProductFlavorElement)setLiteralProperty(APPLICATION_ID, applicationId);
  }

  @Nullable
  public List<String> consumerProguardFiles() {
    return getListProperty(CONSUMER_PROGUARD_FILES, String.class);
  }

  @NotNull
  public ProductFlavorElement addConsumerProguardFile(@NotNull String consumerProguardFile) {
    return (ProductFlavorElement)addToListProperty(CONSUMER_PROGUARD_FILES, consumerProguardFile);
  }

  @NotNull
  public ProductFlavorElement removeConsumerProguardFile(@NotNull String consumerProguardFile) {
    return (ProductFlavorElement)removeFromListProperty(CONSUMER_PROGUARD_FILES, consumerProguardFile);
  }

  @NotNull
  public ProductFlavorElement replaceConsumerProguardFile(@NotNull String oldConsumerProguardFile,
                                                          @NotNull String newConsumerProguardFile) {
    return (ProductFlavorElement)replaceInListProperty(CONSUMER_PROGUARD_FILES, oldConsumerProguardFile, newConsumerProguardFile);
  }

  @Nullable
  public String dimension() {
    return getProperty(DIMENSION, String.class);
  }

  @NotNull
  public ProductFlavorElement setDimension(@NotNull String dimension) {
    return (ProductFlavorElement)setLiteralProperty(DIMENSION, dimension);
  }

  @Nullable
  public Map<String, Object> manifestPlaceholders() {
    return getMapProperty(MANIFEST_PLACEHOLDERS, Object.class);
  }

  @NotNull
  public ProductFlavorElement setManifestPlaceholder(@NotNull String name, @NotNull String value) {
    return (ProductFlavorElement)setInMapProperty(MANIFEST_PLACEHOLDERS, name, value);
  }

  @NotNull
  public ProductFlavorElement setManifestPlaceholder(@NotNull String name, int value) {
    return (ProductFlavorElement)setInMapProperty(MANIFEST_PLACEHOLDERS, name, value);
  }

  @NotNull
  public ProductFlavorElement setManifestPlaceholder(@NotNull String name, boolean value) {
    return (ProductFlavorElement)setInMapProperty(MANIFEST_PLACEHOLDERS, name, value);
  }

  @NotNull
  public ProductFlavorElement removeManifestPlaceholder(@NotNull String name) {
    return (ProductFlavorElement)removeFromMapProperty(MANIFEST_PLACEHOLDERS, name);
  }

  @Nullable
  public Integer maxSdkVersion() {
    return getProperty(MAX_SDK_VERSION, Integer.class);
  }

  @NotNull
  public ProductFlavorElement setMaxSdkVersion(int maxSdkVersion) {
    return (ProductFlavorElement)setLiteralProperty(MAX_SDK_VERSION, maxSdkVersion);
  }

  @Nullable
  public String minSdkVersion() {
    Integer intValue = getProperty(MIN_SDK_VERSION, Integer.class);
    return intValue != null ? intValue.toString() : getProperty(MIN_SDK_VERSION, String.class);
  }

  @NotNull
  public ProductFlavorElement setMinSdkVersion(int minSdkVersion) {
    return (ProductFlavorElement)setLiteralProperty(MIN_SDK_VERSION, minSdkVersion);
  }

  @NotNull
  public ProductFlavorElement setMinSdkVersion(@NotNull String minSdkVersion) {
    return (ProductFlavorElement)setLiteralProperty(MIN_SDK_VERSION, minSdkVersion);
  }

  @Nullable
  public Boolean multiDexEnabled() {
    return getProperty(MULTI_DEX_ENABLED, Boolean.class);
  }

  @NotNull
  public ProductFlavorElement setMultiDexEnabled(boolean multiDexEnabled) {
    return (ProductFlavorElement)setLiteralProperty(MULTI_DEX_ENABLED, multiDexEnabled);
  }

  @Nullable
  public List<String> proguardFiles() {
    return getListProperty(PROGUARD_FILES, String.class);
  }

  @NotNull
  public ProductFlavorElement addProguardFile(@NotNull String proguardFile) {
    return (ProductFlavorElement)addToListProperty(PROGUARD_FILES, proguardFile);
  }

  @NotNull
  public ProductFlavorElement removeProguardFile(@NotNull String proguardFile) {
    return (ProductFlavorElement)removeFromListProperty(PROGUARD_FILES, proguardFile);
  }

  @NotNull
  public ProductFlavorElement replaceProguardFile(@NotNull String oldProguardFile, @NotNull String newProguardFile) {
    return (ProductFlavorElement)replaceInListProperty(PROGUARD_FILES, oldProguardFile, newProguardFile);
  }

  @Nullable
  public List<String> resConfigs() {
    return getListProperty(RES_CONFIGS, String.class);
  }

  @NotNull
  public ProductFlavorElement addResConfig(@NotNull String resConfig) {
    return (ProductFlavorElement)addToListProperty(RES_CONFIGS, resConfig);
  }

  @NotNull
  public ProductFlavorElement removeResConfig(@NotNull String resConfig) {
    return (ProductFlavorElement)removeFromListProperty(RES_CONFIGS, resConfig);
  }

  @NotNull
  public ProductFlavorElement replaceResConfig(@NotNull String oldResConfig, @NotNull String newResConfig) {
    return (ProductFlavorElement)replaceInListProperty(RES_CONFIGS, oldResConfig, newResConfig);
  }

  @Nullable
  public List<ResValue> resValues() {
    GradleDslElementList resValues = getProperty(RES_VALUES, GradleDslElementList.class);
    if (resValues == null) {
      return null;
    }

    List<ResValue> result = Lists.newArrayList();
    for (GradleDslElement resValue : resValues.getElements()) {
      if (resValue instanceof LiteralListElement) {
        LiteralListElement listElement = (LiteralListElement)resValue;
        List<String> values = listElement.getValues(String.class);
        if (values.size() == 3) {
          result.add(new ResValue(values.get(0), values.get(1), values.get(2)));
        }
      }
    }
    return result;
  }

  @NotNull
  public ProductFlavorElement addResValue(@NotNull ResValue resValue) {
    GradleDslElementList elementList = getProperty(RES_VALUES, GradleDslElementList.class);
    if (elementList == null) {
      elementList = new GradleDslElementList(this, RES_VALUES);
      setNewElement(RES_VALUES, elementList);
    }
    elementList.addNewElement(resValue.toLiteralListElement(this));
    return this;
  }

  @NotNull
  public ProductFlavorElement removeResValue(@NotNull ResValue resValue) {
    GradleDslElementList elementList = getProperty(RES_VALUES, GradleDslElementList.class);
    if (elementList != null) {
      for (LiteralListElement element : elementList.getElements(LiteralListElement.class)) {
        List<String> values = element.getValues(String.class);
        if (values.size() == 3
            && resValue.type().equals(values.get(0)) && resValue.name().equals(values.get(1)) && resValue.value().equals(values.get(2))) {
          elementList.removeElement(element);
        }
      }
    }
    return this;
  }

  @NotNull
  public ProductFlavorElement replaceResValue(@NotNull ResValue oldResValue, @NotNull ResValue newResValue) {
    GradleDslElementList elementList = getProperty(RES_VALUES, GradleDslElementList.class);
    if (elementList != null) {
      for (LiteralListElement element : elementList.getElements(LiteralListElement.class)) {
        List<LiteralElement> literalElements = element.getElements();
        if (literalElements.size() == 3
            && oldResValue.type().equals(literalElements.get(0).getValue())
            && oldResValue.name().equals(literalElements.get(1).getValue())
            && oldResValue.value().equals(literalElements.get(2).getValue())) {
          literalElements.get(0).setValue(newResValue.type());
          literalElements.get(1).setValue(newResValue.name());
          literalElements.get(2).setValue(newResValue.value());
        }
      }
    }
    return this;
  }

  @Nullable
  public String targetSdkVersion() {
    Integer intValue = getProperty(TARGET_SDK_VERSION, Integer.class);
    return intValue != null ? intValue.toString() : getProperty(TARGET_SDK_VERSION, String.class);
  }

  @NotNull
  public ProductFlavorElement setTargetSdkVersion(int targetSdkVersion) {
    return (ProductFlavorElement)setLiteralProperty(TARGET_SDK_VERSION, targetSdkVersion);
  }

  @NotNull
  public ProductFlavorElement setTargetSdkVersion(@NotNull String targetSdkVersion) {
    return (ProductFlavorElement)setLiteralProperty(TARGET_SDK_VERSION, targetSdkVersion);
  }

  @Nullable
  public String testApplicationId() {
    return getProperty(TEST_APPLICATION_ID, String.class);
  }

  @NotNull
  public ProductFlavorElement setTestApplicationId(@NotNull String testApplicationId) {
    return (ProductFlavorElement)setLiteralProperty(TEST_APPLICATION_ID, testApplicationId);
  }

  @Nullable
  public Boolean testFunctionalTest() {
    return getProperty(TEST_FUNCTIONAL_TEST, Boolean.class);
  }

  @NotNull
  public ProductFlavorElement setTestFunctionalTest(boolean testFunctionalTest) {
    return (ProductFlavorElement)setLiteralProperty(TEST_FUNCTIONAL_TEST, testFunctionalTest);
  }

  @Nullable
  public Boolean testHandleProfiling() {
    return getProperty(TEST_HANDLE_PROFILING, Boolean.class);
  }

  @NotNull
  public ProductFlavorElement setTestHandleProfiling(boolean testHandleProfiling) {
    return (ProductFlavorElement)setLiteralProperty(TEST_HANDLE_PROFILING, testHandleProfiling);
  }

  @Nullable
  public String testInstrumentationRunner() {
    return getProperty(TEST_INSTRUMENTATION_RUNNER, String.class);
  }

  @NotNull
  public ProductFlavorElement setTestInstrumentationRunner(@NotNull String testInstrumentationRunner) {
    return (ProductFlavorElement)setLiteralProperty(TEST_INSTRUMENTATION_RUNNER, testInstrumentationRunner);
  }

  @Nullable
  public Map<String, String> testInstrumentationRunnerArguments() {
    return getMapProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, String.class);
  }

  @NotNull
  public ProductFlavorElement setTestInstrumentationRunnerArgument(@NotNull String name, @NotNull String value) {
    return (ProductFlavorElement)setInMapProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, name, value);
  }

  @NotNull
  public ProductFlavorElement removeTestInstrumentationRunnerArgument(@NotNull String name) {
    return (ProductFlavorElement)removeFromMapProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, name);
  }

  @Nullable
  public Boolean useJack() {
    return getProperty(USE_JACK, Boolean.class);
  }

  @NotNull
  public ProductFlavorElement setUseJack(boolean useJack) {
    return (ProductFlavorElement)setLiteralProperty(USE_JACK, useJack);
  }

  @Nullable
  public String versionCode() {
    Integer intValue = getProperty(VERSION_CODE, Integer.class);
    return intValue != null ? intValue.toString() : getProperty(VERSION_CODE, String.class);
  }

  @NotNull
  public ProductFlavorElement setVersionCode(int versionCode) {
    return (ProductFlavorElement)setLiteralProperty(VERSION_CODE, versionCode);
  }

  @NotNull
  public ProductFlavorElement setVersionCode(@NotNull String versionCode) {
    return (ProductFlavorElement)setLiteralProperty(VERSION_CODE, versionCode);
  }

  @Nullable
  public String versionName() {
    return getProperty(VERSION_NAME, String.class);
  }

  @NotNull
  public ProductFlavorElement setversionName(@NotNull String versionName) {
    return (ProductFlavorElement)setLiteralProperty(VERSION_NAME, versionName);
  }

  @Override
  public void addParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    if (property.equals(PROGUARD_FILES) || property.equals("proguardFile")) {
      addToListElement(PROGUARD_FILES, element);
      return;
    }

    if (property.equals(RES_CONFIGS) || property.equals("resConfig")) {
      addToListElement(RES_CONFIGS, element);
      return;
    }

    if (property.equals("resValue")) {
      if (!(element instanceof LiteralListElement)) {
        return;
      }
      LiteralListElement listElement = (LiteralListElement)element;
      if (listElement.getElements().size() != 3 || listElement.getValues(String.class).size() != 3) {
        return;
      }

      GradleDslElementList elementList = getProperty(RES_VALUES, GradleDslElementList.class);
      if (elementList == null) {
        elementList = new GradleDslElementList(this, RES_VALUES);
        setParsedElement(RES_VALUES, elementList);
      }
      elementList.addParsedElement(element);
    }

    if (property.equals(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS)) {
      if (!(element instanceof LiteralMapElement)) {
        return;
      }
      LiteralMapElement testInstrumentationRunnerArgumentsElement = getProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, LiteralMapElement.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        setParsedElement(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, element);
      } else {
        LiteralMapElement elementsToAdd = (LiteralMapElement)element;
        for (String key : elementsToAdd.getProperties()) {
          GradleDslElement elementToAdd = elementsToAdd.getPropertyElement(key);
          if (elementToAdd != null) {
            testInstrumentationRunnerArgumentsElement.setParsedElement(key, elementToAdd);
          }
        }
      }
      return;
    }

    if (property.equals("testInstrumentationRunnerArgument")) {
      if (!(element instanceof LiteralListElement)) {
        return;
      }
      LiteralListElement literalListElement = (LiteralListElement)element;
      List<LiteralElement> elements = literalListElement.getElements();
      if (elements.size() != 2) {
        return;
      }

      String key = elements.get(0).getValue(String.class);
      if (key == null) {
        return;
      }
      LiteralElement value = elements.get(1);

      LiteralMapElement testInstrumentationRunnerArgumentsElement = getProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, LiteralMapElement.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        testInstrumentationRunnerArgumentsElement = new LiteralMapElement(this, TEST_INSTRUMENTATION_RUNNER_ARGUMENTS);
      }
      testInstrumentationRunnerArgumentsElement.setParsedElement(key, value);
      return;
    }

    super.addParsedElement(property, element);
  }

  private void addToListElement(@NotNull String property, @NotNull GradleDslElement element) {
    GrLiteral[] literalsToAdd =  null;
    if (element instanceof LiteralElement) {
      literalsToAdd = new GrLiteral[]{((LiteralElement)element).getLiteral()};
    } else if (element instanceof LiteralListElement) {
      List<LiteralElement> literalElements = ((LiteralListElement)element).getElements();
      literalsToAdd = new GrLiteral[literalElements.size()];
      for (int i = 0; i < literalElements.size(); i++) {
        literalsToAdd[i] = literalElements.get(i).getLiteral();
      }
    }
    if (literalsToAdd == null) {
      return;
    }

    LiteralListElement literalListElement = getProperty(property, LiteralListElement.class);
    if (literalListElement != null) {
      literalListElement.add(property, literalsToAdd);
      return;
    }

    literalListElement = new LiteralListElement(this, property, literalsToAdd);
    super.addParsedElement(property, literalListElement);
  }

  /**
   * Represents a {@code resValue} statement defined in the product flavor block of the Gradle file.
   */
  public static final class ResValue {
    @NotNull public static final String NAME = "resValue";

    @NotNull private final String myType;
    @NotNull private final String myName;
    @NotNull private final String myValue;

    public ResValue(@NotNull String type, @NotNull String name, @NotNull String value) {
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
      if (!(o instanceof ResValue)) {
        return false;
      }

      ResValue other = (ResValue)o;
      return myType.equals(other.myType) && myName.equals(other.myName) && myValue.equals(other.myValue);
    }

    @Override
    public String toString() {
      return String.format("Type: %1$s, Name: %2$s, Value: %3$s", myType, myName, myValue);
    }

    @NotNull
    private LiteralListElement toLiteralListElement(@Nullable GradleDslElement parent) {
      LiteralElement typeElement = new LiteralElement(parent, NAME);
      typeElement.setValue(myType);
      LiteralElement nameElement = new LiteralElement(parent, NAME);
      nameElement.setValue(myName);
      LiteralElement valueElement = new LiteralElement(parent, NAME);
      valueElement.setValue(myValue);

      LiteralListElement literalListElement = new LiteralListElement(parent, NAME);
      literalListElement.add(typeElement, nameElement, valueElement);
      return literalListElement;
    }
  }
}
