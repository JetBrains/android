/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.idea.blaze.android.targetmapbuilder;

import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;

/**
 * Base implementation of NbTargetBuilder for utility classes which wrap an instance of {@link
 * TargetIdeInfo.Builder}.
 */
abstract class NbBaseTargetBuilder implements NbTargetBuilder {
  protected final BlazeInfoData blazeInfoData;

  NbBaseTargetBuilder(BlazeInfoData blazeInfoData) {
    this.blazeInfoData = blazeInfoData;
  }

  abstract TargetIdeInfo.Builder getIdeInfoBuilder();

  @Override
  public TargetIdeInfo build() {
    return getIdeInfoBuilder().build();
  }

  public BlazeInfoData getBlazeInfoData() {
    return blazeInfoData;
  }
}
