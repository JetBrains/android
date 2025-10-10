/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

#include "TestClass.h"
#include <iostream>
#include <android/log.h>

#define LOG_TAG "TestClass"

TestClass::TestClass() {
  testString = "test";
  std::cout << "coutn is here" << std::endl;
  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Hello from JNI");
}
