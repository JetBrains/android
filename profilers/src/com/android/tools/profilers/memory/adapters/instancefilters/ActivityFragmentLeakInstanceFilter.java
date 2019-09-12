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
package com.android.tools.profilers.memory.adapters.instancefilters;

import com.android.tools.profilers.memory.adapters.ClassDb;
import com.android.tools.profilers.memory.adapters.FieldObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A filter to locate possible leaked activity/fragment instances.
 */
public class ActivityFragmentLeakInstanceFilter implements CaptureObjectInstanceFilter {

  @VisibleForTesting static final String ACTIVTY_CLASS_NAME = "android.app.Activity";
  // native android Fragment, deprecated as of API 28.
  @VisibleForTesting static final String NATIVE_FRAGMENT_CLASS_NAME = "android.app.Fragment";
  // pre-androidx, support library version of the Fragment implementation.
  @VisibleForTesting static final String SUPPORT_FRAGMENT_CLASS_NAME = "android.support.v4.app.Fragment";
  // androidx version of the Fragment implementation
  @VisibleForTesting static final String ANDROIDX_FRAGMENT_CLASS_NAME = "androidx.fragment.app.Fragment";
  @VisibleForTesting static final String FINISHED_FIELD_NAME = "mFinished";
  @VisibleForTesting static final String DESTROYED_FIELD_NAME = "mDestroyed";
  @VisibleForTesting static final String FRAGFMENT_MANAGER_FIELD_NAME = "mFragmentManager";

  private static final Set<String> FRAGMENT_CLASS_NAMES = ImmutableSet.of(
    NATIVE_FRAGMENT_CLASS_NAME, SUPPORT_FRAGMENT_CLASS_NAME, ANDROIDX_FRAGMENT_CLASS_NAME
  );

  @NotNull
  @Override
  public String getDisplayName() {
    return "Activity/Fragment Leaks";
  }

  @NotNull
  @Override
  public String getSummaryDescription() {
    return "Show Activities and Fragments that are potentially causing memory leaks.";
  }

  @Nullable
  @Override
  public String getDetailedDescription() {
    return "Note that Activity/Fragment leaks may include false positives. Please see the documentation for details.";
  }

  @Nullable
  @Override
  public String getDocumentationLink() {
    return "https://developer.android.com/docs";
  }

  @Override
  public Set<InstanceObject> filter(@NotNull Set<InstanceObject> instances, @NotNull ClassDb classDatabase) {
    Set<ClassDb.ClassEntry> allActivitySubclasses = classDatabase.getEntriesByName(ACTIVTY_CLASS_NAME).stream()
      .flatMap(classEntry -> classDatabase.getDescendantClasses(classEntry.getClassId()).stream())
      .collect(Collectors.toSet());

    Set<ClassDb.ClassEntry> allFragmentSubclasses = FRAGMENT_CLASS_NAMES.stream()
      .flatMap(className -> classDatabase.getEntriesByName(className).stream())
      .flatMap(classEntry -> classDatabase.getDescendantClasses(classEntry.getClassId()).stream())
      .collect(Collectors.toSet());

    return instances.stream().filter(instance -> {
      if (allActivitySubclasses.contains(instance.getClassEntry()) && isPotentialActivityLeak(instance)) {
        return true;
      }
      else if (allFragmentSubclasses.contains(instance.getClassEntry()) && isPotentialFragmentLeak(instance)) {
        return true;
      }
      return false;
    }).collect(Collectors.toSet());
  }

  /**
   * An Activity instance is determined to be leaked if its mDestroyed/mFinished field has been set to true, and the instance still has a
   * valid depth (not waiting to be GC'd).
   */
  private boolean isPotentialActivityLeak(@NotNull InstanceObject instance) {
    int depth = instance.getDepth();
    if (depth == 0 || depth == Integer.MAX_VALUE) {
      return false;
    }

    List<FieldObject> fields = instance.getFields();
    for (FieldObject field : fields) {
      String fieldName = field.getFieldName();
      if (fieldName.equals(FINISHED_FIELD_NAME) || fieldName.equals(DESTROYED_FIELD_NAME)) {
        if ((Boolean)field.getValue()) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * A Fragment instance is determined to be potentially leaked if its mFragmentManager field is null. This indicates that the instance
   * is in its initial state. Note that this can mean that the instance has been destroyed, or just starting to be initialized but before
   * being attached to an activity. The latter gives us false positives, but it should not uncommon as long as users don't create fragments
   * way ahead of the time of adding them to a FragmentManager.
   */
  private boolean isPotentialFragmentLeak(@NotNull InstanceObject instance) {
    int depth = instance.getDepth();
    if (depth == 0 || depth == Integer.MAX_VALUE) {
      return false;
    }

    List<FieldObject> fields = instance.getFields();
    for (FieldObject field : fields) {
      String fieldName = field.getFieldName();
      if (fieldName.equals(FRAGFMENT_MANAGER_FIELD_NAME)) {
        if (field.getValue() == null) {
          return true;
        }
      }
    }

    return false;
  }
}
