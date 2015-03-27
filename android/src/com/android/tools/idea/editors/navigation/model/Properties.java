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
package com.android.tools.idea.editors.navigation.model;

import com.android.tools.idea.editors.navigation.annotations.Transient;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Properties {
  static class PropertyAccessException extends Exception {
    private PropertyAccessException(Throwable throwable) {
      super(throwable);
    }
  }

  abstract static class Property<T> {
    abstract String getName();

    abstract Class getType();

    abstract Object getValue(T o) throws PropertyAccessException;
  }

  static class FieldProperty<T> extends Property<T> {
    private final Field field;

    FieldProperty(Field field) {
      this.field = field;
    }

    @Override
    String getName() {
      return field.getName();
    }

    @Override
    Class getType() {
      return field.getType();
    }

    @Override
    Object getValue(T o) throws PropertyAccessException {
      try {
        return field.get(o);
      }
      catch (IllegalAccessException e) {
        throw new PropertyAccessException(e);
      }
    }
  }

  static class MethodProperty<T> extends Property<T> {
    private final Method method;

    MethodProperty(Method method) {
      this.method = method;
    }

    @Override
    String getName() {
      return Utilities.getPropertyName(method);
    }

    @Override
    Class getType() {
      return method.getReturnType();
    }

    @Override
    Object getValue(T o) throws PropertyAccessException {
      try {
        return method.invoke(o);
      }
      catch (IllegalAccessException e) {
        throw new PropertyAccessException(e);
      }
      catch (InvocationTargetException e) {
        throw new PropertyAccessException(e);
      }
    }
  }

  private static Method[] findGetters(Class c) {
    List<Method> methods = new ArrayList<Method>();
    for (Method m : c.getMethods()) {
      if (!Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length == 0 && m.getName().startsWith("get") && !isTransient(m)) { // todo or "is"
        methods.add(m);
      }
    }
    /*
    // Put all the primitives first, and ensure that property order is not subject to unstable method ordering
    Collections.sort(methods, new Comparator<Method>() {
      @Override
      public int compare(Method m1, Method m2) {
        boolean p1 = isPrimitive(m1.getReturnType());
        boolean p2 = isPrimitive(m2.getReturnType());
        if (p1 != p2) {
          return p1 ? -1 : 1;
        }
        return m1.getName().compareTo(m2.getName());
      }
    });
    */
    Collections.sort(methods, new Comparator<Method>() {
      @Override
      public int compare(Method m1, Method m2) {
        return m1.getName().compareTo(m2.getName());
      }
    });
    return methods.toArray(new Method[methods.size()]);
  }

  private static boolean isTransient(AnnotatedElement e) {
    return e.getAnnotation(Transient.class) != null;
  }

  static Property[] computeProperties(Class c) {
    List<Property> result = new ArrayList<Property>();
    for (Field f : c.getFields()) {
      if (!Modifier.isStatic(f.getModifiers()) && !isTransient(f)) {
        result.add(new FieldProperty(f));
      }
    }
    for (Method m : findGetters(c)) {
      result.add(new MethodProperty(m));
    }
    return result.toArray(new Property[result.size()]);
  }
}
