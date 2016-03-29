/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.service.snippets;

import com.android.tools.rpclib.binary.BinaryObject;

/**
 * Created by anton on 2/10/16.
 */
public abstract class Pathway implements BinaryObject {
  public static Pathway wrap(BinaryObject obj) {
    return (Pathway)obj;
  }

  public BinaryObject unwrap() {
    return this;
  }

  /**
   * Get the base path for this pathway. If this pathway is a root return null.
   *
   * @return the base path for this pathway or null if it is a root.
   */
  public abstract Pathway base();

  /**
   * Make a pathway for a command parameter.
   *
   * @param typeName the name for this type in the API file.
   * @param paramName the name of the parameter.
   * @return a pathway for the parameter.
   */
  public static Pathway param(String typeName, String paramName) {
    return new RelativePath().setTypeName(typeName).field(paramName);
  }

  /**
   * Make a pathway for a global (aka state variable).
   *
   * @param name the name of the global.
   * @return a pathway for the global.
   */
  public static Pathway global(String name) {
    return new RelativePath().setTypeName("State").field(name);
  }

  /**
   * Make a pathway to the field of this the entity at this pathway.
   *
   * @param name the name of the field of the entity.
   * @return a pathway for the field.
   */
  public Pathway field(String name) {
    return new FieldPath(this, name);
  }

  /**
   * Make a pathway to the key of the collection at this pathway.
   *
   * @return a pathway for the key.
   */
  public Pathway key() {
    return new PartPath(this, PartKind.KeyPart);
  }

  /**
   * Make a pathway to the element of the collection at this pathway.
   *
   * @return a pathway for the element.
   */
  public Pathway elem() {
    return new PartPath(this, PartKind.ElemPart);
  }

  /**
   * See if the receiver pathway is a prefix (or equal) to the specified
   * pathway.
   *
   * @param pathway see if the receiver is a prefix of this pathway
   * @return true if the receiver is a prefix of the specified pathway.
   */
  public boolean isPrefix(Pathway pathway) {
    int thisDepth = depth();
    int otherDepth = pathway.depth();
    if (thisDepth > otherDepth) {
      return false;
    }
    int diff = otherDepth - thisDepth;
    Pathway p = pathway;
    for (int i = 0; i < diff; i++) {
      p = p.base();
    }
    if (equals(p)) {
      return true;
    }
    return false;  //equals(p);
  }

  /**
   * Computes the number of steps to reach the root of the pathway.
   *
   * @return number of steps to reach the root.
   */
  private int depth() {
    Pathway p = this;
    int i = 0;
    while (p != null) {
      p = p.base();
      i++;
    }
    return i;
  }
}
