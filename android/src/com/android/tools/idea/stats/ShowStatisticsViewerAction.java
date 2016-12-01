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
package com.android.tools.idea.stats;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * A "Tools/Internal Actions/Android" action to show a dialog displaying statistics as they're logged.
 */
public class ShowStatisticsViewerAction extends AnAction {
  private StatisticsViewer myStatisticsViewer;

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setText(StatisticsViewer.TITLE);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (myStatisticsViewer == null) {
      myStatisticsViewer = new StatisticsViewer() {
        @Override
        public void dispose() {
          super.dispose();
          myStatisticsViewer = null;
        }
      };
    } else {
      myStatisticsViewer.show();
    }
  }
}
