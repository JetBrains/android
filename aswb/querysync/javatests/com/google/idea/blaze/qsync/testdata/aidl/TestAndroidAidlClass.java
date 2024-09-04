/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync.testdata.aidl;

import android.os.RemoteException;

public class TestAndroidAidlClass {
  private final TestAidlService testAidlService;

  public TestAndroidAidlClass(TestAidlService testAidlService) {
    this.testAidlService = testAidlService;
  }

  public int sayHello() throws RemoteException {
    return testAidlService.sayHello(0);
  }
}
