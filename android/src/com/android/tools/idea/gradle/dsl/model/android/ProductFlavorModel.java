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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorPsiElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class ProductFlavorModel {
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

  private final ProductFlavorPsiElement myPsiElement;

  public ProductFlavorModel(@NotNull ProductFlavorPsiElement psiElement) {
    myPsiElement = psiElement;
  }

  @NotNull
  public String name() {
    return myPsiElement.getName();
  }

  @Nullable
  public String applicationId() {
    return myPsiElement.getProperty(APPLICATION_ID, String.class);
  }

  @NotNull
  public ProductFlavorModel setApplicationId(@NotNull String applicationId) {
    myPsiElement.setLiteralProperty(APPLICATION_ID, applicationId);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeApplicationId() {
    myPsiElement.removeProperty(APPLICATION_ID);
    return this;
  }

  @Nullable
  public List<String> consumerProguardFiles() {
    return myPsiElement.getListProperty(CONSUMER_PROGUARD_FILES, String.class);
  }

  @NotNull
  public ProductFlavorModel addConsumerProguardFile(@NotNull String consumerProguardFile) {
    myPsiElement.addToListProperty(CONSUMER_PROGUARD_FILES, consumerProguardFile);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeConsumerProguardFile(@NotNull String consumerProguardFile) {
    myPsiElement.removeFromListProperty(CONSUMER_PROGUARD_FILES, consumerProguardFile);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeAllConsumerProguardFiles() {
    myPsiElement.removeProperty(CONSUMER_PROGUARD_FILES);
    return this;
  }

  @NotNull
  public ProductFlavorModel replaceConsumerProguardFile(@NotNull String oldConsumerProguardFile,
                                                          @NotNull String newConsumerProguardFile) {
    myPsiElement.replaceInListProperty(CONSUMER_PROGUARD_FILES, oldConsumerProguardFile, newConsumerProguardFile);
    return this;
  }

  @Nullable
  public String dimension() {
    return myPsiElement.getProperty(DIMENSION, String.class);
  }

  @NotNull
  public ProductFlavorModel setDimension(@NotNull String dimension) {
    myPsiElement.setLiteralProperty(DIMENSION, dimension);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeDimension() {
    myPsiElement.removeProperty(DIMENSION);
    return this;
  }

  @Nullable
  public Map<String, Object> manifestPlaceholders() {
    return myPsiElement.getMapProperty(MANIFEST_PLACEHOLDERS, Object.class);
  }

  @NotNull
  public ProductFlavorModel setManifestPlaceholder(@NotNull String name, @NotNull String value) {
    myPsiElement.setInMapProperty(MANIFEST_PLACEHOLDERS, name, value);
    return this;
  }

  @NotNull
  public ProductFlavorModel setManifestPlaceholder(@NotNull String name, int value) {
    myPsiElement.setInMapProperty(MANIFEST_PLACEHOLDERS, name, value);
    return this;
  }

  @NotNull
  public ProductFlavorModel setManifestPlaceholder(@NotNull String name, boolean value) {
    myPsiElement.setInMapProperty(MANIFEST_PLACEHOLDERS, name, value);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeManifestPlaceholder(@NotNull String name) {
    myPsiElement.removeFromMapProperty(MANIFEST_PLACEHOLDERS, name);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeAllManifestPlaceholders() {
    myPsiElement.removeProperty(MANIFEST_PLACEHOLDERS);
    return this;
  }

  @Nullable
  public Integer maxSdkVersion() {
    return myPsiElement.getProperty(MAX_SDK_VERSION, Integer.class);
  }

  @NotNull
  public ProductFlavorModel setMaxSdkVersion(int maxSdkVersion) {
    myPsiElement.setLiteralProperty(MAX_SDK_VERSION, maxSdkVersion);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeMaxSdkVersion() {
    myPsiElement.removeProperty(MAX_SDK_VERSION);
    return this;
  }

  @Nullable
  public String minSdkVersion() {
    Integer intValue = myPsiElement.getProperty(MIN_SDK_VERSION, Integer.class);
    return intValue != null ? intValue.toString() : myPsiElement.getProperty(MIN_SDK_VERSION, String.class);
  }

  @NotNull
  public ProductFlavorModel setMinSdkVersion(int minSdkVersion) {
    myPsiElement.setLiteralProperty(MIN_SDK_VERSION, minSdkVersion);
    return this;
  }

  @NotNull
  public ProductFlavorModel setMinSdkVersion(@NotNull String minSdkVersion) {
    myPsiElement.setLiteralProperty(MIN_SDK_VERSION, minSdkVersion);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeMinSdkVersion() {
    myPsiElement.removeProperty(MIN_SDK_VERSION);
    return this;
  }

  @Nullable
  public Boolean multiDexEnabled() {
    return myPsiElement.getProperty(MULTI_DEX_ENABLED, Boolean.class);
  }

  @NotNull
  public ProductFlavorModel setMultiDexEnabled(boolean multiDexEnabled) {
    myPsiElement.setLiteralProperty(MULTI_DEX_ENABLED, multiDexEnabled);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeMultiDexEnabled() {
    myPsiElement.removeProperty(MULTI_DEX_ENABLED);
    return this;
  }

  @Nullable
  public List<String> proguardFiles() {
    return myPsiElement.getListProperty(PROGUARD_FILES, String.class);
  }

  @NotNull
  public ProductFlavorModel addProguardFile(@NotNull String proguardFile) {
    myPsiElement.addToListProperty(PROGUARD_FILES, proguardFile);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeProguardFile(@NotNull String proguardFile) {
    myPsiElement.removeFromListProperty(PROGUARD_FILES, proguardFile);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeAllProguardFiles() {
    myPsiElement.removeProperty(PROGUARD_FILES);
    return this;
  }

  @NotNull
  public ProductFlavorModel replaceProguardFile(@NotNull String oldProguardFile, @NotNull String newProguardFile) {
    myPsiElement.replaceInListProperty(PROGUARD_FILES, oldProguardFile, newProguardFile);
    return this;
  }

  @Nullable
  public List<String> resConfigs() {
    return myPsiElement.getListProperty(RES_CONFIGS, String.class);
  }

  @NotNull
  public ProductFlavorModel addResConfig(@NotNull String resConfig) {
    myPsiElement.addToListProperty(RES_CONFIGS, resConfig);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeResConfig(@NotNull String resConfig) {
    myPsiElement.removeFromListProperty(RES_CONFIGS, resConfig);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeAllResConfigs() {
    myPsiElement.removeProperty(RES_CONFIGS);
    return this;
  }

  @NotNull
  public ProductFlavorModel replaceResConfig(@NotNull String oldResConfig, @NotNull String newResConfig) {
    myPsiElement.replaceInListProperty(RES_CONFIGS, oldResConfig, newResConfig);
    return this;
  }

  @Nullable
  public List<ResValue> resValues() {
    GradlePsiElementList resValues = myPsiElement.getProperty(RES_VALUES, GradlePsiElementList.class);
    if (resValues == null) {
      return null;
    }

    List<ResValue> result = Lists.newArrayList();
    for (GradlePsiElement resValue : resValues.getElements()) {
      if (resValue instanceof GradlePsiLiteralList) {
        GradlePsiLiteralList listElement = (GradlePsiLiteralList)resValue;
        List<String> values = listElement.getValues(String.class);
        if (values.size() == 3) {
          result.add(new ResValue(values.get(0), values.get(1), values.get(2)));
        }
      }
    }
    return result;
  }

  @NotNull
  public ProductFlavorModel addResValue(@NotNull ResValue resValue) {
    GradlePsiElementList elementList = myPsiElement.getProperty(RES_VALUES, GradlePsiElementList.class);
    if (elementList == null) {
      elementList = new GradlePsiElementList(myPsiElement, RES_VALUES);
      myPsiElement.setNewElement(RES_VALUES, elementList);
    }
    elementList.addNewElement(resValue.toLiteralListElement(myPsiElement));
    return this;
  }

  @NotNull
  public ProductFlavorModel removeResValue(@NotNull ResValue resValue) {
    GradlePsiElementList elementList = myPsiElement.getProperty(RES_VALUES, GradlePsiElementList.class);
    if (elementList != null) {
      for (GradlePsiLiteralList element : elementList.getElements(GradlePsiLiteralList.class)) {
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
  public ProductFlavorModel removeAllResValues() {
    myPsiElement.removeProperty(RES_VALUES);
    return this;
  }

  @NotNull
  public ProductFlavorModel replaceResValue(@NotNull ResValue oldResValue, @NotNull ResValue newResValue) {
    GradlePsiElementList elementList = myPsiElement.getProperty(RES_VALUES, GradlePsiElementList.class);
    if (elementList != null) {
      for (GradlePsiLiteralList element : elementList.getElements(GradlePsiLiteralList.class)) {
        List<GradlePsiLiteral> gradlePsiLiterals = element.getElements();
        if (gradlePsiLiterals.size() == 3
            && oldResValue.type().equals(gradlePsiLiterals.get(0).getValue())
            && oldResValue.name().equals(gradlePsiLiterals.get(1).getValue())
            && oldResValue.value().equals(gradlePsiLiterals.get(2).getValue())) {
          gradlePsiLiterals.get(0).setValue(newResValue.type());
          gradlePsiLiterals.get(1).setValue(newResValue.name());
          gradlePsiLiterals.get(2).setValue(newResValue.value());
        }
      }
    }
    return this;
  }

  @Nullable
  public String targetSdkVersion() {
    Integer intValue = myPsiElement.getProperty(TARGET_SDK_VERSION, Integer.class);
    return intValue != null ? intValue.toString() : myPsiElement.getProperty(TARGET_SDK_VERSION, String.class);
  }

  @NotNull
  public ProductFlavorModel setTargetSdkVersion(int targetSdkVersion) {
    myPsiElement.setLiteralProperty(TARGET_SDK_VERSION, targetSdkVersion);
    return this;
  }

  @NotNull
  public ProductFlavorModel setTargetSdkVersion(@NotNull String targetSdkVersion) {
    myPsiElement.setLiteralProperty(TARGET_SDK_VERSION, targetSdkVersion);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeTargetSdkVersion() {
    myPsiElement.removeProperty(TARGET_SDK_VERSION);
    return this;
  }

  @Nullable
  public String testApplicationId() {
    return myPsiElement.getProperty(TEST_APPLICATION_ID, String.class);
  }

  @NotNull
  public ProductFlavorModel setTestApplicationId(@NotNull String testApplicationId) {
    myPsiElement.setLiteralProperty(TEST_APPLICATION_ID, testApplicationId);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeTestApplicationId() {
    myPsiElement.removeProperty(TEST_APPLICATION_ID);
    return this;
  }

  @Nullable
  public Boolean testFunctionalTest() {
    return myPsiElement.getProperty(TEST_FUNCTIONAL_TEST, Boolean.class);
  }

  @NotNull
  public ProductFlavorModel setTestFunctionalTest(boolean testFunctionalTest) {
    myPsiElement.setLiteralProperty(TEST_FUNCTIONAL_TEST, testFunctionalTest);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeTestFunctionalTest() {
    myPsiElement.removeProperty(TEST_FUNCTIONAL_TEST);
    return this;
  }

  @Nullable
  public Boolean testHandleProfiling() {
    return myPsiElement.getProperty(TEST_HANDLE_PROFILING, Boolean.class);
  }

  @NotNull
  public ProductFlavorModel setTestHandleProfiling(boolean testHandleProfiling) {
    myPsiElement.setLiteralProperty(TEST_HANDLE_PROFILING, testHandleProfiling);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeTestHandleProfiling() {
    myPsiElement.removeProperty(TEST_HANDLE_PROFILING);
    return this;
  }

  @Nullable
  public String testInstrumentationRunner() {
    return myPsiElement.getProperty(TEST_INSTRUMENTATION_RUNNER, String.class);
  }

  @NotNull
  public ProductFlavorModel setTestInstrumentationRunner(@NotNull String testInstrumentationRunner) {
    myPsiElement.setLiteralProperty(TEST_INSTRUMENTATION_RUNNER, testInstrumentationRunner);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeTestInstrumentationRunner() {
    myPsiElement.removeProperty(TEST_INSTRUMENTATION_RUNNER);
    return this;
  }

  @Nullable
  public Map<String, String> testInstrumentationRunnerArguments() {
    return myPsiElement.getMapProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, String.class);
  }

  @NotNull
  public ProductFlavorModel setTestInstrumentationRunnerArgument(@NotNull String name, @NotNull String value) {
    myPsiElement.setInMapProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, name, value);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeTestInstrumentationRunnerArgument(@NotNull String name) {
    myPsiElement.removeFromMapProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, name);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeAllTestInstrumentationRunnerArguments() {
    myPsiElement.removeProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS);
    return this;
  }

  @Nullable
  public Boolean useJack() {
    return myPsiElement.getProperty(USE_JACK, Boolean.class);
  }

  @NotNull
  public ProductFlavorModel setUseJack(boolean useJack) {
    myPsiElement.setLiteralProperty(USE_JACK, useJack);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeUseJack() {
    myPsiElement.removeProperty(USE_JACK);
    return this;
  }

  @Nullable
  public String versionCode() {
    Integer intValue = myPsiElement.getProperty(VERSION_CODE, Integer.class);
    return intValue != null ? intValue.toString() : myPsiElement.getProperty(VERSION_CODE, String.class);
  }

  @NotNull
  public ProductFlavorModel setVersionCode(int versionCode) {
    myPsiElement.setLiteralProperty(VERSION_CODE, versionCode);
    return this;
  }

  @NotNull
  public ProductFlavorModel setVersionCode(@NotNull String versionCode) {
    myPsiElement.setLiteralProperty(VERSION_CODE, versionCode);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeVersionCode() {
    myPsiElement.removeProperty(VERSION_CODE);
    return this;
  }

  @Nullable
  public String versionName() {
    return myPsiElement.getProperty(VERSION_NAME, String.class);
  }

  @NotNull
  public ProductFlavorModel setVersionName(@NotNull String versionName) {
    myPsiElement.setLiteralProperty(VERSION_NAME, versionName);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeVersionName() {
    myPsiElement.removeProperty(VERSION_NAME);
    return this;
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
    private GradlePsiLiteralList toLiteralListElement(@NotNull GradlePsiElement parent) {
      GradlePsiLiteral typeElement = new GradlePsiLiteral(parent, NAME);
      typeElement.setValue(myType);
      GradlePsiLiteral nameElement = new GradlePsiLiteral(parent, NAME);
      nameElement.setValue(myName);
      GradlePsiLiteral valueElement = new GradlePsiLiteral(parent, NAME);
      valueElement.setValue(myValue);

      GradlePsiLiteralList gradlePsiLiteralList = new GradlePsiLiteralList(parent, NAME);
      gradlePsiLiteralList.add(typeElement, nameElement, valueElement);
      return gradlePsiLiteralList;
    }
  }
}
