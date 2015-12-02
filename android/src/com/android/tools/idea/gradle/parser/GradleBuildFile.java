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

import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.lint.checks.GradleDetector;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.parser.ValueFactory.KeyFilter;

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
  /**
   * Used as a placeholder for a value in a build file we couldn't understand. We avoid overwriting these unless the explicit intent
   * is to replace an unparseable value with a new, parseable one.
   */
  public static final Object UNRECOGNIZED_VALUE = "Unrecognized value";

  @Nullable
  public static GradleBuildFile get(@NotNull Module module) {
    VirtualFile file = GradleUtil.getGradleBuildFile(module);
    return file != null ? new GradleBuildFile(file, module.getProject()) : null;
  }

  public GradleBuildFile(@NotNull VirtualFile buildFile, @NotNull Project project) {
    super(buildFile, project);
  }

  /**
   * @deprecated Use {@link com.android.tools.idea.gradle.dsl.model.GradleBuildModel#dependencies(boolean)}
   */
  @Deprecated
  @NotNull
  public List<BuildFileStatement> getDependencies() {
    Object dependencies = getValue(BuildFileKey.DEPENDENCIES);
    if (dependencies == null) {
      return Collections.emptyList();
    }
    assert dependencies instanceof List;
    //noinspection unchecked
    return (List<BuildFileStatement>)dependencies;
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
    setValue(myGroovyFile, key, value, null);
  }

  /**
   * Modifies the value in the file. Must be run inside a write action.
   */
  public void setValue(@Nullable GrStatementOwner root, @NotNull BuildFileKey key, @NotNull Object value) {
    checkInitialized();
    commitDocumentChanges();
    setValue(root, key, value, null);
  }

  /**
   * Modifies the value in the file. Must be run inside a write action. The filter is intended for composite value types (e.g.
   * {@link com.android.tools.idea.gradle.parser.NamedObject} and allows greater control over whether a sub-key gets written
   * out.
   */
  public void setValue(@NotNull BuildFileKey key, @NotNull Object value, @Nullable KeyFilter filter) {
    checkInitialized();
    commitDocumentChanges();
    setValue(myGroovyFile, key, value, filter);
  }

  /**
   * Modifies the value in the file. Must be run inside a write action. The filter is intended for composite value types (e.g.
   * {@link com.android.tools.idea.gradle.parser.NamedObject} and allows greater control over whether a sub-key gets written
   * out.
   */
  public void setValue(@Nullable GrStatementOwner root, @NotNull BuildFileKey key, @NotNull Object value, @Nullable KeyFilter filter) {
    checkInitialized();
    commitDocumentChanges();
    if (root == null) {
      root = myGroovyFile;
    }
    setValueStatic(root, key, value, true, filter);
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

  public boolean hasDependency(@NotNull BuildFileStatement statement) {
    List<BuildFileStatement> currentDeps = (List<BuildFileStatement>)getValue(BuildFileKey.DEPENDENCIES);
    if (currentDeps == null) {
      return false;
    }
    return hasDependency(currentDeps, statement);
  }

  public static boolean hasDependency(@NotNull List<BuildFileStatement> currentDeps, @NotNull BuildFileStatement statement) {
    if (currentDeps.contains(statement)) {
      return true;
    }
    if (!(statement instanceof Dependency)) {
      return false;
    }
    for (BuildFileStatement currentStatement : currentDeps) {
      if (currentStatement instanceof Dependency && ((Dependency)currentStatement).matches((Dependency)statement)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a list of all the plugins used by the given build file.
   */
  @NotNull
  public static List<String> getPlugins(GroovyFile buildScript) {
    List<String> plugins = Lists.newArrayListWithExpectedSize(1);
    for (GrMethodCall methodCall : getMethodCalls(buildScript, "apply")) {
      Map<String,Object> values = getNamedArgumentValues(methodCall);
      Object plugin = values.get("plugin");
      if (plugin != null) {
        plugins.add(plugin.toString());
      }
    }
    return plugins;
  }

  /**
   * Returns a list of all the plugins used by the build file.
   */
  @NotNull
  public List<String> getPlugins() {
    return getPlugins(myGroovyFile);
  }

  /**
   * Returns true if the build file uses the android or android-library plugin.
   */
  public boolean hasAndroidPlugin() {
    List<String> plugins = getPlugins();
    return plugins.contains(GradleDetector.APP_PLUGIN_ID) || plugins.contains(GradleDetector.OLD_APP_PLUGIN_ID) ||
           plugins.contains(GradleDetector.LIB_PLUGIN_ID) || plugins.contains(GradleDetector.OLD_LIB_PLUGIN_ID);
  }

  /**
   * Returns true if the current and new values differ in a way that should cause us to write them out to the build file. This differs from
   * simple object equality in that if the only differences between current and new are in unparseable objects, then we ignore those
   * differences for the purpose of this check -- since we don't understand unparseable statements, we can't meaningfully perform object
   * equality checks on them and we should endeavor to not write them back out to the file if we can avoid it.
   */
  public static boolean shouldWriteValue(@Nullable Object currentValue, @Nullable Object newValue) {
    if (Objects.equal(currentValue, newValue)) {
      return false;
    }
    // If it's a list type, then iterate though the elements. If each element is equal or if both the current and new values at a given list
    // position are both unparseable, then we don't need to write it out.
    if (!(currentValue instanceof List && newValue instanceof List)) {
      return true;
    }
    List currentList = (List)currentValue;
    List newList = (List)newValue;
    if (currentList.size() != newList.size()) {
      return true;
    }
    for (int i = 0; i < currentList.size(); i++) {
      Object currentObj = currentList.get(i);
      Object newObj = newList.get(i);
      if (!currentObj.equals(newObj) && !(currentObj instanceof UnparseableStatement && newObj instanceof UnparseableStatement)) {
        return true;
      }
    }
    return false;
  }
}
