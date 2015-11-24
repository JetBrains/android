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
package com.android.tools.idea.editors.theme.qualifiers;

import com.android.ide.common.resources.configuration.*;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class QualifierUtils {
  private static final Logger LOG = Logger.getInstance(QualifierUtils.class);

  /**
   * Invokes getValue method of a ResourceQualifier and returns the result
   */
  @NotNull
  public static Object getValue(@NotNull ResourceQualifier qualifier) {
    try {
      Method getValue = qualifier.getClass().getMethod("getValue");
      return getValue.invoke(qualifier);
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
      throw new IllegalArgumentException(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
      throw new IllegalArgumentException(e);
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * @return class of the return value of the "getValue" method of resourceQualifierClass
   * For Example: return class of {@link LayoutDirectionQualifier#getValue()} is LayoutDirection
   */
  @Nullable("if there is no getValue method")
  public static Class getValueReturnType(@NotNull Class<? extends ResourceQualifier> resourceQualifierClass) {
    try {
      return resourceQualifierClass.getMethod("getValue").getReturnType();
    }
    catch (NoSuchMethodException e) {
      return null;
    }
  }

  @NotNull
  public static ResourceQualifier createNewResourceQualifier(@NotNull Class<? extends ResourceQualifier> qualifierClass, @NotNull Object value) {
    try {
      Class valueClass = value.getClass();
      if (valueClass.equals(Integer.class)) {
        Constructor constructor = qualifierClass.getConstructor(int.class);
        return (ResourceQualifier)constructor.newInstance(((Integer)value).intValue());
      }
      Constructor constructor = qualifierClass.getConstructor(value.getClass());
      return (ResourceQualifier)constructor.newInstance(value);
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
      throw new IllegalArgumentException(e);
    }
    catch (InstantiationException e) {
      LOG.error(e);
      throw new IllegalArgumentException(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
      throw new IllegalArgumentException(e);
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
      throw new IllegalArgumentException(e);
    }
  }
}
