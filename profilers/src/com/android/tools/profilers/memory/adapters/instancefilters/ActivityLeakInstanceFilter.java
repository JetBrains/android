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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * A filter to locate possible leaked activity instances. We do this by locating all android.app.Activity (and subclasses) instances, and
 * check whether their mDestroyed/mFinished fields have been set to true but still has a valid depth (not waiting to be GC'd).
 */
public class ActivityLeakInstanceFilter implements CaptureObjectInstanceFilter {

  static final String ACTIVTY_CLASS_NAME = "android.app.Activity";
  static final String FINISHED_FIELD_NAME = "mFinished";
  static final String DESTROYED_FIELD_NAME = "mDestroyed";

  @NotNull
  @Override
  public String getDisplayName() {
    return "Leaked Activities Only";
  }

  @Override
  public Set<InstanceObject> filter(@NotNull Set<InstanceObject> instances, @NotNull ClassDb classDatabase) {
    Set<ClassDb.ClassEntry> activityClasses = classDatabase.getEntriesByName(ACTIVTY_CLASS_NAME);

    Set<ClassDb.ClassEntry> descendentClasses = new HashSet<>(activityClasses);
    for (ClassDb.ClassEntry activityClass : activityClasses) {
      descendentClasses.addAll(classDatabase.getDescendantClasses(activityClass.getClassId()));
    }

    Set<InstanceObject> matchedInstances = new HashSet<>();
    instances.forEach(instance -> {
      if (!descendentClasses.contains(instance.getClassEntry())) {
        return;
      }

      int depth = instance.getDepth();
      if (depth == 0 || depth == Integer.MAX_VALUE) {
        return;
      }

      List<FieldObject> fields = instance.getFields();
      for (FieldObject field : fields) {
        String fieldName = field.getFieldName();
        if (fieldName.equals(FINISHED_FIELD_NAME) || fieldName.equals(DESTROYED_FIELD_NAME)) {
          if ((Boolean)field.getValue() && instance.getDepth() != Integer.MAX_VALUE) {
            matchedInstances.add(instance);
            return;
          }
        }
      }
    });

    return matchedInstances;
  }
}
