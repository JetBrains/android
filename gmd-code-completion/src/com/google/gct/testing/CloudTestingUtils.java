/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.gct.testing;

import com.android.tools.analytics.UsageTracker;
import com.android.tools.analytics.UsageTrackerUtils;

import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;

public class CloudTestingUtils {

    private static final String GOOGLE_GROUP_URL = "'https://firebase.google.com/support'";

    public static void showErrorMessage(
            @Nullable Project project, String errorDialogTitle, String errorMessage) {
        int newLineIndex = errorMessage.indexOf('\n');
        String userErrorMessage =
                newLineIndex != -1 ? errorMessage.substring(0, newLineIndex) : errorMessage;
        String detailedErrorMessage =
                newLineIndex != -1
                        ? "<html><a href="
                                + GOOGLE_GROUP_URL
                                + ">Report this issue</a> (please copy/paste the text below into"
                                + " the form)<br><br>"
                                + getDetailedErrorMessage(errorMessage.substring(newLineIndex + 1))
                                + "</html>"
                        : "No details...";
        UsageTracker.log(
                UsageTrackerUtils.withProjectId(
                        AndroidStudioEvent.newBuilder()
                                .setCategory(AndroidStudioEvent.EventCategory.CLOUD_TESTING)
                                .setKind(AndroidStudioEvent.EventKind.CLOUD_TESTING_BACKEND_ERROR)
                                .setCloudTestingErrorMessage(userErrorMessage),
                        project));
        showCascadingErrorMessages(
                project, errorDialogTitle, userErrorMessage, detailedErrorMessage);
    }

    private static String getDetailedErrorMessage(String errorMessage) {
        String debugInfoField = "\"debugInfo\" : \""; // Available for internal runs only.
        int debugInfoIndex = errorMessage.indexOf(debugInfoField);
        if (debugInfoIndex != -1) {
            int debugInfoStartIndex = debugInfoIndex + debugInfoField.length();
            int debugInfoEndIndex = errorMessage.indexOf('\"', debugInfoStartIndex);
            if (debugInfoEndIndex != -1) {
                String debugInfo = errorMessage.substring(debugInfoStartIndex, debugInfoEndIndex);
                errorMessage =
                        errorMessage.substring(0, debugInfoIndex)
                                + errorMessage.substring(debugInfoEndIndex + 2);
                String unescapedDebugInfo = debugInfo.replace("\\n", "\n").replace("\\t", "\t");
                errorMessage += "\n\n" + "DEBUG INFO:\n" + unescapedDebugInfo;
            }
        }
        return errorMessage.replace("\n", "<br>").replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;");
    }

    private static void showCascadingErrorMessages(
            @Nullable final Project project,
            final String errorDialogTitle,
            String userErrorMessage,
            final String detailedErrorMessage) {

        new Notification(
                        errorDialogTitle,
                        "",
                        String.format("<b>%s</b> <a href=''>Details</a>", userErrorMessage),
                        NotificationType.WARNING)
                .setListener(
                        new NotificationListener.Adapter() {
                            @Override
                            protected void hyperlinkActivated(
                                    @NotNull Notification notification,
                                    @NotNull HyperlinkEvent event) {
                                notification.expire();
                                Messages.showDialog(
                                        project,
                                        detailedErrorMessage,
                                        errorDialogTitle,
                                        new String[] {Messages.CANCEL_BUTTON},
                                        0,
                                        null);
                            }
                        })
                .notify((project == null || project.isDefault()) ? null : project);
    }
}
