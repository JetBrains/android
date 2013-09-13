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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

/**
 * GradleBuildFile uses PSI to parse build.gradle files and provides high-level methods to read and mutate the file. For many things in
 * the file it uses a simple key/value interface to set and retrieve values. Since a user can potentially edit a build.gradle file by
 * hand and make changes that we are unable to parse, there is also a
 * {@link #canParseValue(com.android.tools.idea.gradle.parser.GradleBuildFile.BuildSettingKey)} method that will query if the value can
 * be edited by this class or not.
 *
 * Note that if you do any mutations on the PSI structure you must be inside a write action. See
 * {@link com.intellij.util.ActionRunner#runInsideWriteAction}.
 */
public class GradleBuildFile extends GradleGroovyFile {
  public static final String GRADLE_PLUGIN_CLASSPATH = "com.android.tools.build:gradle:";

  /**
   * BuildSettingKey enumerates the values we know how to parse out of the build file. This includes values that only occur in one place
   * and are always rooted at the file root (e.g. android/buildToolsVersion) and values that can occur at different places (e.g.
   * signingConfig, which can occur in defaultConfig, a build type, or a flavor. When retrieving keys that are of the former type, you
   * can call {@link #getValue(com.android.tools.idea.gradle.parser.GradleBuildFile.BuildSettingKey)}, which uses the build file itself as
   * the root; in the case of the latter, call
   * {@link #getValue(org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner, com.android.tools.idea.gradle.parser.GradleBuildFile.BuildSettingKey)}
   * and pass in the block that is the root of the key's path.
   */
  public enum BuildSettingKey {
    // Buildscript block
    PLUGIN_CLASSPATH("buildscript/dependencies/classpath"),
    PLUGIN_REPOSITORY("buildscript/repositories"), // TODO: Implement properly. This is not a simple literal.
    PLUGIN_VERSION("buildscript/dependencies/classpath") {
      @Override
      public Object getValue(@NotNull GroovyPsiElement[] args) {
        String s = (String)PLUGIN_CLASSPATH.getValue(args);
        if (s != null && s.startsWith(GRADLE_PLUGIN_CLASSPATH)) {
          return s.substring(GRADLE_PLUGIN_CLASSPATH.length());
        } else {
          return null;
        }
      }

      @Override
      public void setValue(@NotNull Project project, @NotNull GroovyPsiElement[] args, @NotNull Object value) {
        PLUGIN_CLASSPATH.setValue(project, args, GRADLE_PLUGIN_CLASSPATH + value);
      }
    },

    // Repositories block
    // TODO: Implement

    // Dependencies block
    // TODO: Implement

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

    BuildSettingKey(@NotNull String path) {
      myPath = path;
    }

    protected boolean canParseValue(@NotNull GroovyPsiElement[] args) {
      if (args.length != 1) {
        return false;
      }
      return args[0] != null && args[0] instanceof GrLiteral;
    }

    protected @Nullable Object getValue(@NotNull GroovyPsiElement[] args) {
      if (!canParseValue(args)) {
        return null;
      }
      return ((GrLiteral) args[0]).getValue();
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
  }

  public GradleBuildFile(@NotNull VirtualFile buildFile, @NotNull Project project) {
    super(buildFile, project);
  }

  /**
   * @return true if the build file has a value for this key that we know how to safely parse and modify; false if it has user modifications
   * and should be left alone.
   */
  public boolean canParseValue(@NotNull BuildSettingKey key) {
    checkInitialized();
    return canParseValue(myGroovyFile, key);
  }

  /**
   * @return true if the build file has a value for this key that we know how to safely parse and modify; false if it has user modifications
   * and should be left alone.
   */
  public boolean canParseValue(@NotNull GrStatementOwner root, @NotNull BuildSettingKey key) {
    checkInitialized();
    GrMethodCall method = getMethodCallByPath(root, key.myPath);
    if (method == null) {
      return false;
    }
    return key.canParseValue(getArguments(method));
  }

  /**
   * Returns the value in the file for the given key, or null if not present.
   */
  public @Nullable Object getValue(@NotNull BuildSettingKey key) {
    checkInitialized();
    return getValue(myGroovyFile, key);
  }

  /**
   * Returns the value in the file for the given key, or null if not present.
   */
  public @Nullable Object getValue(@NotNull GrStatementOwner root, @NotNull BuildSettingKey key) {
    checkInitialized();
    GrMethodCall method = getMethodCallByPath(root, key.myPath);
    if (method == null) {
      return null;
    }
    return key.getValue(getArguments(method));
  }

  /**
   * Given a path to a method, returns the first argument of that method that is a closure, or null.
   */
  public @Nullable GrStatementOwner getClosure(String path) {
    checkInitialized();
    GrMethodCall method = getMethodCallByPath(myGroovyFile, path);
    if (method == null) {
      return null;
    }
    return getMethodClosureArgument(method);
  }

  /**
   * Modifies the value in the file. Must be run inside a write action.
   */
  public void setValue(@NotNull BuildSettingKey key, @NotNull Object value) {
    checkInitialized();
    commitDocumentChanges();
    setValue(myGroovyFile, key, value);
  }

  /**
   * Modifies the value in the file. Must be run inside a write action.
   */
  public void setValue(@NotNull GrStatementOwner root, @NotNull BuildSettingKey key, @NotNull Object value) {
    checkInitialized();
    commitDocumentChanges();
    GrMethodCall method = getMethodCallByPath(root, key.myPath);
    if (method != null) {
      key.setValue(myProject, getArguments(method), value);
    }
  }
}
