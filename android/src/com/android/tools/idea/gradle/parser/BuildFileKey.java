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
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
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
 * Enumerates the values we know how to parse out of the build file. This includes values that only occur in one place
 * and are always rooted at the file root (e.g. android/buildToolsVersion) and values that can occur at different places (e.g.
 * signingConfig, which can occur in defaultConfig, a build type, or a flavor. When retrieving keys that are of the former type, you
 * can call {@link #getValue(BuildFileKey)}, which uses the build file itself as
 * the root; in the case of the latter, call
 * {@link #getValue(org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner, BuildFileKey)}
 * and pass in the block that is the root of the key's path.
 */
public enum BuildFileKey {
  // Buildscript block
  PLUGIN_CLASSPATH("buildscript/dependencies/classpath"),
  PLUGIN_REPOSITORY("buildscript/repositories"), // TODO: Implement properly. This is not a simple literal.
  PLUGIN_VERSION("buildscript/dependencies/classpath") {
    @Override
    public Object getValue(@NotNull GroovyPsiElement[] args) {
      Object value = PLUGIN_CLASSPATH.getValue(args);
      String s = value != null ? value.toString() : null;
      if (s != null && s.startsWith(GradleBuildFile.GRADLE_PLUGIN_CLASSPATH)) {
        return s.substring(GradleBuildFile.GRADLE_PLUGIN_CLASSPATH.length());
      } else {
        return null;
      }
    }

    @Override
    public void setValue(@NotNull Project project, @NotNull GroovyPsiElement[] args, @NotNull Object value) {
      PLUGIN_CLASSPATH.setValue(project, args, GradleBuildFile.GRADLE_PLUGIN_CLASSPATH + value);
    }
  },

  // Repositories block
  // TODO: Implement

  // Dependencies block
  DEPENDENCIES("dependencies", true) {
    @Override
    public Object getValue(@NotNull GrStatementOwner closure) {
      if (!canParseValue(closure)) {
        return null;
      }
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
    public void setValue(@NotNull Project project, @NotNull GrStatementOwner closure, @NotNull Object value) {
      if (!canParseValue(closure) || !(value instanceof List)) {
        return;
      }
      List<Dependency> deps = (List<Dependency>) value;
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
      closure = (GrStatementOwner)closure.replace(factory.createClosureFromText("{}"));
      for (Dependency dependency : deps) {
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
      new ReformatCodeProcessor(project, closure.getContainingFile(), closure.getTextRange(), false).run();
    }

    @Override
    protected boolean canParseValue(@NotNull GrStatementOwner closure) {
      int callsWeUnderstand = 0;
      for (Dependency.Scope scope : Dependency.Scope.values()) {
        callsWeUnderstand += Iterables.size(GradleGroovyFile.getMethodCalls(closure, scope.getGroovyMethodCall()));
      }
      return (callsWeUnderstand == closure.getStatements().length);

      // TODO: Parse the argument list for each method call and ensure it's kosher.
      // Or, maybe just do the validation in getValue???

    }

    @Override
    protected boolean canParseValue(@NotNull GroovyPsiElement[] args) {
      return false;
    }
  },

  // Android block
  BUILD_TOOLS_VERSION("android/buildToolsVersion"),
  COMPILE_SDK_VERSION("android/compileSdkVersion"),
  IGNORE_ASSETS_PATTERN("android/aaptOptions/ignoreAssetsPattern"),
  INCREMENTAL("android/dexOptions/incremental"),
  NO_COMPRESS("android/aaptOptions/noCompress"), // TODO: Implement properly. This is not a simple literal.
  SOURCE_COMPATIBILITY("android/compileOptions/sourceCompatibility"),  // TODO: Does this work? This is an assignment, not a method call.
  TARGET_COMPATIBILITY("android/compileOptions/targetCompatibility"),  // TODO: Does this work? This is an assignment, not a method call.

  // defaultConfig or build flavor
  MIN_SDK_VERSION("minSdkVersion"),
  PACKAGE_NAME("packageName"),
  PROGUARD_FILE("proguardFile"),
  SIGNING_CONFIG("signingConfig"), // TODO: Implement properly. This is not a simple literal.
  TARGET_SDK_VERSION("targetSdkVersion"),
  TEST_INSTRUMENTATION_RUNNER("testInstrumentationRunner"),
  TEST_PACKAGE_NAME("testPackageName"),
  VERSION_CODE("versionCode"),
  VERSION_NAME("versionName"),

  // Build type
  DEBUGGABLE("debuggable"),
  JNI_DEBUG_BUILD("jniDebugBuild"),
  RENDERSCRIPT_DEBUG_BUILD("renderscriptDebugBuild"),
  RENDERSCRIPT_OPTIM_LEVEL("renderscriptOptimLevel"),
  RUN_PROGUARD("runProguard"),
  PACKAGE_NAME_SUFFIX("packageNameSuffix"),
  VERSION_NAME_SUFFIX("versionNameSuffix"),
  ZIP_ALIGN("zipAlign"),

  // Signing config
  KEY_ALIAS("keyAlias"),
  KEY_PASSWORD("keyPassword"),
  STORE_FILE("storeFile"),
  STORE_PASSWORD("storePassword");

  private final String myPath;
  private final boolean myArgumentIsClosure;

  private static final Logger LOG = Logger.getInstance(BuildFileKey.class);

  private BuildFileKey(@NotNull String path) {
    this(path, false);
  }

  /**
   * @param path an XPath-like identifier to a method call which may be inside nested closures. For example, "a/b/c" will identify
   *             <code>a { b { c("value") } }</code>
   * @param argumentIsClosure true if the value to be found at the given path is expected to itself be a closure.
   */
  private BuildFileKey(@NotNull String path, boolean argumentIsClosure) {
    myPath = path;
    myArgumentIsClosure = argumentIsClosure;
  }

  protected boolean canParseValue(@NotNull GroovyPsiElement[] args) {
    return args.length == 1 && args[0] != null && args[0] instanceof GrLiteral;
  }

  protected boolean canParseValue(@NotNull GrStatementOwner closure) {
    return false;
  }

  protected @Nullable
  Object getValue(@NotNull GroovyPsiElement[] args) {
    if (!canParseValue(args)) {
      return null;
    }
    return ((GrLiteral) args[0]).getValue();
  }

  protected @Nullable
  Object getValue(@NotNull GrStatementOwner closure) {
    return null;
  }

  protected void setValue(@NotNull Project project, @NotNull GroovyPsiElement[] args, @NotNull Object value) {
    // TODO: create path to the value if it's not there
    if (canParseValue(args)) {
      GrLiteral literal;
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
      if (value instanceof String || value instanceof Boolean) {
        literal = factory.createLiteralFromValue(value);
      } else {
        // e.g. Integer
        literal = (GrLiteral)factory.createExpressionFromText(value.toString());
      }
      args[0].replace(literal);
    }
  }

  protected void setValue(@NotNull Project project, @NotNull GrStatementOwner closure, @NotNull Object value) {
    throw new UnsupportedOperationException("setValue not implemented for closures for " + toString());
  }

  public String getPath() {
    return myPath;
  }

  public boolean isArgumentIsClosure() {
    return myArgumentIsClosure;
  }
}
