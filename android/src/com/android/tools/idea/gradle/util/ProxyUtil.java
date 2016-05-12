/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.android.annotations.VisibleForTesting;
import com.google.common.collect.*;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;

import static java.lang.reflect.Proxy.getInvocationHandler;
import static java.lang.reflect.Proxy.isProxyClass;

public final class ProxyUtil {
  /*
   * List of generic classes supported by the reproxy method. When the reproxy method is updated to handle more classes,
   * this list needs to be updated with those additional classes and when this class is used reproxy any class the
   * ProxyUtilTest#testSupportedTypes needs to be updated to keep checking the applicability of reproxying to that class.
   */
  @SuppressWarnings("unchecked") private static final Set<Class<?>> SUPPORTED_TYPES =
    ImmutableSet.of(File.class, Boolean.class, String.class, Integer.class, Collection.class, Set.class, List.class, Map.class);

  private ProxyUtil() {
  }

  @VisibleForTesting
  static boolean isSupported(@NotNull Class<?> clazz) {
    return clazz.isPrimitive() || SUPPORTED_TYPES.contains(clazz);
  }

  /**
   * Regenerate proxy objects with a serializable version of a proxy.
   * This method is intended to be run on objects that are a bag of properties, particularly custom Gradle model objects.
   * Here we assume that the given object can be represented as a map of method name to return value. The original object
   * is regenerated using this assumption which gives a serializable/deserializable object.
   *
   * If a method throws an exception it is assumed that it is the intended behaviour and the same exception will be thrown in the
   * reproxied counterpart. This is useful for Gradle model methods that are not present in the actual model object being used.
   *
   * @param object the object to 'reproxy'.
   * @param type   the runtime type of the object. This is the expected type of object, and must be a superclass or equals to T.
   * @param <T>    the type of the object.
   * @return the reproxied object.
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public static <T> T reproxy(@NotNull Type type, @Nullable T object) {
    if (object == null) {
      return null;
    }
    if (object instanceof InvocationErrorValue) {
      return object;
    }

    if (type instanceof ParameterizedType) {
      ParameterizedType genericType = (ParameterizedType)type;
      if (genericType.getRawType() instanceof Class) {
        Class<?> genericClass = (Class<?>)genericType.getRawType();
        if (Collection.class.isAssignableFrom(genericClass)) {
          Collection<Object> collection = (Collection<Object>)object;
          Collection<Object> newCollection;
          if (genericClass.isAssignableFrom(ArrayList.class)) {
            newCollection = Lists.newArrayListWithCapacity(collection.size());
          }
          else if (genericClass.isAssignableFrom(LinkedHashSet.class)) {
            newCollection = Sets.newLinkedHashSet();
          }
          else {
            throw new IllegalStateException("Unsupported collection type: " + genericClass.getCanonicalName());
          }
          Type argument = genericType.getActualTypeArguments()[0];
          for (Object item : collection) {
            newCollection.add(reproxy(argument, item));
          }
          return (T)newCollection;
        }
        else if (Map.class.isAssignableFrom(genericClass)) {
          Map<Object, Object> map = (Map<Object, Object>)object;
          Map<Object, Object> newMap = Maps.newLinkedHashMap();
          Type keyType = genericType.getActualTypeArguments()[0];
          Type valueType = genericType.getActualTypeArguments()[1];
          for (Map.Entry entry : map.entrySet()) {
            newMap.put(reproxy(keyType, entry.getKey()), reproxy(valueType, entry.getValue()));
          }
          return (T)newMap;
        }
        else {
          throw new IllegalStateException("Unsupported generic type: " + genericClass.getCanonicalName());
        }
      }
      else {
        throw new IllegalStateException("Unsupported raw type.");
      }
    }

    // Only modify proxy objects...
    if (!isProxyClass(object.getClass())) {
      return object;
    }

    // ...that are not our own proxy.
    if (getInvocationHandler(object) instanceof WrapperInvocationHandler) {
      return object;
    }

    Class<?>[] interfaces = object.getClass().getInterfaces();
    if (interfaces.length != 1) {
      throw new IllegalStateException("Cannot 'reproxy' a class with multiple interfaces");
    }
    Class<?> clazz = interfaces[0];

    final Map<String, Object> values = Maps.newHashMap();
    for (Method m : clazz.getMethods()) {
      try {
        if (Modifier.isPublic(m.getModifiers())) {
          Object value;
          try {
            value = m.invoke(object);
          } catch (InvocationTargetException e) {
            value = new InvocationErrorValue(e.getCause());
          }
          values.put(m.toGenericString(), reproxy(m.getGenericReturnType(), value));
        }
      }
      catch (IllegalAccessException e) {
        throw new IllegalStateException("A non public method shouldn't have been called.", e);
      }
    }
    return (T)Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new WrapperInvocationHandler(values));
  }

  static class WrapperInvocationHandler implements InvocationHandler, Serializable {
    private static final Method TO_STRING = getObjectMethod("toString");
    private static final Method HASHCODE = getObjectMethod("hashCode");
    private static final Method EQUALS = getObjectMethod("equals", Object.class);
    @VisibleForTesting
    final Map<String, Object> values;

    WrapperInvocationHandler(@NotNull Map<String, Object> values) {
      this.values = values;
    }

    @NotNull
    private static Method getObjectMethod(@NotNull String name, @NotNull Class<?>... types) {
      try {
        return Object.class.getMethod(name, types);
      }
      catch (NoSuchMethodException e) {
        throw new IllegalStateException("Method should exist in Object", e);
      }
    }

    @Override
    public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
      if (method.equals(TO_STRING) || method.equals(HASHCODE)) {
        return method.invoke(this, objects);
      }
      else if (method.equals(EQUALS)) {
        return proxyEquals(objects[0]);
      }
      else {
        String key = method.toGenericString();
        if (!values.containsKey(key)) {
          throw new UnsupportedMethodException("Method " + key + " not found");
        }
        Object value = values.get(key);
        if (value instanceof InvocationErrorValue) {
          throw ((InvocationErrorValue)value).exception;
        }
        return value;
      }
    }

    private boolean proxyEquals(Object other) {
      return other != null && isProxyClass(other.getClass()) && getInvocationHandler(other).equals(this);
    }
  }

  public static class InvocationErrorValue implements Serializable {
    public Throwable exception;

    private InvocationErrorValue(Throwable exception) {
      this.exception = exception;
    }
  }

  public static boolean isAndroidModelProxyObject(@NotNull Object obj) {
    return isProxyClass(obj.getClass()) && getInvocationHandler(obj) instanceof WrapperInvocationHandler;
  }

  @NotNull
  public static Map<String, Object> getAndroidModelProxyValues(@NotNull Object obj) {
    if (isAndroidModelProxyObject(obj)) {
      WrapperInvocationHandler handler = (WrapperInvocationHandler)getInvocationHandler(obj);
      return handler.values;
    }
    return ImmutableMap.of();
  }

  public static boolean isValidProxyObject(@NotNull Object obj) {
    if (!isAndroidModelProxyObject(obj)) {
      if (obj instanceof Collection && (!((Collection)obj).isEmpty())) {
        for (Object valueObj : (Collection)obj) {
          if (valueObj != null && !isValidProxyObject(valueObj)) {
            return false;
          }
        }
      }
      else if (obj instanceof Map && (!((Map)obj).isEmpty())) {
        Map map = (Map)obj;
        for (Object valueObj : map.values()) {
          if (valueObj != null && !isValidProxyObject(valueObj)) {
            return false;
          }
        }
      }
      return true; // It's not our proxy object and we won't be able to verify it's validity, so lets assume it's valid.
    }

    Class<?>[] interfaces = obj.getClass().getInterfaces();
    if (interfaces.length != 1) {
      return false; // This should never happen because we support only proxying classes with a single interface.
    }
    Class<?> clazz = interfaces[0];

    Map<String, Object> androidModelProxyValues = getAndroidModelProxyValues(obj);
    for (Method m : clazz.getMethods()) {
      if (Modifier.isPublic(m.getModifiers()) && !androidModelProxyValues.containsKey(m.toGenericString())) {
        return false;
      }
    }

    for (Object valueObj : androidModelProxyValues.values()) {
      if (valueObj != null && !isValidProxyObject(valueObj)) {
        return false;
      }
    }

    return true;
  }
}
