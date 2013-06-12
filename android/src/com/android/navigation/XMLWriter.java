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
package com.android.navigation;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class XMLWriter {
  public static final int UNDEFINED = -1;
  private final Map<Class, Property[]> classToProperties = new IdentityHashMap<Class, Property[]>();
  private final PrintStream out;
  private int level;
  private int idCount = 0;

  private Map<Object, Info> objectToInfo = new IdentityHashMap<Object, Info>() {
    @Override
    public Info get(Object o) {
      Info result = super.get(o);
      if (result == null) {
        put(o, result = new Info());
      }
      return result;
    }
  };

  public XMLWriter(OutputStream out) {
    this.out = new PrintStream(out);
  }

  private static boolean isPrimitive(Class type) {
    return type.isPrimitive() || type == String.class;
  }

  static class PropertyAccessException extends Exception {
    private PropertyAccessException(Throwable throwable) {
      super(throwable);
    }
  }

  static class Info {
    int id = UNDEFINED;
    int count = 0;
  }

  class NameValue {
    public final String name;
    public final Object value;

    NameValue(String name, Object value) {
      this.name = name;
      this.value = value;
    }
  }

  class NameValueList extends ArrayList<NameValue> {
    private final Object object;

    NameValueList(Object object) {
      this.object = object;
    }

    private void writeAttribute(String name, Object value) {
      add(new NameValue(name, value));
    }

    private Class getElementClass() {
      for (NameValue p : this) {
        if (p.name == "class") {
          return (Class)p.value;
        }
      }
      throw new RuntimeException("No class defined");
    }

    private Collection<NameValue> attributes() {
      ArrayList<NameValue> result = new ArrayList<NameValue>();
      Info info = objectToInfo.get(object);
      if (info.count > 1) {
        NameValue nameValue = (info.id == UNDEFINED)
                              ? new NameValue(Utilities.ID_ATTRIBUTE_NAME, info.id = idCount++)
                              : new NameValue(Utilities.IDREF_ATTRIBUTE_NAME, info.id);
        result.add(nameValue);
      }
      for (NameValue p : this) {
        if (!(p.value instanceof NameValueList)) {
          result.add(p);
        }
      }
      return result;
    }

    private Collection<NameValueList> body() {
      ArrayList<NameValueList> result = new ArrayList<NameValueList>();
      for (NameValue p : this) {
        Object value = p.value;
        if ((value instanceof NameValueList)) {
          result.add((NameValueList)value);
        }
      }
      return result;
    }

    public void writeElement() {
      level++;
      Class aClass = getElementClass();
      String tag = aClass.getSimpleName();

      Collection<NameValue> attributes = attributes();
      Collection<NameValueList> body = body();
      boolean hasBody = body.size() != 0;

      if (attributes.size() == 0) {
        println("<" + tag + ">");
      }
      else {
        println("<" + tag);
        for (NameValue attribute : attributes) {
          if (attribute.name != "class") {
            XMLWriter.this.writeAttribute(attribute.name, attribute.value);
          }
        }
        if (hasBody) {
          println(">");
        }
      }

      for (NameValueList element : body) {
        element.writeElement();
      }

      println(!hasBody ? "/>" : "</" + tag + ">");
      level--;
    }

  }

  abstract static class Property<T> {
    abstract String getName();

    abstract Class getType();

    abstract Object getValue(T o) throws PropertyAccessException;
  }

  static class FieldProperty extends Property {
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
    Object getValue(Object o) throws PropertyAccessException {
      try {
        return field.get(o);
      }
      catch (IllegalAccessException e) {
        throw new PropertyAccessException(e);
      }
    }
  }

  static class MethodProperty extends Property {
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
    Object getValue(Object o) throws PropertyAccessException {
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
      if (m.getName().startsWith("get")) { // todo or "is"
        methods.add(m);
      }
    }
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
    return methods.toArray(new Method[methods.size()]);
  }

  private static Property[] computeProperties(Class c) {
    List<Property> result = new ArrayList<Property>();
    for (Field f : c.getFields()) {
      result.add(new FieldProperty(f));
    }
    for (Method m : findGetters(c)) {
      result.add(new MethodProperty(m));
    }
    return result.toArray(new Property[result.size()]);
  }

  public Property[] getProperties(Class c) {
    Property[] result = classToProperties.get(c);
    if (result == null) {
      classToProperties.put(c, result = computeProperties(c));
    }
    return result;
  }

  public void write(Object o) {
    println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    level = -1;
    traverse(o, null, true).writeElement();
  }

  private void indent() {
    for (int i = 0; i < level; i++) {
      out.write(' ');
    }
  }

  private void print(String s) {
    indent();
    out.print(s);
  }

  private void println(String s) {
    indent();
    out.println(s);
  }

  private void writeAttribute(String name, Object value) {
    level++;
    println(name + " = \"" + value + "\"");
    level--;
  }

  private NameValueList traverse(Object o, String propertyName, boolean isTopLevel) {
    NameValueList result = new NameValueList(o);
    Class aClass = o.getClass();

    result.writeAttribute("class", aClass);

    if (isTopLevel) {
      String packageName = aClass.getPackage().getName(); // todo deal with multiple packages
      result.writeAttribute(Utilities.NAME_SPACE_TAG, "http://schemas.android.com?import=" + packageName + ".*");
    }

    if (propertyName != null) {
      result.writeAttribute(Utilities.PROPERTY_ATTRIBUTE_NAME, propertyName);
    }

    Info info = objectToInfo.get(o);
    info.count++;
    if (info.count > 1) {
      return result;
    }

    if (o instanceof Collection) {
      for (Object element : (Collection)o) {
        result.writeAttribute(null, traverse(element, null, false));
      }
    }
    else {
      for (Property p : getProperties(aClass)) {
        if (p.getName().equals("class")) {
          continue;
        }
        String name = p.getName();
        try {
          Object value = p.getValue(o);
          if (value != null) {
            Class type = p.getType();
            if (isPrimitive(type)) {
              result.writeAttribute(name, value);
            }
            else {
              result.writeAttribute(name, traverse(value, name, false));
            }
          }
        }
        catch (PropertyAccessException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return result;
  }


}
