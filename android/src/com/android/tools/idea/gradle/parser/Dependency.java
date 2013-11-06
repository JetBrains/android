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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.util.List;

/**
 * Represents a dependency statement in a Gradle build file. Dependencies have a scope (which defines what types of compiles the
 * dependency is relevant for), a type (e.g. Maven, local jar file, etc.), and dependency-specific data.
 */
public class Dependency {
  private static final Logger LOG = Logger.getInstance(Dependency.class);

  public enum Scope {
    COMPILE("Compile", "compile"),
    INSTRUMENT_TEST_COMPILE("Test compile", "instrumentTestCompile"),
    DEBUG_COMPILE("Debug compile", "debugCompile"),
    RELEASE_COMPILE("Release compile", "releaseCompile");

    private final String myGroovyMethodCall;
    private final String myDisplayName;

    Scope(@NotNull String displayName, @NotNull String groovyMethodCall) {
      myDisplayName = displayName;
      myGroovyMethodCall = groovyMethodCall;
    }

    public String getGroovyMethodCall() {
      return myGroovyMethodCall;
    }

    @Nullable
    public static Scope fromMethodCall(@NotNull String methodCall) {
      for (Scope scope : values()) {
        if (scope.myGroovyMethodCall.equals(methodCall)) {
          return scope;
        }
      }
      return null;
    }

    @NotNull
    public String getDisplayName() {
      return myDisplayName;
    }

    @Override
    @NotNull
    public String toString() {
      return myDisplayName;
    }
  }

  public enum Type {
    FILES,
    EXTERNAL,
    MODULE
  }

  public Scope scope;
  public Type type;
  public String data;

  public Dependency(@NotNull Scope scope, @NotNull Type type, @NotNull String data) {
    this.scope = scope;
    this.type = type;
    this.data = data;
  }

  @Override
  public String toString() {
    return "Dependency {" +
           "myScope=" + scope +
           ", myType=" + type +
           ", myData='" + data + '\'' +
           '}';
  }

  public static BuildFileKey.ValueFactory<Dependency> getFactory() {
    return new DependencyFactory();
  }

  private static class DependencyFactory implements BuildFileKey.ValueFactory<Dependency> {
    @NotNull
    @Override
    public List<Dependency> getValues(@NotNull GrStatementOwner closure) {
      List<Dependency> dependencies = Lists.newArrayList();
      for (GrMethodCall call : GradleGroovyFile.getMethodCalls(closure)) {
        Dependency.Scope scope = Dependency.Scope.fromMethodCall(GradleGroovyFile.getMethodCallName(call));
        if (scope == null) {
          continue;
        }
        GrArgumentList argumentList = call.getArgumentList();
        if (argumentList == null) {
          continue;
        }
        for (GroovyPsiElement element : argumentList.getAllArguments()) {
          if (element instanceof GrMethodCall) {
            GrMethodCall method = (GrMethodCall)element;
            String methodName = GradleGroovyFile.getMethodCallName(method);
            if ("project".equals(methodName)) {
              Object value = GradleGroovyFile.getFirstLiteralArgumentValue(method);
              if (value != null) {
                dependencies.add(new Dependency(scope, Dependency.Type.MODULE, value.toString()));
              }
            } else if ("files".equals(methodName)) {
              for (Object o : GradleGroovyFile.getLiteralArgumentValues(method)) {
                dependencies.add(new Dependency(scope, Dependency.Type.FILES, o.toString()));
              }
            } else {
              // Oops, we didn't know how to parse this.
              LOG.warn("Didn't know how to parse dependency method call " + methodName);
            }
          } else if (element instanceof GrLiteral) {
            Object value = ((GrLiteral)element).getValue();
            if (value != null) {
              dependencies.add(new Dependency(scope, Dependency.Type.EXTERNAL, value.toString()));
            }
          } else {
            LOG.warn("Didn't know how to parse dependency statement type " + element.getClass().getName());
          }
        }
      }
      return dependencies;
    }

    @Override
    public void setValues(@NotNull GrStatementOwner closure, @NotNull List<Dependency> values) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(closure.getProject());
      closure = (GrStatementOwner)closure.replace(factory.createClosureFromText("{}"));
      for (Dependency dependency : values) {
        switch (dependency.type) {
          case EXTERNAL:
            closure.addStatementBefore(
              factory.createStatementFromText(dependency.scope.getGroovyMethodCall() + " '" + dependency.data + "'"), null);
            break;
          case MODULE:
            closure.addStatementBefore(
              factory.createStatementFromText(dependency.scope.getGroovyMethodCall() + " project('" + dependency.data + "')"), null);
            break;
          case FILES:
            closure.addStatementBefore(
              factory.createStatementFromText(dependency.scope.getGroovyMethodCall() + " files('" + dependency.data + "')"), null);
            break;
        }
      }
      GradleGroovyFile.reformatClosure(closure);
    }

    @Override
    public boolean canParseValue(@NotNull GrStatementOwner closure) {
      int callsWeUnderstand = 0;
      for (Dependency.Scope scope : Dependency.Scope.values()) {
        callsWeUnderstand += Iterables.size(GradleGroovyFile.getMethodCalls(closure, scope.getGroovyMethodCall()));
      }
      return (callsWeUnderstand == closure.getStatements().length);

      // TODO: Parse the argument list for each method call and ensure it's kosher.
      // Or, maybe just do the validation in getValue???

    }
  }
}
