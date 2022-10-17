/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.testing

/**
 * Checks for whether the threading agent has been loaded. Skips this check if the test is not
 * being run from bazel.
 */
fun maybeCheckThreadingAgentIsRunning() {
  if (!System.getProperties().containsKey("bazel.test_suite")) {
    // At this time we only assert that the threading java agent has been loaded
    // when running the tests from bazel and not the IDE
    return
  }

  // Java agent is loaded by the bootstrap class loader and so the findBootstrapClassOrNull
  // method should be used instead of the findLoadedClass method which can only be used
  // with a non-bootstrap class loader
  val findBootstrapClassOrNullMethod =
    ClassLoader::class.java.getDeclaredMethod("findBootstrapClassOrNull", String::class.java)
  findBootstrapClassOrNullMethod.isAccessible = true
  findBootstrapClassOrNullMethod.invoke(
    ThreadingCheckRule::class.java.classLoader,
    "com.android.tools.instrumentation.threading.agent.Agent")
  ?: throw RuntimeException(
    "ThreadingCheckRule works in conjunction with the threading java agent which can be "
    + "loaded by adding 'test_agents = [\"//tools/base/threading-agent:threading_agent.jar\"]' "
    + "argument to an iml_module build rule.")
}