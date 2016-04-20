/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.assistant.datamodel;

import java.util.List;

/**
 * A tutorial consists of a relatively small number of instructions to
 * achieve a targeted task. It is treated as a single view hanging off of a
 * parent feature.
 */
public interface TutorialData {
  String getLabel();

  String getDescription();

  String getRemoteLink();

  String getRemoteLinkLabel();

  String getKey();

  List<? extends StepData> getSteps();
}
