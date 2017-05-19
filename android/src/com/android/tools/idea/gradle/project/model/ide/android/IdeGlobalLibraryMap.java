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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.builder.model.level2.Library;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Creates a deep copy of {@link GlobalLibraryMap}
 */
public final class IdeGlobalLibraryMap extends IdeModel implements GlobalLibraryMap {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;
  private final int myHashCode;

  @NotNull private final Map<String, Library> myMap;

  public IdeGlobalLibraryMap(@NotNull GlobalLibraryMap item, @NotNull ModelCache modelCache) {
    super(item, modelCache);
    myMap = copy(item.getLibraries(), modelCache, item1 -> IdeLevel2LibraryFactory.create(item1, modelCache));
    myHashCode = calculateHashCode();
  }

  @Override
  @NotNull
  public Map<String, Library> getLibraries() {
    return myMap;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeGlobalLibraryMap)) {
      return false;
    }
    IdeGlobalLibraryMap item = (IdeGlobalLibraryMap)o;
    return Objects.equals(myMap, item.myMap);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myMap);
  }

  @Override
  public String toString() {
    return "IdeGlobalLibraryMap{" +
           "myMap=" + myMap +
           '}';
  }
}
