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

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModel;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

import java.util.List;

import static com.android.tools.idea.gradle.parser.BuildFileKeyType.*;
import static com.android.tools.idea.gradle.parser.ValueFactory.KeyFilter;

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
  PLUGIN_CLASSPATH("buildscript/dependencies/classpath", STRING),
  PLUGIN_REPOSITORY("buildscript/repositories", "Android Plugin Repository", CLOSURE, Repository.getFactory()),
  PLUGIN_VERSION("buildscript/dependencies/classpath", "Android Plugin Version", STRING, null) {
    @Override
    public Object getValue(@NotNull GroovyPsiElement arg) {
      // PLUGIN_CLASSPATH is STRING type; we're guaranteed the getValue result can be cast to String.
      String s = (String)PLUGIN_CLASSPATH.getValue(arg);
      if (s != null && s.startsWith(SdkConstants.GRADLE_PLUGIN_NAME)) {
        return s.substring(SdkConstants.GRADLE_PLUGIN_NAME.length());
      } else {
        return null;
      }
    }

    @Override
    public void setValue(@NotNull GroovyPsiElement arg, @NotNull Object value, @Nullable KeyFilter filter) {
      PLUGIN_CLASSPATH.setValue(arg, SdkConstants.GRADLE_PLUGIN_NAME + value);
    }
  },
  ALLPROJECTS_LIBRARY_REPOSITORY("allprojects/repositories", "Default Library Repository", CLOSURE, Repository.getFactory()),

  LIBRARY_REPOSITORY("repositories", "Library Repository", CLOSURE, Repository.getFactory()),

  // Dependencies block
  DEPENDENCIES("dependencies", null, CLOSURE, Dependency.getFactory()),

  // defaultConfig or build flavor
  MIN_SDK_VERSION("minSdkVersion", INTEGER_OR_STRING),
  APPLICATION_ID("applicationId", STRING),
  PROGUARD_FILE("proguardFile", FILE_AS_STRING),
  SIGNING_CONFIG("signingConfig", REFERENCE),
  TARGET_SDK_VERSION("targetSdkVersion", INTEGER_OR_STRING),
  TEST_INSTRUMENTATION_RUNNER("testInstrumentationRunner", STRING),
  TEST_APPLICATION_ID("testApplicationId", STRING),
  VERSION_CODE("versionCode", INTEGER),
  VERSION_NAME("versionName", STRING),

  // Build type
  DEBUGGABLE("debuggable", BOOLEAN),
  JNI_DEBUG_BUILD("jniDebuggable", BOOLEAN),
  RENDERSCRIPT_DEBUG_BUILD("renderscriptDebuggable", BOOLEAN),
  RENDERSCRIPT_OPTIM_LEVEL("renderscriptOptimLevel", INTEGER),
  MINIFY_ENABLED("minifyEnabled", BOOLEAN),
  PSEUDOLOCALES_ENABLED("pseudoLocalesEnabled", BOOLEAN),
  APPLICATION_ID_SUFFIX("applicationIdSuffix", STRING),
  VERSION_NAME_SUFFIX("versionNameSuffix", STRING),
  ZIP_ALIGN("zipAlignEnabled", BOOLEAN),

  // Signing config
  KEY_ALIAS("keyAlias", STRING),
  KEY_PASSWORD("keyPassword", STRING),
  STORE_FILE("storeFile", FILE),
  STORE_PASSWORD("storePassword", STRING),

  // Android blockx
  DEFAULT_CONFIG("android/defaultConfig", CLOSURE),
  @Deprecated
  /**
   * @deprecated Use {@link AndroidModel#buildToolsVersion()} or {@link AndroidModel#setBuildToolsVersion(String)}
   */
  BUILD_TOOLS_VERSION("android/buildToolsVersion", STRING),
  COMPILE_SDK_VERSION("android/compileSdkVersion", INTEGER_OR_STRING),
  IGNORE_ASSETS_PATTERN("android/aaptOptions/ignoreAssetsPattern", STRING),
  INCREMENTAL_DEX("android/dexOptions/incremental", "Incremental Dex", BOOLEAN, null),
  NO_COMPRESS("android/aaptOptions/noCompress", STRING), // TODO: Implement properly. This is not a simple literal.
  SOURCE_COMPATIBILITY("android/compileOptions/sourceCompatibility", REFERENCE),
  TARGET_COMPATIBILITY("android/compileOptions/targetCompatibility", REFERENCE),

  // Non-Gradle build file keys

  // These keys set values in places other than Gradle build files (e.g. the Gradle wrapper properties, which is a Java properties file)
  // They are included here so that other code can reuse the infrastructure built up around BuildFileKeys.
  GRADLE_WRAPPER_VERSION("", "Gradle version", STRING, NonGroovyValueFactory.getFactory()),

  // These complex types are named entities that within them have key/value pairs where the keys are BuildFileKey instances themselves.
  // We can use a generic container class to deal with them.

  // It would be nice if these lists of sub-parameters were static constants, but that results in unresolvable circular references.
  SIGNING_CONFIGS("android/signingConfigs", null, CLOSURE,
                  NamedObject.getFactory(ImmutableList.of(KEY_ALIAS, KEY_PASSWORD, STORE_FILE, STORE_PASSWORD))),
  FLAVORS("android/productFlavors", null, CLOSURE,
          NamedObject.getFactory(ImmutableList.of(MIN_SDK_VERSION, APPLICATION_ID, PROGUARD_FILE, SIGNING_CONFIG, TARGET_SDK_VERSION,
                                                  TEST_INSTRUMENTATION_RUNNER, TEST_APPLICATION_ID, VERSION_CODE, VERSION_NAME))),
  BUILD_TYPES("android/buildTypes", null, CLOSURE,
              NamedObject.getFactory(ImmutableList
                                       .of(DEBUGGABLE, JNI_DEBUG_BUILD, SIGNING_CONFIG, RENDERSCRIPT_DEBUG_BUILD, RENDERSCRIPT_OPTIM_LEVEL,
                                           MINIFY_ENABLED, PSEUDOLOCALES_ENABLED, PROGUARD_FILE, APPLICATION_ID_SUFFIX, VERSION_NAME_SUFFIX,
                                           ZIP_ALIGN)));

  static {
    SIGNING_CONFIG.myContainerType = SIGNING_CONFIGS;
    SIGNING_CONFIGS.myItemType = SIGNING_CONFIG;
    SIGNING_CONFIGS.myShouldInsertAtBeginning = true;
  }

  private final String myPath;
  private final BuildFileKeyType myType;
  private final ValueFactory myValueFactory;
  private final String myDisplayName;
  private BuildFileKey myContainerType;
  private BuildFileKey myItemType;
  private boolean myShouldInsertAtBeginning;

  BuildFileKey(@NotNull String path, @NotNull BuildFileKeyType type) {
    this(path, null, type, null);
  }

  /**
   * @param path an XPath-like identifier to a method call which may be inside nested closures. For example, "a/b/c" will identify
   *             <code>a { b { c("value") } }</code>
   */
  BuildFileKey(@NotNull String path, @Nullable String displayName, @NotNull BuildFileKeyType type, @Nullable ValueFactory factory) {
    myPath = path;
    myType = type;
    myValueFactory = factory;

    if (displayName != null) {
      myDisplayName = displayName;
    } else {
      int lastSlash = myPath.lastIndexOf('/');
      myDisplayName = splitCamelCase(lastSlash >= 0 ? myPath.substring(lastSlash + 1) : myPath);
    }
  }

  @NotNull
  @VisibleForTesting
  static String splitCamelCase(@NotNull String string) {
    StringBuilder sb = new StringBuilder(2 * string.length());
    int n = string.length();
    boolean lastWasUpperCase = Character.isUpperCase(string.charAt(0));
    boolean capitalizeNext = true;
    for (int i = 0; i < n; i++) {
      char c = string.charAt(i);
      boolean isUpperCase = Character.isUpperCase(c);
      if (isUpperCase && !lastWasUpperCase) {
        sb.append(' ');
        capitalizeNext = true;
      }
      lastWasUpperCase = isUpperCase;
      if (capitalizeNext) {
        c = Character.toUpperCase(c);
        capitalizeNext = false;
      } else {
        c = Character.toLowerCase(c);
      }
      sb.append(c);
    }

    return sb.toString();
  }

  @NotNull
  static String escapeLiteralString(@Nullable Object o) {
    if (o == null) {
      return "";
    }
    return o.toString().replace("'", "\\'");
  }

  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }

  @NotNull
  public BuildFileKeyType getType() {
    return myType;
  }

  @Nullable
  public ValueFactory getValueFactory() {
    return myValueFactory;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  /**
   * For keys whose values are Groovy expressions that reference another object in the same build file, this returns the type of the
   * referenced object. For example, the {@link SIGNING_CONFIG} key returns a reference to the container {@link SIGNING_CONFIGS} key.
   */
  @Nullable
  public BuildFileKey getContainerType() {
    return myContainerType;
  }

  /**
   * For keys whose values are containers of named objects that can be referred to elsewhere in the build file via
   * {@link com.android.tools.idea.gradle.parser.BuildFileKeyType#REFERENCE} links, this returns the key for those named object items.
   * For example, the {@link SIGNING_CONFIGS} key returns a reference to the item type {@link SIGNING_CONFIG} key.
   */
  @Nullable
  public BuildFileKey getItemType() {
    return myItemType;
  }

  /**
   * True if this block should be inserted at the beginning of its parent's closure instead of at the end. For types that can be referenced
   * from other keys, such as {@link SIGNING_CONFIGS}, the objects must appear in the file before references to them. Groovy executes the
   * block sequentially at build file evaluation time, and any references must resolve to objects it's already seen.
   * @return
   */
  public boolean shouldInsertAtBeginning() {
    return myShouldInsertAtBeginning;
  }

  @Nullable
  protected Object getValue(@NotNull GroovyPsiElement arg) {
    if (myValueFactory != null && arg instanceof GrClosableBlock) {
      return myValueFactory.getValues((GrClosableBlock) arg);
    }
    return myType.getValue(arg);
  }

  protected void setValue(@NotNull GroovyPsiElement arg, @NotNull Object value) {
    setValue(arg, value, null);
  }

  protected void setValue(@NotNull GroovyPsiElement arg, @NotNull Object value, @Nullable KeyFilter filter) {
    if (myValueFactory != null && arg instanceof GrClosableBlock && value instanceof List) {
      myValueFactory.setValues((GrClosableBlock) arg, (List)value, filter);
    } else {
      myType.setValue(arg, value);
    }
  }

  @Nullable
  public static BuildFileKey findByPath(@NotNull String path) {
    for (BuildFileKey key : values()) {
      if (path.equals(key.myPath)) {
        return key;
      }
    }
    return null;
  }
}
