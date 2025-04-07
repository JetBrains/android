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
package com.android.tools.tests;

import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.junit.rules.ExternalResource;

/**
 * A rule that ensures any Gradle daemons started over the course of some tests will be shutdown.
 *
 * This may be too aggressive for test classes, but it can be a useful rule for test suites.
 */
public class GradleDaemonsRule extends ExternalResource {
  @Override
  protected void after() {
    DefaultGradleConnector.close();
  }
}
