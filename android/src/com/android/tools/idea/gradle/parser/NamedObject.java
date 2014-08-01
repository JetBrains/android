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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.text.Collator;
import java.util.List;
import java.util.Map;

/**
 * This is a container class for configurable objects in build.gradle files like signing configs, flavors, build types, and the like.
 * These are named entities that live inside a parent closure, like buildType1 and buildType2 in this example:
 * <p>
 * <code>
 *   android {
 *     buildTypes {
 *       buildType1 {
 *         someProperty "someValue"
 *         anotherProperty "anotherValue"
 *       }
 *       buildType2 {
 *         someProperty "differentValue"
 *         anotherProperty "anotherDifferentValue"
 *       }
 *     }
 *   }
 * </code>
 *
 * <p>
 * These objects are syntactically method calls in Groovy; the method name is the object's name, and the argument is a closure that
 * takes property/value statements (which are themselves method calls).
 */
public class NamedObject implements Comparable<NamedObject> {
  private String myName;
  private final boolean myAlwaysOnTop;
  private final Map<BuildFileKey, Object> myValues = Maps.newHashMap();

  public NamedObject(@NotNull String name) {
    this(name, false);
  }

  protected NamedObject(@NotNull String name, boolean alwaysOnTop) {
    myName = name;
    myAlwaysOnTop = alwaysOnTop;
  }

  public NamedObject(@NotNull NamedObject obj) {
    myName = obj.myName;
    myAlwaysOnTop = obj.myAlwaysOnTop;
    myValues.putAll(obj.myValues);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public Map<BuildFileKey, Object> getValues() {
    return myValues;
  }

  @Nullable
  public Object getValue(@NotNull BuildFileKey buildFileKey) {
    return myValues.get(buildFileKey);
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  public void setValue(@NotNull BuildFileKey property, @Nullable Object value) {
    if (value == null) {
      myValues.remove(property);
    } else {
      myValues.put(property, value);
    }
  }

  public static ValueFactory getFactory(@NotNull List<BuildFileKey> properties) {
    return new Factory(properties);
  }

  @Override
  public int compareTo(NamedObject o) {
    if (myAlwaysOnTop != o.myAlwaysOnTop) {
      return myAlwaysOnTop ? -1 : 1;
    }
    return Collator.getInstance().compare(myName, o.myName);
  }

  public static class Factory extends ValueFactory<NamedObject> {
    private final List<BuildFileKey> myProperties;

    private Factory(@NotNull List<BuildFileKey> properties) {
      myProperties = properties;
    }

    /**
     * When looking for values to delete, do a less fragile comparison based only on object names and not identity.
     */
    @Override
    protected Iterable<NamedObject> findValuesToDelete(@NotNull GrStatementOwner closure,
                                                       @NotNull final List<NamedObject> replacementValues) {
      return Iterables.filter(getValues(closure), new Predicate<NamedObject>() {
        @Override
        public boolean apply(NamedObject input) {
          for (NamedObject value : replacementValues) {
            if (value.getName().equals(input.getName())) {
              return false;
            }
          }
          return true;
        }
      });
    }

    @Override
    protected void setValue(@NotNull GrStatementOwner closure, @NotNull NamedObject object, @Nullable KeyFilter filter) {
      GrClosableBlock subclosure = GradleGroovyFile.getMethodClosureArgument(closure, object.myName);
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(closure.getProject());
      if (subclosure == null) {
        closure.addBefore(factory.createStatementFromText(object.getName() + " {\n}\n"), closure.getLastChild());
        subclosure = GradleGroovyFile.getMethodClosureArgument(closure, object.myName);
        if (subclosure == null) {
          return;
        }
      }
      for (BuildFileKey property : myProperties) {
        if (filter != null && !filter.shouldWriteKey(property, object)) {
          continue;
        }
        Object value = object.getValue(property);
        if (value != null) {
          // Don't need to pass the filter along. It's already done its job on the parent key.
          GradleGroovyFile.setValueStatic(subclosure, property, value, false, null);
        } else if (GradleGroovyFile.getValueStatic(subclosure, property) != GradleBuildFile.UNRECOGNIZED_VALUE) {
          GradleGroovyFile.removeValueStatic(subclosure, property);
        }
      }
      GradleGroovyFile.reformatClosure(subclosure);
    }

    @Nullable
    @Override
    public List<NamedObject> getValues(@NotNull PsiElement statement) {
      if (!(statement instanceof GrMethodCall)) {
        return null;
      }
      GrMethodCall method = (GrMethodCall)statement;

      NamedObject item = new NamedObject(GradleGroovyFile.getMethodCallName((GrMethodCall)statement));
        GrClosableBlock subclosure = GradleGroovyFile.getMethodClosureArgument(method);
        if (subclosure == null) {
          return null;
        }
        for (BuildFileKey property : myProperties) {
          Object value = GradleGroovyFile.getValueStatic(subclosure, property);
          if (value != null) {
            item.setValue(property, value);
          }
        }
      return ImmutableList.of(item);
    }

    @Override
    protected void removeValue(@NotNull GrStatementOwner closure, @NotNull NamedObject value) {
      GrMethodCall call = GradleGroovyFile.getMethodCall(closure, value.getName());
      if (call != null) {
        call.removeStatement();
      }
    }

    @NotNull
    public List<BuildFileKey> getProperties() {
      return myProperties;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NamedObject)) return false;

    NamedObject that = (NamedObject)o;

    if (!myName.equals(that.myName)) return false;
    if (!myValues.equals(that.myValues)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myValues.hashCode();
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("NamedObject ");
    sb.append(myName);
    sb.append(' ');
    sb.append(myValues.toString());
    return sb.toString();
  }
}
