package org.jetbrains.android.database;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

class AndroidDbErrorReporterImpl extends AndroidDbErrorReporter {
  private final Project myProject;
  private final AndroidDataSource myDataSource;
  private final boolean myUpload;

  AndroidDbErrorReporterImpl(@NotNull Project project, @NotNull AndroidDataSource dataSource, boolean upload) {
    myProject = project;
    myDataSource = dataSource;
    myUpload = upload;
  }

  @Override
  public synchronized void reportError(@NotNull String message) {
    super.reportError(message);
    final Notification notification = new Notification(
      AndroidDataSourceManager.NOTIFICATION_GROUP_ID, "Data Source Synchronization Error",
      "Cannot " + (myUpload ? "upload" : "synchronize") + " '" + myDataSource.getName() + "': " + message,
      NotificationType.ERROR);
    Notifications.Bus.notify(notification, myProject);
  }
}
