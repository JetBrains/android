/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import android.view.ViewGroup;
import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.base.Splitter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.android.resources.ResourceType.ID;
import static java.util.Arrays.stream;

/**
 * Class to handle the access to LayoutParams instances
 */
public class LayoutParamsManager {
  /**
   * Object that represents a missing default (we can not use null since null is a valid default).
   */
  private static final Object MISSING = new Object();

  private enum FieldType {
    UNKNOWN,
    INTEGER,
    DIMENSION,
    FLOAT,
    STRING,
    BOOLEAN,
    ENUM,
    FLAG;

    public static FieldType fromType(@NotNull Class type) {
      if (type == Integer.class || type == int.class) {
        return INTEGER;
      }
      else if (type == Float.class || type == float.class) {
        return FLOAT;
      }
      else if (type == String.class) {
        return STRING;
      }

      return UNKNOWN;
    }
  }

  private static final Cache<String, Map<String, Object>> ourDefaultValuesCache = CacheBuilder.newBuilder()
    .expireAfterAccess(10, TimeUnit.MINUTES)
    .softValues()
    .build();
  private static final Map<String, Function<String, MappedField>> FIELD_MAPPERS = new HashMap<>();

  /**
   * Registers the given field mapper to resolve attributes for the given LayoutParams class. The field mapper will return the field name
   * and type that store the attribute value for a given attribute name.
   */
  public static void registerFieldMapper(@NotNull String layoutParamsClassName, @NotNull Function<String, MappedField> mapper) {
    FIELD_MAPPERS.put(layoutParamsClassName, mapper);
  }

  static {
    registerFieldMapper(ViewGroup.LayoutParams.class.getName(), (attributeName) -> {
      switch (attributeName) {
        case "width":
        case "height":
          return new MappedField(attributeName, FieldType.DIMENSION);
        case "gravity":
          return new MappedField(attributeName, FieldType.FLAG);
      }

      return null;
    });
    registerFieldMapper(ViewGroup.MarginLayoutParams.class.getName(), (attributeName) -> {
      switch (attributeName) {
        case "marginBottom":
          return new MappedField("bottomMargin", FieldType.DIMENSION);
        case "marginTop":
          return new MappedField("topMargin", FieldType.DIMENSION);
        case "marginLeft":
          return new MappedField("leftMargin", FieldType.DIMENSION);
        case "marginRight":
          return new MappedField("rightMargin", FieldType.DIMENSION);
        case "marginStart":
          return new MappedField(attributeName, FieldType.DIMENSION);
        case "marginEnd":
          return new MappedField(attributeName, FieldType.DIMENSION);
      }

      return null;
    });
    registerFieldMapper("android.support.constraint.ConstraintLayout$LayoutParams", (attributeName) -> {
      /*
       This field mapper converts the given ConstraintLayout$LayoutParams attribute name into the field name that
       stores its value.
       All ConstraintLayout attributes are in the form of layout_constraint* so we do the following processing:
       - Remove the "constraint" part (the layout_ prefix is already removed before the call to this mapper
       - Remove "Of" at the end (if it is present). While some fields have "Of" at the end, the corresponding fields
         do not.
       - Convert the case from the lower underscores format to camel case.

       For an attribute like "layout_constraintTop_toTopOf", the resulting field would be "topToTop"
       */

      attributeName = StringUtil.trimStart(attributeName, "constraint");
      attributeName = StringUtil.trimEnd(attributeName, "Of");

      StringBuilder fieldName = new StringBuilder();
      boolean first = true;
      for (String component : Splitter.on('_').split(attributeName)) {
        fieldName.append(first ? StringUtil.decapitalize(component) : StringUtil.capitalize(component));
        first = false;
      }
      return new MappedField(fieldName.toString(), FieldType.UNKNOWN);
    });
  }

  private static boolean setField(@NotNull Object target, @NotNull MappedField fieldName, @Nullable Object value) {
    try {
      target.getClass().getField(fieldName.name).set(target, value);
      return true;
    }
    catch (IllegalAccessException | NoSuchFieldException ignored) {
    }

    // In some cases, like MarginLayoutParams, some attributes need a setter call. Try that here
    String setterName = "set" + StringUtil.capitalize(fieldName.name);

    // Find the setter method. We do not use getMethod since that requires us knowing in advance if the type used is,
    // for example, Integer or int
    // TODO: Should this deal with overloading?
    Optional<Method> setterMethod = stream(target.getClass().getMethods())
      .filter((method) -> method.getParameterCount() == 1 && method.getName().equals(setterName))
      .findFirst();

    if (setterMethod.isPresent()) {
      try {
        setterMethod.get().invoke(target, value);
        return true;
      }
      catch (IllegalAccessException | InvocationTargetException ignored) {
      }
    }

    return false;
  }

  /**
   * Returns the passed string value as a dimension in pixels taking into account the given screen dpi
   */
  @NotNull
  private static Integer getDimensionValue(@NotNull String value, @NotNull Configuration configuration) {
    switch (value) {
      case SdkConstants.VALUE_FILL_PARENT:
      case SdkConstants.VALUE_MATCH_PARENT:
        return ViewGroup.LayoutParams.MATCH_PARENT;
      case SdkConstants.VALUE_WRAP_CONTENT:
        return ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    ResourceHelper.TypedValue out = new ResourceHelper.TypedValue();
    if (ResourceHelper.parseFloatAttribute(value, out, true)) {
      return ResourceHelper.TypedValue.complexToDimensionPixelSize(out.data, configuration);
    }
    return 0;
  }

  /**
   * Infers a {@link FieldType} from the LayoutParams class field type
   */
  @NotNull
  private static FieldType inferTypeFromField(@NotNull Object layoutParams, @NotNull MappedField mappedField) {
    try {
      Field field = layoutParams.getClass().getField(mappedField.name);

      return FieldType.fromType(field.getType());
      // TODO: LayoutParams fields contain the ViewDebug runtime annotation that would allow us mapping both enums and flags
    }
    catch (NoSuchFieldException ignored) {
    }

    return FieldType.UNKNOWN;
  }

  /**
   * Returns the default value of the given field in the passed layoutParams.
   * @throws NoSuchElementException if the method wasn't able to find a default value for the given fieldName
   */
  @Nullable
  private static Object getDefaultValue(@NotNull Object layoutParams, @NotNull MappedField field) throws NoSuchElementException {
    String layoutParamsClassName = layoutParams.getClass().getName();
    Map<String, Object> layoutParamsDefaults = ourDefaultValuesCache.getIfPresent(layoutParamsClassName);
    if (layoutParamsDefaults == null) {
      layoutParamsDefaults = getDefaultValuesFromClass(layoutParams.getClass());
      ourDefaultValuesCache.put(layoutParamsClassName, layoutParamsDefaults);
    }

    Object defaultValue = layoutParamsDefaults.getOrDefault(field.name, MISSING);
    if (defaultValue == MISSING) {
      throw new NoSuchElementException();
    }

    return defaultValue;
  }

  /**
   * Infers a {@link FieldType} from passed value
   */
  private static FieldType inferTypeFromValue(@Nullable String value) {
    if (value != null) {
      if (value.endsWith(SdkConstants.UNIT_DP) || value.endsWith(SdkConstants.UNIT_DIP) || value.endsWith(SdkConstants.UNIT_PX)) {
        return FieldType.DIMENSION;
      }
    }

    return FieldType.UNKNOWN;
  }

  /**
   * Returns a map containing the default values for all the fields in the class
   */
  @NotNull
  private static Map<String, Object> getDefaultValuesFromClass(@NotNull Class layoutParamsClass) {
    Object layoutParamsClassInstance = null;
    // Find a constructor that we can instantiate. Usually we can use one with one or two ints and set them to 0
    for (Constructor constructor : layoutParamsClass.getConstructors()) {
      Class<?>[] parameterTypes = constructor.getParameterTypes();
      Object[] parameterDefaults = stream(parameterTypes).map((type) -> {
        if (type == Integer.class || type == int.class) {
          return Integer.valueOf(0);
        }
        return null;
      }).filter(Objects::nonNull).toArray();

      if (parameterTypes.length == parameterDefaults.length) {
        try {
          layoutParamsClassInstance = constructor.newInstance(parameterDefaults);
          break;
        }
        catch (InstantiationException | IllegalAccessException | InvocationTargetException ignore) {
        }
        // Try next constructor
      }
    }

    if (layoutParamsClassInstance == null) {
      return Collections.emptyMap();
    }

    Field[] fields = layoutParamsClass.getFields();
    HashMap<String, Object> defaults = new HashMap<>();
    Object finalLayoutParamsClassInstance = layoutParamsClassInstance;
    stream(fields)
      // Filter final or static fields
      .filter(field -> !Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()))
      .forEach(field -> {
        try {
          defaults.put(field.getName(), field.get(finalLayoutParamsClassInstance));
        }
        catch (IllegalAccessException ignored) {
        }
      });

    // Get values for properties that are only accessible through getters
    Method[] methods = layoutParamsClass.getMethods();
    stream(methods)
      .filter(method -> method.getParameterCount() == 0 && Modifier.isPublic(method.getModifiers()) && method.getName().startsWith("get"))
      .forEach(method -> {
        String propertyName = StringUtil.decapitalize(StringUtil.trimStart(method.getName(), "get"));
        if (!defaults.containsKey(propertyName)) {
          // We do not have a value for that property already, call the getter
          try {
            defaults.put(propertyName, method.invoke(finalLayoutParamsClassInstance));
          }
          catch (IllegalAccessException | InvocationTargetException ignore) {
          }
        }
      });

    return defaults;
  }

  /**
   * Maps the given attribute name to a field name in the passed layout params.
   */
  @NotNull
  private static MappedField mapField(@NotNull Object layoutParams, @NotNull String attributeName) {
    Class currentClass = layoutParams.getClass();
    while (!currentClass.equals(Object.class)) {
      Function<String, MappedField> fieldMapper = FIELD_MAPPERS.get(currentClass.getName());
      if (fieldMapper != null) {
        MappedField mappedField = fieldMapper.apply(attributeName);
        if (mappedField != null) {
          try {
            currentClass.getDeclaredField(mappedField.name);
            return mappedField;
          }
          catch (NoSuchFieldException ignore) {
          }

          String setterName = "set" + StringUtil.capitalize(mappedField.name);
          for (Method method : currentClass.getDeclaredMethods()) {
            if (setterName.equals(method.getName())) {
              return mappedField;
            }
          }
        }
      }
      currentClass = currentClass.getSuperclass();
    }

    // We do not know anything about the field so keep the name and type unknown
    return new MappedField(attributeName, FieldType.UNKNOWN);
  }

  /**
   * Sets the given attribute in the passed layoutParams instance. This method tries to infer the type to use from the attribute name and
   * the field type.
   * <p>
   * @return whether the method was able to set the attribute or not.
   */
  public static boolean setAttribute(@NotNull Object layoutParams,
                                     @NotNull String attributeName,
                                     @Nullable String value,
                                     @NotNull NlModel model) {
    FieldType inferredType = FieldType.UNKNOWN;
    if (value != null &&
        (value.startsWith(SdkConstants.PREFIX_RESOURCE_REF) || value.startsWith(SdkConstants.PREFIX_THEME_REF)) &&
        model.getConfiguration().getResourceResolver() != null) {
      // This is a reference so we resolve the actual value and we try to infer the type from the given reference type
      ResourceValue resourceValue = model.getConfiguration().getResourceResolver().findResValue(value, false);

      if (resourceValue != null) {
        value = resourceValue.getValue();

        // Try to use the reference to infer the type
        //noinspection EnumSwitchStatementWhichMissesCases
        switch (resourceValue.getResourceType()) {
          case INTEGER:
          case ID:
          case DIMEN:
            inferredType = FieldType.INTEGER;
            break;
          case FRACTION:
            inferredType = FieldType.FLOAT;
            break;
        }

        if (resourceValue.getResourceType() == ID) {
          Integer resolvedId = AppResourceRepository.getAppResources(model.getFacet(), true).getResourceId(ID, resourceValue.getName());
          // TODO: Remove this wrapping/unwrapping
          value = resolvedId.toString();
        }
      }
    }

    // Now we have a value and an attributeName. We now try to map the given attributeName to the field in the LayoutParams that
    // stores its value.
    MappedField mappedField = mapField(layoutParams, attributeName);

    if (inferredType == FieldType.UNKNOWN) {
      // If we don't know the type yet, use the field type.
      inferredType = mappedField.type;
    }

    // If we still don't have a type, we will now try to infer the type from:
    // 1. The value (ex. if it contains "px" or "dp", we know it's a dimension
    // 2. The field type in the LayoutParams class
    // 3. Lastly, if we do not have a better option, we try to infer the value from the default value in the class
    if (inferredType == FieldType.UNKNOWN) {
      inferredType = inferTypeFromValue(value);
    }

    if (inferredType == FieldType.UNKNOWN) {
      inferredType = inferTypeFromField(layoutParams, mappedField);
    }

    Object defaultValue = null;
    try {
      defaultValue = getDefaultValue(layoutParams, mappedField);
    } catch (NoSuchElementException ignore) {
    }
    if (defaultValue != null && inferredType == FieldType.UNKNOWN) {
      inferredType = FieldType.fromType(defaultValue.getClass());
    }

    if (value == null) {
        return setField(layoutParams, mappedField, defaultValue);
    }
    else {
      // TODO: correctly fixes enum resolution
      String layoutParamsName = layoutParams.getClass().getName();
      if (inferredType == FieldType.INTEGER
          && value.equalsIgnoreCase(SdkConstants.ATTR_PARENT)
          && layoutParamsName.equalsIgnoreCase(SdkConstants.CLASS_CONSTRAINT_LAYOUT_PARAMS)) {
        value = "0";
      }
      boolean fieldSet;
      switch (inferredType) {
        case DIMENSION:
          fieldSet = setField(layoutParams, mappedField, getDimensionValue(value, model.getConfiguration()));
          break;
        case INTEGER:
          try {
            fieldSet = setField(layoutParams, mappedField, Integer.parseInt(value));
          } catch (NumberFormatException e) {
            fieldSet = false;
          }
          break;
        case STRING:
          fieldSet = setField(layoutParams, mappedField, value);
          break;
        case BOOLEAN:
          fieldSet = setField(layoutParams, mappedField, Boolean.parseBoolean(value));
          break;
        case FLOAT:
          try {
            fieldSet = setField(layoutParams, mappedField, Float.parseFloat(value));
          } catch (NumberFormatException e) {
            fieldSet = false;
          }
          break;
        case ENUM:
        case FLAG:
        case UNKNOWN:
        default:
          return false; // Couldn't be applied
      }

      return fieldSet;
    }
  }

  /**
   * Class that contains a field name and its associated data type
   */
  private static class MappedField {
    @NotNull private final String name;
    @NotNull private final FieldType type;

    MappedField(@NotNull String fieldName, @NotNull FieldType type) {
      this.name = fieldName;
      this.type = type;
    }
  }
}
