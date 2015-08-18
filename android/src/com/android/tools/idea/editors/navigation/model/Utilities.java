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

import com.intellij.openapi.util.text.StringUtil;
import org.xml.sax.Attributes;

import java.lang.reflect.Method;
import java.util.*;

public class Utilities {
  public static final String NAME_SPACE_ATRIBUTE_NAME = "ns";
  public static final String ID_ATTRIBUTE_NAME = "id";
  public static final String IDREF_ATTRIBUTE_NAME = "idref";
  public static final String PROPERTY_ATTRIBUTE_NAME = "outer.property";
  public static final Set<String> RESERVED_ATTRIBUTES = new HashSet<String>(
    Arrays.asList(NAME_SPACE_ATRIBUTE_NAME, ID_ATTRIBUTE_NAME, IDREF_ATTRIBUTE_NAME, PROPERTY_ATTRIBUTE_NAME));

  public static Map<String, String> toMap(Attributes attributes) {
    Map<String, String> nameToValue = new LinkedHashMap<String, String>();
    for (int i = 0; i < attributes.getLength(); i++) {
      nameToValue.put(attributes.getQName(i), attributes.getValue(i));
    }
    return nameToValue;
  }


  public static String getGetterMethodName(String propertyName) {
    return "get" + StringUtil.capitalize(propertyName);
  }

  public static String getPropertyName(Method getter) {
    return getPropertyName(getter.getName());
  }

  public static String getPropertyName(String getterName) {
    assert getterName.startsWith("get"); // todo check for "is"
    return decapitalize(getterName.substring(3));
  }

  public static String getSetterMethodName(String propertyName) {
    return "set" + StringUtil.capitalize(propertyName);
  }

  public static String decapitalize(String propertyName) {
    return Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
  }

  static Class wrapperForPrimitiveType(Class type) {
    if (!type.isPrimitive()) {
      return type;
    }
    if (type == boolean.class) {
      return Boolean.class;
    }
    if (type == byte.class) {
      return Byte.class;
    }
    if (type == short.class) {
      return Short.class;
    }
    if (type == int.class) {
      return Integer.class;
    }
    if (type == long.class) {
      return Long.class;
    }
    if (type == float.class) {
      return Float.class;
    }
    if (type == double.class) {
      return Double.class;
    }
    if (type == char.class) {
      return Character.class;
    }
    throw new RuntimeException("Internal error");
  }
}
