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
package com.android.tools.idea.gradle.dsl.parser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

import java.util.List;

public class DependenciesElement implements GradleDslElement {
  @NotNull private final GrClosableBlock myPsiElement;
  @NotNull private final List<ExternalDependencyElement> myExternalDependencies = Lists.newArrayList();

  DependenciesElement(@NotNull GrClosableBlock psiElement) {
    myPsiElement = psiElement;
  }

  void addAll(@NotNull List<DependencyElement> dependencies) {
    for (DependencyElement dependency : dependencies) {
      add(dependency);
    }
  }

  private void add(@NotNull DependencyElement dependency) {
    if (dependency instanceof ExternalDependencyElement) {
      myExternalDependencies.add((ExternalDependencyElement)dependency);
    }
  }

  @NotNull
  public List<ExternalDependencyElement> getExternalDependenciesView() {
    return ImmutableList.copyOf(myExternalDependencies);
  }

  /**
   * Adds a new external dependency to the build.gradle file. Please note the new dependency will <b>not</b> be included in
   * {@link #getExternalDependenciesView()}, unless you invoke {@link GradleBuildFile#reparse()}.
   *
   * @param configurationName the name of the configuration (e.g. "compile", "compileTest", "runtime", etc.)
   * @param compactNotation the dependency in "compact" notation: "group:name:version:classifier@extension".
   */
  public void addExternalDependency(@NotNull String configurationName, @NotNull String compactNotation) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    Project project = myPsiElement.getProject();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    GrStatement statement = factory.createStatementFromText(configurationName + " '" + compactNotation + "'");
    myPsiElement.addStatementBefore(statement, null);
    CodeStyleManager.getInstance(project).reformat(statement);
  }
}
