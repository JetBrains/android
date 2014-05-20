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

import com.android.annotations.Nullable;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.util.List;

/**
 * This is a generic class that knows how to convert items in a Gradle buildfile closure into Java-domain objects of a given type.
 * It can take a closure and turn it into a list of Java objects, or given a list of Java objects, write them into the closure.
 */
public abstract class ValueFactory<E> {
  @NotNull
  public List<E> getValues(@NotNull GrStatementOwner closure) {
    List<E> result = Lists.newArrayList();
    for (PsiElement element : closure.getChildren()) {
      List<E> values = getValues(element);
      if (values != null && !values.isEmpty()) {
        result.addAll(values);
      }
    }
    return result;
  }

  /**
   * <p>This implementation of setValues calls {@link #setValue(org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner, Object)},
   * which is suitable for cases where the value uniquely identifies a statement in the buildfile, such as a "named object" that consists
   * of a method call whose name is the name of the object, and a closure consisting of key/value statements; examples of named objects
   * include build types and flavors. In other cases, such as repositories and dependencies, we can't tell by looking at an individual
   * value what statement in the build file it corresponds to: there's no name or other information that makes it unique. setValue
   * won't work for writing those types of objects into build files, and for those types, this class will need to be subclassed and this
   * method overridden to work a different way.</p>
   * <p>It also calls {@link #removeValue(org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner, Object)} to remove objects
   * that aren't in the passed-in list.</p>
   */
  public void setValues(@NotNull GrStatementOwner closure, @NotNull List<E> values) {
    for (E value : values) {
      setValue(closure, value);
    }
    for (E existingValue : getValues(closure)) {
      if (!values.contains(existingValue)) {
        removeValue(closure, existingValue);
      }
    }
    GradleGroovyFile.reformatClosure(closure);
  }

  /**
   * If your subclass supports parsing values from string, override this method to implement that functionality
   */
  @NotNull
  public E parse(@NotNull String s, Project project) {
    throw new UnsupportedOperationException("parse not implemented");
  }

  /**
   * See {@link #setValues(org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner, java.util.List)} for documentation.
   */
  protected abstract void setValue(@NotNull GrStatementOwner closure, @NotNull E value);

  /**
   * Given a PSI element that represents a single statement or line of code, returns the Java objects parsed from that element. This
   * element is generally a {@link org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement} or a
   * {@link com.intellij.psi.PsiComment}.
   */
  @Nullable
  abstract protected List<E> getValues(@NotNull PsiElement statement);

  /**
   * If your subclass supports removal of individual objects, override this method to implement that functionality.
   */
  protected void removeValue(@NotNull GrStatementOwner closure, @NotNull E value) {
    throw new UnsupportedOperationException("removeValue not implemented");
  }
}
