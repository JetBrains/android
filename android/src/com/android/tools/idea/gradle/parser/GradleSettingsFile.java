/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.parser;

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.Facets;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.Arrays;

/**
 * GradleSettingsFile uses PSI to parse settings.gradle files and provides high-level methods to read and mutate the file.
 *
 * Note that if you do any mutations on the PSI structure you must be inside a write action. See
 * {@link com.intellij.util.ActionRunner#runInsideWriteAction}.
 */
public class GradleSettingsFile extends GradleGroovyFile {

  public static final String INCLUDE_METHOD = "include";
  private static final Iterable<String> EMPTY_ITERABLE = Arrays.asList(new String[] {});

  public GradleSettingsFile(@NotNull VirtualFile file, @NotNull Project project) {
    super(file, project);
  }

  /**
   * Adds a reference to the module to the settings file, if there is not already one. Must be run inside a write action.
   */
  public void addModule(@NotNull Module module) {
    checkInitialized();
    addModule(getModuleGradlePath(module));
  }

  /**
   * Adds a reference to the module to the settings file, if there is not already one. The module path must be colon separated, with a
   * leading colon, e.g. ":project:subproject". Must be run inside a write action.
   */
  public void addModule(@NotNull String modulePath) {
    checkInitialized();
    commitDocumentChanges();
    for (GrMethodCall includeStatement : getMethodCalls(myGroovyFile, INCLUDE_METHOD)) {
      for (GrLiteral lit : getLiteralArguments(includeStatement)) {
        if (modulePath.equals(lit.getValue())) {
          return;
        }
      }
    }
    GrMethodCall includeStatement = getMethodCall(myGroovyFile, INCLUDE_METHOD);
    if (includeStatement != null) {
      GrArgumentList argList = includeStatement.getArgumentList();
      if (argList != null) {
        GrLiteral literal = GroovyPsiElementFactory.getInstance(myProject).createLiteralFromValue(modulePath);
        argList.addAfter(literal, argList.getLastChild());
        return;
      }
    }
    GrStatement statement =
      GroovyPsiElementFactory.getInstance(myProject).createStatementFromText(INCLUDE_METHOD + " '" + modulePath + "'");
    myGroovyFile.add(statement);
  }

  /**
   * Removes the reference to the module from the settings file, if present. Must be run inside a write action.
   */
  public void removeModule(@NotNull Module module) {
    checkInitialized();
    removeModule(getModuleGradlePath(module));
  }

  /**
   * Removes the reference to the module from the settings file, if present. The module path must be colon separated, with a
   * leading colon, e.g. ":project:subproject". Must be run inside a write action.
   */
  public void removeModule(String modulePath) {
    checkInitialized();
    commitDocumentChanges();
    for (GrMethodCall includeStatement : getMethodCalls(myGroovyFile, INCLUDE_METHOD)) {
      for (GrLiteral lit : getLiteralArguments(includeStatement)) {
        if (modulePath.equals(lit.getValue())) {
          lit.delete();
          if (getArguments(includeStatement).length == 0) {
            includeStatement.delete();
            // If this happens we will fall through both for loops before we get into iteration trouble. We want to keep iterating in
            // case the module is added more than once (via hand-editing of the file).
          }
        }
      }
    }
  }

  /**
   * Returns all of the literal-typed arguments of all include statements in the file.
   */
  public Iterable<String> getModules() {
    checkInitialized();
    return Iterables.concat(Iterables.transform(getMethodCalls(myGroovyFile, INCLUDE_METHOD),
                                                new Function<GrMethodCall, Iterable<String>>() {
      @Override
      public Iterable<String> apply(@Nullable GrMethodCall input) {
        if (input != null) {
          return Iterables.transform(getLiteralArgumentValues(input), new Function<Object, String>() {
            @Override
            public String apply(@Nullable Object input) {
              return input != null ? input.toString() : null;
            }
          });
        } else {
          return EMPTY_ITERABLE;
        }
      }
    }));
  }

  @NotNull
  private static String getModuleGradlePath(@NotNull Module module) {
    AndroidGradleFacet androidGradleFacet = Facets.getFirstFacetOfType(module, AndroidGradleFacet.TYPE_ID);
    if (androidGradleFacet == null) {
      String msg = String.format("The module '%s$1' does not have the 'Android-Gradle' facet", module.getName());
      throw new IllegalStateException(msg);
    }
    return androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
  }
}
