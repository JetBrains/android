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
package com.google.idea.blaze.base.projectview.section;

import com.google.common.base.Objects;
import java.io.Serializable;

/**
 * A section is a part of an project view file. For instance:
 *
 * <p>directories java/com/a java/com/b
 *
 * <p>Is a directory section with two items.
 */
public abstract class Section<T> implements Serializable {
  private static final long serialVersionUID = 2L;
  private final String sectionName;

  protected Section(SectionKey<T, ?> key) {
    this.sectionName = key.getName();
  }

  public boolean isSectionType(SectionKey<?, ?> key) {
    return this.sectionName.equals(key.getName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Section<?> section = (Section<?>) o;
    return Objects.equal(sectionName, section.sectionName);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(sectionName);
  }
}
