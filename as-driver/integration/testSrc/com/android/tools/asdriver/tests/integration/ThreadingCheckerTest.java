/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.tests.integration;

import static org.junit.Assert.fail;

import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidStudioInstallation;
import com.android.tools.asdriver.tests.AndroidSystem;
import com.android.tools.asdriver.tests.Display;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class ThreadingCheckerTest {
  public final AndroidSystem androidSystem = AndroidSystem.standard();
  public final ExpectedException thrown = ExpectedException.none();

  @Rule
  public RuleChain chain = RuleChain.outerRule(thrown).around(androidSystem);

  /**
   * Verifies that threading errors triggered by the threading agent will cause
   * the AndroidSystem test rule to throw an exception.
   * */
  @Test
  public void verifiesThreadingProblemsTest() throws Exception {
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("One or more methods called on a wrong thread");

    AndroidStudioInstallation installation = androidSystem.getInstallation();
    installation.addVmOption("-Didea.is.internal=true");
    installation.addVmOption("-Didea.log.debug.categories=#com.android.tools.idea.instrumentation.threading.ThreadingChecker");

    try (Display display = Display.createDefault();
         AndroidStudio studio = installation.run(display)) {
      installation.getIdeaLog()
        .waitForMatchingLine(".*ThreadingChecker listener has been installed.*", 10, TimeUnit.SECONDS);

      // Call an action that triggers a threading violation
      studio.executeAction("ThreadingChecker.ForceViolation");
    } catch (Exception e) {
      fail("No exceptions should be thrown by the test body itself, but there was an exception:\n" + e);
    }
  }
}
