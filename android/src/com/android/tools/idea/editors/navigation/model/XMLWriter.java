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

import com.android.annotations.NonNull;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

public class XMLWriter {
  public static final String UNDEFINED = null;
  private final Map<Class, Properties.Property[]> classToProperties = new IdentityHashMap<Class, Properties.Property[]>();
  private final PrintStream out;
  private int level;
  private Map<Class, Integer> idCounts = new IdentityHashMap<Class, Integer>();

  public XMLWriter(OutputStream out) {
    this.out = new PrintStream(out);
  }

  private static boolean isPrimitive(Class type) {
    return type.isPrimitive() || type == String.class;
  }

  private String nextId(Class c) {
    Integer prev = idCounts.get(c);
    if (prev == null) {
      prev = -1;
    }
    int result = prev + 1;
    idCounts.put(c, result);
    return Utilities.decapitalize(c.getSimpleName()) + result;
  }

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

  static class Info {
    String id = UNDEFINED;
    int count = 0;
  }

  abstract class NameValue<T> {
    public final String name;
    public final T value;

    NameValue(String name, T value) {
      this.name = name;
      this.value = value;
    }

    public abstract void write();

    public abstract void addToParent(Element parent);
  }

  class Attribute<T> extends NameValue<T> {
    Attribute(String name, T value) {
      super(name, value);
    }

    @Override
    public void write() {
      writeAttribute(name, value);
    }

    @Override
    public void addToParent(Element parent) {
      parent.attributes.add(this);
    }
  }


  class ClassAttribute extends Attribute<Class> {
    public ClassAttribute(String name, Class value) {
      super(name, value);
    }

    /**
     * This method is called when we are considering whether to add a class attribute to, for example,
     * the value of the "state" property of a {@link Transition}. If the property is "get/setState()" we needn't record
     * the fully qualified "State" class as {@link XMLReader} can be infer it from the type of the getter/setter pair.
     */
    @Override
    public void addToParent(Element parent) {
      if (parent.type != value) {
        if (parent.tag == null) {
          parent.tag = Utilities.decapitalize(value.getSimpleName());
        }
        else {
          super.addToParent(parent);
        }
      }
    }

    @Override
    public void write() {
      writeAttribute(name, value.getName());
    }
  }

  class Element extends NameValue<Object> {
    public String tag;
    public final Class type;
    public final ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    public final ArrayList<Element> elements = new ArrayList<Element>();

    Element(Class type, String name, Object value) {
      super(name, value);
      this.tag = name;
      this.type = type;
    }

    @Override
    public void write() {
      level++;
      String tag = this.tag == null ? "object" : this.tag;
      print("<" + tag);
      Info info = objectToInfo.get(value);
      if (info.count > 1) {
        if (info.id == UNDEFINED) {
          writeAttribute(Utilities.ID_ATTRIBUTE_NAME, info.id = nextId(value.getClass()));
        }
        else {
          writeAttribute(Utilities.IDREF_ATTRIBUTE_NAME, info.id);
        }
      }
      for (Attribute attribute : attributes) {
        attribute.write();
      }
      if (!elements.isEmpty()) {
        out.println(">");
        for (Element element : elements) {
          element.write();
        }
        println("</" + tag + ">");
      }
      else {
        out.println("/>");
      }
      level--;
    }

    @Override
    public void addToParent(Element parent) {
      parent.elements.add(this);
    }
  }

  public Properties.Property[] getProperties(Class c) {
    Properties.Property[] result = classToProperties.get(c);
    if (result == null) {
      classToProperties.put(c, result = Properties.computeProperties(c));
    }
    return result;
  }

  public void write(Object o) {
    println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    level = -1;
    NameValue traversal = traverse(Object.class, null, o, true);
    traversal.write();
  }

  private void indent() {
    for (int i = 0; i < level; i++) {
      out.write(' ');
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
    print("\n");
    level++;
    print(name + " = \"" + value + "\"");
    level--;
  }

  private NameValue traverse(@NonNull Class type, String name, Object value, boolean isTopLevel) {
    if (isPrimitive(type)) {
      return new Attribute<Object>(name, value);
    }

    if (type == Class.class) {
      return new ClassAttribute(name, (Class)value);
    }

    Element result = new Element(type, name, value);
    Class aClass = value.getClass();

    if (isTopLevel) {
      String packageName = aClass.getPackage().getName(); // todo deal with multiple packages
      result.attributes.add(new Attribute<Object>(Utilities.NAME_SPACE_ATRIBUTE_NAME, "http://schemas.android.com?import=" + packageName + ".*"));
    }

    Info info = objectToInfo.get(value);
    info.count++;
    if (info.count > 1) {
      return result;
    }

    // recurse into each property, using reflection
    for (Properties.Property p : getProperties(aClass)) {
      try {
        Object propertyValue = p.getValue(value);
        if (propertyValue != null) {
          traverse(p.getType(), p.getName(), propertyValue, false).addToParent(result);
        }
      }
      catch (Properties.PropertyAccessException e) {
        throw new RuntimeException(e);
      }
    }
    // special-case Collections
    if (value instanceof Collection) {
      for (Object element : (Collection)value) {
        traverse(Object.class, null, element, false).addToParent(result);
      }
    }
    return result;
  }
}
