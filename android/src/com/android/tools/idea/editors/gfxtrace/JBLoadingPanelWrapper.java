/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace;

import com.android.tools.rpclib.futures.SingleInFlight;
import com.intellij.ui.components.JBLoadingPanel;

/**
 * An implementation of {@link SingleInFlight.Listener} that drives the loading status of the
 * {@link JBLoadingPanel} passed to the constructor.
 */
public class JBLoadingPanelWrapper implements SingleInFlight.Listener {
  private final JBLoadingPanel myLoadingPanel;

  public JBLoadingPanelWrapper(JBLoadingPanel panel) {
    myLoadingPanel = panel;
  }

  @Override
  public void onIdleToWorking() {
    myLoadingPanel.startLoading();
  }

  @Override
  public void onWorkingToIdle() {
    myLoadingPanel.stopLoading();
  }
}
