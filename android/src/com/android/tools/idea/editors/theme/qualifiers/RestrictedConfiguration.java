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
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

/**
 * This class represents a compact form of a FolderConfiguration, that can be restricted by some values on a ResourceQualifier.
 */
public class RestrictedConfiguration {
  private final RestrictedQualifier[] myRestrictedQualifiers = new RestrictedQualifier[FolderConfiguration.getQualifierCount()];
  private final Class<? extends ResourceQualifier>[] myQualifiersClasses = new Class[FolderConfiguration.getQualifierCount()];

  public RestrictedConfiguration() {
    // we are creating Default FolderConfiguration, just to extract information for Reflection
    FolderConfiguration configuration = new FolderConfiguration();
    configuration.createDefault();

    for (int i = 0; i < FolderConfiguration.getQualifierCount(); ++i) {
      ResourceQualifier qualifier = configuration.getQualifier(i);
      assert qualifier != null;

      myQualifiersClasses[i] = qualifier.getClass();
      if (myQualifiersClasses[i].equals(LocaleQualifier.class)) {
        myRestrictedQualifiers[i] = new RestrictedLocale();
      }
      else if (myQualifiersClasses[i].equals(VersionQualifier.class)) {
        myRestrictedQualifiers[i] = new RestrictedValue();
      }
      else {

        Class getValueType = QualifierUtils.getValueReturnType(myQualifiersClasses[i]);

        if (getValueType == null) {
          // TODO: support other ResourceQualifier's as well
          continue;
        }

        if (getValueType.equals(int.class)) {
          myRestrictedQualifiers[i] = new RestrictedValue();
        }
        else {
          assert Arrays.asList(getValueType.getInterfaces()).contains(ResourceEnum.class);
          myRestrictedQualifiers[i] = new RestrictedEnum(getValueType);
        }
      }
    }
  }

  @Nullable("if empty intersection, i.e some of the qualifiers can't have any value due to the restrictions in both")
  public RestrictedConfiguration intersect(@NotNull RestrictedConfiguration otherRestricted) {
    RestrictedConfiguration resultRestricted = new RestrictedConfiguration();

    for (int i = 0; i < FolderConfiguration.getQualifierCount(); ++i) {
      RestrictedQualifier thisQualifier = myRestrictedQualifiers[i];
      RestrictedQualifier otherQualifier = otherRestricted.myRestrictedQualifiers[i];

      // TODO: we have't supported all ResourceQualifiers yet
      if (thisQualifier == null) {
        assert otherQualifier == null;
        continue;
      }
      RestrictedQualifier intersection = thisQualifier.intersect(otherQualifier);
      if (intersection == null) {
        return null;
      }
      resultRestricted.myRestrictedQualifiers[i] = intersection;
    }
    return resultRestricted;
  }

  /**
   * @return any {@link FolderConfiguration} that matches to constraints
   */
  @NotNull
  public FolderConfiguration getAny() {
    FolderConfiguration configuration = new FolderConfiguration();

    for (int i = 0; i < FolderConfiguration.getQualifierCount(); ++i) {
      if (myRestrictedQualifiers[i] == null) {
        continue;
      }
      Object value = myRestrictedQualifiers[i].getAny();
      if (value != null) {
        configuration.addQualifier(QualifierUtils.createNewResourceQualifier(myQualifiersClasses[i], value));
      }
    }
    return configuration;
  }

  /**
   * @param compatible FolderConfiguration that needs to be matching
   * @param incompatibles Collection of FolderConfigurations need to avoid matching
   * @return FolderConfiguration that matches with compatible, but doesn't match with any from incompatibles.
   * Backward implementation to the <a href="http://developer.android.com/guide/topics/resources/providing-resources.html">algorithm</a>.
   */
  @Nullable("if there is no configuration that matches the constraints")
  public static RestrictedConfiguration restrict(@NotNull FolderConfiguration compatible,
                                                 @NotNull Collection<FolderConfiguration> incompatibles) {
    RestrictedConfiguration restricted = new RestrictedConfiguration();
    if (incompatibles.isEmpty()) {
      return restricted;
    }
    ArrayList<FolderConfiguration> matchingIncompatibles = Lists.newArrayList(incompatibles);

    for (int qualifierIndex = 0; qualifierIndex < FolderConfiguration.getQualifierCount(); ++qualifierIndex) {
      ResourceQualifier compatibleQualifier = compatible.getQualifier(qualifierIndex);
      RestrictedQualifier restrictedQualifier = restricted.myRestrictedQualifiers[qualifierIndex];

      ArrayList<ResourceQualifier> incompatibleQualifiers = Lists.newArrayList();
      for (FolderConfiguration matching : matchingIncompatibles) {
        ResourceQualifier qualifier = matching.getQualifier(qualifierIndex);
        if (qualifier != null) {
          incompatibleQualifiers.add(qualifier);
        }
      }

      // If no one has this qualifier, we skip it
      if (compatibleQualifier == null && incompatibleQualifiers.isEmpty()) {
        continue;
      }

      assert restrictedQualifier != null;
      restrictedQualifier.setRestrictions(compatibleQualifier, incompatibleQualifiers);

      if (restrictedQualifier.isEmpty()) {
        return null;
      }

      Iterator<FolderConfiguration> matchingIterator = matchingIncompatibles.iterator();
      while (matchingIterator.hasNext()) {
        ResourceQualifier incompatibleQualifier = matchingIterator.next().getQualifier(qualifierIndex);

        if (compatibleQualifier != null && !restrictedQualifier.isMatchFor(incompatibleQualifier)) {
          // If 'compatible' has such qualifier, we eliminate any qualifier that doesn't match to it
          matchingIterator.remove();
        }
        else if (compatibleQualifier == null && incompatibleQualifier != null) {
          // if 'compatible' hasn't such qualifier, it means that we need to avoid qualifiers of this type,
          // this done by setting boundaries to incompatible in RestrictedQualifier
          matchingIterator.remove();
        }
      }

      if (matchingIncompatibles.isEmpty()) {
        return restricted;
      }
    }
    return null;
  }

  /**
   * Returns a restricted version of the passed configuration. The value returned will be incompatible with any other configuration in the
   * item. This configuration can be used when we want to make sure that the configuration selected will be displayed.
   * Note: allItems should contain compatible
   */
  @Nullable("if there is no configuration that matches the constraints")
  public static <T> FolderConfiguration restrict(@NotNull ConfiguredElement<T> compatible,
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
    RestrictedConfiguration restricted = restrict(compatible.getConfiguration(), incompatibleConfigurations);
    return (restricted != null) ? restricted.getAny() : null;
  }

}
