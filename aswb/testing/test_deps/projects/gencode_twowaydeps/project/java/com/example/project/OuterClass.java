/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.example.project;

import com.example.external.annotation.Annotation;

/**
 * A simple class to exercise {@link com.example.external.processor.Processor} and reproduce a
 * corner case in the query sync/IntelliJ Kotlin support.
 *
 * <p>Ideally, this class would be a plain {@code internal} kotlin class. But the kotlin build
 * support in {@code studio-main} does not support annotation processors at time of writing. So we
 * simulate an {@code internal} class using a package-private inner class in Java; the error that
 * we're reproducing here is seen in the kotlin code {@link KotlinClass} since it's kotlin specific.
 */
class OuterClass {
  @Annotation
  static class AnnotatedClass {
    public void sayHello() {
      System.out.println("Hello!");
    }
  }
}
