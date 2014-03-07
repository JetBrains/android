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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.util.Collections;
import java.util.List;

/**
 * GradleBuildFile uses PSI to parse build.gradle files and provides high-level methods to read and mutate the file. For many things in
 * the file it uses a simple key/value interface to set and retrieve values. Since a user can potentially edit a build.gradle file by
 * hand and make changes that we are unable to parse, there is also a
 * {@link #canParseValue(BuildFileKey)} method that will query if the value can
 * be edited by this class or not.
 *
 * Note that if you do any mutations on the PSI structure you must be inside a write action. See
 * {@link com.intellij.util.ActionRunner#runInsideWriteAction}.
 */
public class GradleBuildFile extends GradleGroovyFile {
  public static final String GRADLE_PLUGIN_CLASSPATH = SdkConstants.GRADLE_PLUGIN_NAME;

  public GradleBuildFile(@NotNull VirtualFile buildFile, @NotNull Project project) {
    super(buildFile, project);
  }

  /**
   * @return true if the build file has a value for this key that we know how to safely parse and modify; false if it has user modifications
   * and should be left alone.
   */
  public boolean canParseValue(@NotNull BuildFileKey key) {
    checkInitialized();
    return canParseValue(myGroovyFile, key);
  }

  /**
   * @return true if the build file has a value for this key that we know how to safely parse and modify; false if it has user modifications
   * and should be left alone.
   */
  public boolean canParseValue(@NotNull GrStatementOwner root, @NotNull BuildFileKey key) {
    checkInitialized();
    GrMethodCall method = getMethodCallByPath(root, key.getPath());
    if (method == null) {
      return false;
    }
    GroovyPsiElement arg = key.getType() == BuildFileKeyType.CLOSURE ? getMethodClosureArgument(method) : getFirstArgument(method);
    if (arg == null) {
      return false;
    }
    return key.canParseValue(arg);
  }

  @NotNull
  public List<Dependency> getDependencies() {
    Object dependencies = getValue(BuildFileKey.DEPENDENCIES);
    if (dependencies == null) {
      return Collections.emptyList();
    }
    assert dependencies instanceof List;
    //noinspection unchecked
    return (List<Dependency>)dependencies;
  }

  /**
   * Returns the value in the file for the given key, or null if not present.
   */
  public @Nullable Object getValue(@NotNull BuildFileKey key) {
    checkInitialized();
    return getValue(myGroovyFile, key);
  }

  /**
   * Returns the value in the file for the given key, or null if not present.
   */
  public @Nullable Object getValue(@Nullable GrStatementOwner root, @NotNull BuildFileKey key) {
    checkInitialized();
    if (root == null) {
      root = myGroovyFile;
    }
    return getValueStatic(root, key);
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
  public void setValue(@NotNull BuildFileKey key, @NotNull Object value) {
    checkInitialized();
    commitDocumentChanges();
    setValue(myGroovyFile, key, value);
  }

  /**
   * Modifies the value in the file. Must be run inside a write action.
   */
  public void setValue(@Nullable GrStatementOwner root, @NotNull BuildFileKey key, @NotNull Object value) {
    checkInitialized();
    commitDocumentChanges();
    if (root == null) {
      root = myGroovyFile;
    }
    setValueStatic(root, key, value);
  }

  /**
   * If the given key has a value at the given root, removes it and returns true. Returns false if there is no value for that key.
   */
  public boolean removeValue(@Nullable GrStatementOwner root, @NotNull BuildFileKey key) {
    checkInitialized();
    commitDocumentChanges();
    if (root == null) {
      root = myGroovyFile;
    }
    GrMethodCall method = getMethodCallByPath(root, key.getPath());
    if (method != null) {
      GrStatementOwner parent = (GrStatementOwner)method.getParent();
      parent.removeElements(new PsiElement[]{method});
      reformatClosure(parent);
      return true;
    }
    return false;
  }
}
