// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.help;

import com.intellij.openapi.help.HelpManager;
import org.jetbrains.android.AndroidTestCase;

import static com.android.tools.idea.help.StudioHelpManagerImpl.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class StudioHelpManagerImplTest extends AndroidTestCase {
  private HelpManager myHelpManager;
  private Browser myMockBrowser;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myHelpManager = HelpManager.getInstance();
    myMockBrowser = mock(Browser.class);
  }

  public void testIsStudioHelp() {
    assertTrue(myHelpManager instanceof StudioHelpManagerImpl);
  }

  public void testStudioHelpPrefix() {
    ((StudioHelpManagerImpl)myHelpManager).setBrowser(myMockBrowser);
    myHelpManager.invokeHelp(STUDIO_HELP_PREFIX + "test");
    verify(myMockBrowser).browse(STUDIO_HELP_URL + "test");
  }

  public void testStudioHelpSpecialCase() {
    ((StudioHelpManagerImpl)myHelpManager).setBrowser(myMockBrowser);
    myHelpManager.invokeHelp("reference.dialogs.rundebug.Android");
    verify(myMockBrowser).browse(STUDIO_HELP_URL + "/r/studio-ui/rundebugconfig.html");
  }

  public void testStudioHelpRedirectIntellijHelp() {
    StudioHelpManagerImpl studioHelpManager = (StudioHelpManagerImpl)myHelpManager;
    studioHelpManager.setBrowser(myMockBrowser);
    myHelpManager.invokeHelp("test.intellij.helpid");
    verify(myMockBrowser).browse(STUDIO_HELP_URL + REDIRECT_URL_EXTENSION + studioHelpManager.getVersion() + "/?test.intellij.helpid");
  }
}
