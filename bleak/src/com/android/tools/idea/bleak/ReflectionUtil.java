/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.bleak;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/* Mostly copied from com.intellij.util.ref.DebugReflectionUtil */
public class ReflectionUtil implements DoNotTrace {

  private static final Hash.Strategy<Class> hashingStrategy = new Hash.Strategy<>() {
    // default strategy seems to be too slow
    @Override
    public int hashCode(Class aClass) {
      if (aClass == null) {
        return 0;
      }
      return aClass.getName().hashCode();
    }

    @Override
    public boolean equals(Class o1, Class o2) {
      return o1 == o2;
    }
  };
  private static final Map<Class, Long> objectSizes = new Object2ObjectOpenCustomHashMap<>(hashingStrategy);
  private static final Map<Class, Field[]> allFields = new Object2ObjectOpenCustomHashMap<>(hashingStrategy);
  private static final Field[] EMPTY_FIELD_ARRAY = new Field[0];

  private static final int OBJECT_HEADER_SIZE = 16;
  private static final int ARRAY_HEADER_SIZE = 20;
  private static final int POINTER_SIZE = 4; // assuming we're using compressed oops

  @NotNull
  public static Field[] getAllFields(@NotNull Class aClass) {
    Field[] cached = allFields.get(aClass);
    if (cached == null) {
      long size = 0;
      try {
        Field[] declaredFields = aClass.getDeclaredFields();
        List<Field> fields = new ArrayList<>(declaredFields.length + 5);
        for (Field declaredField : declaredFields) {
          declaredField.setAccessible(true);
          Class<?> type = declaredField.getType();
          if ((declaredField.getModifiers() & Modifier.STATIC) == 0) size += sizeOf(type);
          if (isTrivial(type)) continue; // unable to hold references, skip
          fields.add(declaredField);
        }
        Class superclass = aClass.getSuperclass();
        if (superclass != null) {
          for (Field sup : getAllFields(superclass)) {
            if (!fields.contains(sup)) {
              fields.add(sup);
            }
          }
          size += objectSizes.get(superclass);
        }
        cached = fields.isEmpty() ? EMPTY_FIELD_ARRAY : fields.toArray(new Field[0]);
      }
      catch (IncompatibleClassChangeError | NoClassDefFoundError | SecurityException e) {
        //this exception may be thrown because there are two different versions of org.objectweb.asm.tree.ClassNode from different plugins
        //I don't see any sane way to fix it until we load all the plugins by the same classloader in tests
        cached = EMPTY_FIELD_ARRAY;
      }
      catch (RuntimeException e) {
        // field.setAccessible() can now throw this exception when accessing unexported module
        if (e.getClass().getName().equals("java.lang.reflect.InaccessibleObjectException")) {
          cached = EMPTY_FIELD_ARRAY;
        }
        else {
          throw e;
        }
      }
      objectSizes.put(aClass, size);
      allFields.put(aClass, cached);
    }
    return cached;
  }

  private static boolean isTrivial(@NotNull Class<?> type) {
    return type.isPrimitive() || type == Class.class;
  }

  // the size that a field of type 'type' takes in an object.
  private static long sizeOf(Class<?> type) {
    if (!type.isPrimitive()) return POINTER_SIZE;
    if (type == Boolean.TYPE) return 1;
    if (type == Byte.TYPE) return 1;
    if (type == Character.TYPE) return 2;
    if (type == Short.TYPE) return 2;
    if (type == Integer.TYPE) return 4;
    if (type == Long.TYPE) return 8;
    if (type == Float.TYPE) return 4;
    if (type == Double.TYPE) return 8;
    throw new IllegalStateException("Size computation: unknown type: " + type.getName());
  }

  private static int arrayLength(Object obj) {
    if (obj.getClass().getComponentType().isPrimitive()) {
      if (obj instanceof boolean[]) return ((boolean[]) obj).length;
      if (obj instanceof byte[]) return ((byte[]) obj).length;
      if (obj instanceof char[]) return ((char[]) obj).length;
      if (obj instanceof short[]) return ((short[]) obj).length;
      if (obj instanceof int[]) return ((int[]) obj).length;
      if (obj instanceof long[]) return ((long[]) obj).length;
      if (obj instanceof float[]) return ((float[]) obj).length;
      if (obj instanceof double[]) return ((double[]) obj).length;
    } else {
      return ((Object[]) obj).length;
    }
    throw new IllegalStateException("Bad array type: " + obj.getClass().getName());
  }

  private static Class<?> getBufferComponentType(Class<?> bufferClass) {
    if (ByteBuffer.class.isAssignableFrom(bufferClass)) return Byte.TYPE;
    if (CharBuffer.class.isAssignableFrom(bufferClass)) return Character.TYPE;
    if (ShortBuffer.class.isAssignableFrom(bufferClass)) return Short.TYPE;
    if (IntBuffer.class.isAssignableFrom(bufferClass)) return Integer.TYPE;
    if (LongBuffer.class.isAssignableFrom(bufferClass)) return Long.TYPE;
    if (FloatBuffer.class.isAssignableFrom(bufferClass)) return Float.TYPE;
    if (DoubleBuffer.class.isAssignableFrom(bufferClass)) return Double.TYPE;
    throw new IllegalStateException("Unknown buffer type: " + bufferClass.getName());
  }

  // estimates the size of obj. This is an underestimate of the real size, as it does not take into account any
  // padding between fields or alignment requirements of the whole object.
  public static long estimateSize(Object obj) {
    if (obj == null) return 0;
    Class<?> klass = obj.getClass();
    if (klass.isArray()) {
      return sizeOf(klass.getComponentType()) * arrayLength(obj) + ARRAY_HEADER_SIZE;
    }
    if (!objectSizes.containsKey(klass)) {
      getAllFields(klass);
    }

    long size = objectSizes.get(klass) + OBJECT_HEADER_SIZE;

    // account for native memory consumed by direct buffers
    if (obj instanceof Buffer) {
      Buffer buf = (Buffer) obj;
      if (buf.isDirect()) {
        size += buf.capacity() * sizeOf(getBufferComponentType(klass));
      }
    }
    return size;
  }

}
