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

  @Nullable
  public RestrictedQualifier getRestrictedQualifierAt(int index) {
    return myRestrictedQualifiers[index];
  }

  /**
   * @param compatible FolderConfiguration that needs to be matching
   * @param incompatibles Collection of FolderConfigurations need to avoid matching
   * @return FolderConfiguration that matches with compatible, but doesn't match with any from incompatibles.
   * Backward implementation to the <a href="http://developer.android.com/guide/topics/resources/providing-resources.html">algorithm</a>.
   */
  @Nullable("if there is no configuration that matches the constraints")
  public static RestrictedConfiguration restrictConfiguration(@NotNull FolderConfiguration compatible,
                                                              @NotNull Collection<FolderConfiguration> incompatibles) {
    RestrictedConfiguration restricted = new RestrictedConfiguration();
    if (incompatibles.isEmpty()) {
      return restricted;
    }
    ArrayList<FolderConfiguration> matchingIncompatibles = Lists.newArrayList(incompatibles);

    for (int qualifierIndex = 0; qualifierIndex < FolderConfiguration.getQualifierCount(); ++qualifierIndex) {
      ResourceQualifier compatibleQualifier = compatible.getQualifier(qualifierIndex);
      RestrictedQualifier restrictedQualifier = restricted.getRestrictedQualifierAt(qualifierIndex);

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
}
