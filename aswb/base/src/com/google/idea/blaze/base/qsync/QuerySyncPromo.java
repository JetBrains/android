/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.base.qsync.settings.QuerySyncConfigurableProvider;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.common.experiments.IntExperiment;
import com.google.idea.common.util.MorePlatformUtils;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;

/** Shows a promo for query sync when legacy sync has been running for a number of minites. */
public class QuerySyncPromo {

  private static final IntExperiment PROMO_DISPLAY_DELAY_MINUTES =
      new IntExperiment("query.sync.promo.delay.minutes", 10);

  private final Project project;
  private final QuerySyncManager querySyncManager;

  public QuerySyncPromo(Project project) {
    this.project = project;
    querySyncManager = QuerySyncManager.getInstance(project);
  }

  public Future<?> getPromoShowFuture() {
    int delayMins = PROMO_DISPLAY_DELAY_MINUTES.getValue();
    if (delayMins <= 0) {
      return Futures.immediateCancelledFuture();
    }
    return AppExecutorUtil.getAppScheduledExecutorService()
        .schedule(this::show, delayMins, MINUTES);
  }

  public void show() {
    if (!MorePlatformUtils.isAndroidStudio()) {
      return;
    }
    querySyncManager.getQuerySyncUrl().ifPresent(this::displayPopupWithUrl);
  }

  private void displayPopupWithUrl(String url) {
    boolean qsEnabled = QuerySync.useForNewProjects();
    Notification promo =
      NotificationGroupManager.getInstance()
        .getNotificationGroup("QuerySyncPromo")
        .createNotification(
          "Sync taking a long time?",
          qsEnabled ? "Re-create your project to get the benefits of query sync" :
          "Try query sync, the new improved sync solution. Now in beta!",
          NotificationType.INFORMATION);
    promo.addAction(
        new AnAction("Learn More") {
          @Override
          public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            BrowserUtil.browse(url);
          }
        });
    promo.addAction(
      new AnAction(qsEnabled ? "Query Sync Settings" : "Enable Now") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
          ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, QuerySyncConfigurableProvider.getConfigurableClass());
        }
      });
    promo.notify(project);
  }
}
