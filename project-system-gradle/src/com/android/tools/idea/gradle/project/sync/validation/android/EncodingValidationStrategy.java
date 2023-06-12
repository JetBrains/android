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
package com.android.tools.idea.gradle.project.sync.validation.android;

import com.android.ide.common.repository.AgpVersion;
import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import static com.android.tools.idea.project.messages.MessageType.INFO;

class EncodingValidationStrategy extends AndroidProjectValidationStrategy {
  @NotNull private final EncodingProjectManager myEncodings;
  @NotNull private final Charset myProjectEncoding;
  @NotNull private final AgpVersion myOneDotTwoPluginVersion;

  @Nullable private String myMismatchingEncoding;

  EncodingValidationStrategy(@NotNull Project project) {
    this(project, EncodingProjectManager.getInstance(project));
  }

  @VisibleForTesting
  EncodingValidationStrategy(@NotNull Project project, @NotNull EncodingProjectManager encodings) {
    super(project);
    myEncodings = encodings;
    myProjectEncoding = myEncodings.getDefaultCharset();
    myOneDotTwoPluginVersion = new AgpVersion(1, 2, 0);
  }

  @Override
  void validate(@NotNull Module module, @NotNull GradleAndroidModel androidModel) {
    AgpVersion modelVersion = (androidModel.getAgpVersion());
    if (modelVersion != null) {
      boolean isOneDotTwoOrNewer = modelVersion.compareIgnoringQualifiers(myOneDotTwoPluginVersion) >= 0;

      // Verify that the encoding in the model is the same as the encoding in the IDE's project settings.
      Charset modelEncoding = null;
      if (isOneDotTwoOrNewer) {
        try {
          IdeAndroidProject androidProject = androidModel.getAndroidProject();
          modelEncoding = Charset.forName(androidProject.getJavaCompileOptions().getEncoding());
        }
        catch (UnsupportedCharsetException ignore) {
          // It's not going to happen.
        }
      }
      if (myMismatchingEncoding == null && modelEncoding != null && myProjectEncoding.compareTo(modelEncoding) != 0) {
        myMismatchingEncoding = modelEncoding.displayName();
      }
    }
  }

  @Override
  void fixAndReportFoundIssues() {
    if (myMismatchingEncoding != null) {
      Project project = getProject();

      // Fix encoding mismatch.
      myEncodings.setDefaultCharsetName(myMismatchingEncoding);

      // Report encoding mismatch.
      String line = String.format("The project encoding (%1$s) has been reset to the encoding specified in the Gradle build files (%2$s).",
                                  myEncodings.getDefaultCharset().displayName(), myMismatchingEncoding);
      String[] text = {line, "Mismatching encodings can lead to serious bugs."};

      SyncMessage message = new SyncMessage(SyncMessage.DEFAULT_GROUP, INFO, text);

      GradleSyncMessages.getInstance(project).report(message);
    }
  }

  @VisibleForTesting
  @Nullable
  String getMismatchingEncoding() {
    return myMismatchingEncoding;
  }

  @VisibleForTesting
  void setMismatchingEncoding(@Nullable String mismatchingEncoding) {
    myMismatchingEncoding = mismatchingEncoding;
  }
}
