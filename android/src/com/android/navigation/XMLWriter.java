package com.android.navigation;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class XMLWriter {
  private final Map<Class, Method[]> classToProperties = new IdentityHashMap<Class, Method[]>();
  private final PrintStream out;
  private int level;

  public XMLWriter(OutputStream out) {
    this.out = new PrintStream(out);
  }

  private static boolean isPrimitive(Class type) {
    return type.isPrimitive() || type == String.class;
  }

  private static Method[] findProperties(Class c) {
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

  public Method[] getProperties(Class c) {
    Method[] result = classToProperties.get(c);
    if (result == null) {
      classToProperties.put(c, result = findProperties(c));
    }
    return result;
  }

  public void write(Object o) {
    println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    level = -1;
    write(o, null);
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

  private void write(Object o, String propertyName) {
    level++;
    Class aClass = o.getClass();
    String tag = aClass.getSimpleName();
    boolean hadBody = false;
    println("<" + tag);
    if (level == 0) {
      String packageName = aClass.getPackage().getName(); // todo deal with multiple packages
      writeAttribute("xmlns", "http://schemas.android.com?import=" + packageName + ".*");
    }
    if (propertyName != null) {
      writeAttribute(Utilities.PROPERTY_ATTRIBUTE_NAME, propertyName);
    }
    if (o instanceof Collection) {
      for (Object element : (Collection)o) {
        if (!hadBody) {
          println(">");
          hadBody = true;
        }
        write(element, null);
      }
    }
    else {
      for (Method m : getProperties(aClass)) {
        if (m.getName().equals("getClass")) {
          continue;
        }
        String name = Utilities.getPropertyName(m);
        try {
          Object value = m.invoke(o);
          if (value != null) {
            Class type = m.getReturnType();
            if (isPrimitive(type)) {
              writeAttribute(name, value);
            }
            else {
              if (!hadBody) {
                println(">");
                hadBody = true;
              }
              write(value, name);
            }
          }
        }
        catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
        catch (InvocationTargetException e) {
          throw new RuntimeException(e);
        }
      }
    }
    println(!hadBody ? "/>" : "</" + tag + ">");
    level--;
  }
}
