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
package com.android.tools.idea.gradle.dsl.model.repositories;

import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RepositoriesModel extends GradleDslBlockModel {
  public RepositoriesModel(@NotNull RepositoriesDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  public List<RepositoryModel> repositories() {
    GradleDslElementList repositoriesElementList = myDslElement.getProperty(RepositoriesDslElement.NAME, GradleDslElementList.class);
    if (repositoriesElementList == null) {
      return ImmutableList.of();
    }

    List<RepositoryModel> result = Lists.newArrayList();
    for (GradleDslElement element : repositoriesElementList.getElements()) {
      if (element instanceof GradleDslMethodCall) {
        if (MavenCentralRepositoryModel.MAVEN_CENTRAL.equals(element.getName())) {
          result.add(new MavenCentralRepositoryModel());
        }
        else if (JCenterDefaultRepositoryModel.JCENTER.equals(element.getName())) {
          result.add(new JCenterDefaultRepositoryModel());
        }
      }
      else if (element instanceof MavenRepositoryDslElement) {
        if (MavenRepositoryDslElement.MAVEN.equals(element.getName())) {
          result.add(new MavenRepositoryModel((MavenRepositoryDslElement)element));
        }
        else if (MavenRepositoryDslElement.JCENTER.equals(element.getName())) {
          result.add(new JCenterRepositoryModel((MavenRepositoryDslElement)element));
        }
      }
    }
    return result;
  }
}
