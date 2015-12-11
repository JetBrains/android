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
package com.android.tools.idea.gradle.dsl.model.build;

import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import org.jetbrains.annotations.NotNull;

public class BuildScriptModel extends GradleDslBlockModel {

  public BuildScriptModel(@NotNull BuildScriptDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  public DependenciesModel dependencies() {
    DependenciesDslElement dependenciesDslElement = myDslElement.getProperty(DependenciesDslElement.NAME, DependenciesDslElement.class);
    if (dependenciesDslElement == null) {
      dependenciesDslElement = new DependenciesDslElement(myDslElement);
      myDslElement.setNewElement(DependenciesDslElement.NAME, dependenciesDslElement);
    }
    return new DependenciesModel(dependenciesDslElement);
  }
}
