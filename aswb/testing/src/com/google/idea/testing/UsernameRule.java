/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.testing;

import com.google.common.base.StandardSystemProperty;
import javax.annotation.Nullable;
import org.junit.rules.ExternalResource;

/**
 * Stores the original {@link StandardSystemProperty#USER_NAME} value before a test and resets to it
 * afterwards.
 */
public class UsernameRule extends ExternalResource {
  @Nullable private String originalUsername;

  @Override
  protected void before() {
    originalUsername = StandardSystemProperty.USER_NAME.value();
  }

  @Override
  protected void after() {
    if (originalUsername == null) {
      System.clearProperty(StandardSystemProperty.USER_NAME.key());
    } else {
      setUsername(originalUsername);
    }
  }

  public void setUsername(String userName) {
    System.setProperty(StandardSystemProperty.USER_NAME.key(), userName);
  }
}
