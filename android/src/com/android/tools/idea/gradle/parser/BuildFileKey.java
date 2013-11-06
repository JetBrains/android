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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.io.File;
import java.util.List;

/**
 * Enumerates the values we know how to parse out of the build file. This includes values that only occur in one place
 * and are always rooted at the file root (e.g. android/buildToolsVersion) and values that can occur at different places (e.g.
 * signingConfig, which can occur in defaultConfig, a build type, or a flavor. When retrieving keys that are of the former type, you
 * can call {@link GradleBuildFile#getValue(BuildFileKey)}, which uses the build file itself as
 * the root; in the case of the latter, call
 * {@link GradleBuildFile#getValue(org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner, BuildFileKey)}
 * and pass in the block that is the root of the key's path.
 * <p>
 * Values have types. Scalar types like string, integer, etc. are self-explanatory, but complex closure types are supported as well. In
 * that case, you can supply a factory class that knows how to instantiate container instances for subobjects that are found within a
 * given closure in the build file.
 */
public enum BuildFileKey {
  // Buildscript block
  PLUGIN_CLASSPATH("buildscript/dependencies/classpath", Type.STRING),
  PLUGIN_REPOSITORY("buildscript/repositories", Type.CLOSURE), // TODO: Build Repository class to handle these
  PLUGIN_VERSION("buildscript/dependencies/classpath", Type.STRING) {
    @Override
    public Object getValue(@NotNull GroovyPsiElement[] args) {
      String s = PLUGIN_CLASSPATH.getStringValue(args);
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

  LIBRARY_REPOSITORIES("repositories", Type.CLOSURE), // TODO: Build Repository class to handle these

  // Dependencies block
  DEPENDENCIES("dependencies", Type.CLOSURE, Dependency.getFactory()),

  // defaultConfig or build flavor
  MIN_SDK_VERSION("minSdkVersion", Type.INTEGER),
  PACKAGE_NAME("packageName", Type.STRING),
  PROGUARD_FILE("proguardFile", Type.FILE_AS_STRING),
  SIGNING_CONFIG("signingConfig", Type.REFERENCE),
  TARGET_SDK_VERSION("targetSdkVersion", Type.INTEGER),
  TEST_INSTRUMENTATION_RUNNER("testInstrumentationRunner", Type.STRING),
  TEST_PACKAGE_NAME("testPackageName", Type.STRING),
  VERSION_CODE("versionCode", Type.INTEGER),
  VERSION_NAME("versionName", Type.STRING),

  // Build type
  DEBUGGABLE("debuggable", Type.BOOLEAN),
  JNI_DEBUG_BUILD("jniDebugBuild", Type.BOOLEAN),
  RENDERSCRIPT_DEBUG_BUILD("renderscriptDebugBuild", Type.STRING),
  RENDERSCRIPT_OPTIM_LEVEL("renderscriptOptimLevel", Type.STRING),
  RUN_PROGUARD("runProguard", Type.BOOLEAN),
  PACKAGE_NAME_SUFFIX("packageNameSuffix", Type.STRING),
  VERSION_NAME_SUFFIX("versionNameSuffix", Type.STRING),
  ZIP_ALIGN("zipAlign", Type.BOOLEAN),

  // Signing config
  KEY_ALIAS("keyAlias", Type.STRING),
  KEY_PASSWORD("keyPassword", Type.STRING),
  STORE_FILE("storeFile", Type.FILE),
  STORE_PASSWORD("storePassword", Type.STRING),

  // Android block
  BUILD_TOOLS_VERSION("android/buildToolsVersion", Type.STRING),
  COMPILE_SDK_VERSION("android/compileSdkVersion", Type.INTEGER),
  IGNORE_ASSETS_PATTERN("android/aaptOptions/ignoreAssetsPattern", Type.STRING),
  INCREMENTAL("android/dexOptions/incremental", Type.BOOLEAN),
  NO_COMPRESS("android/aaptOptions/noCompress", Type.STRING), // TODO: Implement properly. This is not a simple literal.
  SOURCE_COMPATIBILITY("android/compileOptions/sourceCompatibility", Type.STRING),  // TODO: This is an assignment, not a method call.
  TARGET_COMPATIBILITY("android/compileOptions/targetCompatibility", Type.STRING),  // TODO: This is an assignment, not a method call.

  // These complex types are named entities that within them have key/value pairs where the keys are BuildFileKey instances themselves.
  // We can use a generic container class to deal with them.

  // It would be nice if these lists of sub-parameters were static constants, but that results in unresolvable circular references.
  SIGNING_CONFIGS("android/signingConfigs", Type.CLOSURE,
                  NamedObject.getFactory(ImmutableList.of(KEY_ALIAS, KEY_PASSWORD, STORE_FILE, STORE_PASSWORD))),
  FLAVORS("android/productFlavors", Type.CLOSURE,
          NamedObject.getFactory(ImmutableList.of(MIN_SDK_VERSION, PACKAGE_NAME, PROGUARD_FILE, SIGNING_CONFIG, TARGET_SDK_VERSION,
                                                  TEST_INSTRUMENTATION_RUNNER, TEST_PACKAGE_NAME, VERSION_CODE, VERSION_NAME))),
  BUILD_TYPES("android/buildTypes", Type.CLOSURE,
              NamedObject.getFactory(ImmutableList
                                       .of(DEBUGGABLE, JNI_DEBUG_BUILD, SIGNING_CONFIG, RENDERSCRIPT_DEBUG_BUILD, RENDERSCRIPT_OPTIM_LEVEL,
                                           RUN_PROGUARD, PROGUARD_FILE, PACKAGE_NAME_SUFFIX, VERSION_NAME_SUFFIX, ZIP_ALIGN)));

  // TODO: There are quite a few places in the code where it does a switch statement on the type. Perhaps some of that functionality
  // should be moved into the enum instead.
  public enum Type {
    STRING,
    INTEGER,
    BOOLEAN,
    CLOSURE,
    FILE, // Represented in Groovy as file('/path/to/file')
    FILE_AS_STRING, // Represented in Groovy as '/path/to/file'
    REFERENCE // TODO: for reference types, encode the BuildFileKey of the target being referred to.
  }

  public interface ValueFactory<E> {
    @NotNull List<E> getValues(@NotNull GrStatementOwner closure);
    void setValues(@NotNull GrStatementOwner closure, @NotNull List<E> values);
    boolean canParseValue(@NotNull GrStatementOwner closure);
  }

  private final String myPath;
  private final Type myType;
  private final ValueFactory<?> myValueFactory;

  BuildFileKey(@NotNull String path, @NotNull Type type) {
    this(path, type, null);
  }

  /**
   * @param path an XPath-like identifier to a method call which may be inside nested closures. For example, "a/b/c" will identify
   *             <code>a { b { c("value") } }</code>
   */
  BuildFileKey(@NotNull String path, @NotNull Type type, @Nullable ValueFactory<?> factory) {
    myPath = path;
    myType = type;
    myValueFactory = factory;
  }

  @NotNull
  public String getDisplayName() {
    int lastSlash = myPath.lastIndexOf('/');
    return lastSlash >= 0 ? myPath.substring(lastSlash) : myPath;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  @Nullable
  public ValueFactory<?> getValueFactory() {
    return myValueFactory;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  public boolean isArgumentIsClosure() {
    return myType == Type.CLOSURE;
  }

  protected boolean canParseValue(@NotNull GroovyPsiElement[] args) {
    if (args.length != 1 || args[0] == null) {
      return false;
    }
    GroovyPsiElement arg = args[0];
    switch(myType) {
      case STRING:
      case FILE_AS_STRING:
        return isParseableAs(arg, String.class);
      case INTEGER:
        return isParseableAs(arg, Integer.class);
      case BOOLEAN:
        return isParseableAs(arg, Boolean.class);
      case FILE:
        if (!(arg instanceof GrMethodCall)) {
          return false;
        }
        GrMethodCall call = (GrMethodCall) arg;
        if (!"file".equals(GradleGroovyFile.getMethodCallName(call))) {
          return false;
        }
        Object path = GradleGroovyFile.getFirstLiteralArgumentValue(call);
        return path != null && path instanceof String;
      case REFERENCE:
        return arg instanceof GrReferenceExpression && arg.getText() != null;
      case CLOSURE:
      default:
        return false;
    }
  }

  private static boolean isParseableAs(GroovyPsiElement arg, Class<?> clazz) {
    if (!(arg instanceof GrLiteral)) {
      return false;
    }
    Object value = ((GrLiteral)arg).getValue();
    return value != null && clazz.isAssignableFrom(value.getClass());
  }

  protected boolean canParseValue(@NotNull GrStatementOwner closure) {
    if (myType == Type.CLOSURE) {
      if (myValueFactory == null) {
        return false;
      }
      return myValueFactory.canParseValue(closure);
    } else {
      return false;
    }
  }

  protected @Nullable
  Object getValue(@NotNull GroovyPsiElement[] args) {
    if (!canParseValue(args) || args.length == 0) {
      return null;
    }
    switch(myType) {
      case INTEGER:
      case STRING:
      case BOOLEAN:
        return ((GrLiteral) args[0]).getValue();
      case FILE:
        GroovyPsiElement element = args[0];
        if (!(element instanceof GrMethodCall)) {
          return null;
        }
        GrMethodCall call = (GrMethodCall)element;
        if (!("file".equals(GradleGroovyFile.getMethodCallName(call)))) {
          return null;
        }
        Object path = GradleGroovyFile.getFirstLiteralArgumentValue(call);
        if (path == null) {
          return null;
        }
        return new File(path.toString());
      case FILE_AS_STRING:
        Object value = ((GrLiteral) args[0]).getValue();
        return value != null ? new File(value.toString()) : null;
      case REFERENCE:
        return args[0].getText();
      case CLOSURE:
      default:
        return null;
    }
  }

  @Nullable
  protected String getStringValue(@NotNull GroovyPsiElement[] args) {
    Object value = getValue(args);
    return value != null ? value.toString() : null;
  }

  protected @Nullable
  Object getValue(@NotNull GrStatementOwner closure) {
    if (!canParseValue(closure)) {
      return null;
    }
    if (myType == Type.CLOSURE) {
      if (myValueFactory == null) {
        return null;
      }
      return myValueFactory.getValues(closure);
    } else {
      throw new UnsupportedOperationException("getValue not implemented for closures for " + toString());
    }
  }

  protected void setValue(@NotNull Project project, @NotNull GroovyPsiElement[] args, @NotNull Object value) {
    if ((myType == Type.FILE  || myType == Type.FILE_AS_STRING) && value instanceof File) {
      value = ((File)value).getPath();
    }
    if (canParseValue(args)) {
      GroovyPsiElement psiValue;
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
      switch(myType) {
        case STRING:
        case FILE_AS_STRING:
          psiValue = factory.createLiteralFromValue(value);
          break;
        case FILE:
          psiValue = factory.createStatementFromText("file('" + value.toString() + "')");
          break;
        default:
          // e.g. Integer
          psiValue = factory.createExpressionFromText(value.toString());
          break;
      }
      args[0].replace(psiValue);
    }
  }

  protected void setValue(@NotNull GrStatementOwner closure, @NotNull Object value) {
    if (!canParseValue(closure)) {
      return;
    }
    if (myType == Type.CLOSURE) {
      if (myValueFactory == null || !(value instanceof List)) {
        return;
      }
      myValueFactory.setValues(closure, (List)value);
    } else {
      throw new UnsupportedOperationException("setValue not implemented for closures for " + toString());
    }
  }
}
