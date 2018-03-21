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

import com.android.tools.idea.gradle.dsl.api.BuildScriptModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModelImpl;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoriesModelImpl;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import static com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement.DEPENDENCIES_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement.REPOSITORIES_BLOCK_NAME;

public class BuildScriptModelImpl extends GradleDslBlockModel implements BuildScriptModel {

  public BuildScriptModelImpl(@NotNull BuildScriptDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  @Override
  public DependenciesModel dependencies() {
    DependenciesDslElement dependenciesDslElement = myDslElement.getPropertyElement(DEPENDENCIES_BLOCK_NAME, DependenciesDslElement.class);
    if (dependenciesDslElement == null) {
      dependenciesDslElement = new DependenciesDslElement(myDslElement);
      myDslElement.setNewElement(DEPENDENCIES_BLOCK_NAME, dependenciesDslElement);
    }
    return new DependenciesModelImpl(dependenciesDslElement);
  }

  @NotNull
  @Override
  public RepositoriesModel repositories() {
    RepositoriesDslElement repositoriesDslElement = myDslElement.getPropertyElement(REPOSITORIES_BLOCK_NAME, RepositoriesDslElement.class);
    if (repositoriesDslElement == null) {
      repositoriesDslElement = new RepositoriesDslElement(myDslElement);
      myDslElement.setNewElement(REPOSITORIES_BLOCK_NAME, repositoriesDslElement);
    }
    return new RepositoriesModelImpl(repositoriesDslElement);
  }

  /**
   * Removes property {@link RepositoriesDslElement#REPOSITORIES_BLOCK_NAME}.
   */
  @Override
  @TestOnly
  public void removeRepositoriesBlocks() {
    myDslElement.removeProperty(REPOSITORIES_BLOCK_NAME);
  }
}
