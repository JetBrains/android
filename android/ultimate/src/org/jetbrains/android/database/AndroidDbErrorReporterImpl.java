package org.jetbrains.android.database;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
class AndroidDbErrorReporterImpl extends AndroidDbErrorReporter {
  private final Project myProject;
  private final AndroidDataSource myDataSource;

  public AndroidDbErrorReporterImpl(Project project, AndroidDataSource dataSource) {
    myProject = project;
    myDataSource = dataSource;
  }

  @Override
  public void reportError(@NotNull String message) {
    reportErrorToEventLog(myProject, myDataSource, message);
  }

  private static void reportErrorToEventLog(@NotNull Project project, @NotNull AndroidDataSource dataSource, @NotNull String message) {
    final Notification notification = new Notification(
      AndroidDbManager.NOTIFICATION_GROUP_ID, "Data Source Synchronization Error",
      "Cannot synchronize '" + dataSource.getName() + "': " + message, NotificationType.ERROR);
    Notifications.Bus.notify(notification, project);
  }
}
