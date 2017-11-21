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
package com.android.tools.idea.tests.gui.framework.guitestsystem;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation is used by {@see BuildSystemMultiplexer} that runs a ui-test
 * multiple times with different build systems as specified.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RunWithBuildSystem {

  /**
   * Available build systems to test with.
   */
  enum BuildSystem {
    GRADLE, BAZEL
  }

  /**
   * The default value is set to GRADLE to make sure tests are always annotated
   * with at least one build system to run with.
   */
  @NotNull BuildSystem[] value() default {BuildSystem.GRADLE};
}
