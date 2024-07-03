/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.SdkConstants;
import com.android.utils.HashCodes;
import com.intellij.psi.xml.XmlAttribute;
import java.util.Comparator;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A class holding a pair of attribute namespace and attribute name.
 *
 * This class holds methods specific to MotionLayout.
 */
public class AttrName implements Comparable<AttrName> {
  private final String myNamespace;
  private final String myName;

  public AttrName(@NotNull String namespace, @NotNull String name) {
    this.myNamespace = namespace;
    this.myName = name;
  }

  public AttrName(@NotNull XmlAttribute attribute) {
    this(attribute.getNamespace(), attribute.getLocalName());
  }

  @NotNull
  public String getNamespace() {
    return myNamespace;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public static String getMotionNamespace() {
    // TODO: Replace AUTO_URI with method to get the uri of the motion layout namespace
    return SdkConstants.AUTO_URI;
  }

  @NotNull
  public static AttrName motionAttr(@NotNull String name) {
    return new AttrName(getMotionNamespace(), name);
  }

  @NotNull
  public static AttrName androidAttr(@NotNull String name) {
    return new AttrName(SdkConstants.ANDROID_URI, name);
  }

  @NotNull
  public static AttrName customAttr(@NotNull String name) {
    return new AttrName("", name);
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(myNamespace.hashCode(), myName.hashCode());
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (!(other instanceof AttrName)) {
      return false;
    }
    AttrName attr = (AttrName)other;
    return Objects.equals(myNamespace, attr.myNamespace) && Objects.equals(myName, attr.myName);
  }

  private Comparator<AttrName> ourComparator = Comparator.comparing(AttrName::getName).thenComparing(AttrName::getNamespace);

  @Override
  public int compareTo(@NotNull AttrName other) {
    return ourComparator.compare(this, other);
  }

  @Override
  @NotNull
  public String toString() {
    return myName;
  }

  private boolean is(@NotNull String namespace, @NotNull String name) {
    return this.myNamespace.equals(namespace) && this.myName.equals(name);
  }

  public boolean isMotionAttr(@NotNull String name) {
    return is(getMotionNamespace(), name);
  }

  public boolean isId() {
    return is(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
  }
}
