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
package com.google.idea.blaze.android.projectview;

import com.google.common.base.Objects;
import com.google.idea.blaze.base.ui.BlazeValidationError;
import java.io.Serializable;
import java.util.List;

/** Project view entry data for {@link GeneratedAndroidResourcesSection}. */
public class GenfilesPath implements Serializable {
  private static final long serialVersionUID = 1L;

  public final String relativePath;

  public GenfilesPath(String relativePath) {
    this.relativePath = relativePath;
  }

  static boolean validate(String path, List<BlazeValidationError> errors) {
    if (path.startsWith("/")) {
      BlazeValidationError.collect(
          errors,
          new BlazeValidationError(
              "Genfiles path must be relative; cannot start with '/': " + path));
      return false;
    }

    if (path.endsWith("/")) {
      BlazeValidationError.collect(
          errors, new BlazeValidationError("Genfiles path may not end with '/': " + path));
      return false;
    }

    if (path.indexOf(':') >= 0) {
      BlazeValidationError.collect(
          errors, new BlazeValidationError("Genfiles path may not contain ':': " + path));
      return false;
    }
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GenfilesPath that = (GenfilesPath) o;
    return Objects.equal(relativePath, that.relativePath);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(relativePath);
  }

  @Override
  public String toString() {
    return relativePath;
  }
}
