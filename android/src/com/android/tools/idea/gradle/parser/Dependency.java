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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.parser.BuildFileKey.escapeLiteralString;

/**
 * Represents a dependency statement in a Gradle build file. Dependencies have a scope (which defines what types of compiles the
 * dependency is relevant for), a type (e.g. Maven, local jar file, etc.), and dependency-specific data.
 */
public class Dependency extends BuildFileStatement {
  private static final Logger LOG = Logger.getInstance(Dependency.class);

  public enum Scope {
    COMPILE("Compile", "compile"),
    PROVIDED("Provided", "provided"),
    APK("APK", "apk"),
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
    FILETREE,
    EXTERNAL,
    MODULE
  }

  public Scope scope;
  public Type type;
  public Object data;

  public Dependency(@NotNull Scope scope, @NotNull Type type, @NotNull Object data) {
    this.scope = scope;
    this.type = type;
    this.data = data;
  }

  @Override
  @NotNull
  public List<PsiElement> getGroovyElements(@NotNull GroovyPsiElementFactory factory) {
    String extraGroovyCode;
    switch (type) {
      case EXTERNAL:
        extraGroovyCode =  " '" + escapeLiteralString(data) + "'";
        break;
      case MODULE:
        extraGroovyCode =  " project('" + escapeLiteralString(data) + "')";
        break;
      case FILES:
        extraGroovyCode = " files('" + escapeLiteralString(data) + "')";
        break;
      case FILETREE:
        extraGroovyCode = " fileTree(" + GradleGroovyFile.convertMapToGroovySource((Map<String, Object>)data) + ")";
        break;
      default:
        extraGroovyCode = "";
        break;
    }
    return ImmutableList.of(
      (PsiElement)factory.createStatementFromText(scope.getGroovyMethodCall() + extraGroovyCode)
    );
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) { return true; }
    if (o == null || getClass() != o.getClass()) { return false; }

    Dependency that = (Dependency)o;

    if (data != null ? !data.equals(that.data) : that.data != null) { return false; }
    if (scope != that.scope) { return false; }
    if (type != that.type) { return false; }

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(scope, type, data);
  }

  @Override
  public String toString() {
    return "Dependency {" +
           "myScope=" + scope +
           ", myType=" + type +
           ", myData='" + data + '\'' +
           '}';
  }

  public @NotNull String getValueAsString() {
    return data.toString();
  }

  public static ValueFactory getFactory() {
    return new DependencyFactory();
  }

  private static class DependencyFactory extends BuildFileStatementFactory {
    @NotNull
    @Override
    public List<BuildFileStatement> getValues(@NotNull PsiElement statement) {
      if (!(statement instanceof GrMethodCall)) {
          return getUnparseableStatements(statement);
      }
      GrMethodCall call = (GrMethodCall)statement;
        Dependency.Scope scope = Dependency.Scope.fromMethodCall(GradleGroovyFile.getMethodCallName(call));
      if (scope == null) {
        return getUnparseableStatements(statement);
      }
      GrArgumentList argumentList = call.getArgumentList();
      if (argumentList == null) {
        return getUnparseableStatements(statement);
      }
      List<BuildFileStatement> dependencies = Lists.newArrayList();
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
          } else if ("fileTree".equals(methodName)) {
            Map<String, Object> values = GradleGroovyFile.getNamedArgumentValues(method);
            dependencies.add(new Dependency(scope, Type.FILETREE, values));
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
          return getUnparseableStatements(statement);
        }
      }
      return dependencies;
    }
  }
}
