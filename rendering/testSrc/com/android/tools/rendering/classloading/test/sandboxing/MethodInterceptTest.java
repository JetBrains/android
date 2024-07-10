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
package com.android.tools.rendering.classloading.test.sandboxing;

public class MethodInterceptTest {
  public void noArgsInstanceMethod() {
    System.out.println("MethodInterceptTest#noArgsInstanceMethod");
  }

  public void instanceMethod(int a, long b, String c) {
    System.out.println("MethodInterceptTest#instanceMethod " + a + " " + b + " " + c);
  }

  public static void noArgsStaticMethod() {
    System.out.println("MethodInterceptTest.noArgsStaticMethod");
  }

  public static void staticMethod(int a, long b, String c) throws ClassNotFoundException {
    System.out.println("MethodInterceptTest#staticMethod " + a + " " + b + " " + c);

    throw new ClassNotFoundException("Hello");
  }

  @Override
  public String toString() {
    // Hardcoded toString to allow for assertions in testing to use the class name as part of the checks
    return "MethodInterceptTest@1";
  }
}
