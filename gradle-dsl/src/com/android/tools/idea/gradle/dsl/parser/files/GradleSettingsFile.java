/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.files;

import static com.android.tools.idea.gradle.dsl.parser.include.IncludeDslElement.INCLUDE;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.dsl.model.GradleBlockModelMap;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.ElementState;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementEnum;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.include.IncludeDslElement;
import com.android.tools.idea.gradle.dsl.parser.plugins.PluginsDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.android.tools.idea.gradle.dsl.parser.settings.DependencyResolutionManagementDslElement;
import com.android.tools.idea.gradle.dsl.parser.settings.PluginManagementDslElement;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.parser.include.IncludeDslElement.INCLUDE;
import static com.android.tools.idea.gradle.dsl.parser.settings.DefaultsDslElement.DEFAULTS_DSL_ELEMENT;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

public class GradleSettingsFile extends GradleScriptFile {
  public GradleSettingsFile(@NotNull VirtualFile file,
                            @NotNull Project project,
                            @NotNull String moduleName,
                            @NotNull BuildModelContext context) {
    super(file, project, moduleName, context);
    populateGlobalProperties();
  }

  // org.gradle.api.initialization.resolve.RepositoriesMode
  public static Map<String,String> REPOSITORIES_MODE_ENUM_MAP = Stream.of("FAIL_ON_PROJECT_REPOS", "PREFER_PROJECT", "PREFER_SETTINGS")
    .map(s -> new String[] { s, "RepositoriesMode." + s })
    .collect(toImmutableMap(o -> o[0], o -> o[1]));

  private void populateGlobalProperties() {
    GradleDslElement repositoriesMode = new GradleDslElementEnum(this, GradleNameElement.fake("RepositoriesMode"), REPOSITORIES_MODE_ENUM_MAP);
    myGlobalProperties.addElement(repositoriesMode, ElementState.DEFAULT, false);
  }

  public static final ImmutableMap<String, PropertiesElementDescription<?>> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"dependencyResolutionManagement", DependencyResolutionManagementDslElement.DEPENDENCY_RESOLUTION_MANAGEMENT},
    {"pluginManagement", PluginManagementDslElement.PLUGIN_MANAGEMENT_DSL_ELEMENT},
    {"plugins", PluginsDslElement.PLUGINS},
  }).collect(toImmutableMap(data -> (String) data[0], data -> (PropertiesElementDescription) data[1]));

  public static final ImmutableMap<String, PropertiesElementDescription<?>> CHILD_PROPERTIES_DECLARATIVE_MAP = Stream.of(new Object[][]{
    {"dependencyResolutionManagement", DependencyResolutionManagementDslElement.DEPENDENCY_RESOLUTION_MANAGEMENT},
    {"pluginManagement", PluginManagementDslElement.PLUGIN_MANAGEMENT_DSL_ELEMENT},
    {"defaults", DEFAULTS_DSL_ELEMENT},
    {"plugins", PluginsDslElement.PLUGINS},
  }).collect(toImmutableMap(data -> (String) data[0], data -> (PropertiesElementDescription) data[1]));

  @Override
  public ImmutableMap<String, PropertiesElementDescription<?>> getChildPropertiesElementsDescriptionMap(
    GradleDslNameConverter.Kind kind
  ) {
    if(kind == GradleDslNameConverter.Kind.DECLARATIVE)
      return CHILD_PROPERTIES_DECLARATIVE_MAP;

    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    if (INCLUDE.name.equals(element.getName())) {
      IncludeDslElement includeDslElement = getPropertyElement(INCLUDE);
      if (includeDslElement == null) {
        includeDslElement = new IncludeDslElement(this, GradleNameElement.create(INCLUDE.name));
        super.addParsedElement(includeDslElement);
      }
      includeDslElement.addParsedElement(element);
      return;
    }
    super.addParsedElement(element);
  }
}