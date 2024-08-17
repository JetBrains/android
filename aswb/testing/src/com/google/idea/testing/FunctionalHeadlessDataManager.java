/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

import com.intellij.ide.impl.HeadlessDataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Key;
import java.awt.Component;
import javax.annotation.Nullable;

/**
 * IntelliJ replaces the normal DataManager with a mock version in headless environments. We rely on
 * a functional DataManager in run configuration tests to recognize when multiple psi elements are
 * selected.
 */
public class FunctionalHeadlessDataManager extends HeadlessDataManager {
  private volatile DataContext dataContext;

  @Override
  public <T> void saveInDataContext(DataContext dataContext, Key<T> dataKey, @Nullable T data) {
    this.dataContext = dataContext;
    super.saveInDataContext(dataContext, dataKey, data);
  }

  @Override
  public DataContext getDataContext() {
    return dataContext != null ? dataContext : super.getDataContext();
  }

  @Override
  public DataContext getDataContext(Component component) {
    // ignore component in this mock version
    return dataContext != null ? dataContext : super.getDataContext();
  }
}
