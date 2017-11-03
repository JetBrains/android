/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters;

import com.android.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * A class that shares class name {@link String}s, and provides facilities for splitting the class name to its constituent parts.
 */
public final class ClassDb {
  public static final String JAVA_LANG_STRING = "java.lang.String";
  public static final String JAVA_LANG_CLASS = "java.lang.Class";

  private final Map<Long, Map<String, ClassEntry>> myClassEntries = new HashMap<>();
  private final Map<Long, ClassEntry> myTagMap = new HashMap<>();

  public void clear() {
    myClassEntries.clear();
    myTagMap.clear();
  }

  @NotNull
  public ClassEntry registerClass(long classLoaderId, @NotNull String className) {
    return myClassEntries.computeIfAbsent(classLoaderId, id -> new HashMap<>()).computeIfAbsent(className, ClassEntry::new);
  }

  @NotNull
  public ClassEntry registerClass(long classLoaderId, @NotNull String className, long tag) {
    ClassEntry entry = registerClass(classLoaderId, className);
    myTagMap.put(tag, entry);
    return entry;
  }

  public boolean containsClassEntry(long classLoaderId, @NotNull String className) {
    return myClassEntries.containsKey(classLoaderId) && myClassEntries.get(classLoaderId).containsKey(className);
  }

  @NotNull
  public ClassEntry getEntry(long tag) {
    assert myTagMap.containsKey(tag);
    return myTagMap.get(tag);
  }

  public static class ClassEntry {
    @NotNull private final String myClassName;
    @NotNull private final String myPackageName;
    @NotNull private final String mySimpleClassName;
    @NotNull private final String[] mySplitPackageName;

    @VisibleForTesting
    public ClassEntry(@NotNull String className) {
      myClassName = className;
      int lastIndexOfDot = myClassName.lastIndexOf('.');
      myPackageName = lastIndexOfDot > 0 ? myClassName.substring(0, lastIndexOfDot) : "";
      mySimpleClassName = myClassName.substring(lastIndexOfDot + 1);
      //noinspection SSBasedInspection
      mySplitPackageName = myPackageName.isEmpty() ? new String[0] : myPackageName.split("\\.");
    }

    @NotNull
    public String getClassName() {
      return myClassName;
    }

    @NotNull
    public String getPackageName() {
      return myPackageName;
    }

    @NotNull
    public String[] getSplitPackageName() {
      return mySplitPackageName;
    }

    @NotNull
    public String getSimpleClassName() {
      return mySimpleClassName;
    }

    @Override
    public int hashCode() {
      return myClassName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ClassEntry && myClassName.equals(((ClassEntry)obj).myClassName);
    }
  }
}
