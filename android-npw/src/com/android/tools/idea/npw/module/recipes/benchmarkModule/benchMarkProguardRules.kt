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
package com.android.tools.idea.npw.module.recipes.benchmarkModule

fun benchmarkKeepRules() =
  """
  # Add benchmark module specific R8 rules here.
  # AGP will combine all keep rule files in src/main/keepRules to pass to R8
  #
  # For more details, see
  #   https://d.android.com/r/tools/r8/keep-rules

  -dontobfuscate

  -ignorewarnings

  -keepattributes *Annotation*

  -dontnote junit.framework.**
  -dontnote junit.runner.**

  -dontwarn androidx.test.**
  -dontwarn org.junit.**
  -dontwarn org.hamcrest.**
  -dontwarn com.squareup.javawriter.JavaWriter

  -keepclasseswithmembers @org.junit.runner.RunWith public class *
  """
    .trimIndent()

fun benchmarkProguardRules() =
  """
  # Add benchmark module specific ProGuard rules here.
  # You can control the set of applied configuration files using the
  # proguardFiles setting in build.gradle.
  #
  # For more details, see
  #   https://d.android.com/r/tools/r8/keep-rules

  -dontobfuscate

  -ignorewarnings

  -keepattributes *Annotation*

  -dontnote junit.framework.**
  -dontnote junit.runner.**

  -dontwarn androidx.test.**
  -dontwarn org.junit.**
  -dontwarn org.hamcrest.**
  -dontwarn com.squareup.javawriter.JavaWriter

  -keepclasseswithmembers @org.junit.runner.RunWith public class *
  """
    .trimIndent()
