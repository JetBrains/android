/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.producers;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.testFramework.MapDataContext;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for {@link JUnitConfigurationUtil}. Currently just tests that the upstream
 * method we delegate to still exists.
 */
@RunWith(JUnit4.class)
public class JUnitConfigurationUtilTest extends BlazeIntegrationTestCase {

  @Test
  public void testMethodDoesNotThrow() {
    JUnitConfigurationUtil.isMultipleElementsSelected(createDummyContext());
  }

  private ConfigurationContext createDummyContext() {
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, getProject());
    return ConfigurationContext.getFromContext(dataContext);
  }
}
