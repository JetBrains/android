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

import com.android.annotations.Property;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.HashMap;

class ReflectiveHandler extends DefaultHandler {
  // public static final List<String> DEFAULT_PACKAGES = Arrays.<String>asList("java.lang", "android.view", "android.widget");
  public static final List<String> DEFAULT_PACKAGES = Arrays.asList();
  public static final List<String> DEFAULT_CLASSES = Arrays.asList();

  private final List<String> packagesImports = new ArrayList<String>(DEFAULT_PACKAGES);
  private final List<String> classImports = new ArrayList<String>(DEFAULT_CLASSES);
  private final ErrorHandler errorHandler;
  private final Stack<Object> stack = new Stack<Object>();
  private final Stack<String> nameStack = new Stack<String>();
  private final Map<String, Object> idToValue = new HashMap<String, Object>();

  private Locator documentLocator;
  public Object result;

  public ReflectiveHandler(ErrorHandler errorHandler) {
    this.errorHandler = errorHandler;
  }

  @Override
  public void setDocumentLocator(Locator documentLocator) {
    this.documentLocator = documentLocator;
  }

  private void processNameSpace(String nameSpace) {
    String[] imports = nameSpace.split("[?;]");
    for (String imp : imports) {
      String importPrefix = "import=";
      String packageSuffix = ".*";
      if (imp.startsWith(importPrefix)) {
        if (imp.endsWith(packageSuffix)) {
          packagesImports.add(imp.substring(importPrefix.length(), imp.length() - packageSuffix.length()));
        }
        else {
          classImports.add(imp.substring(importPrefix.length()));
        }
      }
    }
  }

  public Class getClassForName(String tag) throws ClassNotFoundException {
    ClassLoader classLoader = getClass().getClassLoader();
    for (String clazz : classImports) {
      if (clazz.endsWith("." + tag)) {
        return classLoader.loadClass(clazz);
      }
    }
    for (String pkg : packagesImports) {
      try {
        return classLoader.loadClass(pkg + "." + tag);
      }
      catch (ClassNotFoundException e) {
        // Class was not defined by this import, continue.
      }
    }
    throw new ClassNotFoundException("Could not find class for tag: " + tag);
  }

  private static class PropertyAnnotationNotFoundException extends Exception {
  }

  private static String getName(Annotation[] parameterAnnotation) throws PropertyAnnotationNotFoundException {
    for (Annotation a : parameterAnnotation) {
      if (a instanceof Property) {
        return ((Property)a).value();
      }
    }
    throw new PropertyAnnotationNotFoundException();
  }

  private static Object valueFor(Class<?> type, String stringValue)
    throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    if (type == String.class) {
      return stringValue;
    }
    if (type.isPrimitive()) {
      type = Utilities.wrapperForPrimitiveType(type);
    }
    return type.getConstructor(String.class).newInstance(stringValue);
  }

  static class Pair<T1, T2> {
    T1 o1;
    T2 o2;

    private Pair(T1 o1, T2 o2) {
      this.o1 = o1;
      this.o2 = o2;
    }

    static <T1, T2> Pair<T1, T2> of(T1 o1, T2 o2) {
      return new Pair<T1, T2>(o1, o2);
    }
  }

  private static Pair<String[], Object> createInstance(Class clz, Map<String, String> attributes) throws InstantiationException {
    Constructor[] constructors = clz.getConstructors();
    Arrays.sort(constructors, new Comparator<Constructor>() {
      @Override
      public int compare(Constructor c1, Constructor c2) {
        return c1.getParameterTypes().length - c2.getParameterTypes().length;
      }
    });
    for (Constructor constructor : constructors) {
      try {
        String[] parameterNames = getParameterNames(constructor);
        Object value = constructor.newInstance(getParameterValues(constructor, parameterNames, attributes));
        return Pair.of(parameterNames, value);
      }
      catch (PropertyAnnotationNotFoundException e) {
        // ok, try next constructor
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      catch (InstantiationException e) {
        throw new RuntimeException(e);
      }
      catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
    throw new InstantiationException();
  }

  private static String[] getParameterNames(Constructor constructor) throws PropertyAnnotationNotFoundException {
    Annotation[][] annotations = constructor.getParameterAnnotations();
    String[] result = new String[annotations.length];
    for (int i = 0; i < annotations.length; i++) {
      result[i] = getName(annotations[i]);
    }
    return result;
  }

  private static Object[] getParameterValues(Constructor constructor, String[] parameterNames, Map<String, String> attributes) throws
                                                                                                NoSuchMethodException,
                                                                                                IllegalAccessException,
                                                                                                InvocationTargetException,
                                                                                                InstantiationException {
    Class[] types = constructor.getParameterTypes();
    Object[] result = new Object[parameterNames.length];
    for (int i = 0; i < parameterNames.length; i++) {
      result[i] = valueFor(types[i], attributes.get(parameterNames[i]));
    }
    return result;
  }

  private static void installInOuter(Object outer, String propertyName, Object instance)
    throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    if (propertyName != null) {
      applySetter(outer, propertyName, instance);
    }
    else {
      applyMethod(outer, "add", instance);
    }
  }

  private static void applySetter(Object outer, String propertyName, Object instance)
    throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    getSetter(outer.getClass(), propertyName).invoke(outer, instance);
  }

  private static void applyMethod(Object target, String methodName, Object parameter)
    throws InvocationTargetException, IllegalAccessException {
    Class targetType = target.getClass();
    Class parameterType = parameter.getClass();
    for (Method m : targetType.getMethods()) {
      if (m.getName().equals(methodName)) {
        Class<?>[] parameterTypes = m.getParameterTypes();
        if (parameterTypes.length == 1 && parameterTypes[0].isAssignableFrom(parameterType)) {
          m.invoke(target, parameter);
          break;
        }
      }
    }
  }

  private void handleWarning(Exception e) throws SAXException {
    errorHandler.warning(new SAXParseException(e.getMessage(), documentLocator, e));
  }

  private void handleError(Exception e) throws SAXException {
    errorHandler.error(new SAXParseException(e.getMessage(), documentLocator, e));
  }

  private static Method getSetter(Class<?> type, String propertyName) throws NoSuchMethodException {
    String getterMethodName = Utilities.getGetterMethodName(propertyName);
    String setterMethodName = Utilities.getSetterMethodName(propertyName);
    Method getter = type.getMethod(getterMethodName);
    Class propertyType = getter.getReturnType();
    return type.getMethod(setterMethodName, propertyType);
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    Map<String, String> nameToValue = Utilities.toMap(attributes);
    String nameSpace = nameToValue.get(Utilities.NAME_SPACE_ATRIBUTE_NAME);
    Set<String> constructorParameterNames = Collections.emptySet();
    if (nameSpace != null) {
      processNameSpace(nameSpace);
    }
    try {
      String idref = nameToValue.get(Utilities.IDREF_ATTRIBUTE_NAME);
      Object instance;
      if (idref != null) {
        if (!idToValue.containsKey(idref)) {
          throw new SAXException("IDREF attribute, \"" + idref + "\" , was used before corresponding ID was defined.");
        }
        instance = idToValue.get(idref);
      }
      else {
        Pair<String[], Object> result = createInstance(getClassForName(qName), nameToValue);
        constructorParameterNames = new HashSet<String>(Arrays.asList(result.o1));
        instance = result.o2;
      }
      String id = nameToValue.get(Utilities.ID_ATTRIBUTE_NAME);
      if (id != null) {
        idToValue.put(id, instance);
      }

      if (stack.size() != 0) {
        nameStack.add(nameToValue.get(Utilities.PROPERTY_ATTRIBUTE_NAME));
      }
      if (idref == null) {
        for (Map.Entry<String, String> entry : nameToValue.entrySet()) {
          String attributeName = entry.getKey();
          if (Utilities.RESERVED_ATTRIBUTES.contains(attributeName) || constructorParameterNames.contains(attributeName)) {
            continue;
          }
          try {
            Method setter = getSetter(instance.getClass(), attributeName);
            Class argType = Utilities.wrapperForPrimitiveType(setter.getParameterTypes()[0]);
            setter.invoke(instance, valueFor(argType, entry.getValue()));
          }
          catch (NoSuchMethodException e) {
            handleWarning(e);
          }
          catch (IllegalAccessException e) {
            handleWarning(e);
          }
          catch (InvocationTargetException e) {
            handleWarning(e);
          }
          catch (InstantiationException e) {
            handleWarning(e);
          }
        }
      }
      stack.push(instance);
    }
    catch (ClassNotFoundException e) {
      handleError(e);
    }
    catch (InstantiationException e) {
      handleError(e);
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) throws SAXException {
  }

  @Override
  public void endElement(String uri, String localName, String qName) throws SAXException {
    result = stack.pop();
    if (stack.size() != 0) {
      try {
        installInOuter(stack.getLast(), nameStack.pop(), result); // note destructive
      }
      catch (InvocationTargetException e) {
        handleError(e);
      }
      catch (NoSuchMethodException e) {
        handleError(e);
      }
      catch (IllegalAccessException e) {
        handleError(e);
      }
    }
  }
}
