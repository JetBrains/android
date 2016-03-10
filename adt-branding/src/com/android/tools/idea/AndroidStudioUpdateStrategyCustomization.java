package com.android.tools.idea;

import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;

/**
 * @author nik
 */
public class AndroidStudioUpdateStrategyCustomization extends UpdateStrategyCustomization {
  @Override
  public boolean forceEapUpdateChannelForEapBuilds() {
    return false;
  }

  @Override
  public boolean allowMajorVersionUpdate() {
    return true;
  }

  @Override
  public boolean useOnlyCurrentChannel() {
    return true;
  }
}
