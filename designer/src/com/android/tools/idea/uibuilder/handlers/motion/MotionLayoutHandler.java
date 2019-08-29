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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlComponentDelegate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.common.scene.target.ComponentAssistantViewAction;
import com.android.tools.idea.common.scene.target.LassoTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.rendering.parsers.AttributeSnapshot;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.handlers.assistant.MotionLayoutAssistantPanel;
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutNotchProvider;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintAnchorTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintDragTarget;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionAccessoryPanel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionAttributePanel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionLayoutInterface;
import com.android.tools.idea.uibuilder.property.assistant.ComponentAssistantFactory;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.ATTR_TRANSITION_SHOW_PATHS;

public class MotionLayoutHandler extends ConstraintLayoutHandler implements NlComponentDelegate {
  private static final boolean DEBUG = false;
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_TRANSITION_SHOW_PATHS);
  }

  @Nullable
  private static ComponentAssistantFactory getComponentAssistant(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    if (!StudioFlags.NELE_MOTION_LAYOUT_ANIMATIONS.get() || !SdkConstants.MOTION_LAYOUT.isEquals(component.getTagName())) {
      return null;
    }

    return (context) -> new MotionLayoutAssistantPanel(surface, context.getComponent());
  }

  @NotNull
  @Override
  public List<Target> createTargets(@NotNull SceneComponent sceneComponent) {
    sceneComponent.setNotchProvider(new ConstraintLayoutNotchProvider());
    return ImmutableList.of(
      new LassoTarget(),
      new ConstraintAnchorTarget(AnchorTarget.Type.LEFT, false),
      new ConstraintAnchorTarget(AnchorTarget.Type.TOP, false),
      new ConstraintAnchorTarget(AnchorTarget.Type.RIGHT, false),
      new ConstraintAnchorTarget(AnchorTarget.Type.BOTTOM, false)
    );
  }

  @Override
  public boolean addPopupMenuActions(@NotNull SceneComponent component, @NotNull List<ViewAction> actions) {
    MotionLayoutInterface panel = getTimeline(component.getNlComponent());
    if (panel == null || panel.showPopupMenuActions()) {
      super.addPopupMenuActions(component, actions);
    }

    actions.add(new ComponentAssistantViewAction((nlComponent) -> getComponentAssistant(component.getScene().getDesignSurface(), nlComponent)));

    return false;
  }

  @Override
  public boolean needsAccessoryPanel(@NotNull AccessoryPanel.Type type) {
    switch (type) {
      case SOUTH_PANEL:
      case EAST_PANEL:
        return true;
    }
    return false;
  }

  @Override
  @NotNull
  public AccessoryPanelInterface createAccessoryPanel(@NotNull DesignSurface surface,
                                                      @NotNull AccessoryPanel.Type type,
                                                      @NotNull NlComponent parent,
                                                      @NotNull AccessoryPanelVisibility panelVisibility) {
    if (true) {
      switch (type) {
        case SOUTH_PANEL:
          if (DEBUG) {
            Debug.println("SOUTH PANEL");
          }
          return new MotionAccessoryPanel(surface, parent, panelVisibility);
         //return new MotionLayoutTimelinePanel(surface, parent, panelVisibility);
        case EAST_PANEL:
          if (DEBUG) {
            Debug.println("EAST PANEL");
          }
          return  new MotionAttributePanel(parent, panelVisibility);
        //return  new MotionLayoutAttributePanel(parent, panelVisibility);
      }
    } else {
      switch (type) {
        case SOUTH_PANEL:
          return new MotionAccessoryPanel(surface, parent, panelVisibility);
        case EAST_PANEL:
          return new MotionAccessoryPanel(surface, parent, panelVisibility);
      }
    }
     throw new IllegalArgumentException("Unsupported type");
  }

  @Override
  public Interaction createInteraction(@NotNull ScreenView screenView, @NotNull NlComponent component) {
    return super.createInteraction(screenView, component);
//    return new MotionLayoutSceneInteraction(screenView, component);
  }
  //
  //@NotNull
  //@Override
  //public List<Target> createChildTargets(@NotNull SceneComponent parentComponent, @NotNull SceneComponent childComponent) {
  //  MotionLayoutInterface panel = getTimeline(childComponent.getNlComponent());
  //  if (panel != null) {
  //    if (panel.getCurrentState() == MotionLayoutTimelinePanel.State.TL_PLAY
  //        || panel.getCurrentState() == MotionLayoutTimelinePanel.State.TL_PAUSE
  //        || panel.getCurrentState() == MotionLayoutTimelinePanel.State.TL_TRANSITION
  //        || panel.getCurrentState() == MotionLayoutTimelinePanel.State.TL_UNKNOWN) {
  //      ImmutableList.Builder<Target> listBuilder = new ImmutableList.Builder<>();
  //      listBuilder.add(
  //        new ConstraintDragTarget()
  //      );
  //      return listBuilder.build();
  //    }
  //  }
  //  return super.createChildTargets(parentComponent, childComponent);
  //}

  /////////////////////////////////////////////////////////////////////////////////////////////////
  // Delegation of NlComponent
  /////////////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public NlComponentDelegate getNlComponentDelegate() {
    return this;
  }

  public static MotionLayoutInterface getTimeline(@NotNull NlComponent component) {
    Object property = component.getClientProperty(MotionLayoutTimelinePanel.TIMELINE);
    if (property == null && component.getParent() != null) {
      // need to grab the timeline from the MotionLayout component...
      // TODO: walk the tree up until we find the MotionLayout?
      property = component.getParent().getClientProperty(MotionLayoutTimelinePanel.TIMELINE);
    }
    if (property == null || !(property instanceof MotionLayoutTimelinePanel)) {
      return null;
    }
    return (MotionLayoutTimelinePanel) property;
  }

  @Override
  public boolean handlesAttribute(@NotNull NlComponent component, @Nullable String namespace, @NotNull String attribute) {
    MotionLayoutInterface panel = getTimeline(component);
    if (panel == null) {
      return false;
    }
    return panel.getNlComponentDelegate().handlesAttribute(component, namespace, attribute);
  }

  @Override
  public boolean handlesAttributes(NlComponent component) {
    MotionLayoutInterface panel = getTimeline(component);
    if (panel == null) {
      return false;
    }
    return panel.getNlComponentDelegate().handlesAttributes(component);
  }

  @Override
  public boolean handlesApply(ComponentModification modification) {
    MotionLayoutInterface panel = getTimeline(modification.getComponent());
    if (panel == null) {
      return false;
    }
    return panel.getNlComponentDelegate().handlesApply(modification);
  }

  @Override
  public boolean handlesCommit(ComponentModification modification) {
    MotionLayoutInterface panel = getTimeline(modification.getComponent());
    if (panel == null) {
      return false;
    }
    return panel.getNlComponentDelegate().handlesCommit(modification);
  }

  @Override
  public String getAttribute(@NotNull NlComponent component, @Nullable String namespace, @NotNull String attribute) {
    MotionLayoutInterface panel = getTimeline(component);
    if (panel == null) {
      return null;
    }
    return panel.getNlComponentDelegate().getAttribute(component, namespace, attribute);
  }

  @Override
  public List<AttributeSnapshot> getAttributes(NlComponent component) {
    MotionLayoutInterface panel = getTimeline(component);
    if (panel == null) {
      return Collections.emptyList();
    }
    return panel.getNlComponentDelegate().getAttributes(component);
  }

  @Override
  public void apply(ComponentModification modification) {
    MotionLayoutInterface panel = getTimeline(modification.getComponent());
    if (panel != null) {
      panel.getNlComponentDelegate().apply(modification);
    }
  }

  @Override
  public void commit(ComponentModification modification) {
    MotionLayoutInterface panel = getTimeline(modification.getComponent());
    if (panel != null) {
      panel.getNlComponentDelegate().commit(modification);
    }
  }

  @Override
  public void setAttribute(NlComponent component, String namespace, String attribute, String value) {
    ComponentModification modification = new ComponentModification(component, "Set Attribute " + attribute);
    MotionLayoutInterface panel = getTimeline(modification.getComponent());
    if (panel != null) {
      modification.setAttribute(namespace, attribute, value);
      panel.getNlComponentDelegate().commit(modification);
    }
  }

  @Override
  public void clearCaches() {
    // nothing here
  }

  @Override
  public void willRemoveChild(@NotNull NlComponent component) {
    // nmothing here
  }

  @Override
  public boolean commitToMotionScene(Pair<String, String> key) {
    return false;
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////

}
