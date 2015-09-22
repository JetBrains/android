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
import com.android.resources.*;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.google.common.collect.*;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

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
    .put(ScreenRoundQualifier.class, ScreenRound.class)
    .build();

  /**
   * @return one enum value that it's not present in the passed set
   */
  @Nullable("if all the values are contained in the set")
  private static <T extends Enum<T>> T findIncompatibleEnumValue(EnumSet<T> currentValues) {
    EnumSet<T> complement = EnumSet.complementOf(currentValues);

    return Iterables.getFirst(complement, null);
  }

  private static <E extends Enum<E>, Q extends ResourceQualifier> ResourceQualifier getIncompatibleEnum(Class<E> enumType, Class<Q> resourceQualifierType, Collection<ResourceQualifier> resourceQualifiers) {
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
   * @param qualifiers the list of version qualifiers.
   */
  private static ResourceQualifier getIncompatibleVersionQualifier(@NotNull Collection<ResourceQualifier> qualifiers) {
    assert !qualifiers.isEmpty();
    int minApiLevel = Integer.MAX_VALUE;
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
   * Note: qualifiers shouldn't be empty and all elements from qualifiers should have same qualifier type, f.e all should be LocaleQualifier
   */
  private static ResourceQualifier getIncompatibleQualifier(@NotNull Collection<ResourceQualifier> qualifiers) {
    assert !qualifiers.isEmpty();
    Class type = qualifiers.iterator().next().getClass();
    // Check all qualifiers are the same type inside the collection
    for (ResourceQualifier qualifier : qualifiers) {
      assert type == qualifier.getClass();
    }

    if (type == VersionQualifier.class) {
      return getIncompatibleVersionQualifier(qualifiers);
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
   * Returns a restricted version of the passed configuration. The value returned will be incompatible with any other configuration in the
   * item. This configuration can be used when we want to make sure that the configuration selected will be displayed.
   * Note: allItems should contain compatible
   */
  @Nullable("if there is no configuration that matches the constraints")
  public static <T> FolderConfiguration restrictConfiguration(@NotNull ConfiguredElement<T> compatible,
                                                              Collection<ConfiguredElement<T>> allItems) {
    ArrayList<FolderConfiguration> incompatibleConfigurations = Lists.newArrayListWithCapacity(allItems.size());
    boolean found = false;
    for (ConfiguredElement configuredItem : allItems) {
      FolderConfiguration configuration = configuredItem.getConfiguration();
      if (configuredItem.equals(compatible)) {
        found = true;
        continue;
      }
      incompatibleConfigurations.add(configuration);
    }
    assert found;
    return restrictConfiguration(compatible.getConfiguration(), incompatibleConfigurations);
  }

  /**
   * @param compatible FolderConfiguration that needs to be matching
   * @param incompatibles Collection of FolderConfigurations need to avoid matching
   * @return FolderConfiguration that matches with compatible, but doesn't match with any from incompatibles.
   * Backward implementation to the <a href="http://developer.android.com/guide/topics/resources/providing-resources.html">algorithm</a>.
   */
  @Nullable("if there is no configuration that matches the constraints")
  public static FolderConfiguration restrictConfiguration(@NotNull FolderConfiguration compatible,
                                                          @NotNull Collection<FolderConfiguration> incompatibles) {
    ArrayList<FolderConfiguration> matchingIncompatibles = Lists.newArrayList();
    // Find all 'incompatibles' that are matches for all of the qualifiers of the 'compatible'.
    for (FolderConfiguration incompatible : incompatibles) {
      if (incompatible.isMatchFor(compatible)) {
        matchingIncompatibles.add(incompatible);
      }
    }

    // If none of the 'incompatibles' is a match for 'compatible', return the 'compatible'.
    if (matchingIncompatibles.isEmpty()) {
      return compatible;
    }

    FolderConfiguration restrictedConfiguration = FolderConfiguration.copyOf(compatible);
    // Starting from highest priority qualifier according to the table from above link,
    // tries to eliminate items of matchingIncompatibles.
    for (int qualifierIndex = 0; qualifierIndex < FolderConfiguration.getQualifierCount(); ++qualifierIndex) {
      ResourceQualifier compatibleQualifier = compatible.getQualifier(qualifierIndex);

      // No such qualifier: try to find incompatible qualifier, that will eliminate matchingIncompatibles having this type of qualifier
      if (compatibleQualifier == null) {
        ArrayList<ResourceQualifier> incompatibleQualifiers = Lists.newArrayList();
        for (FolderConfiguration incompatible : matchingIncompatibles) {
          ResourceQualifier incompatibleQualifier = incompatible.getQualifier(qualifierIndex);
          if (incompatibleQualifier != null) {
            incompatibleQualifiers.add(incompatibleQualifier);
          }
        }

        // Current qualifier does not feature in any of compatible or incompatible qualifiers, so skip it.
        if (incompatibleQualifiers.isEmpty()) {
          continue;
        }

        // This qualifier is in the 'incompatible', but not in 'compatible',
        // so we need to get one that's incompatible with our 'incompatibles', and add to our result.
        ResourceQualifier qualifier = getIncompatibleQualifier(incompatibleQualifiers);

        // Couldn't find any incompatible, so we can't avoid matching to incompatibles
        if (qualifier == null) {
          return null;
        }
        restrictedConfiguration.addQualifier(qualifier);
      }

      // Now we will find and remove all FolderConfiguration that now do not match.
      Iterator<FolderConfiguration> matchingIterator = matchingIncompatibles.iterator();
      while (matchingIterator.hasNext()) {
        ResourceQualifier incompatibleQualifier = matchingIterator.next().getQualifier(qualifierIndex);

        if (compatibleQualifier != null && !compatibleQualifier.equals(incompatibleQualifier)) {
          // If 'compatible' has such qualifier, we eliminate any qualifier that isn't equal to it
          matchingIterator.remove();
        }
        else if (compatibleQualifier == null && incompatibleQualifier != null) {
          // if 'compatible' hasn't such qualifier, it means that we have found incompatible qualifier.
          // So, if this has a such qualifier then it contradicts to incompatible, so remove it.
          assert !incompatibleQualifier.isMatchFor(restrictedConfiguration.getQualifier(qualifierIndex));
          matchingIterator.remove();
        }
      }

      // If there are no more 'incompatibles' that can match our 'restrictedConfiguration' then we are done, and return the result.
      if (matchingIncompatibles.isEmpty()) {
        return restrictedConfiguration;
      }
    }

    return null;
  }

}
