/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BARRIER_DIRECTION;
import static com.android.SdkConstants.ATTR_GUIDELINE_ORIENTATION_HORIZONTAL;
import static com.android.SdkConstants.ATTR_GUIDELINE_ORIENTATION_VERTICAL;
import static com.android.SdkConstants.ATTR_LAYOUT_BASELINE_CREATOR;
import static com.android.SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_CREATOR;
import static com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_DIMENSION_RATIO;
import static com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X;
import static com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y;
import static com.android.SdkConstants.ATTR_LAYOUT_END_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_END_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_GONE_MARGIN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_GONE_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_GONE_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_GONE_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_GONE_MARGIN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_GONE_MARGIN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT_DEFAULT;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT_MAX;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT_MIN;
import static com.android.SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS;
import static com.android.SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE;
import static com.android.SdkConstants.ATTR_LAYOUT_HORIZONTAL_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_LEFT_CREATOR;
import static com.android.SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_MAX_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MAX_WIDTH;
import static com.android.SdkConstants.ATTR_LAYOUT_MIN_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MIN_WIDTH;
import static com.android.SdkConstants.ATTR_LAYOUT_RIGHT_CREATOR;
import static com.android.SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_START_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_START_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TOP_CREATOR;
import static com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS;
import static com.android.SdkConstants.ATTR_LAYOUT_VERTICAL_CHAIN_STYLE;
import static com.android.SdkConstants.ATTR_LAYOUT_VERTICAL_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH_DEFAULT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH_MAX;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH_MIN;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.CONSTRAINT_BARRIER_BOTTOM;
import static com.android.SdkConstants.CONSTRAINT_BARRIER_END;
import static com.android.SdkConstants.CONSTRAINT_BARRIER_LEFT;
import static com.android.SdkConstants.CONSTRAINT_BARRIER_RIGHT;
import static com.android.SdkConstants.CONSTRAINT_BARRIER_START;
import static com.android.SdkConstants.CONSTRAINT_BARRIER_TOP;
import static com.android.SdkConstants.CONSTRAINT_REFERENCED_IDS;
import static com.android.SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN;
import static com.android.SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END;
import static com.android.SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.PREFIX_ANDROID;
import static com.android.SdkConstants.PREFIX_APP;
import static com.android.SdkConstants.SHERPA_URI;
import static com.android.SdkConstants.TAG_INCLUDE;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_MATCH_CONSTRAINT;
import static com.android.SdkConstants.VALUE_N_DP;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;
import static com.android.SdkConstants.VALUE_ZERO_DP;
import static com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawGuidelineCycle.BEGIN;
import static com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawGuidelineCycle.END;
import static com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawGuidelineCycle.PERCENT;

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.android.ide.common.gradle.Version;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlDependencyManager;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintAnchorTarget;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import com.android.tools.idea.uibuilder.scout.Direction;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Encapsulate basic querys on a ConstraintLayout component
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public final class ConstraintComponentUtilities {

  public static final HashMap<String, String> ourReciprocalAttributes;
  public static final Map<String, String> ourOtherSideAttributes;
  public static final HashMap<String, String> ourMapMarginAttributes;
  private static final HashMap<String, AnchorTarget.Type> ourMapSideToOriginAnchors;
  public static final ArrayList<String> ourLeftAttributes;
  public static final ArrayList<String> ourTopAttributes;
  public static final ArrayList<String> ourRightAttributes;
  public static final ArrayList<String> ourBottomAttributes;
  public static final ArrayList<String> ourStartAttributes;
  public static final ArrayList<String> ourEndAttributes;
  public static final ArrayList<String> ourBaselineAttributes;
  private static final ArrayList<String> ourHorizontalAttributes;
  private static final ArrayList<String> ourVerticalAttributes;

  public static final HashMap<String, String> ourLayoutUriToPrefix = new HashMap<>();

  static {
    ourLayoutUriToPrefix.put(ANDROID_URI, PREFIX_ANDROID);
    ourLayoutUriToPrefix.put(SHERPA_URI, PREFIX_APP);
  }

  public static final ArrayList<Pair<String, String>> ourLayoutAttributes = new ArrayList<>();

  static {
    ourLayoutAttributes.add(Pair.of(ANDROID_URI, ATTR_LAYOUT_WIDTH));
    ourLayoutAttributes.add(Pair.of(ANDROID_URI, ATTR_LAYOUT_HEIGHT));
    ourLayoutAttributes.add(Pair.of(ANDROID_URI, ATTR_LAYOUT_MARGIN_START));
    ourLayoutAttributes.add(Pair.of(ANDROID_URI, ATTR_LAYOUT_MARGIN_END));
    ourLayoutAttributes.add(Pair.of(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT));
    ourLayoutAttributes.add(Pair.of(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT));
    ourLayoutAttributes.add(Pair.of(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP));
    ourLayoutAttributes.add(Pair.of(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_END_TO_START_OF));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_END_TO_END_OF));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_START_TO_START_OF));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_START_TO_END_OF));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_LEFT_TO_LEFT_OF));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_LEFT_TO_RIGHT_OF));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_RIGHT_TO_LEFT_OF));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_TOP_TO_TOP_OF));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_TOP_TO_BOTTOM_OF));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_BOTTOM_TO_TOP_OF));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_VERTICAL_BIAS));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_VERTICAL_CHAIN_STYLE));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_WEIGHT));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_VERTICAL_WEIGHT));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_DIMENSION_RATIO));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_GUIDELINE_ORIENTATION_HORIZONTAL));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_GUIDELINE_ORIENTATION_VERTICAL));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_GONE_MARGIN_START));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_GONE_MARGIN_END));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_GONE_MARGIN_LEFT));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_GONE_MARGIN_RIGHT));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_GONE_MARGIN_TOP));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_GONE_MARGIN_BOTTOM));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_WIDTH_DEFAULT));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_HEIGHT_DEFAULT));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_MIN_WIDTH));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_MIN_HEIGHT));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_MAX_WIDTH));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_MAX_HEIGHT));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, ATTR_LAYOUT_MIN_WIDTH));
    ourLayoutAttributes.add(Pair.of(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X));
    ourLayoutAttributes.add(Pair.of(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, LAYOUT_CONSTRAINT_GUIDE_BEGIN));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, LAYOUT_CONSTRAINT_GUIDE_END));
    ourLayoutAttributes.add(Pair.of(SHERPA_URI, LAYOUT_CONSTRAINT_GUIDE_PERCENT));
  }

  private static final HashMap<Pair<AnchorTarget.Type, AnchorTarget.Type>, String> ourConstraintAttributes = new HashMap<>();

  static {
    ourConstraintAttributes.put(Pair.of(AnchorTarget.Type.LEFT, AnchorTarget.Type.LEFT), ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourConstraintAttributes.put(Pair.of(AnchorTarget.Type.LEFT, AnchorTarget.Type.RIGHT), ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    ourConstraintAttributes.put(Pair.of(AnchorTarget.Type.RIGHT, AnchorTarget.Type.LEFT), ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourConstraintAttributes.put(Pair.of(AnchorTarget.Type.RIGHT, AnchorTarget.Type.RIGHT), ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);
    ourConstraintAttributes.put(Pair.of(AnchorTarget.Type.TOP, AnchorTarget.Type.TOP), ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourConstraintAttributes.put(Pair.of(AnchorTarget.Type.TOP, AnchorTarget.Type.BOTTOM), ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    ourConstraintAttributes.put(Pair.of(AnchorTarget.Type.BOTTOM, AnchorTarget.Type.TOP), ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    ourConstraintAttributes.put(Pair.of(AnchorTarget.Type.BOTTOM, AnchorTarget.Type.BOTTOM), ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
  }

  private static final HashMap<String, String> ourLTRConvertedAttributes = new HashMap<>();
  private static final HashMap<Pair<AnchorTarget.Type, AnchorTarget.Type>, String> ourLTRConstraintAnchorsToMargin = new HashMap<>();
  public static final HashMap<String, AnchorTarget.Type> ourLTRMapSideToTargetAnchors = new HashMap<>();

  static {
    ourLTRConvertedAttributes.put(ATTR_LAYOUT_LEFT_TO_LEFT_OF, ATTR_LAYOUT_START_TO_START_OF);
    ourLTRConvertedAttributes.put(ATTR_LAYOUT_LEFT_TO_RIGHT_OF, ATTR_LAYOUT_START_TO_END_OF);
    ourLTRConvertedAttributes.put(ATTR_LAYOUT_RIGHT_TO_LEFT_OF, ATTR_LAYOUT_END_TO_START_OF);
    ourLTRConvertedAttributes.put(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, ATTR_LAYOUT_END_TO_END_OF);
    ourLTRConstraintAnchorsToMargin.put(Pair.of(AnchorTarget.Type.LEFT, AnchorTarget.Type.LEFT), ATTR_LAYOUT_MARGIN_START);
    ourLTRConstraintAnchorsToMargin.put(Pair.of(AnchorTarget.Type.LEFT, AnchorTarget.Type.RIGHT), ATTR_LAYOUT_MARGIN_START);
    ourLTRConstraintAnchorsToMargin.put(Pair.of(AnchorTarget.Type.RIGHT, AnchorTarget.Type.LEFT), ATTR_LAYOUT_MARGIN_END);
    ourLTRConstraintAnchorsToMargin.put(Pair.of(AnchorTarget.Type.RIGHT, AnchorTarget.Type.RIGHT), ATTR_LAYOUT_MARGIN_END);
    ourLTRMapSideToTargetAnchors.put(ATTR_LAYOUT_LEFT_TO_LEFT_OF, AnchorTarget.Type.LEFT);
    ourLTRMapSideToTargetAnchors.put(ATTR_LAYOUT_LEFT_TO_RIGHT_OF, AnchorTarget.Type.RIGHT);
    ourLTRMapSideToTargetAnchors.put(ATTR_LAYOUT_RIGHT_TO_LEFT_OF, AnchorTarget.Type.LEFT);
    ourLTRMapSideToTargetAnchors.put(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, AnchorTarget.Type.RIGHT);
    ourLTRMapSideToTargetAnchors.put(ATTR_LAYOUT_TOP_TO_TOP_OF, AnchorTarget.Type.TOP);
    ourLTRMapSideToTargetAnchors.put(ATTR_LAYOUT_TOP_TO_BOTTOM_OF, AnchorTarget.Type.BOTTOM);
    ourLTRMapSideToTargetAnchors.put(ATTR_LAYOUT_BOTTOM_TO_TOP_OF, AnchorTarget.Type.TOP);
    ourLTRMapSideToTargetAnchors.put(ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, AnchorTarget.Type.BOTTOM);
    ourLTRMapSideToTargetAnchors.put(ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, AnchorTarget.Type.BASELINE);
    ourLTRMapSideToTargetAnchors.put(ATTR_LAYOUT_START_TO_START_OF, AnchorTarget.Type.LEFT);
    ourLTRMapSideToTargetAnchors.put(ATTR_LAYOUT_START_TO_END_OF, AnchorTarget.Type.RIGHT);
    ourLTRMapSideToTargetAnchors.put(ATTR_LAYOUT_END_TO_START_OF, AnchorTarget.Type.LEFT);
    ourLTRMapSideToTargetAnchors.put(ATTR_LAYOUT_END_TO_END_OF, AnchorTarget.Type.RIGHT);
  }

  private static final HashMap<String, String> ourRTLConvertedAttributes = new HashMap<>();
  private static final HashMap<Pair<AnchorTarget.Type, AnchorTarget.Type>, String> ourRTLConstraintAnchorsToMargin = new HashMap<>();
  public static final HashMap<String, AnchorTarget.Type> ourRTLMapSideToTargetAnchors = new HashMap<>();

  static {
    ourRTLConvertedAttributes.put(ATTR_LAYOUT_LEFT_TO_LEFT_OF, ATTR_LAYOUT_END_TO_END_OF);
    ourRTLConvertedAttributes.put(ATTR_LAYOUT_LEFT_TO_RIGHT_OF, ATTR_LAYOUT_END_TO_START_OF);
    ourRTLConvertedAttributes.put(ATTR_LAYOUT_RIGHT_TO_LEFT_OF, ATTR_LAYOUT_START_TO_END_OF);
    ourRTLConvertedAttributes.put(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, ATTR_LAYOUT_START_TO_START_OF);
    ourRTLConstraintAnchorsToMargin.put(Pair.of(AnchorTarget.Type.LEFT, AnchorTarget.Type.LEFT), ATTR_LAYOUT_MARGIN_END);
    ourRTLConstraintAnchorsToMargin.put(Pair.of(AnchorTarget.Type.LEFT, AnchorTarget.Type.RIGHT), ATTR_LAYOUT_MARGIN_END);
    ourRTLConstraintAnchorsToMargin.put(Pair.of(AnchorTarget.Type.RIGHT, AnchorTarget.Type.LEFT), ATTR_LAYOUT_MARGIN_START);
    ourRTLConstraintAnchorsToMargin.put(Pair.of(AnchorTarget.Type.RIGHT, AnchorTarget.Type.RIGHT), ATTR_LAYOUT_MARGIN_START);
    ourRTLMapSideToTargetAnchors.put(ATTR_LAYOUT_LEFT_TO_LEFT_OF, AnchorTarget.Type.LEFT);
    ourRTLMapSideToTargetAnchors.put(ATTR_LAYOUT_LEFT_TO_RIGHT_OF, AnchorTarget.Type.RIGHT);
    ourRTLMapSideToTargetAnchors.put(ATTR_LAYOUT_RIGHT_TO_LEFT_OF, AnchorTarget.Type.LEFT);
    ourRTLMapSideToTargetAnchors.put(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, AnchorTarget.Type.RIGHT);
    ourRTLMapSideToTargetAnchors.put(ATTR_LAYOUT_TOP_TO_TOP_OF, AnchorTarget.Type.TOP);
    ourRTLMapSideToTargetAnchors.put(ATTR_LAYOUT_TOP_TO_BOTTOM_OF, AnchorTarget.Type.BOTTOM);
    ourRTLMapSideToTargetAnchors.put(ATTR_LAYOUT_BOTTOM_TO_TOP_OF, AnchorTarget.Type.TOP);
    ourRTLMapSideToTargetAnchors.put(ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, AnchorTarget.Type.BOTTOM);
    ourRTLMapSideToTargetAnchors.put(ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, AnchorTarget.Type.BASELINE);
    ourRTLMapSideToTargetAnchors.put(ATTR_LAYOUT_START_TO_START_OF, AnchorTarget.Type.RIGHT);
    ourRTLMapSideToTargetAnchors.put(ATTR_LAYOUT_START_TO_END_OF, AnchorTarget.Type.LEFT);
    ourRTLMapSideToTargetAnchors.put(ATTR_LAYOUT_END_TO_START_OF, AnchorTarget.Type.RIGHT);
    ourRTLMapSideToTargetAnchors.put(ATTR_LAYOUT_END_TO_END_OF, AnchorTarget.Type.LEFT);
  }

  public static final HashMap<AnchorTarget.Type, Pair<String, String>> ourPotentialAttributes = new HashMap<>();

  static {
    ourPotentialAttributes.put(AnchorTarget.Type.LEFT, Pair.of(ATTR_LAYOUT_LEFT_TO_LEFT_OF, ATTR_LAYOUT_LEFT_TO_RIGHT_OF));
    ourPotentialAttributes.put(AnchorTarget.Type.RIGHT, Pair.of(ATTR_LAYOUT_RIGHT_TO_LEFT_OF, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF));
    ourPotentialAttributes.put(AnchorTarget.Type.TOP, Pair.of(ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_LAYOUT_TOP_TO_BOTTOM_OF));
    ourPotentialAttributes.put(AnchorTarget.Type.BOTTOM, Pair.of(ATTR_LAYOUT_BOTTOM_TO_TOP_OF, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF));
  }

  public static final HashMap<AnchorTarget.Type, Pair<String, String>> ourPotentialLTRAttributes = new HashMap<>();

  static {
    ourPotentialLTRAttributes.put(AnchorTarget.Type.LEFT, Pair.of(ATTR_LAYOUT_START_TO_START_OF, ATTR_LAYOUT_START_TO_END_OF));
    ourPotentialLTRAttributes.put(AnchorTarget.Type.RIGHT, Pair.of(ATTR_LAYOUT_END_TO_START_OF, ATTR_LAYOUT_END_TO_END_OF));
  }

  public static final HashMap<AnchorTarget.Type, Pair<String, String>> ourPotentialRTLAttributes = new HashMap<>();

  static {
    ourPotentialRTLAttributes.put(AnchorTarget.Type.LEFT, Pair.of(ATTR_LAYOUT_END_TO_END_OF, ATTR_LAYOUT_END_TO_START_OF));
    ourPotentialRTLAttributes.put(AnchorTarget.Type.RIGHT, Pair.of(ATTR_LAYOUT_START_TO_END_OF, ATTR_LAYOUT_START_TO_START_OF));
  }

  public static final String[] ourConstraintLayoutAttributesToClear = {
    ATTR_LAYOUT_LEFT_TO_LEFT_OF,
    ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
    ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
    ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
    ATTR_LAYOUT_TOP_TO_TOP_OF,
    ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
    ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
    ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
    ATTR_LAYOUT_BASELINE_TO_BASELINE_OF,
    ATTR_LAYOUT_START_TO_END_OF,
    ATTR_LAYOUT_START_TO_START_OF,
    ATTR_LAYOUT_END_TO_START_OF,
    ATTR_LAYOUT_END_TO_END_OF,
    ATTR_LAYOUT_GONE_MARGIN_LEFT,
    ATTR_LAYOUT_GONE_MARGIN_TOP,
    ATTR_LAYOUT_GONE_MARGIN_RIGHT,
    ATTR_LAYOUT_GONE_MARGIN_BOTTOM,
    ATTR_LAYOUT_GONE_MARGIN_START,
    ATTR_LAYOUT_GONE_MARGIN_END,
    ATTR_LAYOUT_HORIZONTAL_BIAS,
    ATTR_LAYOUT_VERTICAL_BIAS,
    ATTR_LAYOUT_WIDTH_DEFAULT,
    ATTR_LAYOUT_HEIGHT_DEFAULT,
    ATTR_LAYOUT_WIDTH_MIN,
    ATTR_LAYOUT_WIDTH_MAX,
    ATTR_LAYOUT_HEIGHT_MIN,
    ATTR_LAYOUT_HEIGHT_MAX,
    ATTR_LAYOUT_LEFT_CREATOR,
    ATTR_LAYOUT_TOP_CREATOR,
    ATTR_LAYOUT_RIGHT_CREATOR,
    ATTR_LAYOUT_BOTTOM_CREATOR,
    ATTR_LAYOUT_BASELINE_CREATOR,
    ATTR_LAYOUT_DIMENSION_RATIO,
    ATTR_LAYOUT_HORIZONTAL_WEIGHT,
    ATTR_LAYOUT_VERTICAL_WEIGHT,
    ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE,
    ATTR_LAYOUT_VERTICAL_CHAIN_STYLE,
  };
  private static final String[] ourLayoutAttributesToClear = {
    ATTR_LAYOUT_MARGIN,
    ATTR_LAYOUT_MARGIN_LEFT,
    ATTR_LAYOUT_MARGIN_START,
    ATTR_LAYOUT_MARGIN_RIGHT,
    ATTR_LAYOUT_MARGIN_END,
    ATTR_LAYOUT_MARGIN_TOP,
    ATTR_LAYOUT_MARGIN_BOTTOM,
    ATTR_LAYOUT_MARGIN_START,
  };

  static {
    ourReciprocalAttributes = new HashMap<>();
    ourReciprocalAttributes.put(ATTR_LAYOUT_LEFT_TO_LEFT_OF, ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_LEFT_TO_RIGHT_OF, ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_RIGHT_TO_LEFT_OF, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_TOP_TO_BOTTOM_OF, ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_BOTTOM_TO_TOP_OF, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_START_TO_START_OF, ATTR_LAYOUT_START_TO_END_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_START_TO_END_OF, ATTR_LAYOUT_START_TO_START_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_END_TO_START_OF, ATTR_LAYOUT_END_TO_END_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_END_TO_END_OF, ATTR_LAYOUT_END_TO_START_OF);

    ourOtherSideAttributes = new ImmutableMap.Builder()
      .put(ATTR_LAYOUT_LEFT_TO_LEFT_OF, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF)
      .put(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, ATTR_LAYOUT_LEFT_TO_LEFT_OF)
      .put(ATTR_LAYOUT_END_TO_END_OF, ATTR_LAYOUT_START_TO_START_OF)
      .put(ATTR_LAYOUT_START_TO_START_OF, ATTR_LAYOUT_END_TO_END_OF)
      .put(ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF)
      .put(ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, ATTR_LAYOUT_TOP_TO_TOP_OF)
      .build();

    ourMapMarginAttributes = new HashMap<>();
    ourMapMarginAttributes.put(ATTR_LAYOUT_LEFT_TO_LEFT_OF, ATTR_LAYOUT_MARGIN_LEFT);
    ourMapMarginAttributes.put(ATTR_LAYOUT_LEFT_TO_RIGHT_OF, ATTR_LAYOUT_MARGIN_LEFT);
    ourMapMarginAttributes.put(ATTR_LAYOUT_RIGHT_TO_LEFT_OF, ATTR_LAYOUT_MARGIN_RIGHT);
    ourMapMarginAttributes.put(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, ATTR_LAYOUT_MARGIN_RIGHT);
    ourMapMarginAttributes.put(ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_LAYOUT_MARGIN_TOP);
    ourMapMarginAttributes.put(ATTR_LAYOUT_TOP_TO_BOTTOM_OF, ATTR_LAYOUT_MARGIN_TOP);
    ourMapMarginAttributes.put(ATTR_LAYOUT_BOTTOM_TO_TOP_OF, ATTR_LAYOUT_MARGIN_BOTTOM);
    ourMapMarginAttributes.put(ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, ATTR_LAYOUT_MARGIN_BOTTOM);
    ourMapMarginAttributes.put(ATTR_LAYOUT_START_TO_START_OF, ATTR_LAYOUT_MARGIN_START);
    ourMapMarginAttributes.put(ATTR_LAYOUT_START_TO_END_OF, ATTR_LAYOUT_MARGIN_START);
    ourMapMarginAttributes.put(ATTR_LAYOUT_END_TO_START_OF, ATTR_LAYOUT_MARGIN_END);
    ourMapMarginAttributes.put(ATTR_LAYOUT_END_TO_END_OF, ATTR_LAYOUT_MARGIN_END);

    ourMapSideToOriginAnchors = new HashMap<>();
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_LEFT_TO_LEFT_OF, AnchorTarget.Type.LEFT);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_LEFT_TO_RIGHT_OF, AnchorTarget.Type.LEFT);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_RIGHT_TO_LEFT_OF, AnchorTarget.Type.RIGHT);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, AnchorTarget.Type.RIGHT);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_TOP_TO_TOP_OF, AnchorTarget.Type.TOP);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_TOP_TO_BOTTOM_OF, AnchorTarget.Type.TOP);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_BOTTOM_TO_TOP_OF, AnchorTarget.Type.BOTTOM);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, AnchorTarget.Type.BOTTOM);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, AnchorTarget.Type.BASELINE);

    ourLeftAttributes = new ArrayList<>();
    ourLeftAttributes.add(ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourLeftAttributes.add(ATTR_LAYOUT_LEFT_TO_RIGHT_OF);

    ourTopAttributes = new ArrayList<>();
    ourTopAttributes.add(ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourTopAttributes.add(ATTR_LAYOUT_TOP_TO_BOTTOM_OF);

    ourRightAttributes = new ArrayList<>();
    ourRightAttributes.add(ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourRightAttributes.add(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);

    ourBottomAttributes = new ArrayList<>();
    ourBottomAttributes.add(ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    ourBottomAttributes.add(ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);

    ourBaselineAttributes = new ArrayList<>();
    ourBaselineAttributes.add(ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);

    ourStartAttributes = new ArrayList<>();
    ourStartAttributes.add(ATTR_LAYOUT_START_TO_START_OF);
    ourStartAttributes.add(ATTR_LAYOUT_START_TO_END_OF);

    ourEndAttributes = new ArrayList<>();
    ourEndAttributes.add(ATTR_LAYOUT_END_TO_START_OF);
    ourEndAttributes.add(ATTR_LAYOUT_END_TO_END_OF);

    ourHorizontalAttributes = new ArrayList<>();
    ourHorizontalAttributes.add(ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourHorizontalAttributes.add(ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    ourHorizontalAttributes.add(ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourHorizontalAttributes.add(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);
    ourHorizontalAttributes.add(ATTR_LAYOUT_START_TO_START_OF);
    ourHorizontalAttributes.add(ATTR_LAYOUT_START_TO_END_OF);
    ourHorizontalAttributes.add(ATTR_LAYOUT_END_TO_START_OF);
    ourHorizontalAttributes.add(ATTR_LAYOUT_END_TO_END_OF);

    ourVerticalAttributes = new ArrayList<>();
    ourVerticalAttributes.add(ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourVerticalAttributes.add(ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    ourVerticalAttributes.add(ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    ourVerticalAttributes.add(ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
    ourVerticalAttributes.add(ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);
  }

  /**
   * Returns the attribute for the given source and destination anchor
   *
   * @param source           the source anchor
   * @param destination      the destination anchor
   * @param useRtlAttributes if true, we should use start/end
   * @param isRtl            if true, we are in RTL, otherwise in LTR
   * @return the corresponding attribute
   */
  public static String getAttribute(AnchorTarget.Type source, AnchorTarget.Type destination, boolean useRtlAttributes, boolean isRtl) {
    if (source == AnchorTarget.Type.BASELINE && destination == AnchorTarget.Type.BASELINE) {
      return ATTR_LAYOUT_BASELINE_TO_BASELINE_OF;
    }
    String attribute = ourConstraintAttributes.get(Pair.of(source, destination));
    if (useRtlAttributes) {
      String converted;
      if (isRtl) {
        converted = ourRTLConvertedAttributes.get(attribute);
      }
      else {
        converted = ourLTRConvertedAttributes.get(attribute);
      }
      if (converted != null) {
        attribute = converted;
      }
    }
    return attribute;
  }

  /**
   * Clear the attribute of the given anchor type
   *
   * @param type             type of the anchor
   * @param transaction      attributes transaction
   * @param useRtlAttributes if true, we should use start/end
   * @param isRtl            if true, we are in RTL, otherwise in LTR
   */
  public static void clearAnchor(AnchorTarget.Type type, NlAttributesHolder transaction, boolean useRtlAttributes, boolean isRtl) {
    switch (type) {
      case LEFT: {
        clearAttributes(SHERPA_URI, ourPotentialAttributes.get(AnchorTarget.Type.LEFT), transaction);
        if (useRtlAttributes) {
          if (isRtl) {
            clearAttributes(SHERPA_URI, ourPotentialRTLAttributes.get(AnchorTarget.Type.LEFT), transaction);
          }
          else {
            clearAttributes(SHERPA_URI, ourPotentialLTRAttributes.get(AnchorTarget.Type.LEFT), transaction);
          }
        }
      }
      break;
      case RIGHT: {
        clearAttributes(SHERPA_URI, ourPotentialAttributes.get(AnchorTarget.Type.RIGHT), transaction);
        if (useRtlAttributes) {
          if (isRtl) {
            clearAttributes(SHERPA_URI, ourPotentialRTLAttributes.get(AnchorTarget.Type.RIGHT), transaction);
          }
          else {
            clearAttributes(SHERPA_URI, ourPotentialLTRAttributes.get(AnchorTarget.Type.RIGHT), transaction);
          }
        }
      }
      break;
      case TOP: {
        clearAttributes(SHERPA_URI, ourTopAttributes, transaction);
      }
      break;
      case BOTTOM: {
        clearAttributes(SHERPA_URI, ourBottomAttributes, transaction);
      }
      break;
      case BASELINE: {
        transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, null);
      }
    }
  }

  /**
   * is the given anchor type connected for this component
   *
   * @param type             type of the anchor
   * @param component        the component of the anchor
   * @param useRtlAttributes if true, we should use start/end
   * @param isRtl            if true, we are in RTL, otherwise in LTR
   * @return true if the component has an attribute indicated it's connected
   */
  public static boolean isAnchorConnected(AnchorTarget.Type type, NlComponent component, boolean useRtlAttributes, boolean isRtl) {
    if (type == AnchorTarget.Type.BASELINE) {
      return component.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF) != null;
    }
    boolean isConnected = false;
    Pair pair = ourPotentialAttributes.get(type);
    if (pair != null) {
      // noinspection ConstantConditions
      isConnected |= component.getLiveAttribute(SHERPA_URI, (String)pair.getFirst()) != null;
      isConnected |= component.getLiveAttribute(SHERPA_URI, (String)pair.getSecond()) != null;
    }
    if (useRtlAttributes) {
      if (isRtl) {
        pair = ourPotentialRTLAttributes.get(type);
        if (pair != null) {
          isConnected |= component.getLiveAttribute(SHERPA_URI, (String)pair.getFirst()) != null;
          isConnected |= component.getLiveAttribute(SHERPA_URI, (String)pair.getSecond()) != null;
        }
      }
      else {
        pair = ourPotentialLTRAttributes.get(type);
        if (pair != null) {
          isConnected |= component.getLiveAttribute(SHERPA_URI, (String)pair.getFirst()) != null;
          isConnected |= component.getLiveAttribute(SHERPA_URI, (String)pair.getSecond()) != null;
        }
      }
    }
    return isConnected;
  }

  /**
   * Given a NlComponent and an attribute, return the corresponding ConstraintAnchorTarget
   *
   * @param scene
   * @param targetComponent
   * @param attribute
   * @return
   */
  public static ConstraintAnchorTarget getOriginAnchor(Scene scene, NlComponent targetComponent, String attribute) {
    AnchorTarget.Type type = ourMapSideToOriginAnchors.get(attribute);
    SceneComponent component = scene.getSceneComponent(targetComponent);
    if (component != null) {
      return getAnchorTarget(component, type);
    }
    return null;
  }

  /**
   * Given a NlComponent and an attribute, return the corresponding ConstraintAnchorTarget
   */
  public static ConstraintAnchorTarget getTargetAnchor(Scene scene,
                                                       NlComponent targetComponent,
                                                       String attribute,
                                                       boolean supportsRtl,
                                                       boolean isInRtl) {
    SceneComponent component = scene.getSceneComponent(targetComponent);
    if (component == null) {
      return null;
    }
    if (supportsRtl) {
      if (isInRtl) {
        return getAnchorTarget(component, ourRTLMapSideToTargetAnchors.get(attribute));
      }
      else {
        return getAnchorTarget(component, ourLTRMapSideToTargetAnchors.get(attribute));
      }
    }
    return getAnchorTarget(component, ourLTRMapSideToTargetAnchors.get(attribute));
  }

  public static ConstraintAnchorTarget getAnchorTarget(@NotNull SceneComponent component, @NotNull AnchorTarget.Type type) {
    for (Target target : component.getTargets()) {
      if (target instanceof ConstraintAnchorTarget) {
        if (((ConstraintAnchorTarget)target).getType() == type) {
          return (ConstraintAnchorTarget)target;
        }
      }
    }
    return null;
  }

  private static boolean hasConstraints(NlComponent component, String uri, ArrayList<String> constraints) {
    int count = constraints.size();
    for (int i = 0; i < count; i++) {
      if (component.getLiveAttribute(uri, constraints.get(i)) != null) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasHorizontalConstraints(NlComponent component) {
    return hasConstraints(component, SHERPA_URI, ourHorizontalAttributes);
  }

  public static boolean hasVerticalConstraints(NlComponent component) {
    return hasConstraints(component, SHERPA_URI, ourVerticalAttributes);
  }

  /**
   * Return a dp value correctly resolved. This is only intended for generic
   * dimensions (number + unit). Do not use this if the string can contain
   * wrap_content or match_parent.
   *
   * @param component the component we are looking at
   * @param value     the attribute value we want to parse
   * @return the value of the attribute in Dp, or zero if impossible to resolve
   */
  @AndroidDpCoordinate
  public static int getDpValue(@NotNull NlComponent component, String value) {
    if (value != null) {
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      if (resourceResolver != null) {
        Integer px = ViewEditor.resolveDimensionPixelSize(resourceResolver, value, configuration);
        return px == null ? 0 : Coordinates.pxToDp(component.getModel(), px);
      }
    }
    return 0;
  }

  public static int getGuidelineMode(SceneComponent component) {
    NlComponent nlComponent = component.getAuthoritativeNlComponent();
    String begin = nlComponent.getLiveAttribute(SHERPA_URI, LAYOUT_CONSTRAINT_GUIDE_BEGIN);
    String end = nlComponent.getLiveAttribute(SHERPA_URI, LAYOUT_CONSTRAINT_GUIDE_END);

    if (begin != null) {
      return BEGIN;
    }
    else if (end != null) {
      return END;
    }
    else {
      return PERCENT;
    }
  }

  /**
   * Returns the original component if the component passed in parameter is a reference.
   *
   * @param component
   * @return
   */
  private static NlComponent getOriginalComponent(@NotNull NlComponent component) {
    if (NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_REFERENCE)) {
      NlComponent parent = component.getParent();
      assert parent != null;

      if (NlComponentHelperKt.isOrHasSuperclass(parent, AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS)) {
        parent = parent.getParent();
        assert parent != null;

        if (NlComponentHelperKt.isOrHasSuperclass(parent, AndroidXConstants.CLASS_CONSTRAINT_LAYOUT)) {
          for (NlComponent child : parent.getChildren()) {
            if (child.getId() != null && child.getId().equals(component.getId())) {
              return child;
            }
          }
        }
      }
    }
    return component;
  }

  public static void clearAttributes(@NotNull NlComponent component) {
    ComponentModification modification = new ComponentModification(component, "Cleared all constraints");
    clearAllAttributes(component, modification);
    modification.commit();
  }


  public static void setDpAttribute(String uri, String attribute, NlAttributesHolder transaction, int value) {
    if (value > 0) {
      String position = String.format(VALUE_N_DP, value);
      transaction.setAttribute(uri, attribute, position);
    }
  }

  public static void clearAttributes(String uri, ArrayList<String> attributes, NlAttributesHolder transaction) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      transaction.setAttribute(uri, attribute, null);
    }
  }

  public static void clearAttributes(String uri, String[] attributes, NlAttributesHolder transaction) {
    for (int i = 0; i < attributes.length; i++) {
      transaction.setAttribute(uri, attributes[i], null);
    }
  }

  public static void clearAttributes(String uri, Pair<String, String> attributes, NlAttributesHolder transaction) {
    transaction.setAttribute(uri, attributes.getFirst(), null);
    transaction.setAttribute(uri, attributes.getSecond(), null);
  }

  private static void clearConnections(NlComponent component, ArrayList<String> attributes, NlAttributesHolder transaction) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      transaction.setAttribute(SHERPA_URI, attribute, null);
    }
    if (attributes == ourLeftAttributes) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT, null);
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    }
    else if (attributes == ourRightAttributes) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT, null);
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    }
    else if (attributes == ourStartAttributes) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_START, null);
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    }
    else if (attributes == ourEndAttributes) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_END, null);
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    }
    else if (attributes == ourTopAttributes) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP, null);
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_VERTICAL_BIAS, null);
    }
    else if (attributes == ourBottomAttributes) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM, null);
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_VERTICAL_BIAS, null);
    }
    if (!hasHorizontalConstraints(component)) {
      int offsetX = 0;
      NlComponent parent = component.getParent();
      if (parent != null) {
        offsetX = NlComponentHelperKt.getX(component) - NlComponentHelperKt.getX(parent);
        offsetX = Coordinates.pxToDp(component.getModel(), offsetX);
      }
      setDpAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, transaction, offsetX);
    }
    if (!hasVerticalConstraints(component)) {
      int offsetY = 0;
      NlComponent parent = component.getParent();
      if (parent != null) {
        offsetY = NlComponentHelperKt.getY(component) - NlComponentHelperKt.getY(parent);
        offsetY = Coordinates.pxToDp(component.getModel(), offsetY);
      }
      setDpAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, transaction, offsetY);
    }
  }

  /**
   * Clear the currently selected constraint on the design surface. Removes from XML as well.
   *
   * @return Whether it deleted a constraint or not.
   */
  public static boolean clearSelectedConstraint(@NotNull DesignSurface<?> surface) {
    // TODO: Move uses to a more common place for deletion.
    SelectionModel selectionModel = surface.getSelectionModel();
    Object secondarySelection = selectionModel.getSecondarySelection();
    Scene scene = surface.getScene();
    if (secondarySelection instanceof SecondarySelector.Constraint && scene != null) {
      SecondarySelector.Constraint constraint = (SecondarySelector.Constraint)secondarySelection;
      AnchorTarget.Type type = null;
      switch (constraint) {
        case LEFT:
          type = AnchorTarget.Type.LEFT;
          break;
        case TOP:
          type = AnchorTarget.Type.TOP;
          break;
        case RIGHT:
          type = AnchorTarget.Type.RIGHT;
          break;
        case BOTTOM:
          type = AnchorTarget.Type.BOTTOM;
          break;
        case BASELINE:
          type = AnchorTarget.Type.BASELINE;
          break;
      }
      SceneComponent component = scene.getSceneComponent(selectionModel.getPrimary());
      if (component == null) {
        return false;
      }
      AnchorTarget selectedTarget = AnchorTarget.findAnchorTarget(component, type);
      if (selectedTarget != null) {
        NlComponent nlComponent = component.getNlComponent();
        ComponentModification modification = new ComponentModification(nlComponent, "Constraint Disconnected");
        clearAnchor(type, modification, component.useRtlAttributes(), scene.isInRTL());
        cleanup(modification, nlComponent);
        modification.commit();
        scene.markNeedsLayout(Scene.ANIMATED_LAYOUT);
        selectionModel.clearSecondary();
        return true;
      }
    }
    return false;
  }

  private static void clearAllAttributes(NlComponent component, NlAttributesHolder transaction) {
    if (isWidthConstrained(component) && isHorizontalResizable(component)) {
      String fixedWidth = String.format(VALUE_N_DP, getDpWidth(component));
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, fixedWidth);
    }

    if (isHeightConstrained(component) && isVerticalResizable(component)) {
      String fixedHeight = String.format(VALUE_N_DP, getDpHeight(component));
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, fixedHeight);
    }
    clearAttributes(SHERPA_URI, ourConstraintLayoutAttributesToClear, transaction);
    clearAttributes(ANDROID_URI, ourLayoutAttributesToClear, transaction);
    component = getOriginalComponent(component);
    int offsetX = Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getX(component) -
                                                           (component.isRoot() ? 0 : NlComponentHelperKt.getX(component.getParent())));
    int offsetY = Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getY(component) -
                                                           (component.isRoot() ? 0 : NlComponentHelperKt.getY(component.getParent())));
    setDpAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, transaction, offsetX);
    setDpAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, transaction, offsetY);
  }

  public static void updateOnDelete(NlComponent component, String targetId) {
    ComponentModification transaction = null;
    // noinspection ConstantConditions
    transaction = updateOnDelete(component, ourLeftAttributes, transaction, targetId);
    transaction = updateOnDelete(component, ourTopAttributes, transaction, targetId);
    transaction = updateOnDelete(component, ourRightAttributes, transaction, targetId);
    transaction = updateOnDelete(component, ourBottomAttributes, transaction, targetId);
    transaction = updateOnDelete(component, ourBaselineAttributes, transaction, targetId);
    transaction = updateOnDelete(component, ourStartAttributes, transaction, targetId);
    transaction = updateOnDelete(component, ourEndAttributes, transaction, targetId);

    if (transaction != null) {
      transaction.commit();
    }
  }

  private static ComponentModification updateOnDelete(NlComponent component,
                                                      ArrayList<String> attributes,
                                                      ComponentModification modification,
                                                      String targetId) {
    if (isConnectedTo(component, attributes, targetId)) {
      if (modification == null) {
        modification = new ComponentModification(component, "Update on Delete");
      }
      clearConnections(component, attributes, modification);
    }
    return modification;
  }

  private static boolean isConnectedTo(NlComponent component, ArrayList<String> attributes, String targetId) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      String target = component.getLiveAttribute(SHERPA_URI, attribute);
      target = NlComponent.extractId(target);
      if (target != null && target.equalsIgnoreCase(targetId)) {
        return true;
      }
    }
    return false;
  }

  public static void ensureHorizontalPosition(NlComponent component, NlAttributesHolder transaction) {
    if (hasHorizontalConstraints(component)) {
      return;
    }
    int dx = getXfromParent(component);
    if (dx > 0) {
      String position = String.format(VALUE_N_DP, Coordinates.pxToDp(component.getModel(), dx));
      transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, position);
    }
  }

  public static void ensureVerticalPosition(NlComponent component, NlAttributesHolder transaction) {
    if (hasVerticalConstraints(component)) {
      return;
    }
    int dy = getYfromParent(component);
    if (dy > 0) {
      String position = String.format(VALUE_N_DP, Coordinates.pxToDp(component.getModel(), dy));
      transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, position);
    }
  }

  /**
   * Returns the X coordinate of the component relative to its parent
   */
  @AndroidCoordinate
  private static int getXfromParent(@NotNull NlComponent component) {
    return NlComponentHelperKt.getX(component) - (component.getParent() != null ? NlComponentHelperKt.getX(component.getParent()) : 0);
  }

  /**
   * Returns the Y coordinate of the component relative to its parent
   */
  @AndroidCoordinate
  private static int getYfromParent(@NotNull NlComponent component) {
    return NlComponentHelperKt.getY(component) - (component.getParent() != null ? NlComponentHelperKt.getY(component.getParent()) : 0);
  }


  public static boolean isConstraintModelGreaterThan(@NotNull ViewEditor editor, String version) {
    GoogleMavenArtifactId artifact = GoogleMavenArtifactId.ANDROIDX_CONSTRAINT_LAYOUT;
    Version v = NlDependencyManager.getInstance().getModuleDependencyVersion(artifact, editor.getModel().getFacet());
    if (v == null) return true;
    return v.compareTo(Version.Companion.parse(version)) > 0;
  }

  /////////////////////////////////////////////////////////////////////////////
  // Utility methods for Scout
  /////////////////////////////////////////////////////////////////////////////

  @AndroidDpCoordinate
  public static int getDpX(@NotNull NlComponent component) {
    return Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getX(component));
  }

  private static boolean hasAttributes(@NotNull NlAttributesHolder transaction, String uri, ArrayList<String> attributes) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      if (transaction.getAttribute(uri, attribute) != null) {
        return true;
      }
    }
    return false;
  }

  public static String getConnectionId(@NotNull NlComponent component, String uri, ArrayList<String> attributes) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = component.getLiveAttribute(uri, attributes.get(i));
      if (attribute != null) {
        return NlComponent.extractId(attribute);
      }
    }
    return null;
  }

  private static boolean hasLeft(@NotNull NlAttributesHolder transaction) {
    return hasAttributes(transaction, SHERPA_URI, ourLeftAttributes);
  }

  private static boolean hasTop(@NotNull NlAttributesHolder transaction) {
    return hasAttributes(transaction, SHERPA_URI, ourTopAttributes);
  }

  private static boolean hasRight(@NotNull NlAttributesHolder transaction) {
    return hasAttributes(transaction, SHERPA_URI, ourRightAttributes);
  }

  private static boolean hasBottom(@NotNull NlAttributesHolder transaction) {
    return hasAttributes(transaction, SHERPA_URI, ourBottomAttributes);
  }

  private static boolean hasStart(@NotNull NlAttributesHolder transaction) {
    return hasAttributes(transaction, SHERPA_URI, ourStartAttributes);
  }

  private static boolean hasEnd(@NotNull NlAttributesHolder transaction) {
    return hasAttributes(transaction, SHERPA_URI, ourEndAttributes);
  }

  // check if the component is added into a Flow helper.
  private static boolean isInFlow(@NotNull NlComponent component) {
    NlComponent parent = component.getParent();
    if (parent == null) {
      return false;
    }

    String componentId = component.getId();
    for (NlComponent child: parent.getChildren()) {
      if (NlComponentHelperKt.isOrHasSuperclass(child, AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_FLOW)) {
        String attr = child.getAttribute(SHERPA_URI, CONSTRAINT_REFERENCED_IDS);
        if (attr != null) {
          for(String id: attr.split(",")) {
            if (id.equals(componentId)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  /**
   * This clean up any left over attributes (margins, chain style, bias..) when
   * they are not applicable anymore.
   *
   * @param transaction
   * @param component
   */
  public static void cleanup(@NotNull NlAttributesHolder transaction, @NotNull NlComponent component) {
    boolean hasLeft = hasLeft(transaction);
    boolean hasRight = hasRight(transaction);
    boolean hasTop = hasTop(transaction);
    boolean hasBottom = hasBottom(transaction);
    boolean hasBaseline = transaction.getAttribute(SHERPA_URI, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF) != null;
    boolean hasStart = hasStart(transaction);
    boolean hasEnd = hasEnd(transaction);
    boolean inFlow = isInFlow(component);
    String margin;
    // Horizontal attributes
    // cleanup needs to be sdk range specific
    //
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(component.getModel().getFacet());
    boolean remove_left_right = moduleInfo.getMinSdkVersion().isGreaterOrEqualThan(17);

    margin = transaction.getAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT);
    if (margin != null && margin.equalsIgnoreCase(VALUE_ZERO_DP)) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT, null);
    }

    margin = transaction.getAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT);
    if (margin != null && margin.equalsIgnoreCase(VALUE_ZERO_DP)) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT, null);
    }

    if (!hasStart) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_START, null);
      if (!hasLeft) {
        transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT, null);
      }
    }
    else {
      margin = transaction.getAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_START);
      if (margin != null && margin.equalsIgnoreCase(VALUE_ZERO_DP)) {
        transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_START, null);
        transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT, null);
      }
    }
    if (!hasEnd) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_END, null);
      if (!hasRight) {
        transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT, null);
      }
    }
    else {
      margin = transaction.getAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_END);
      if (margin != null && margin.equalsIgnoreCase(VALUE_ZERO_DP)) {
        transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_END, null);
        transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT, null);
      }
    }

    if (!(hasLeft && hasRight) && !(hasStart && hasEnd)) {
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    }

    if (!hasLeft && !hasRight && !hasStart && !hasEnd) {
      if (transaction.getAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X) == null && !inFlow) {
        setDpAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, transaction, pixelToDP(component, getXfromParent(component)));
        transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE, null);
      }
    }
    else {
      transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, null);
    }

    // Vertical attributes

    if (!hasTop) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP, null);
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_VERTICAL_BIAS, null);
    }
    else {
      margin = transaction.getAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP);
      if (margin != null && margin.equalsIgnoreCase(VALUE_ZERO_DP)) {
        transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP, null);
      }
    }
    if (!hasBottom) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM, null);
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_VERTICAL_BIAS, null);
    }
    else {
      margin = transaction.getAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM);
      if (margin != null && margin.equalsIgnoreCase(VALUE_ZERO_DP)) {
        transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM, null);
      }
    }
    if (!hasTop && !hasBottom && !hasBaseline) {
      if (transaction.getAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y) == null && !inFlow) {
        setDpAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, transaction, pixelToDP(component, getYfromParent(component)));
        transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_VERTICAL_CHAIN_STYLE, null);
      }
    }
    else {
      transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, null);
    }

    if (isGuideLine(component) || isBarrier(component) || isGroup(component)) {
      transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, null);
      transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, null);
    }
    if (remove_left_right) {
      boolean start = null != transaction.getAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_START);
      if (start) {
        boolean left = null != transaction.getAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT);
        if (left) {
          transaction.removeAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT);
        }
      }
      boolean end = null != transaction.getAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_END);
      if (end) {
        boolean right = null != transaction.getAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT);
        if (right) {
          transaction.removeAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT);
        }
      }
    }
  }

  public static boolean isGuideLine(@NotNull NlComponent component) {
    return AndroidXConstants.CONSTRAINT_LAYOUT_GUIDELINE.isEqualsIgnoreCase(component.getTagName());
  }

  private static boolean isBarrier(@NotNull NlComponent component) {
    return AndroidXConstants.CONSTRAINT_LAYOUT_BARRIER.isEqualsIgnoreCase(component.getTagName());
  }

  private static boolean isGroup(@NotNull NlComponent component) {
    return AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_GROUP.isEqualsIgnoreCase(component.getTagName());
  }

  public static @Nullable
  SceneComponent findChainHead(@NotNull SceneComponent component, @NotNull ArrayList<String> sideA, @NotNull ArrayList<String> sideB) {
    final int maxAttempts = 1000;
    for (int i = 0; i < maxAttempts; i++) {
      NlComponent nlComponent = component.getAuthoritativeNlComponent();
      String attributeA = getConnectionId(nlComponent, SHERPA_URI, sideA);
      if (attributeA == null) {
        return component;
      }
      SceneComponent target = component.getScene().getSceneComponent(attributeA);
      if (target == null) {
        return component;
      }
      String attributeB = getConnectionId(target.getAuthoritativeNlComponent(), SHERPA_URI, sideB);
      if (attributeB == null) {
        return component;
      }
      if (attributeB.equalsIgnoreCase(nlComponent.getId())) {
        component = target;
      }
      else {
        return component;
      }
    }
    return null;
  }

  public static boolean isInChain(ArrayList<String> sideA, ArrayList<String> sideB, SceneComponent component) {
    String attributeA = getConnectionId(component.getAuthoritativeNlComponent(), SHERPA_URI, sideA);
    if (attributeA != null) {
      SceneComponent target = component.getScene().getSceneComponent(attributeA);
      if (target != null) {
        String attributeB = getConnectionId(target.getAuthoritativeNlComponent(), SHERPA_URI, sideB);
        if (attributeB != null) {
          if (attributeB.equalsIgnoreCase(component.getAuthoritativeNlComponent().getId())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static @Nullable
  NlComponent findChainHead(@NotNull NlComponent component, @Nullable ArrayList<String> sideA, @Nullable ArrayList<String> sideB) {
    final int maxAttempts = 1000;
    for (int i = 0; i < maxAttempts; i++) {
      String attributeA = getConnectionId(component, SHERPA_URI, sideA);
      if (attributeA == null) {
        return component;
      }
      NlComponent parent = component.getParent();
      assert parent != null;
      List<NlComponent> list = parent.getChildren();

      NlComponent target = getComponent(list, attributeA);
      if (target == null) {
        return component;
      }
      String attributeB = getConnectionId(target, SHERPA_URI, sideB);
      if (attributeB == null) {
        return component;
      }
      if (attributeB.equalsIgnoreCase(component.getId())) {
        component = target;
      }
      else {
        return component;
      }
    }
    return null;
  }

  public static NlComponent getComponent(List<NlComponent> list, String id) {
    for (NlComponent nlComponent : list) {
      if (id.equals(nlComponent.getId())) {
        return nlComponent;
      }
    }
    return null;
  }

  /**
   * Returns true if the given component is part of a chain
   *
   * @param sideA
   * @param sideB
   * @param component the component to test
   * @return true if the component is in a chain, false otherwise
   */
  public static boolean isInChain(@NotNull ArrayList<String> sideA, @NotNull ArrayList<String> sideB, @NotNull NlComponent component) {
    String attributeA = getConnectionId(component, SHERPA_URI, sideA);
    NlComponent parent = component.getParent();
    if (attributeA != null && parent != null) {
      List<NlComponent> list = parent.getChildren();
      NlComponent target = getComponent(list, attributeA);
      if (target != null) {
        String attributeB = getConnectionId(target, SHERPA_URI, sideB);
        if (attributeB != null) {
          if (attributeB.equalsIgnoreCase(component.getId())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @AndroidDpCoordinate
  public static int getDpY(@NotNull NlComponent component) {
    return Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getY(component));
  }

  @AndroidDpCoordinate
  public static int getDpWidth(@NotNull NlComponent component) {
    return Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getW(component));
  }

  @AndroidDpCoordinate
  public static int getDpHeight(@NotNull NlComponent component) {
    return Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getH(component));
  }

  @AndroidDpCoordinate
  public static int pixelToDP(@NotNull NlComponent component, int size) {
    return Coordinates.pxToDp(component.getModel(), size);
  }

  @AndroidDpCoordinate
  public static int getDpBaseline(@NotNull NlComponent component) {
    return Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getBaseline(component));
  }

  public static boolean hasBaseline(@NotNull NlComponent component) {
    return NlComponentHelperKt.getBaseline(component) > 0;
  }

  /**
   * Test for guideline or BARRIER
   *
   * @param component
   * @return
   */
  public static boolean isLine(@NotNull NlComponent component) {
    ViewInfo viewInfo = NlComponentHelperKt.getViewInfo(component);
    if (viewInfo == null) {
      return false;
    }
    if (NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.CONSTRAINT_LAYOUT_GUIDELINE)) {
      return true;
    }
    return NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.CONSTRAINT_LAYOUT_BARRIER);
  }

  /**
   * is the component a vertical line (guideline or barrier
   *
   * @param component
   * @return
   */
  public static boolean isVerticalLine(@NotNull NlComponent component) {
    ViewInfo viewInfo = NlComponentHelperKt.getViewInfo(component);
    if (viewInfo == null) {
      return false;
    }
    if (NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.CONSTRAINT_LAYOUT_GUIDELINE)) {
      String orientation = component.getAttribute(ANDROID_URI, ATTR_ORIENTATION);
      if (orientation != null && orientation.equalsIgnoreCase(ATTR_GUIDELINE_ORIENTATION_VERTICAL)) {
        return true;
      }
    }
    if (NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.CONSTRAINT_LAYOUT_BARRIER)) {
      String dir = component.getAttribute(SHERPA_URI, ATTR_BARRIER_DIRECTION);
      if (dir != null) {
        if (dir.equalsIgnoreCase(CONSTRAINT_BARRIER_LEFT)
            || dir.equalsIgnoreCase(CONSTRAINT_BARRIER_RIGHT)
            || dir.equalsIgnoreCase(CONSTRAINT_BARRIER_START)
            || dir.equalsIgnoreCase(CONSTRAINT_BARRIER_END)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * is the component a horizontal line (guideline or barrier
   *
   * @param component
   * @return
   */
  public static boolean isHorizontalLine(@NotNull NlComponent component) {
    ViewInfo viewInfo = NlComponentHelperKt.getViewInfo(component);
    if (viewInfo == null) {
      return false;
    }
    if (NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.CONSTRAINT_LAYOUT_GUIDELINE)) {
      String orientation = component.getAttribute(ANDROID_URI, ATTR_ORIENTATION);
      if (orientation != null && orientation.equalsIgnoreCase(ATTR_GUIDELINE_ORIENTATION_HORIZONTAL)) {
        return true;
      }
    }
    if (NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.CONSTRAINT_LAYOUT_BARRIER)) {
      String dir = component.getAttribute(SHERPA_URI, ATTR_BARRIER_DIRECTION);

      if (dir != null) {
        if (dir.equalsIgnoreCase(CONSTRAINT_BARRIER_TOP)
            || dir.equalsIgnoreCase(CONSTRAINT_BARRIER_BOTTOM)) {
          return true;
        }
      }
    }
    return false;
  }


  public static boolean isHorizontalGuideline(@NotNull NlComponent component) {
    if (NlComponentHelperKt.getViewInfo(component) != null &&
        NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.CONSTRAINT_LAYOUT_GUIDELINE)) {
      String orientation = component.getAttribute(ANDROID_URI, ATTR_ORIENTATION);
      if (orientation != null && orientation.equalsIgnoreCase(ATTR_GUIDELINE_ORIENTATION_HORIZONTAL)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isVerticalGuideline(@NotNull NlComponent component) {
    if (NlComponentHelperKt.getViewInfo(component) != null &&
        NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.CONSTRAINT_LAYOUT_GUIDELINE)) {
      String orientation = component.getAttribute(ANDROID_URI, ATTR_ORIENTATION);
      if (orientation != null && orientation.equalsIgnoreCase(ATTR_GUIDELINE_ORIENTATION_VERTICAL)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Test if the height is constrained by constraints on both sides
   *
   * @param component view to be tested
   * @return true if constrained on both sides
   */
  private static boolean isHeightConstrained(@NotNull NlComponent component) {
    String tb = component.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    String tt = component.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_TOP_TO_TOP_OF);
    if (!(tt != null || tb != null)) { // efficiency short cut
      return false;
    }
    String bb = component.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
    String bt = component.getAttribute(SHERPA_URI, ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    return bt != null || bb != null;
  }

  /**
   * Test if the width is constrained by constraints on both sides
   * Test adheres to the rule that if start or end is used left & right are ignored
   *
   * @param component view to be tested
   * @return true if constrained on both sides
   */
  private static boolean isWidthConstrained(@NotNull NlComponent component) {
    String se = component.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_START_TO_END_OF);
    String ss = component.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_START_TO_START_OF);
    String ee = component.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_END_TO_END_OF);
    String es = component.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_END_TO_START_OF);
    if (ee != null || es != null || se != null || ss != null) { // if you use any start end ignore left root
      return ((ee != null || es != null) && (se != null || ss != null));
    }
    String ll = component.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    String lr = component.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    String rr = component.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);
    String rl = component.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    return ((ll != null || lr != null) && (rl != null || rr != null));
  }

  public static boolean isHorizontalResizable(@NotNull NlComponent component) {
    String dimension = component.getAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH);
    if (dimension == null) {
      return false;
    }
    if (dimension.equalsIgnoreCase(VALUE_MATCH_CONSTRAINT)) {
      return true;
    }
    return false;
  }

  public static boolean isVerticalResizable(@NotNull NlComponent component) {
    String dimension = component.getAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
    if (dimension == null) {
      return false;
    }
    if (dimension.equalsIgnoreCase(VALUE_MATCH_CONSTRAINT)) {
      return true;
    }
    return false;
  }

  public static boolean hasUserResizedHorizontally(@NotNull NlComponent component) {
    String dimension = component.getAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH);
    assert dimension != null || TAG_INCLUDE.equals(component.getTagName());

    if (dimension == null || dimension.equalsIgnoreCase(VALUE_WRAP_CONTENT)) {
      return false;
    }

    return true;
  }

  public static boolean hasUserResizedVertically(@NotNull NlComponent component) {
    String dimension = component.getAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
    assert dimension != null || TAG_INCLUDE.equals(component.getTagName());

    if (dimension == null || dimension.equalsIgnoreCase(VALUE_WRAP_CONTENT)) {
      return false;
    }

    return true;
  }

  // TODO: add support for RTL in Scout
  @AndroidDpCoordinate
  public static int getMargin(@NotNull NlComponent component, String margin_attr) {
    int margin = 0;

    String marginString = component.getLiveAttribute(ANDROID_URI, margin_attr);
    if (marginString == null) {
      if (ATTR_LAYOUT_MARGIN_LEFT.equalsIgnoreCase(margin_attr)) { // left check if it is start
        marginString = component.getLiveAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_START);
      }
      else { // right check if it is end
        marginString = component.getLiveAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_END);
      }
    }
    if (marginString != null) {
      if (marginString.startsWith("@")) {
        // TODO handle isMarginReference = true;
      }
    }
    return Coordinates.pxToDp(component.getModel(), margin);
  }

  public static void setScoutAbsoluteDpX(@NotNull NlComponent component, @AndroidDpCoordinate int dp, boolean apply) {
    setScoutAttributeValue(component, TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, dp, apply);
  }

  public static void setScoutAbsoluteDpY(@NotNull NlComponent component, @AndroidDpCoordinate int dp, boolean apply) {
    setScoutAttributeValue(component, TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, dp, apply);
  }

  public static void setScoutAbsoluteDpWidth(@NotNull NlComponent component, @AndroidDpCoordinate int dp, boolean apply) {
    setScoutAttributeValue(component, ANDROID_URI, ATTR_LAYOUT_WIDTH, dp, apply);
  }

  public static void setScoutAbsoluteDpHeight(@NotNull NlComponent component, @AndroidDpCoordinate int dp, boolean apply) {
    setScoutAttributeValue(component, ANDROID_URI, ATTR_LAYOUT_HEIGHT, dp, apply);
  }

  public static void setScoutVerticalBiasPercent(@NotNull NlComponent component, @AndroidDpCoordinate float value) {
    AttributesTransaction transaction = component.startAttributeTransaction();
    transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_VERTICAL_BIAS, Float.toString(value));
    transaction.apply();
  }

  public static void setScoutHorizontalBiasPercent(@NotNull NlComponent component, @AndroidDpCoordinate float value) {
    AttributesTransaction transaction = component.startAttributeTransaction();
    transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS, Float.toString(value));
    transaction.apply();
  }

  private static void setScoutAttributeValue(@NotNull NlComponent component, @NotNull String uri,
                                             @NotNull String attribute, @AndroidDpCoordinate int dp, boolean apply) {
    if (dp <= 0) {
      return;
    }
    String position = String.format(VALUE_N_DP, dp);
    AttributesTransaction transaction = component.startAttributeTransaction();
    transaction.setAttribute(uri, attribute, position);
    if (apply) {
      transaction.apply();
    }
  }

  public static void scoutClearAttributes(@NotNull NlComponent component, ArrayList<String> attributes) {
    AttributesTransaction transaction = component.startAttributeTransaction();
    clearConnections(component, attributes, transaction);
    transaction.apply();
  }

  public static void setScoutAttributeValue(@NotNull NlComponent component, @NotNull String uri,
                                            @NotNull String attribute, @NotNull String value) {
    AttributesTransaction transaction = component.startAttributeTransaction();
    transaction.setAttribute(uri, attribute, value);
    transaction.apply();
  }

  public static boolean isConstraintLayout(@NotNull NlComponent component) {
    return NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.CONSTRAINT_LAYOUT)
           || AndroidXConstants.CONSTRAINT_LAYOUT.isEquals(component.getTagDeprecated().getName()); // used during layout conversion
  }

  // ordered the same as Direction enum
  private static String[][] ATTRIB_MATRIX = {
    {ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_LAYOUT_TOP_TO_BOTTOM_OF, null, null, null},
    {ATTR_LAYOUT_BOTTOM_TO_TOP_OF, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, null, null, null},
    {null, null, ATTR_LAYOUT_START_TO_START_OF, ATTR_LAYOUT_START_TO_END_OF, null},
    {null, null, ATTR_LAYOUT_END_TO_START_OF, ATTR_LAYOUT_END_TO_END_OF, null},
    {null, null, null, null, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF}
  };
  private static String[][] ATTRIB_CLEAR = {
    {ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_LAYOUT_TOP_TO_BOTTOM_OF, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF},
    {ATTR_LAYOUT_BOTTOM_TO_TOP_OF, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF},
    {ATTR_LAYOUT_LEFT_TO_LEFT_OF, ATTR_LAYOUT_LEFT_TO_RIGHT_OF},
    {ATTR_LAYOUT_RIGHT_TO_LEFT_OF, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, ATTR_LAYOUT_TO_START_OF},
    {ATTR_LAYOUT_BASELINE_TO_BASELINE_OF}
  };

  private static String[] ATTRIB_MARGIN = {
    ATTR_LAYOUT_MARGIN_TOP,
    ATTR_LAYOUT_MARGIN_BOTTOM,
    ATTR_LAYOUT_MARGIN_START,
    ATTR_LAYOUT_MARGIN_END
  };

  private static String[] ATTRIB_MARGIN_LR = {
    null,
    null,
    ATTR_LAYOUT_MARGIN_LEFT,
    ATTR_LAYOUT_MARGIN_RIGHT
  };


  public static void clearAttributes(String uri, ArrayList<String> attributes, MTag.TagWriter tagwriter) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      tagwriter.setAttribute(uri, attribute, null);
    }
  }

  /**
   * Apply scout to constraintSet not NLcomponent
   * @param source
   * @param sourceDirection
   * @param target
   * @param targetDirection
   * @param margin
   */
  public static void scoutConstraintSetConnect( NlComponent source,
    Direction sourceDirection,
    NlComponent target,
    Direction targetDirection,
    int margin) {
    int srcIndex = sourceDirection.ordinal();
    String attrib = ATTRIB_MATRIX[srcIndex][targetDirection.ordinal()];
    if (attrib == null) {
      throw new RuntimeException("cannot connect " + sourceDirection + " to " + targetDirection);
    }
    ArrayList<String> list = new ArrayList<>();
    for (int i = 0; i < ATTRIB_CLEAR[srcIndex].length; i++) {
      String clr_attr = ATTRIB_CLEAR[srcIndex][i];
      if (!attrib.equals(clr_attr)) {
        list.add(clr_attr);
      }
    }

    MTag.TagWriter tagwriter = MotionSceneUtils.getTagWriter(source);

    clearAttributes(SHERPA_URI, list, tagwriter);
    String targetId;
    if (target == source.getParent()) {
      targetId = ATTR_PARENT;
    }
    else {
      targetId = NEW_ID_PREFIX + NlComponentHelperKt.ensureLiveId(target);
    }
    tagwriter.setAttribute(SHERPA_URI, attrib, targetId);
    if ((srcIndex <= Direction.BASELINE.ordinal()) && (margin > 0)) {
      tagwriter.setAttribute(ANDROID_URI, ATTRIB_MARGIN[srcIndex], margin + "dp");
      if (ATTRIB_MARGIN_LR[srcIndex] != null) { // add the left and right as needed
        tagwriter.setAttribute(ANDROID_URI, ATTRIB_MARGIN_LR[srcIndex], margin + "dp");
      }
    }
    tagwriter.commit("create connection");
    String str;
    switch (sourceDirection) {
      case BASELINE:
        str = DecoratorUtilities.BASELINE_CONNECTION;
        break;
      case BOTTOM:
        str = DecoratorUtilities.BOTTOM_CONNECTION;
        break;
      case LEFT:
        str = DecoratorUtilities.LEFT_CONNECTION;
        break;
      case RIGHT:
        str = DecoratorUtilities.RIGHT_CONNECTION;
        break;
      case TOP:
        str = DecoratorUtilities.TOP_CONNECTION;
        break;
      default:
        str = null;
        break;
    }
    // noinspection ConstantConditions
    if (str != null) {
      DecoratorUtilities.setTimeChange(source, str, DecoratorUtilities.ViewStates.INFERRED, DecoratorUtilities.ViewStates.SELECTED);
    }
  }

  public static void scoutConnect(NlComponent source,
                                  Direction sourceDirection,
                                  NlComponent target,
                                  Direction targetDirection,
                                  int margin) {
    if (MotionSceneUtils.isUnderConstraintSet(source)) {
      scoutConstraintSetConnect(source, sourceDirection, target, targetDirection, margin);
      return;
    }
    int srcIndex = sourceDirection.ordinal();
    String attrib = ATTRIB_MATRIX[srcIndex][targetDirection.ordinal()];
    if (attrib == null) {
      throw new RuntimeException("cannot connect " + sourceDirection + " to " + targetDirection);
    }
    ArrayList<String> list = new ArrayList<>();
    for (int i = 0; i < ATTRIB_CLEAR[srcIndex].length; i++) {
      String clr_attr = ATTRIB_CLEAR[srcIndex][i];
      if (!attrib.equals(clr_attr)) {
        list.add(clr_attr);
      }
    }
    final AttributesTransaction transaction = source.startAttributeTransaction();
    clearAttributes(SHERPA_URI, list, transaction);
    String targetId;
    if (target == source.getParent()) {
      targetId = ATTR_PARENT;
    }
    else {
      targetId = NEW_ID_PREFIX + NlComponentHelperKt.ensureLiveId(target);
    }
    transaction.setAttribute(SHERPA_URI, attrib, targetId);
    if ((srcIndex <= Direction.BASELINE.ordinal()) && (margin > 0)) {
      transaction.setAttribute(ANDROID_URI, ATTRIB_MARGIN[srcIndex], margin + "dp");
      if (ATTRIB_MARGIN_LR[srcIndex] != null) { // add the left and right as needed
        transaction.setAttribute(ANDROID_URI, ATTRIB_MARGIN_LR[srcIndex], margin + "dp");
      }
    }
    transaction.apply();
    String str;
    switch (sourceDirection) {
      case BASELINE:
        str = DecoratorUtilities.BASELINE_CONNECTION;
        break;
      case BOTTOM:
        str = DecoratorUtilities.BOTTOM_CONNECTION;
        break;
      case LEFT:
        str = DecoratorUtilities.LEFT_CONNECTION;
        break;
      case RIGHT:
        str = DecoratorUtilities.RIGHT_CONNECTION;
        break;
      case TOP:
        str = DecoratorUtilities.TOP_CONNECTION;
        break;
      default:
        str = null;
        break;
    }
    // noinspection ConstantConditions
    if (str != null) {
      DecoratorUtilities.setTimeChange(source, str, DecoratorUtilities.ViewStates.INFERRED, DecoratorUtilities.ViewStates.SELECTED);
    }
  }

  /**
   * Used by ScoutChains to create chains
   *
   * @param source          component to add to chain link
   * @param sourceDirection direction of chain link
   * @param target          target target to link to
   * @param targetDirection direction of target link
   * @param attrList        fadingValue attributes to add during transaction
   */
  public static void scoutChainConnect(NlComponent source, Direction sourceDirection, NlComponent target, Direction targetDirection,
                                       ArrayList<String[]> attrList) {
    int srcIndex = sourceDirection.ordinal();
    String attrib = ATTRIB_MATRIX[srcIndex][targetDirection.ordinal()];
    if (attrib == null) {
      throw new RuntimeException("cannot connect " + sourceDirection + " to " + targetDirection);
    }
    ArrayList<String> list = new ArrayList<>();
    for (int i = 0; i < ATTRIB_CLEAR[srcIndex].length; i++) {
      String clr_attr = ATTRIB_CLEAR[srcIndex][i];
      if (!attrib.equals(clr_attr)) {
        list.add(clr_attr);
      }
    }
    final AttributesTransaction transaction = source.startAttributeTransaction();
    clearAttributes(SHERPA_URI, list, transaction);
    String targetId;
    if (target == source.getParent()) {
      targetId = ATTR_PARENT;
    }
    else {
      targetId = NEW_ID_PREFIX + NlComponentHelperKt.ensureLiveId(target);
    }
    transaction.setAttribute(SHERPA_URI, attrib, targetId);
    for (int i = 0; i < attrList.size(); i++) {
      String[] bundle = attrList.get(i);
      transaction.setAttribute(bundle[0], bundle[1], bundle[2]);
    }

    transaction.apply();
    String str = null;
    switch (sourceDirection) {
      case BASELINE:
        str = DecoratorUtilities.BASELINE_CONNECTION;
        break;
      case BOTTOM:
        str = DecoratorUtilities.BOTTOM_CONNECTION;
        break;
      case LEFT:
        str = DecoratorUtilities.LEFT_CONNECTION;
        break;
      case RIGHT:
        str = DecoratorUtilities.RIGHT_CONNECTION;
        break;
      case TOP:
        str = DecoratorUtilities.TOP_CONNECTION;
        break;
    }

    DecoratorUtilities.setTimeChange(source, str, DecoratorUtilities.ViewStates.INFERRED, DecoratorUtilities.ViewStates.SELECTED);
  }

  public static boolean wouldCreateLoop(NlComponent source, Direction sourceDirection, NlComponent target) {
    HashSet<NlComponent> connected;
    if (source.getParent() == null) {
      return true;
    }
    List<NlComponent> sisters = source.getParent().getChildren();
    switch (sourceDirection) {
      case TOP:
      case BOTTOM:
        connected =
          DecoratorUtilities.getConnectedNlComponents(source, sisters, ourBottomAttributes, ourTopAttributes, ourBaselineAttributes);
        return connected.contains(target);
      case RIGHT:
      case LEFT:
        connected = DecoratorUtilities
          .getConnectedNlComponents(source, sisters, ourRightAttributes, ourLeftAttributes, ourStartAttributes, ourEndAttributes);
        return connected.contains(target);

      case BASELINE:
        connected =
          DecoratorUtilities.getConnectedNlComponents(source, sisters, ourBottomAttributes, ourTopAttributes, ourBaselineAttributes);
        return connected.contains(target);
    }
    return false;
  }

  @SafeVarargs
  private static HashSet<String> getConnected(NlComponent c, List<NlComponent> sisters, ArrayList<String>... list) {
    HashSet<String> set = new HashSet<>();
    set.add(c.getId());
    int lastCount;
    do {
      lastCount = set.size();
      for (NlComponent sister : sisters) {
        for (int i = 0; i < list.length; i++) {
          String str = getConnectionId(sister, SdkConstants.SHERPA_URI, list[i]);
          if (set.contains(str)) {
            set.add(sister.getId());
          }
        }
      }
    }
    while (set.size() > lastCount);
    return set;
  }

  /**
   * Search for any connection of type
   *
   * @param component
   * @param sisters
   * @param list
   * @return
   */
  @SafeVarargs
  private static boolean isConnected(NlComponent component, ArrayList<String>... list) {

    for (int i = 0; i < list.length; i++) {
      int count = list[i].size();
      for (int k = 0; k < count; k++) {
        if (null != component.getLiveAttribute(SdkConstants.SHERPA_URI, list[i].get(k))) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Logic to decide when to use left and right margins
   *
   * @param component
   * @return
   */
  static boolean useLeftRight(NlComponent component) {
    if (isConnected(component, ourStartAttributes, ourEndAttributes)) {
      return false;
    }
    if (isConnected(component, ourLeftAttributes, ourRightAttributes)) {
      return true;
    }
    return false;
  }
}
