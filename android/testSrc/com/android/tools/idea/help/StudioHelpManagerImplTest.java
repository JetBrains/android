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

import static com.android.tools.idea.help.StudioHelpManagerImpl.Browser;
import static com.android.tools.idea.help.StudioHelpManagerImpl.REDIRECT_URL_EXTENSION;
import static com.android.tools.idea.help.StudioHelpManagerImpl.STUDIO_HELP_URL;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.intellij.idea.Bombed;
import com.intellij.openapi.help.HelpManager;
import org.jetbrains.android.AndroidTestCase;
import java.util.Calendar;

@Bombed(year = 2022, month = Calendar.JULY, day = 1,
  user = "andrei.kuznetsov", description = "This test is for AndroidStudio specific components, " +
                                           "it shall not pass (c) in IDEA, and should be moved to " +
                                           "adt.branding module. Unfortunately, at the moment some shared " +
                                           "(between AS and IDEA) modules depend on adt.branding, so " +
                                           "(in addition to the risk that IDEA may use classes that are " +
                                           "not good for IDEA) adt.branding may not depend on android.core " +
                                           "due to cyclic dependencies which will appear.")
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

  public void testStudioHelpRedirectIntellijHelp() {
    StudioHelpManagerImpl studioHelpManager = (StudioHelpManagerImpl)myHelpManager;
    studioHelpManager.setBrowser(myMockBrowser);
    myHelpManager.invokeHelp("test.intellij.helpid");
    verify(myMockBrowser).browse(STUDIO_HELP_URL + REDIRECT_URL_EXTENSION + studioHelpManager.getVersion() + "/?test.intellij.helpid");
  }
}
