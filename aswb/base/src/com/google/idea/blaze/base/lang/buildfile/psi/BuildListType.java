/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.lang.buildfile.psi;

import com.google.common.collect.ImmutableList;
import com.intellij.lang.ASTNode;
import javax.annotation.Nullable;

/** Common interface for BUILD psi elements containing a list / sequence of child elements. */
public abstract class BuildListType<E extends BuildElement> extends BuildElementImpl {

  private final Class<E> elementClass;

  public BuildListType(ASTNode astNode, Class<E> elementClass) {
    super(astNode);
    this.elementClass = elementClass;
  }

  public E[] getElements() {
    return findChildrenByClass(elementClass);
  }

  @Nullable
  public E getFirstElement() {
    return findChildByClass(elementClass);
  }

  public boolean isEmpty() {
    return getFirstElement() != null;
  }

  /**
   * The offset into the document at which child elements start. For lists wrapped in braces, this
   * is the offset after the opening brace. For statement lists, this is the offset after the colon.
   */
  public int getStartOffset() {
    return getNode().getStartOffset() + 1;
  }

  /** The character(s) which can end this list */
  public abstract ImmutableList<Character> getEndChars();
}
