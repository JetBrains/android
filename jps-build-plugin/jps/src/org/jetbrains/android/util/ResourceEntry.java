// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.util;

import org.jetbrains.annotations.NotNull;

public class ResourceEntry {
  private final String myType;
  private final String myName;
  private final String myContext;

  public ResourceEntry(@NotNull String type, @NotNull String name, @NotNull String context) {
    myType = type;
    myName = name;
    myContext = context;
  }

  @NotNull
  public String getContext() {
    return myContext;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ResourceEntry that = (ResourceEntry)o;

    if (!myContext.equals(that.myContext)) return false;
    if (!myName.equals(that.myName)) return false;
    if (!myType.equals(that.myType)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myType.hashCode();
    result = 31 * result + myName.hashCode();
    result = 31 * result + myContext.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() +
           '@' +
           Integer.toHexString(System.identityHashCode(this)) +
           '(' +
           myType +
           ',' +
           myName +
           (myContext.isEmpty() ? "" : ',' + myContext + ')');
  }
}
