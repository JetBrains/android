// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.help;

import com.intellij.openapi.help.WebHelpProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StudioWebHelpProvider extends WebHelpProvider {
  @Nullable
  @Override
  public String getHelpPageUrl(@NotNull String helpTopicIdWithPrefix) {
    assert helpTopicIdWithPrefix.startsWith(StudioHelpManagerImpl.STUDIO_HELP_PREFIX);
    String topicIdWithNoPrefix = helpTopicIdWithPrefix.substring(StudioHelpManagerImpl.STUDIO_HELP_PREFIX.length());

    if (topicIdWithNoPrefix.startsWith("reference.dialogs.rundebug.Android")) {
      return studioWebHelpUrl("r/studio-ui/rundebugconfig.html");
    }

    return studioWebHelpUrl(topicIdWithNoPrefix);
  }

  @NonNls
  @NotNull
  static String studioWebHelpUrl(String topicIdWithNoPrefix) {
    return StudioHelpManagerImpl.STUDIO_HELP_URL + topicIdWithNoPrefix;
  }

  @NotNull
  @Override
  public String getHelpTopicPrefix() {
    return StudioHelpManagerImpl.STUDIO_HELP_PREFIX;
  }
}
