package org.jetbrains.android.database;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

class AndroidDbErrorReporter {
  private volatile boolean myHasError;

  private final Project myProject;
  private final AndroidDataSource myDataSource;
  private final boolean myUpload;

  AndroidDbErrorReporter(@NotNull Project project, @NotNull AndroidDataSource dataSource, boolean upload) {
    myProject = project;
    myDataSource = dataSource;
    myUpload = upload;
  }

  public synchronized boolean hasError() {
    return myHasError;
  }

  public synchronized void reportError(@NotNull @Nls String message) {
    myHasError = true;
    final Notification notification = new Notification(
      AndroidDataSourceManager.NOTIFICATION_GROUP_ID, "Data Source Synchronization Error",
      "Cannot " + (myUpload ? "upload" : "synchronize") + " '" + myDataSource.getName() + "': " + message,
      NotificationType.ERROR);
    Notifications.Bus.notify(notification, myProject);
  }

  public synchronized void reportInfo(@NotNull @Nls String message) {
    final Notification notification = new Notification(
      AndroidDataSourceManager.NOTIFICATION_GROUP_ID, "Data Source Synchronization",
      "'" + myDataSource.getName() + "': " + message,
      NotificationType.INFORMATION);
    Notifications.Bus.notify(notification, myProject);
  }
}
