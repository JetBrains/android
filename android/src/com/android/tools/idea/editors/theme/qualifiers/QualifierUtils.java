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

import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.KeyboardStateQualifier;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.resources.configuration.NavigationMethodQualifier;
import com.android.ide.common.resources.configuration.NavigationStateQualifier;
import com.android.ide.common.resources.configuration.NightModeQualifier;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier;
import com.android.ide.common.resources.configuration.ScreenRatioQualifier;
import com.android.ide.common.resources.configuration.ScreenSizeQualifier;
import com.android.ide.common.resources.configuration.TextInputMethodQualifier;
import com.android.ide.common.resources.configuration.TouchScreenQualifier;
import com.android.ide.common.resources.configuration.UiModeQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.Density;
import com.android.resources.Keyboard;
import com.android.resources.KeyboardState;
import com.android.resources.LayoutDirection;
import com.android.resources.Navigation;
import com.android.resources.NavigationState;
import com.android.resources.NightMode;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenSize;
import com.android.resources.TouchScreen;
import com.android.resources.UiMode;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

public class QualifierUtils {
  private static final Logger LOG = Logger.getInstance(QualifierUtils.class);

  /**
   * This map contains all the enum qualifiers that can be automatically processed to get an incompatible configuration. For these qualifiers
   * it's just enough to take one of the values that it's not the original value from the configuration we are using.
   * DensityQualifier is excluded from this class because it is evaluated in a different way and simply getting a different density is
   * not enough to make a configuration "incompatible".
   */
  static final ImmutableMap<Class<? extends ResourceQualifier>, Class<? extends Enum>> ENUM_QUALIFIERS_MAPPING =
    ImmutableMap.<Class<? extends ResourceQualifier>, Class<? extends Enum>>builder()
    .put(NightModeQualifier.class, NightMode.class)
    .put(KeyboardStateQualifier.class, KeyboardState.class)
    .put(ScreenSizeQualifier.class, ScreenSize.class)
    .put(NavigationStateQualifier.class, NavigationState.class)
    .put(ScreenOrientationQualifier.class, ScreenOrientation.class)
    .put(LayoutDirectionQualifier.class, LayoutDirection.class)
    .put(TouchScreenQualifier.class, TouchScreen.class)
    .put(NavigationMethodQualifier.class, Navigation.class)
    .put(UiModeQualifier.class, UiMode.class)
    .put(TextInputMethodQualifier.class, Keyboard.class)
    .put(ScreenRatioQualifier.class, ScreenRatio.class)
    .build();

  /**
   * Returns one enum value that it's not present in the passed set or null if all the values are contained in the set.
   */
  @Nullable
  static <T extends Enum<T>> T findIncompatibleEnumValue(EnumSet<T> currentValues) {
    EnumSet<T> complement = EnumSet.complementOf(currentValues);

    return Iterables.getFirst(complement, null);
  }

  static <E extends Enum<E>, Q extends ResourceQualifier> ResourceQualifier getIncompatibleEnum(Class<E> enumType, Class<Q> resourceQualifierType, Collection<ResourceQualifier> resourceQualifiers) {
    List<E> currentValues = Lists.newArrayList();

    // TODO: Remove the use of reflection here by improving the Qualifiers hierarchy
    Method getValueMethod;
    try {
      getValueMethod = resourceQualifierType.getMethod("getValue");
    }
    catch (NoSuchMethodException e) {
      LOG.error("getValue method not found on the qualifier type (not an enum qualifier)", e);
      return null;
    }

    for (ResourceQualifier qualifier : resourceQualifiers) {
      // Type check to make sure the passed class type matches the passed resources
      if (qualifier.getClass() != resourceQualifierType) {
        LOG.error(String.format("The passed list of qualifiers of type '$1%s' doesn't match the passed type '$2%s",
                                qualifier.getClass().getSimpleName(),
                                resourceQualifierType.getSimpleName()));
        return null;
      }

      //noinspection unchecked
      Q enumQualifier = (Q)qualifier;

      try {
        //noinspection unchecked
        currentValues.add((E)getValueMethod.invoke(enumQualifier));
      }
      catch (IllegalAccessException e) {
        LOG.error("getValue method is not public", e);
      }
      catch (InvocationTargetException e) {
        LOG.error("InvocationTargetException", e);
      }
    }

    E incompatibleEnumValue = findIncompatibleEnumValue(EnumSet.copyOf(currentValues));

    if (incompatibleEnumValue == null) {
      // None found
      return null;
    }

    try {
      Constructor<Q> constructor = resourceQualifierType.getConstructor(enumType);
      return constructor.newInstance(incompatibleEnumValue);
    }
    catch (NoSuchMethodException e) {
      LOG.error("The qualifier type does not have a constructor with the passed enum type", e);
    }
    catch (InvocationTargetException e) {
      LOG.error("Error calling qualifier constructor", e);
    }
    catch (InstantiationException e) {
      LOG.error("Error calling qualifier constructor", e);
    }
    catch (IllegalAccessException e) {
      LOG.error("Error calling qualifier constructor", e);
    }

    return null;
  }

  /**
   * Returns a version qualifier that is lower than any of the version qualifiers found in the passed list.
   * @param maxInstalledApiLevel the maximum available API level. This method can not return a version that is higher than this.
   * @param qualifiers the list of version qualifiers.
   */
  static ResourceQualifier getIncompatibleVersionQualifier(int maxInstalledApiLevel, @NotNull Collection<ResourceQualifier> qualifiers) {
    // We start at maxInstalledApiLevel + 1 because this method always return one version lower than the API level found.
    int minApiLevel = maxInstalledApiLevel + 1;
    for (ResourceQualifier qualifier : qualifiers) {
      VersionQualifier versionQualifier = (VersionQualifier)qualifier;

      minApiLevel = Math.min(versionQualifier.getVersion(), minApiLevel);
    }

    return new VersionQualifier(minApiLevel - 1);
  }

  static ResourceQualifier getIncompatibleDensityQualifier(@NotNull Collection<ResourceQualifier> qualifiers) {
    Density minDensity = null;
    for (ResourceQualifier qualifier : qualifiers) {
      DensityQualifier densityQualifier = (DensityQualifier)qualifier;
      Density value = densityQualifier.getValue();

      if (minDensity == null || value.getDpiValue() < minDensity.getDpiValue()) {
        minDensity = value;
      }
    }

    // There is nothing lower than NODPI
    if (minDensity == null || minDensity == Density.NODPI) {
      return null;
    }

    // Now select the next lower density to the minimum we've found
    Density lowerDensity = Density.NODPI;
    for (Density value : Density.values()) {
      if (value.getDpiValue() > lowerDensity.getDpiValue() && value.getDpiValue() < minDensity.getDpiValue()) {
        lowerDensity = value;
      }
    }

    return new DensityQualifier(lowerDensity);
  }

  /**
   * Returns a ResourceQualifier that doesn't match any of the passed qualifiers. If there are no incompatible qualifiers, then this method
   * returns null.
   */
  public static ResourceQualifier getIncompatibleQualifier(@NotNull ConfigurationManager configurationManager,
                                                           @NotNull Class<? extends ResourceQualifier> type,
                                                           @NotNull Collection<ResourceQualifier> qualifiers) {
    if (type == VersionQualifier.class) {
      if (configurationManager.getHighestApiTarget() == null) {
        return null;
      }

      int maxApi = configurationManager.getHighestApiTarget().getVersion().getApiLevel();
      return getIncompatibleVersionQualifier(maxApi, qualifiers);
    } else if (type == LocaleQualifier.class) {
      // The FAKE_VALUE doesn't match any real locales
      return new LocaleQualifier(LocaleQualifier.FAKE_VALUE);
    } else if (type == DensityQualifier.class) {
      return getIncompatibleDensityQualifier(qualifiers);
    }

    if (ENUM_QUALIFIERS_MAPPING.containsKey(type)) {
      Class<? extends Enum> enumType = ENUM_QUALIFIERS_MAPPING.get(type);
      return getIncompatibleEnum(enumType, type, qualifiers);
    }

    return null;
  }

  /**
   * Finds a configuration that matches with compatible and doesn't match with any of the incompatible ones.
   */
  public static FolderConfiguration restrictConfiguration(@NotNull ConfigurationManager configurationManager,
                                                          @NotNull FolderConfiguration compatible,
                                                          @NotNull Collection<FolderConfiguration> incompatible) {
    FolderConfiguration finalConfiguration = FolderConfiguration.copyOf(compatible);

    if (incompatible.isEmpty()) {
      return finalConfiguration;
    }

    // Sort qualifiers based on their type
    Multimap<Class<? extends ResourceQualifier>, ResourceQualifier> qualifiers = HashMultimap.create();

    for (FolderConfiguration incompatibleConfiguration : incompatible) {
      if (incompatibleConfiguration == null) {
        continue;
      }
      for (ResourceQualifier qualifier : incompatibleConfiguration.getQualifiers()) {
        if (qualifier != null) {
          qualifiers.put(qualifier.getClass(), qualifier);
        }
      }
    }

    HashSet<String> existingQualifiers = Sets.newHashSetWithExpectedSize(finalConfiguration.getQualifiers().length);
    for (ResourceQualifier existingQualifier : finalConfiguration.getQualifiers()) {
      existingQualifiers.add(existingQualifier.getName());
    }

    for (Class<? extends ResourceQualifier> qualifier : qualifiers.keySet()) {
      ResourceQualifier incompatibleQualifier = getIncompatibleQualifier(configurationManager, qualifier, qualifiers.get(qualifier));

      if (incompatibleQualifier == null) {
        return null;
      }

      if (!existingQualifiers.contains(incompatibleQualifier.getName())) {
        finalConfiguration.addQualifier(incompatibleQualifier);
      }
    }

    return finalConfiguration;
  }
}
