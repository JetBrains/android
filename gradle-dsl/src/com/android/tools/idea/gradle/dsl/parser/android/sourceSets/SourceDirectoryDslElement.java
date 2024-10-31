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
package com.android.tools.idea.gradle.dsl.parser.android.sourceSets;

import static com.android.tools.idea.gradle.dsl.model.android.sourceSets.SourceDirectoryModelImpl.EXCLUDES;
import static com.android.tools.idea.gradle.dsl.model.android.sourceSets.SourceDirectoryModelImpl.INCLUDES;
import static com.android.tools.idea.gradle.dsl.model.android.sourceSets.SourceDirectoryModelImpl.SRC_DIRS;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class SourceDirectoryDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<SourceDirectoryDslElement> AIDL =
    new PropertiesElementDescription<>("aidl",
                                       SourceDirectoryDslElement.class,
                                       SourceDirectoryDslElement::new);
  public static final PropertiesElementDescription<SourceDirectoryDslElement> ASSETS =
    new PropertiesElementDescription<>("assets",
                                       SourceDirectoryDslElement.class,
                                       SourceDirectoryDslElement::new);
  public static final PropertiesElementDescription<SourceDirectoryDslElement> JAVA =
    new PropertiesElementDescription<>("java",
                                       SourceDirectoryDslElement.class,
                                       SourceDirectoryDslElement::new);
  public static final PropertiesElementDescription<SourceDirectoryDslElement> JNI =
    new PropertiesElementDescription<>("jni",
                                       SourceDirectoryDslElement.class,
                                       SourceDirectoryDslElement::new);
  public static final PropertiesElementDescription<SourceDirectoryDslElement> JNI_LIBS =
    new PropertiesElementDescription<>("jniLibs",
                                       SourceDirectoryDslElement.class,
                                       SourceDirectoryDslElement::new);
  public static final PropertiesElementDescription<SourceDirectoryDslElement> ML_MODELS =
    new PropertiesElementDescription<>("mlModels",
                                       SourceDirectoryDslElement.class,
                                       SourceDirectoryDslElement::new);
  public static final PropertiesElementDescription<SourceDirectoryDslElement> RENDERSCRIPT =
    new PropertiesElementDescription<>("renderscript",
                                       SourceDirectoryDslElement.class,
                                       SourceDirectoryDslElement::new);
  public static final PropertiesElementDescription<SourceDirectoryDslElement> RES =
    new PropertiesElementDescription<>("res",
                                       SourceDirectoryDslElement.class,
                                       SourceDirectoryDslElement::new);
  public static final PropertiesElementDescription<SourceDirectoryDslElement> RESOURCES =
    new PropertiesElementDescription<>("resources",
                                       SourceDirectoryDslElement.class,
                                       SourceDirectoryDslElement::new);
  public static final PropertiesElementDescription<SourceDirectoryDslElement> SHADERS =
    new PropertiesElementDescription<>("shaders",
                                       SourceDirectoryDslElement.class,
                                       SourceDirectoryDslElement::new);

  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"excludes", property, EXCLUDES, VAL},
    {"exclude", atLeast(0), EXCLUDES, OTHER},
    {"includes", property, INCLUDES, VAL},
    {"include", atLeast(0), INCLUDES, OTHER},
    {"srcDirs", property, SRC_DIRS, VAL},
    {"srcDirs", atLeast(0), SRC_DIRS, OTHER},
    {"setSrcDirs", exactly(1), SRC_DIRS, SET},
    {"srcDir", exactly(1), SRC_DIRS, OTHER}
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"excludes", property, EXCLUDES, VAL},
    {"exclude", atLeast(0), EXCLUDES, OTHER},
    {"includes", property, INCLUDES, VAL},
    {"include", atLeast(0), INCLUDES, OTHER},
    {"srcDirs", property, SRC_DIRS, VAR},
    {"srcDirs", atLeast(0), SRC_DIRS, OTHER},
    {"srcDir", exactly(1), SRC_DIRS, OTHER}
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"excludes", property, EXCLUDES, VAL},
    {"includes", property, INCLUDES, VAL},
    {"srcDirs", property, SRC_DIRS, VAR},
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  public SourceDirectoryDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();

    if (property.equals("setSrcDirs")) {
      removeProperty(SRC_DIRS);
      addToParsedExpressionList(SRC_DIRS, element);
      return;
    }

    if (property.equals("srcDirs") || property.equals("srcDir")) {
      addToParsedExpressionList(SRC_DIRS, element);
      return;
    }

    if (property.equals("include")) {
      addToParsedExpressionList(INCLUDES, element);
      return;
    }

    if (property.equals("exclude")) {
      addToParsedExpressionList(EXCLUDES, element);
      return;
    }

    super.addParsedElement(element);
  }
}
