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
package com.android.tools.idea.gradle.dsl.dependencies.external;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.dependencies.Dependencies;
import com.android.tools.idea.gradle.dsl.dependencies.Dependency;
import com.android.tools.idea.gradle.dsl.dependencies.ExternalDependencySpec;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A Gradle external dependency. There are two notations supported for declaring a dependency on an external module. One is a string
 * notation formatted this way:
 * <pre>
 * configurationName "group:name:version:classifier@extension"
 * </pre>
 * The other is a map notation:
 * <pre>
 * configurationName group: group:, name: name, version: version, classifier: classifier, ext: extension
 * </pre>
 * For more details, visit:
 * <ol>
 * <li><a href="https://docs.gradle.org/2.4/userguide/dependency_management.html">Gradle Dependency Management</a></li>
 * <li><a href="https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.dsl.DependencyHandler.html">Gradle
 * DependencyHandler</a></li>
 * </ol>
 */
public abstract class ExternalDependency extends Dependency {
  @NotNull protected final ExternalDependencySpec mySpec;

  @Nullable private String myNewVersion;

  protected ExternalDependency(@NotNull Dependencies parent,
                               @NotNull GrMethodCall methodCall,
                               @NotNull String configurationName,
                               @NotNull ExternalDependencySpec spec) {
    super(parent, methodCall, configurationName);
    mySpec = spec;
  }

  @Nullable
  public String group() {
    return mySpec.group;
  }

  @NotNull
  public String name() {
    return mySpec.name;
  }

  @Nullable
  public String version() {
    return mySpec.version;
  }

  public void version(@NotNull String version) {
    myNewVersion = version;
    setModified(true);
  }

  @Nullable
  public String classifier() {
    return mySpec.classifier;
  }

  @Nullable
  public String extension() {
    return mySpec.extension;
  }

  @NotNull
  public String compactNotation() {
    return mySpec.toString();
  }

  @VisibleForTesting
  @NotNull
  public ExternalDependencySpec spec() {
    return mySpec;
  }

  @Override
  protected void apply() {
    applyVersionChange();
  }

  private void applyVersionChange() {
    if (myNewVersion == null) {
      return;
    }
    applyVersion(myNewVersion);
    reset();
  }

  protected abstract void applyVersion(@NotNull String newVersion);

  @Override
  protected void reset() {
    myNewVersion = null;
  }

  /**
   * Attempts to parse an external dependency.
   *
   * @param parent            represents the parent "dependencies" block.
   * @param methodCall        the PSI element containing the complete dependency declaration.
   * @param configurationName the PSI element containing the dependency's configuration name.
   * @param arguments         the arguments of {@code methodCall}. In the case of "compact notation", each argument may represent an
   *                          individual dependency. In the case of "map notation", each argument may represent an entry in the map
   *                          (e.g. "group").
   * @return the parsed dependencies, or an empty list if the passed PSI elements do not belong to dependency declarations.
   */
  @NotNull
  public static List<Dependency> parse(@NotNull Dependencies parent,
                                       @NotNull GrMethodCall methodCall,
                                       @NotNull GrReferenceExpression configurationName,
                                       @NotNull GroovyPsiElement[] arguments) {
    if (arguments.length == 0) {
      return Collections.emptyList();
    }
    GroovyPsiElement first = arguments[0];
    if (first instanceof GrLiteral) {
      return parseCompactNotation(parent, methodCall, configurationName, arguments);
    }
    if (first instanceof GrNamedArgument) {
      Dependency dependency = parseMapNotation(parent, methodCall, configurationName, arguments);
      if (dependency != null) {
        return Collections.singletonList(dependency);
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  private static List<Dependency> parseCompactNotation(@NotNull Dependencies parent,
                                                       @NotNull GrMethodCall methodCall,
                                                       @NotNull GrReferenceExpression configurationName,
                                                       @NotNull GroovyPsiElement[] arguments) {
    List<Dependency> dependencies = Lists.newArrayList();
    for (GroovyPsiElement argument : arguments) {
      if (argument instanceof GrLiteral) {
        GrLiteral literal = (GrLiteral)argument;
        ExternalDependency dependency = CompactNotation.parse(parent, methodCall, configurationName.getText(), literal);
        if (dependency != null) {
          dependencies.add(dependency);
        }
      }
    }
    return dependencies;
  }

  @Nullable
  private static ExternalDependency parseMapNotation(@NotNull Dependencies parent,
                                                     @NotNull GrMethodCall methodCall,
                                                     @NotNull GrReferenceExpression configurationName,
                                                     @NotNull GroovyPsiElement[] arguments) {
    List<GrNamedArgument> namedArguments = Lists.newArrayList();
    for (GroovyPsiElement argument : arguments) {
      if (argument instanceof GrNamedArgument) {
        namedArguments.add((GrNamedArgument)argument);
      }
    }
    if (namedArguments.isEmpty()) {
      return null;
    }
    GrNamedArgument[] namedArgumentArray = namedArguments.toArray(new GrNamedArgument[namedArguments.size()]);
    ExternalDependency dependency = MapNotation.parse(parent, methodCall, configurationName.getText(), namedArgumentArray, null);
    if (dependency != null) {
      return dependency;
    }
    return null;
  }

  @NotNull
  public static List<Dependency> parse(@NotNull Dependencies parent, @NotNull GrMethodCallExpression expression) {
    GrReferenceExpression configurationName = findValidConfigurationNameExpression(expression);
    if (configurationName == null) {
      return Collections.emptyList();
    }

    GrArgumentList argumentList = expression.getArgumentList();

    List<Dependency> dependencies = Lists.newArrayList();
    for (GroovyPsiElement arg : argumentList.getAllArguments()) {
      if (!(arg instanceof GrListOrMap)) {
        continue;
      }
      GrListOrMap listOrMap = (GrListOrMap)arg;
      if (!listOrMap.isMap()) {
        continue;
      }
      GrNamedArgument[] namedArgs = listOrMap.getNamedArguments();
      if (namedArgs.length > 0) {
        ExternalDependency dependency = MapNotation.parse(parent, expression, configurationName.getText(), namedArgs, listOrMap);
        if (dependency != null) {
          dependencies.add(dependency);
        }
      }
    }
    return dependencies;
  }

  /**
   * Removes the given argument from {@link #getMethodCall()} if the {@code GrMethodCall} has more than one arguments.
   * <p>
   * Having more than one arguments indicates that there are multiple dependency declarations using a single configuration name, as follows:
   * <pre>
   *  runtime 'org.springframework:spring-core:2.5', 'org.springframework:spring-aop:2.5'
   *
   *  runtime(
   *    [group: 'com.google.code.guice', name: 'guice', version: '1.0'],
   *    [group: 'com.google.guava', name: 'guava', version: '18.0'],
   *    [group: 'com.android.support', name: 'appcompat-v7', version: '22.1.1']
   *  )
   * </pre>
   * </p>
   * @param toRemove the argument to remove, representing a dependency declaration.
   * @return {@code true} if the argument was removed; {@code false} otherwise.
   */
  protected boolean removeArgumentIfMoreThanOne(@NotNull GroovyPsiElement toRemove) {
    GrMethodCall methodCall = getMethodCall();
    GroovyPsiElement[] arguments = methodCall.getArgumentList().getAllArguments();

    if (arguments.length > 1) {
      List<String> newArguments = Lists.newArrayList();
      for (GroovyPsiElement argument : arguments) {
        if (argument != toRemove) {
          newArguments.add(argument.getText());
        }
      }
      if (!newArguments.isEmpty()) {
        // If there are any arguments left, carefully remove the argument that represents this dependency.
        String text = Joiner.on(',').join(newArguments);
        if (toRemove instanceof GrListOrMap) {
          text = '(' + text + ')';
        }

        Project project = toRemove.getProject();
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
        GrArgumentList newArgumentList = factory.createArgumentListFromText(text);
        methodCall.getArgumentList().replaceWithArgumentList(newArgumentList);

        CodeStyleManager.getInstance(project).reformat(methodCall);
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return mySpec.toString();
  }

  @Override
  @NotNull
  protected Collection<GradleDslElement> getChildren() {
    return ImmutableList.of();
  }
}
