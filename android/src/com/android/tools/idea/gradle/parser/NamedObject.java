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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

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
public class NamedObject {
  private String myName;
  private final Map<BuildFileKey, Object> myValues = Maps.newHashMap();

  public NamedObject(@NotNull String name) {
    myName = name;
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
    myValues.put(property, value);
  }

  public static BuildFileKey.ValueFactory<NamedObject> getFactory(@NotNull List<BuildFileKey> properties) {
    return new Factory(properties);
  }

  public static class Factory implements BuildFileKey.ValueFactory<NamedObject> {
    private final List<BuildFileKey> myProperties;

    private Factory(@NotNull List<BuildFileKey> properties) {
      myProperties = properties;
    }

    @NotNull
    @Override
    public List<NamedObject> getValues(@NotNull GrStatementOwner closure) {
      List<NamedObject> list = Lists.newArrayList();
      for (GrMethodCall method : GradleGroovyFile.getMethodCalls(closure)) {
        NamedObject item = new NamedObject(GradleGroovyFile.getMethodCallName(method));
        list.add(item);
        GrClosableBlock subclosure = GradleGroovyFile.getMethodClosureArgument(closure, item.getName());
        if (subclosure == null) {
          continue;
        }
        for (BuildFileKey property : myProperties) {
          item.setValue(property, GradleGroovyFile.getValueStatic(subclosure, property));
        }
      }
      return list;
    }

    @Override
    public void setValues(@NotNull GrStatementOwner closure, @NotNull List<NamedObject> value) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(closure.getProject());
      closure = (GrStatementOwner)closure.replace(factory.createClosureFromText("{}"));
      for (NamedObject item : value) {
        closure.addStatementBefore(factory.createStatementFromText(item.getName() + " {}"), null);
        GrStatementOwner subclosure = GradleGroovyFile.getMethodClosureArgument(closure, item.getName());
        if (subclosure == null) {
          continue;
        }
        for (Map.Entry<BuildFileKey, Object> entry : item.getValues().entrySet()) {
          if (entry.getValue() != null) {
            GradleGroovyFile.setValueStatic(subclosure, entry.getKey(), entry.getValue());
          }
        }
      }
      GradleGroovyFile.reformatClosure(closure);
    }

    @Override
    public boolean canParseValue(@NotNull GrStatementOwner closure) {
      return true;
    }

    @NotNull
    public List<BuildFileKey> getProperties() {
      return myProperties;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

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
}
