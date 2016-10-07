/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import com.intellij.openapi.project.ex.ProjectManagerEx;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * Calls {@link ProjectManagerEx#blockReloadingProjectOnExternalChanges} before the test and
 * {@link ProjectManagerEx#unblockReloadingProjectOnExternalChanges} after.
 * <p>
 * There is a race condition between reloading the configuration file after file deletion detected
 * and the serialization of IDEA model we just customized so that modules can't be loaded correctly.
 * This is a hack to prevent StoreAwareProjectManager from doing any reloading during test.
 */
class BlockReloading extends TestWatcher {

  @Override
  protected void starting(Description description) {
    ProjectManagerEx.getInstanceEx().blockReloadingProjectOnExternalChanges();
  }

  @Override
  protected void finished(Description description) {
    ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges();
  }
}
