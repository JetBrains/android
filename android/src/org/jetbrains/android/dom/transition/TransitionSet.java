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
package org.jetbrains.android.dom.transition;

import com.intellij.util.xml.DefinesXml;
import com.intellij.util.xml.SubTagList;
import org.jetbrains.android.dom.Styleable;

import java.util.List;

/**
 * Relevant code in the framework: TransitionInflater#createTransitionsFromXml
 */
@DefinesXml
@Styleable({"TransitionSet", "Transition"})
public interface TransitionSet extends Transition {
  @SubTagList("transitionSet")
  List<TransitionSet> getTransitionSets();

  @SubTagList("fade")
  List<Fade> getFades();

  @SubTagList("changeBounds")
  List<ChangeBounds> getChangeBounds();

  @SubTagList("slide")
  List<Slide> getSlides();

  @SubTagList("explode")
  List<Explode> getExplodes();

  @SubTagList("changeImageTransform")
  List<ChangeImageTransform> getChangeImageTransforms();

  @SubTagList("changeTransform")
  List<ChangeTransform> getChangeTransforms();

  @SubTagList("changeClipBounds")
  List<ChangeClipBounds> getChangeClipBounds();

  @SubTagList("autoTransition")
  List<AutoTransition> getAutoTransitions();

  @SubTagList("recolor")
  List<Recolor> getRecolors();

  @SubTagList("changeScroll")
  List<ChangeScroll> getChangeScrolls();

  @SubTagList("arcMotion")
  List<ArcMotion> getArcMotions();

  @SubTagList("pathMotion")
  List<PathMotion> getPathMotions();

  @SubTagList("patternPathMotion")
  List<PatternPathMotion> getPatternPathMotions();

  @SubTagList("transition")
  List<TransitionSetTransition> getTransitions();
}
