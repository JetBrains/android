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
package com.android.tools.idea.lang.androidSql.room;

import com.google.common.collect.ImmutableList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

// TODO: Remove this copy when the studio-platform JAR contains the AndroidxName API with more than two names.
//  Then import com.android.support.AndroidxName in RoomAnnotations.kt and RoomSchemaManager.kt again.
/**
 * Type representing an Android Support Library name. This type contains the old name and the new
 * name(s) of a class. The type also returns a "default name" that will be changed from the old to
 * new eventually.
 */
public class RoomAndroidxName {

  private final ImmutableList<String> myNames;

  private RoomAndroidxName(@NotNull List<String> names) {
    myNames = ImmutableList.copyOf(names);
  }

  public RoomAndroidxName(
    @NotNull String oldName, @NotNull String newName, @NotNull String... otherNames) {
    this(
      ImmutableList.<String>builder()
        .add(oldName)
        .add(newName)
        .addAll(Arrays.asList(otherNames))
        .build());
  }

  /** Creates a new instance for the given package and class name */
  @NotNull
  public static RoomAndroidxName of(@NotNull RoomAndroidxName pkg, @NotNull String simpleClassName) {
    assert !simpleClassName.contains(".");

    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (String name : pkg.myNames) {
      builder.add(name + (name.endsWith(".") ? "" : ".") + simpleClassName);
    }
    return new RoomAndroidxName(builder.build());
  }

  /** Returns all versions of the name */
  @NotNull
  public List<String> names() {
    return myNames;
  }

  /** Returns the {@code com.android.support} version of the name */
  @NotNull
  public String oldName() {
    return myNames.get(0);
  }

  /** Returns the {@code androidx} version of the name */
  @NotNull
  public String newName() {
    return myNames.size() > 1 ? myNames.get(1) : myNames.get(0);
  }

  /**
   * Returns the {@code com.android.support} version of the name. This method will start returning
   * to the {@code androidx} once those are the default versions to be added to the project. You
   * should avoid using this method when possible and use {@link #oldName()} or {@link #newName()}
   * depending on the dependencies of the module.
   */
  @NotNull
  public String defaultName() {
    return oldName();
  }

  /** Returns if the current name is a prefix of the given name. */
  public boolean isPrefix(@Nullable String name) {
    return isPrefix(name, false);
  }

  /**
   * Returns if the current name is a prefix of the given name.
   *
   * @param name the name to check
   * @param strict true if the name needs to be strictly longer than the prefix
   */
  public boolean isPrefix(@Nullable String name, boolean strict) {
    if (name == null) {
      return false;
    }

    for (String n : myNames) {
      if (name.startsWith(n) && (!strict || n.length() < name.length())) {
        return true;
      }
    }

    return false;
  }

  /**
   * Removes the current name from the given qualified name.
   *
   * <p>For example, if the <code>AndroidxName</code> is "android" and the passed qualifiedName is
   * "android.TestClass", this method, will return "TestClass"
   */
  public String removeFrom(@NotNull String qualifiedName) {
    for (String n : myNames) {
      if (qualifiedName.startsWith(n)) {
        return qualifiedName.substring(n.length());
      }
    }

    return qualifiedName;
  }

  /** Compares the current name with the given string */
  public boolean isEquals(@Nullable String strName) {
    if (strName == null) {
      return false;
    }

    return myNames.contains(strName);
  }

  /** Compares the current name with the given string ignoring case sensitivity */
  public boolean isEqualsIgnoreCase(@Nullable String strName) {
    if (strName == null) {
      return false;
    }

    for (String n : myNames) {
      if (n.equalsIgnoreCase(strName)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof String) {
      throw new IllegalStateException("You probably meant to call isEquals!");
    }
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    return myNames.equals(((RoomAndroidxName) other).myNames);
  }

  @Override
  public int hashCode() {
    return myNames.hashCode();
  }

  @Override
  public String toString() {
    assert false : "toString can not be used on AndroidxName";
    return super.toString();
  }
}
