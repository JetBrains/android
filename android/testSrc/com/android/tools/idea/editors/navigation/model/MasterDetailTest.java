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
package com.android.tools.idea.editors.navigation.model;

public class MasterDetailTest extends NavigationEditorTest {

  @Override
  protected String getPath() {
    return "/projects/navigationEditor/masterDetail/";
  }

  public void testTransitionDerivationForDefaultDevice() throws Exception {
    assertDerivationCounts("raw", 2, 1);
  }

  /* When a master-detail app like simplemail runs on a tablet, there is one less transition in landscape. */
  public void testTransitionDerivationForTabletInLandscape() throws Exception {
    assertDerivationCounts("raw-sw600dp-land", 1, 0);
  }

}
