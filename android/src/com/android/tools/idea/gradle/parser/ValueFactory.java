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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.util.List;

/**
 * This is a generic class that knows how to convert items in a Gradle buildfile closure into Java-domain objects of a given type.
 * It can take a closure and turn it into a list of Java objects, or given a list of Java objects, write them into the closure.
 */
public abstract class ValueFactory<E> {

  /**
   * Specifies a filter that allows greater control over whether certain keys get written to the build file or not. Intended for use in
   * composite types such as {@link com.android.tools.idea.gradle.parser.NamedObject} to prevent writing out of sub-keys that don't need
   * to be written (necessary since you normally invoke a call to write out the entire object). If you avoid overwriting unmodified keys,
   * then you won't stomp on user-specified script that the user didn't intend to change.
   */
  public interface KeyFilter {
    boolean shouldWriteKey(BuildFileKey key, Object object);
  }

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
  public void setValues(@NotNull GrStatementOwner closure, @NotNull List<E> values, @Nullable KeyFilter filter) {
    for (E value : values) {
      setValue(closure, value, filter);
    }
    for (E existingValue : findValuesToDelete(closure, values)) {
      removeValue(closure, existingValue);
    }
    GradleGroovyFile.reformatClosure(closure);
  }

  /**
   * This method is called when all of the values underneath the key are being replaced via a call to
   * {@link #setValues(org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner, java.util.List, com.android.tools.idea.gradle.parser.ValueFactory.KeyFilter)}.
   * It looks for values that are in the build file that are not in the replacement values; these entries in the build file need to be
   * removed. The method returns those need-to-be-deleted objects. The base implementation does an {@link Object#equals(Object)} test on
   * objects to determine if a build file object exists in the replacement value list or not; subclasses can override this to provide more
   * specialized behavior.
   */
  protected Iterable<E> findValuesToDelete(@NotNull GrStatementOwner closure, @NotNull final List<E> replacementValues) {
    return Iterables.filter(getValues(closure), new Predicate<E>() {
      @Override
      public boolean apply(E input) {
        return !replacementValues.contains(input);
      }
    });
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
  protected abstract void setValue(@NotNull GrStatementOwner closure, @NotNull E value, @Nullable KeyFilter filter);

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
