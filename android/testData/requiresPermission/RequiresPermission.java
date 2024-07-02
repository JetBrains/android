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
package androidx.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.CLASS;

/** Stub only annotation. Do not use directly. */
@Retention(CLASS)
@Target({ANNOTATION_TYPE, METHOD, CONSTRUCTOR, FIELD, PARAMETER})
public @interface RequiresPermission {
  /**
   * The name of the permission that is required, if precisely one permission is required. If more
   * than one permission is required, specify either {@link #allOf()} or {@link #anyOf()} instead.
   *
   * <p>If specified, {@link #anyOf()} and {@link #allOf()} must both be null.
   */
  String value() default "";

  /**
   * Specifies a list of permission names that are all required.
   *
   * <p>If specified, {@link #anyOf()} and {@link #value()} must both be null.
   */
  String[] allOf() default {};

  /**
   * Specifies a list of permission names where at least one is required
   *
   * <p>If specified, {@link #allOf()} and {@link #value()} must both be null.
   */
  String[] anyOf() default {};

  /**
   * If true, the permission may not be required in all cases (e.g. it may only be enforced on
   * certain platforms, or for certain call parameters, etc.
   */
  boolean conditional() default false;

  // STUBS ONLY: historical API range for when this permission applies.
  // Used for merged annotations.
  String apis() default "";

  /**
   * Specifies that the given permission is required for read operations.
   *
   * <p>When specified on a parameter, the annotation indicates that the method requires a
   * permission which depends on the value of the parameter (and typically the corresponding field
   * passed in will be one of a set of constants which have been annotated with a
   * {@code @RequiresPermission} annotation.)
   */
  @Target({FIELD, METHOD, PARAMETER})
  @interface Read {
    RequiresPermission value() default @RequiresPermission;
  }

  /**
   * Specifies that the given permission is required for write operations.
   *
   * <p>When specified on a parameter, the annotation indicates that the method requires a
   * permission which depends on the value of the parameter (and typically the corresponding field
   * passed in will be one of a set of constants which have been annotated with a
   * {@code @RequiresPermission} annotation.)
   */
  @Target({FIELD, METHOD, PARAMETER})
  @interface Write {
    RequiresPermission value() default @RequiresPermission;
  }
}
