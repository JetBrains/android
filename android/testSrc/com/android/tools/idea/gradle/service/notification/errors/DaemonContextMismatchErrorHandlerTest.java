/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification.errors;

import junit.framework.TestCase;

/**
 * Tests for {@link com.android.tools.idea.gradle.service.notification.errors.DaemonContextMismatchErrorHandler}.
 */
public class DaemonContextMismatchErrorHandlerTest extends TestCase {
  public void testHandleErrorWithErrorFromBugReport() {
    String error = "The newly created daemon process has a different context than expected.\n" +
                   "It won't be possible to reconnect to this daemon. Context mismatch: \n" +
                   "Java home is different.\n" +
                   "javaHome=c:\\Program Files\\Java\\jdk,daemonRegistryDir=C:\\Users\\user.name\\.gradle\\daemon,pid=7868,idleTimeout=null]\n" +
                   "javaHome=C:\\Program Files\\Java\\jdk\\jre,daemonRegistryDir=C:\\Users\\user.name\\.gradle\\daemon,pid=4792,idleTimeout=10800000]";
    String s = DaemonContextMismatchErrorHandler.parseExpectedAndActualJavaHomes(error);
    assertEquals("Expecting: 'c:\\Program Files\\Java\\jdk' but was: 'C:\\Program Files\\Java\\jdk\\jre'.", s);
  }

  public void testHandleErrorWithErrorFromGradleForum() {
    String error = "The newly created daemon process has a different context than expected.\n" +
                   "It won't be possible to reconnect to this daemon. Context mismatch: \n" +
                   "Java home is different.\n" +
                   "Wanted: DefaultDaemonContext[uid=null,javaHome=/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home,daemonRegistryDir=/Users/Nikem/.gradle/daemon,pid=555]\n" +
                   "Actual: DefaultDaemonContext[uid=0f3a0315-c1e6-44d6-962d-9a604d59a158,javaHome=/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home/jre,daemonRegistryDir=/Users/Nikem/.gradle/daemon,pid=568]";
    String s = DaemonContextMismatchErrorHandler.parseExpectedAndActualJavaHomes(error);
    assertEquals("Expecting: '/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home' but was: '/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home/jre'.", s);
  }
}
