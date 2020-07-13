// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.help;

import static com.android.tools.idea.help.StudioHelpManagerImpl.STUDIO_HELP_PREFIX;
import static com.android.tools.idea.help.StudioHelpManagerImpl.STUDIO_HELP_URL;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StudioWebHelpProviderTest {
  @Test
  public void testStudioHelpPrefix() {
    StudioWebHelpProvider inst = new StudioWebHelpProvider();
    String resolvedUrl = inst.getHelpPageUrl(STUDIO_HELP_PREFIX + "test");
    assertEquals(STUDIO_HELP_URL + "test", resolvedUrl);
  }

  @Test
  public void testStudioHelpSpecialCase() {
    StudioWebHelpProvider inst = new StudioWebHelpProvider();
    String resolvedUrl = inst.getHelpPageUrl(STUDIO_HELP_PREFIX + "reference.dialogs.rundebug.Android");
    assertEquals(STUDIO_HELP_URL + "r/studio-ui/rundebugconfig.html", resolvedUrl);
  }
}