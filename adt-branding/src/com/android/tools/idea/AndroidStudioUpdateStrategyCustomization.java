package com.android.tools.idea;

import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class AndroidStudioUpdateStrategyCustomization extends UpdateStrategyCustomization {
  @Override
  public boolean forceEapUpdateChannelForEapBuilds() {
    return false;
  }

  @Override
  public boolean isChannelActive(@NotNull ChannelStatus channel) {
    return true;
  }
}