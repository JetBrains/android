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

import static com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX;
import static com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_PARAMS;
import static com.android.AndroidXConstants.CLASS_COORDINATOR_LAYOUT;
import static com.android.resources.ResourceType.ID;
import static java.util.Arrays.stream;

import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.android.SdkConstants;
import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.tools.dom.attrs.AttributeDefinition;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.res.FloatResources;
import com.android.tools.idea.res.ResourceIdManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class to handle the access to LayoutParams instances
 */
public class LayoutParamsManager {
  /**
   * Object that represents a missing default (we can not use null since null is a valid default).
   */
  private static final Object MISSING = new Object();

  private static final Cache<String, Map<String, Object>> ourDefaultValuesCache = CacheBuilder.newBuilder()
    .expireAfterAccess(10, TimeUnit.MINUTES)
    .softValues()
    .build();
  private static final Map<String, Function<String, MappedField>> FIELD_MAPPERS = new HashMap<>();

  private static final Function<String, MappedField> CONSTRAINT_LAYOUT_MAPPER = (attributeName) -> {
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
    return new MappedField(fieldName.toString(), null);
  };
  private static final Function<String, MappedField> COORDINATOR_LAYOUT_MAPPER = (attributeName) -> {
    if ("anchor".equals(attributeName)) {
      return new MappedField("anchorId", AttributeFormat.INTEGER);
    }

    return null;
  };

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
          return new MappedField(attributeName, AttributeFormat.DIMENSION);
        case "gravity":
          return new MappedField(attributeName, AttributeFormat.FLAGS);
      }

      return null;
    });
    registerFieldMapper(LinearLayout.LayoutParams.class.getName(),
                        (attributeName) -> "gravity".equals(attributeName) ? new MappedField(attributeName, AttributeFormat.FLAGS) : null);
    registerFieldMapper(ViewGroup.MarginLayoutParams.class.getName(), (attributeName) -> {
      switch (attributeName) {
        case "marginBottom":
          return new MappedField("bottomMargin", AttributeFormat.DIMENSION);
        case "marginTop":
          return new MappedField("topMargin", AttributeFormat.DIMENSION);
        case "marginLeft":
          return new MappedField("leftMargin", AttributeFormat.DIMENSION);
        case "marginRight":
          return new MappedField("rightMargin", AttributeFormat.DIMENSION);
        case "marginStart":
          return new MappedField(attributeName, AttributeFormat.DIMENSION);
        case "marginEnd":
          return new MappedField(attributeName, AttributeFormat.DIMENSION);
      }

      return null;
    });
    registerFieldMapper(CLASS_CONSTRAINT_LAYOUT_PARAMS.oldName(), CONSTRAINT_LAYOUT_MAPPER);
    registerFieldMapper(CLASS_CONSTRAINT_LAYOUT_PARAMS.newName(), CONSTRAINT_LAYOUT_MAPPER);
    registerFieldMapper(CLASS_COORDINATOR_LAYOUT.oldName() + "$LayoutParams", COORDINATOR_LAYOUT_MAPPER);
    registerFieldMapper(CLASS_COORDINATOR_LAYOUT.newName() + "$LayoutParams", COORDINATOR_LAYOUT_MAPPER);
  }

  /**
   * Returns the matching {@link AttributeFormat}s for the given type or null if there is no match.
   */
  @NotNull
  private static EnumSet<AttributeFormat> attributeFormatFromType(@NotNull Class type) {
    if (type == Integer.class || type == int.class) {
      return EnumSet.of(AttributeFormat.INTEGER);
    }
    else if (type == Float.class || type == float.class) {
      return EnumSet.of(AttributeFormat.FLOAT);
    }
    else if (type == String.class) {
      return EnumSet.of(AttributeFormat.STRING);
    }

    return EnumSet.noneOf(AttributeFormat.class); // unknown
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
      catch (Throwable ignored) {
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

    FloatResources.TypedValue out = new FloatResources.TypedValue();
    if (FloatResources.parseFloatAttribute(value, out, true)) {
      return FloatResources.TypedValue.complexToDimensionPixelSize(out.data, configuration);
    }
    return 0;
  }

  /**
   * Infers the {@link AttributeFormat}s from the LayoutParams class field type
   */
  @NotNull
  private static EnumSet<AttributeFormat> inferTypeFromField(@NotNull Object layoutParams, @NotNull MappedField mappedField) {
    try {
      Field field = layoutParams.getClass().getField(mappedField.name);

      return attributeFormatFromType(field.getType());
      // TODO: LayoutParams fields contain the ViewDebug runtime annotation that would allow us mapping both enums and flags
    }
    catch (NoSuchFieldException ignored) {
    }

    return EnumSet.noneOf(AttributeFormat.class);
  }

  /**
   * Returns the default value of the given field in the passed layoutParams.
   * @throws NoSuchElementException if the method wasn't able to find a default value for the given fieldName
   */
  @VisibleForTesting
  @Nullable
  static Object getDefaultValue(@NotNull Object layoutParams, @NotNull MappedField field) throws NoSuchElementException {
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
   * Infers the {@link AttributeFormat}a from the passed value
   */
  @NotNull
  private static EnumSet<AttributeFormat> inferTypeFromValue(@Nullable String value) {
    if (value != null) {
      if (value.endsWith(SdkConstants.UNIT_DP) || value.endsWith(SdkConstants.UNIT_DIP) || value.endsWith(SdkConstants.UNIT_PX)) {
        return EnumSet.of(AttributeFormat.DIMENSION);
      }
    }

    return EnumSet.noneOf(AttributeFormat.class);
  }

  /**
   * Returns a map containing the default values for all the fields in the class
   */
  @VisibleForTesting
  @NotNull
  static Map<String, Object> getDefaultValuesFromClass(@NotNull Class layoutParamsClass) {
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
      // Filter layoutAnimationParameters declared in ViewGroup.LayoutParams (not relevant for attribute defaults)
      .filter(field -> !"layoutAnimationParameters".equals(field.getName()))
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
      // Remove methods coming from java.lang.Object
      .filter(method -> !method.getDeclaringClass().getName().startsWith("java.lang"))
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
  @VisibleForTesting
  @NotNull
  static MappedField mapField(@NotNull Object layoutParams, @NotNull String attributeName) {
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
    return new MappedField(attributeName, null);
  }

  /**
   * Sets the given attribute in the passed layoutParams instance. This method tries to infer the type to use from the attribute name and
   * the field type.
   * <p>
   * @return whether the method was able to set the attribute or not.
   */
  @VisibleForTesting
  static boolean setAttribute(@Nullable AttributeDefinition attributeDefinition,
                              @NotNull Object layoutParams,
                              @NotNull String attributeName,
                              @Nullable String value,
                              @NotNull Module module,
                              @NotNull Configuration configuration) {
    // Try to get the types from the attribute definition
    EnumSet<AttributeFormat> inferredTypes =
      attributeDefinition != null && !attributeDefinition.getFormats().isEmpty()
      ? EnumSet.copyOf(attributeDefinition.getFormats())
      : EnumSet.noneOf(AttributeFormat.class);
    if (value != null &&
        (value.startsWith(SdkConstants.PREFIX_RESOURCE_REF) || value.startsWith(SdkConstants.PREFIX_THEME_REF)) &&
        configuration.getResourceResolver() != null) {
      // This is a reference so we resolve the actual value and we try to infer the type from the given reference type
      ResourceValue resourceValue = configuration.getResourceResolver().findResValue(value, false);

      if (resourceValue != null) {
        value = resourceValue.getValue();

        // Try to use the reference to infer the type
        //noinspection EnumSwitchStatementWhichMissesCases
        switch (resourceValue.getResourceType()) {
          case INTEGER:
          case ID:
          case DIMEN:
            inferredTypes.add(AttributeFormat.INTEGER);
            break;
          case FRACTION:
            inferredTypes.add(AttributeFormat.FLOAT);
            break;
        }

        if (resourceValue.getResourceType() == ID) {
          // TODO: Remove this wrapping/unwrapping
          value = String.valueOf(ResourceIdManager.get(module).getOrGenerateId(resourceValue.asReference()));
        }
      }
    }

    // Now we have a value and an attributeName. We now try to map the given attributeName to the field in the LayoutParams that
    // stores its value.
    MappedField mappedField = mapField(layoutParams, attributeName);

    if (inferredTypes.isEmpty()) {
      // If we don't know the type yet, use the field type.
      inferredTypes.addAll(mappedField.type);
    }

    // If we still don't have a type, we will now try to infer the type from:
    // 1. The value (ex. if it contains "px" or "dp", we know it's a dimension
    // 2. The field type in the LayoutParams class
    // 3. Lastly, if we do not have a better option, we try to infer the value from the default value in the class
    if (inferredTypes.isEmpty()) {
      inferredTypes.addAll(inferTypeFromValue(value));
    }

    if (inferredTypes.isEmpty()) {
      inferredTypes.addAll(inferTypeFromField(layoutParams, mappedField));
    }

    Object defaultValue = null;
    try {
      defaultValue = getDefaultValue(layoutParams, mappedField);
    } catch (NoSuchElementException ignore) {
    }
    if (defaultValue != null && inferredTypes.isEmpty()) {
      inferredTypes.addAll(attributeFormatFromType(defaultValue.getClass()));
    }

    if (value == null) {
        return setField(layoutParams, mappedField, defaultValue);
    }
    else {
      boolean fieldSet = false;

      for (AttributeFormat type : inferredTypes) {
        switch (type) {
          case DIMENSION:
            fieldSet = setField(layoutParams, mappedField, getDimensionValue(value, configuration));
            break;
          case INTEGER:
            try {
              fieldSet = setField(layoutParams, mappedField, Integer.parseInt(value));
            }
            catch (NumberFormatException e) {
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
            }
            catch (NumberFormatException e) {
              fieldSet = false;
            }
            break;
          case ENUM: {
            Integer intValue = attributeDefinition != null ? attributeDefinition.getValueMapping(value) : null;
            if (intValue != null) {
              fieldSet = setField(layoutParams, mappedField, intValue);
            }
          }
          break;
          case FLAGS: {
            if (attributeDefinition == null) {
              continue;
            }

            OptionalInt flagValue = Splitter.on('|').splitToList(value).stream()
              .map(StringUtil::trim)
              .map(attributeDefinition::getValueMapping)
              .filter(Objects::nonNull)
              .mapToInt(Integer::intValue)
              .reduce((a, b) -> a | b);
            if (flagValue.isPresent()) {
              fieldSet = setField(layoutParams, mappedField, flagValue.getAsInt());
            }
          }
          break;
          default:
            // Couldn't be applied. If there are more types, try the rest
        }

        if (fieldSet) {
          return true;
        }
      }

      return false;
    }
  }

  /**
   * Sets the given attribute in the passed layoutParams instance. This method tries to infer the type to use from the attribute name and
   * the field type.
   * <p>
   *
   * @return whether the method was able to set the attribute or not.
   */
  public static boolean setAttribute(@NotNull Object layoutParams,
                                     @NotNull String attributeName,
                                     @Nullable String value,
                                     @NotNull Module module,
                                     @NotNull Configuration configuration) {
    AttributeDefinition attributeDefinition =
      ResolutionUtils.getAttributeDefinition(module, configuration, ATTR_LAYOUT_RESOURCE_PREFIX + attributeName);
    return setAttribute(attributeDefinition, layoutParams, attributeName, value, module, configuration);
  }

  /**
   * Class that contains a field name and its associated data type
   */
  @VisibleForTesting
  static class MappedField {
    @VisibleForTesting
    @NotNull
    final String name;
    @VisibleForTesting
    @NotNull
    final EnumSet<AttributeFormat> type;

    MappedField(@NotNull String fieldName, @Nullable AttributeFormat type) {
      this.name = fieldName;
      this.type = type != null ? EnumSet.of(type) : EnumSet.noneOf(AttributeFormat.class);
    }
  }
}
