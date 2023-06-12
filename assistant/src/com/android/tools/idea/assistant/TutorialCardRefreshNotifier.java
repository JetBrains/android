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
package com.android.tools.idea.assistant;

import com.intellij.util.messages.Topic;

/**
 * Notifies the underlying TutorialCard to do a refresh. Allows for the {@link com.android.tools.idea.assistant.datamodel.TutorialData}
 * content to be dynamic by changing what's in the TutorialData's list of steps
 */
public interface TutorialCardRefreshNotifier {

  Topic<TutorialCardRefreshNotifier> TUTORIAL_CARD_TOPIC = Topic.create("Refresh tutorial card", TutorialCardRefreshNotifier.class);

  /**
   * Called when there is a signal to do a refresh of the tutorial card.
   * Pass in a step number to default the scroll position to.
   * If the tutorialStepNumber <= 0 || tutorialStepNumber > the number TutorialStep(s), it will set the view to the top of the card.
   */
  void stateUpdated(int tutorialStepNumberToMakeVisible);
}
