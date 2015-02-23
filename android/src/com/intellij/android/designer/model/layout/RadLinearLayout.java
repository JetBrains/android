/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.model.layout;

import com.android.SdkConstants;
import com.android.tools.idea.designer.LinearLayoutResizeOperation;
import com.android.tools.idea.designer.ResizeOperation;
import com.intellij.android.designer.designSurface.TreeDropToOperation;
import com.intellij.android.designer.designSurface.graphics.DirectionResizePoint;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import com.intellij.android.designer.designSurface.graphics.ResizeSelectionDecorator;
import com.intellij.android.designer.designSurface.layout.LinearLayoutOperation;
import com.intellij.android.designer.designSurface.layout.actions.LayoutMarginOperation;
import com.intellij.android.designer.designSurface.layout.flow.FlowStaticDecorator;
import com.intellij.android.designer.model.RadComponentOperations;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.RadViewLayoutWithData;
import com.intellij.android.designer.model.layout.actions.*;
import com.intellij.designer.componentTree.TreeEditOperation;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.selection.ResizePoint;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.intellij.android.designer.designSurface.graphics.DrawingStyle.SHOW_STATIC_GRID;

/**
 * @author Alexander Lobas
 */
public class RadLinearLayout extends RadViewLayoutWithData implements ILayoutDecorator {

  private static final String[] LAYOUT_PARAMS = {"LinearLayout_Layout", "ViewGroup_MarginLayout"};

  private ResizeSelectionDecorator mySelectionDecorator;
  private FlowStaticDecorator myLineDecorator;

  @Override
  @NotNull
  public String[] getLayoutParams() {
    return LAYOUT_PARAMS;
  }

  public boolean isHorizontal() {
    return !VALUE_VERTICAL.equals(((RadViewComponent)myContainer).getTag().getAttributeValue(ATTR_ORIENTATION, ANDROID_URI));
  }

  @Override
  public EditOperation processChildOperation(OperationContext context) {
    if (context.isCreate() || context.isPaste() || context.isAdd() || context.isMove()) {
      if (context.isTree()) {
        if (TreeEditOperation.isTarget(myContainer, context)) {
          return new TreeDropToOperation(myContainer, context);
        }
        return null;
      }
      return new LinearLayoutOperation(myContainer, context, isHorizontal());
    }
    if (context.is(ResizeOperation.TYPE)) {
      return new LinearLayoutResizeOperation(context);
    }
    if (context.is(LayoutMarginOperation.TYPE)) {
      return new LayoutMarginOperation(context);
    }
    return null;
  }

  private StaticDecorator getLineDecorator() {
    if (myLineDecorator == null) {
      myLineDecorator = new FlowStaticDecorator(myContainer) {
        @Override
        protected boolean isHorizontal() {
          return RadLinearLayout.this.isHorizontal();
        }
      };
    }
    return myLineDecorator;
  }

  @Override
  public void addStaticDecorators(List<StaticDecorator> decorators, List<RadComponent> selection) {
    //noinspection ConstantConditions
    if (!SHOW_STATIC_GRID) {
      return;
    }

    if (selection.contains(myContainer)) {
      if (!(myContainer.getParent().getLayout() instanceof ILayoutDecorator)) {
        decorators.add(getLineDecorator());
      }
    }
    else {
      for (RadComponent component : selection) {
        if (component.getParent() == myContainer) {
          decorators.add(getLineDecorator());
          return;
        }
      }
      super.addStaticDecorators(decorators, selection);
    }
  }

  private static final int POINTS_SIZE = 16;

  @Override
  public ComponentDecorator getChildSelectionDecorator(RadComponent component, List<RadComponent> selection) {
    if (mySelectionDecorator == null) {
      mySelectionDecorator = new ResizeSelectionDecorator(DrawingStyle.SELECTION) {
        @Override
        protected boolean visible(RadComponent component, ResizePoint point) {
          if (point.getType() == LayoutMarginOperation.TYPE) {
            boolean horizontal = isHorizontal();
            Pair<Gravity, Gravity> gravity = Gravity.getSides(component);
            int direction = ((DirectionResizePoint)point).getDirection();
            Rectangle bounds = component.getBounds();
            boolean goodWidth = bounds.width >= POINTS_SIZE;
            boolean goodHeight = bounds.height >= POINTS_SIZE;

            if (direction == Position.WEST) { // left
              return (horizontal || gravity.first != Gravity.right) && goodHeight;
            }
            if (direction == Position.EAST) { // right
              return (horizontal || gravity.first != Gravity.left) && goodHeight;
            }
            if (direction == Position.NORTH) { // top
              return (!horizontal || gravity.second != Gravity.bottom) && goodWidth;
            }
            if (direction == Position.SOUTH) { // bottom
              return (!horizontal || gravity.second != Gravity.top) && goodWidth;
            }
          }
          return true;
        }
      };
    }

    mySelectionDecorator.clear();
    if (selection.size() == 1) {
      ResizeOperation.addResizePoints(mySelectionDecorator, (RadViewComponent)selection.get(0));
    } else {
      ResizeOperation.addResizePoints(mySelectionDecorator);
    }

    return mySelectionDecorator;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Actions
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Returns true if this LinearLayout supports switching orientation.
   *
   * @return true if this layout supports orientations
   */
  protected boolean supportsOrientation() {
    return true;
  }

  @Override
  public void addContainerSelectionActions(DesignerEditorPanel designer,
                                           DefaultActionGroup actionGroup,
                                           List<? extends RadViewComponent> selectedChildren) {
    RadViewComponent layout = (RadViewComponent)myContainer;
    List<? extends RadViewComponent> children = RadViewComponent.getViewComponents(layout.getChildren());

    boolean addSeparator = false;
    if (supportsOrientation()) {
      actionGroup.add(new OrientationAction(designer, layout, true));
      addSeparator = true;
    }
    if (isHorizontal()) {
      actionGroup.add(new BaselineAction(designer, layout));
      addSeparator = true;
    }
    if (addSeparator) {
      actionGroup.addSeparator();
      addSeparator = false;
    }
    if (!selectedChildren.isEmpty()) {
      actionGroup.add(new GravityAction(designer, selectedChildren));
      addSeparator = true;
    }

    // TODO: Create margin action?

    if (addSeparator) {
      actionGroup.addSeparator();
    }

    // Add in wrap width/height actions from the parent
    super.addContainerSelectionActions(designer, actionGroup, selectedChildren);

    actionGroup.addSeparator();

    // For some actions (clear weights, distribute weights), if you've selected just one child, we want
    // the action to work as if it's operating on all the children. If however you select multiple children,
    // we're distributing the width among just those children.
    List<? extends RadViewComponent> multipleChildren = selectedChildren.size() <= 1 ? children : selectedChildren;

    actionGroup.add(new DistributeWeightsAction(designer, layout, multipleChildren));
    if (!selectedChildren.isEmpty()) {
      actionGroup.add(new DominateWeightsAction(designer, layout, selectedChildren));
      actionGroup.add(new AssignWeightAction(designer, layout, selectedChildren));
    }
    actionGroup.add(new ClearWeightsAction(designer, layout, multipleChildren));
  }

  private static final List<Gravity> HORIZONTALS = Arrays.asList(Gravity.left, Gravity.center, Gravity.right, null);
  private static final List<Gravity> VERTICALS = Arrays.asList(Gravity.top, Gravity.center, Gravity.bottom, null);

  private class GravityAction extends AbstractGravityAction<Gravity> {
    private Gravity mySelection;

    public GravityAction(DesignerEditorPanel designer, List<? extends RadViewComponent> components) {
      super(designer, components);
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      boolean horizontal = isHorizontal();
      Gravity unknown = horizontal ? Gravity.left : Gravity.top;
      setItems(horizontal ? VERTICALS : HORIZONTALS, unknown);

      Iterator<? extends RadViewComponent> I = myComponents.iterator();
      mySelection = LinearLayoutOperation.getGravity(horizontal, I.next());

      while (I.hasNext()) {
        if (mySelection != LinearLayoutOperation.getGravity(horizontal, I.next())) {
          mySelection = unknown;
          break;
        }
      }

      return super.createPopupActionGroup(button);
    }

    @Override
    protected void update(Gravity item, Presentation presentation, boolean popup) {
      if (popup) {
        presentation.setIcon(mySelection == item ? CHECKED : null);
        presentation.setText(item == null ? "fill" : item.name());
      }
    }

    @Override
    protected boolean selectionChanged(final Gravity item) {
      execute(new Runnable() {
        @Override
        public void run() {
          LinearLayoutOperation.applyGravity(isHorizontal(), item, myComponents);
        }
      });

      return false;
    }

    @Override
    public void update() {
    }
  }

  @Override
  public boolean isWrapIn(List<RadComponent> components) {
    List<RadComponent> children = myContainer.getChildren();

    int[] indexes = new int[components.size()];
    for (int i = 0; i < indexes.length; i++) {
      indexes[i] = children.indexOf(components.get(i));
    }
    Arrays.sort(indexes);

    for (int i = 0; i < indexes.length - 1; i++) {
      if (indexes[i + 1] - indexes[i] != 1) {
        return false;
      }
    }

    return true;
  }

  @Override
  public void wrapIn(final RadViewComponent newParent, final List<RadViewComponent> components) throws Exception {
    final boolean horizontal = isHorizontal();
    RadViewComponent firstComponent = components.get(0);
    boolean single = components.size() == 1;
    String layoutWidth = single ? firstComponent.getTag().getAttributeValue("layout_width", SdkConstants.NS_RESOURCES) : "wrap_content";
    String layoutHeight = single ? firstComponent.getTag().getAttributeValue("layout_height", SdkConstants.NS_RESOURCES) : "wrap_content";
    String layoutGravity = firstComponent.getTag().getAttributeValue("layout_gravity", SdkConstants.NS_RESOURCES);

    if (horizontal) {
      for (RadViewComponent component : components.subList(1, components.size())) {
        String height = component.getTag().getAttributeValue("layout_height", SdkConstants.NS_RESOURCES);
        if ("fill_parent".equals(height) || "match_parent".equals(height)) {
          layoutHeight = "match_parent";
          layoutGravity = null;
        }
        if (layoutGravity != null &&
            layoutGravity.equals(component.getTag().getAttributeValue("layout_gravity", SdkConstants.NS_RESOURCES))) {

        }
      }
    }
    else {

    }

    if (newParent.getLayout() instanceof RadLinearLayout) {
      RadLinearLayout layout = (RadLinearLayout)newParent.getLayout();
      if (horizontal != layout.isHorizontal()) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            newParent.getTag().setAttribute("orientation", SdkConstants.NS_RESOURCES, horizontal ? "horizontal" : "vertical");
          }
        });
      }
    }
    else {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          for (RadViewComponent component : components) {
            RadComponentOperations.deleteAttribute(component.getTag(), "layout_gravity");
          }
        }
      });
    }
  }
}
