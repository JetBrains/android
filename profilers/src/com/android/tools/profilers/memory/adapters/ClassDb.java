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

import com.intellij.util.ArrayUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import org.jetbrains.annotations.NotNull;

/**
 * A class that shares class name {@link String}s, and provides facilities for splitting the class name to its constituent parts.
 */
public final class ClassDb {
  public static final int INVALID_CLASS_ID = -1;
  public static final String JAVA_LANG_STRING = "java.lang.String";
  public static final String JAVA_LANG_CLASS = "java.lang.Class";

  // class id to class mapping.
  private final Map<Long, ClassEntry> myClassEntries = new HashMap<>();
  private boolean myResolvedSubclasses = false;

  public void clear() {
    myClassEntries.clear();
    myResolvedSubclasses = false;
  }

  @NotNull
  public ClassEntry registerClass(long classId, @NotNull String className) {
    return registerClass(classId, INVALID_CLASS_ID, className);
  }

  @NotNull
  public ClassEntry registerClass(long classId, long superClassId, @NotNull String className) {
    ClassEntry entry = myClassEntries.get(classId);
    if (entry == null
        // FIXME(b/159029403) Below checks aren't necessary in production:
        //   `superClassId` and `className` are supposed to be the same if the `classId` exists.
        //   But right now, asserting that would break an existing unrealistic test where
        //   `java.lang.Class` is absent (`HeapDumpCaptureObjectTest.testHeapDumpObjectsGeneration`)
        || superClassId != entry.mySuperClassId || !className.equals(entry.myClassName)) {
      entry = new ClassEntry(classId, superClassId, className);
      myClassEntries.put(classId, entry);
    }
    return entry;
  }

  @NotNull
  public ClassEntry getEntry(long classId) {
    assert myClassEntries.containsKey(classId);
    return myClassEntries.get(classId);
  }

  @NotNull
  public Set<ClassEntry> getEntriesByName(@NotNull String className) {
    Set<ClassEntry> entries = new HashSet<>();

    for (ClassEntry entry : myClassEntries.values()) {
      if (entry.getClassName().equals(className)) {
        entries.add(entry);
      }
    }

    return entries;
  }

  /**
   * @return All subclasses that has class with id equals |classId| on their inheritance paths. (inclusive)
   */
  public Set<ClassEntry> getDescendantClasses(long classId) {
    resolveSubClasses();

    ClassEntry klass = getEntry(classId);
    Set<ClassEntry> descendants = new HashSet<>();
    Stack<ClassEntry> searchStack = new Stack<>();
    searchStack.push(klass);
    while (!searchStack.isEmpty()) {
      ClassEntry searchEntry = searchStack.pop();
      descendants.add(searchEntry);
      for (long subClassId : searchEntry.getSubClassIds()) {
        searchStack.push(getEntry(subClassId));
      }
    }
    return descendants;
  }

  /**
   * Registered classes contain a one-way path to its super class. This helper method fills in the reverse relationship (parent class to
   * children classes), and it should be called only after all the classes have been registered.
   * @return false if the subclass information is already resolved previously, true otherwise.
   */
  private boolean resolveSubClasses() {
    if (myResolvedSubclasses) {
      return false;
    }

    for (ClassEntry entry : myClassEntries.values()) {
      long superClassId = entry.getSuperClassId();
      if (superClassId != -1) {
        getEntry(superClassId).getSubClassIds().add(entry.getClassId());
      }
    }
    myResolvedSubclasses = true;
    return true;
  }

  public static class ClassEntry {
    @NotNull private final Set<Long> mySubClassIds = new HashSet<>();

    @NotNull private final long myClassId;
    @NotNull private final long mySuperClassId;
    @NotNull private final String myClassName;
    @NotNull private final String[] mySplitPackageName;

    /**=
     * @param classId       unique identifier for the class.
     * @param superClassId  unique identifier for the direct super class.
     * @param className     fully qualified name of the class.
     */
    public ClassEntry(long classId, long superClassId, @NotNull String className) {
      myClassId = classId;
      mySuperClassId = superClassId;
      myClassName = className;
      String packageName = getPackageName();
      mySplitPackageName = packageName.isEmpty() ? ArrayUtil.EMPTY_STRING_ARRAY : packageName.split("\\.");
    }

    public long getClassId() {
      return myClassId;
    }

    /**
     * @return Id of the direct super class
     */
    public long getSuperClassId() {
      return mySuperClassId;
    }

    /**
     * @return Ids of the immediate children classes. Note that the set is only valid after {@link ClassDb#resolveSubClasses()} is called.
     */
    public Set<Long> getSubClassIds() {
      return mySubClassIds;
    }

    @NotNull
    public String getClassName() {
      return myClassName;
    }

    @NotNull
    public String getPackageName() {
      int lastIndexOfDot = getLastIndexOfDot();
      return lastIndexOfDot > 0 ? myClassName.substring(0, lastIndexOfDot) : "";
    }

    @NotNull
    public String[] getSplitPackageName() {
      return mySplitPackageName;
    }

    @NotNull
    public String getSimpleClassName() {
      return myClassName.substring(getLastIndexOfDot() + 1);
    }

    @Override
    public int hashCode() {
      return myClassName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ClassEntry && myClassName.equals(((ClassEntry)obj).myClassName);
    }

    private int getLastIndexOfDot() {
      return myClassName.lastIndexOf('.');
    }
  }
}
