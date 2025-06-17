/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.common.experiments;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import java.util.List;

/**
 * An AppLifecycleListener that initializes the ExperimentService
 */
public class ExperimentServiceAppLifecycleListener implements AppLifecycleListener {
  private static final Logger logger = Logger.getInstance(ExperimentServiceAppLifecycleListener.class);

  private static final long TIMEOUT_SECONDS = 30L;

  @Override
  public void appFrameCreated(List<String> commandLineArgs) {
    try {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          ExperimentService.getInstance().initService();
        });
      logger.info("Successfully initialized ExperimentService");
    } catch (Exception e) {
      logger.error("An error occurred during ExperimentService initialization.", e);
    }
  }
}
