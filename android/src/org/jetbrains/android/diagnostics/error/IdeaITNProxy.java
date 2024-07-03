// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.diagnostics.error;

import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class IdeaITNProxy {
  public static List<Pair<String, String>> getKeyValuePairs(@Nullable String login,
                                                            @Nullable String password,
                                                            ErrorBean error,
                                                            Application application,
                                                            ApplicationInfoEx appInfo,
                                                            ApplicationNamesInfo namesInfo,
                                                            UpdateSettings updateSettings) {
    List<Pair<String, String>> params = new ArrayList<>();

    params.add(Pair.create("protocol.version", "1"));

    if (login != null) {
      params.add(Pair.create("user.login", login));
      params.add(Pair.create("user.password", password));
    }

    params.add(Pair.create("os.name", SystemInfo.OS_NAME));
    params.add(Pair.create("java.version", SystemInfo.JAVA_VERSION));
    params.add(Pair.create("java.vm.vendor", SystemInfo.JAVA_VENDOR));

    params.add(Pair.create("app.name", namesInfo.getProductName()));
    params.add(Pair.create("app.name.full", namesInfo.getFullProductName()));
    params.add(Pair.create("app.name.version", appInfo.getVersionName()));
    params.add(Pair.create("app.eap", Boolean.toString(appInfo.isEAP())));
    params.add(Pair.create("app.internal", Boolean.toString(application.isInternal())));
    params.add(Pair.create("app.build", appInfo.getBuild().asString()));
    params.add(Pair.create("app.version.major", appInfo.getMajorVersion()));
    params.add(Pair.create("app.version.minor", appInfo.getMinorVersion()));
    params.add(Pair.create("app.build.date", format(appInfo.getBuildDate())));
    params.add(Pair.create("app.build.date.release", format(appInfo.getMajorReleaseBuildDate())));

    params.add(Pair.create("update.channel.status", updateSettings.getSelectedChannelStatus().getCode()));
    params.add(Pair.create("update.ignored.builds", StringUtil.join(updateSettings.getIgnoredBuildNumbers(), ",")));

    params.add(Pair.create("plugin.name", error.getPluginName()));
    params.add(Pair.create("plugin.version", error.getPluginVersion()));

    params.add(Pair.create("last.action", error.getLastAction()));
    params.add(Pair.create("previous.exception", null));

    params.add(Pair.create("error.message", error.getMessage()));
    params.add(Pair.create("error.stacktrace", error.getStackTrace()));
    params.add(Pair.create("error.description", error.getDescription()));

    params.add(Pair.create("assignee.id", null));

    List<Attachment> attachments = error.getAttachments();
    for (int i = 0; i < attachments.size(); i++) {
      Attachment attachment = attachments.get(i);
      // The list of key-value pairs may be converted to a map later, add indices to the keys' names to make sure they are unique
      params.add(Pair.create("attachment" + (i + 1) + ".name", attachment.getName()));
      params.add(Pair.create("attachment" + (i + 1) + ".value", getAttachmentValue(attachment)));
    }

    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; i < projects.length; i++) {
      params.add(Pair.create("is.gradle.project." + i, Boolean.toString(ProjectSystemUtil.requiresAndroidModel(projects[i]))));
    }

    return params;
  }

  private static String format(Calendar calendar) {
    return calendar == null ? "" : Long.toString(calendar.getTime().getTime());
  }

  /**
   * Names of attachment types that are text-based (i.e., when getting those attachments' values, we should get them in text format, not
   * binary format).
   */
  @NotNull
  private static final List<String> TEXT_BASED_ATTACHMENTS = new ImmutableList.Builder<String>()
    .add("Kotlin version") // See https://github.com/JetBrains/intellij-community/blob/8cd28dd/plugins/kotlin/gradle/gradle/src/org/jetbrains/kotlin/idea/gradle/diagnostic/KotlinGradleBuildErrorsChecker.kt#L74
    .build();

  @NotNull
  private static String getAttachmentValue(@NotNull Attachment attachment) {
    if (TEXT_BASED_ATTACHMENTS.contains(attachment.getName())) {
      return attachment.getDisplayText();
    } else {
      return attachment.getEncodedBytes();
    }
  }

}
