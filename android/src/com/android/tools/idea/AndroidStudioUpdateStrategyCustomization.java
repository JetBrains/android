package com.android.tools.idea;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import com.intellij.openapi.updateSettings.impl.UpdateChannel;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NlsContexts;
import java.util.regex.Pattern;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidStudioUpdateStrategyCustomization extends UpdateStrategyCustomization {
  @Override
  public boolean forceEapUpdateChannelForEapBuilds() {
    return false;
  }

  @Override
  public boolean isChannelActive(@NotNull ChannelStatus channel) {
    return true;
  }

  @Override
  public boolean haveSameMajorVersion(@NotNull BuildNumber build1, @NotNull BuildNumber build2) {
    return androidStudioVersion(build1) == androidStudioVersion(build2);
  }

  @Override
  public boolean isNewerVersion(@NotNull BuildNumber candidateBuild, @NotNull BuildNumber currentBuild) {
    if (androidStudioVersion(candidateBuild) == androidStudioVersion(currentBuild)) {
      return super.isNewerVersion(candidateBuild, currentBuild);
    }
    else {
      return androidStudioVersion(candidateBuild) > androidStudioVersion(currentBuild);
    }
  }

  /**
   * Parse Android Studio version, and use that as default update channel
   * <p>
   * Together with Android Studio customizations this locks channel for IDE
   * - disabling update channel selection by user (making default channel a selected channel)
   * - allowing only updates from selected channel
   */
  @Override
  public @NotNull ChannelStatus changeDefaultChannel(@NotNull ChannelStatus currentChannel) {
    //TODO find a better way to obtain release channel. (extend AndroidStudioApplicationInfo.xml schema ?)
    String versionName = ApplicationInfo.getInstance().getFullVersion();
    return versionNameToChannelStatus(versionName);
  }

  @Override
  public boolean isChannelApplicableForUpdates(@NotNull UpdateChannel updateChannel,
                                               @NotNull ChannelStatus selectedChannel) {
    return updateChannel.getStatus().equals(selectedChannel);
  }

  @Override
  public boolean canBeUsedForIntermediatePatches(@NotNull UpdateChannel updateChannel,
                                                 @NotNull ChannelStatus selectedChannel) {
    return updateChannel.getStatus().equals(selectedChannel);
  }

  @Override
  public @NlsContexts.DetailedDescription @Nullable String getChannelSelectionLockedMessage() {
    return AndroidBundle.message("updates.settings.channel.locked");
  }

  private static int androidStudioVersion(BuildNumber buildNumber) {
    return buildNumber.getComponents()[3];
  }

  private boolean versionNameContainsChannel(String versionName, String channel) {
    return Pattern.compile("\\b" + channel + "\\b", Pattern.CASE_INSENSITIVE).matcher(versionName).find();
  }

  protected ChannelStatus versionNameToChannelStatus(String versionName) {
    if (versionNameContainsChannel(versionName, "nightly") || versionNameContainsChannel(versionName, "dev")) {
      return ChannelStatus.MILESTONE;
    }
    if (versionNameContainsChannel(versionName, "canary")) {
      return ChannelStatus.EAP;
    }
    else if (versionNameContainsChannel(versionName, "rc") || versionNameContainsChannel(versionName, "beta")) {
      return ChannelStatus.BETA;
    }
    else {
      return ChannelStatus.RELEASE;
    }
  }
}
