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
package com.intellij.android.designer.model;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.designer.AndroidMetaModel;
import com.android.tools.idea.designer.Insets;
import com.android.tools.idea.rendering.IncludeReference;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.intellij.android.designer.AndroidDesignerUtils;
import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.android.designer.designSurface.TransformedComponent;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.ScalableComponent;
import com.intellij.designer.model.*;
import com.intellij.designer.palette.PaletteItem;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.hash.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.rendering.IncludeReference.ATTR_RENDER_IN;

/**
 * @author Alexander Lobas
 */
public class RadViewComponent extends RadVisualComponent {
  private final List<RadComponent> myChildren = new ArrayList<RadComponent>();
  protected ViewInfo myViewInfo;
  private Insets myMargins;
  private Insets myPadding;
  private XmlTag myTag;
  private List<Property> myProperties;
  private PaletteItem myPaletteItem;

  public RadViewComponent() {
  }

  @NotNull
  public XmlTag getTag() {
    if (myTag == null || myTag.getParent() == null || !myTag.isValid()) {
      return EmptyXmlTag.INSTANCE;
    }
    return myTag;
  }

  public void setTag(@Nullable XmlTag tag) {
    myTag = tag;
  }

  @Nullable
  public String getAttribute(@NotNull String name, @Nullable String namespace) {
    if (namespace != null) {
      return myTag.getAttributeValue(name, namespace);
    } else {
      return myTag.getAttributeValue(name);
    }
  }

  public void setAttribute(@NotNull String name, @Nullable String namespace, @Nullable String value) {
    if (namespace != null) {
      //noinspection ConstantConditions
      myTag.setAttribute(name, namespace, value);
    } else {
      //noinspection ConstantConditions
      myTag.setAttribute(name, value);
    }
  }

  public void updateTag(XmlTag tag) {
    setTag(tag);

    int size = myChildren.size();
    XmlTag[] tags = tag.getSubTags();

    for (int i = 0; i < size; i++) {
      RadViewComponent child = (RadViewComponent)myChildren.get(i);
      child.updateTag(tags[i]);
    }
  }

  public String getCreationXml() {
    throw new UnsupportedOperationException();
  }

  public ViewInfo getViewInfo() {
    return myViewInfo;
  }

  public void setViewInfo(ViewInfo viewInfo) {
    myViewInfo = viewInfo;
    myMargins = null;
  }

  @Override
  public AndroidMetaModel getMetaModel() {
    return (AndroidMetaModel)super.getMetaModel();
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public String ensureId() {
    String id = getId();
    if (id == null) {
      id = IdManager.get().assignId(this);
    }
    return id;
  }

  @Nullable
  public String getId() {
    return StringUtil.nullize(getTag().getAttributeValue(ATTR_ID, ANDROID_URI), false);
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public int getBaseline() {
    try {
      Object viewObject = myViewInfo.getViewObject();
      return (Integer)viewObject.getClass().getMethod("getBaseline").invoke(viewObject);
    }
    catch (Throwable e) {
    }

    return -1;
  }

  @NotNull
  public Insets getMargins() {
    if (myMargins == null) {
      try {
        Object layoutParams = myViewInfo.getLayoutParamsObject();
        Class<?> layoutClass = layoutParams.getClass();

        int left = fixDefault(layoutClass.getField("leftMargin").getInt(layoutParams)); // TODO: startMargin?
        int top = fixDefault(layoutClass.getField("topMargin").getInt(layoutParams));
        int right = fixDefault(layoutClass.getField("rightMargin").getInt(layoutParams));
        int bottom = fixDefault(layoutClass.getField("bottomMargin").getInt(layoutParams));
        if (left == 0 && top == 0 && right == 0 && bottom == 0) {
          myMargins = Insets.NONE;
        } else {
          myMargins = new Insets(left, top, right, bottom);
        }
      }
      catch (Throwable e) {
        myMargins = Insets.NONE;
      }
    }
    return myMargins;
  }

  @NotNull
  public Insets getPadding() {
    if (myPadding == null) {
      try {
        Object layoutParams = myViewInfo.getViewObject();
        Class<?> layoutClass = layoutParams.getClass();

        int left = fixDefault((Integer)layoutClass.getMethod("getPaddingLeft").invoke(layoutParams)); // TODO: getPaddingStart!
        int top = fixDefault((Integer)layoutClass.getMethod("getPaddingTop").invoke(layoutParams));
        int right = fixDefault((Integer)layoutClass.getMethod("getPaddingRight").invoke(layoutParams));
        int bottom = fixDefault((Integer)layoutClass.getMethod("getPaddingBottom").invoke(layoutParams));
        if (left == 0 && top == 0 && right == 0 && bottom == 0) {
          myPadding = Insets.NONE;
        } else {
          myPadding = new Insets(left, top, right, bottom);
        }
      }
      catch (Throwable e) {
        myPadding = Insets.NONE;
      }
    }
    return myPadding;
  }

  public Insets getMargins(Component relativeTo) {
    Insets margins = getMargins();
    if (margins.isEmpty()) {
      return margins;
    }
    return fromModel(relativeTo, margins);
  }

  public Insets getPadding(Component relativeTo) {
    Insets padding = getPadding();
    if (padding.isEmpty()) {
      return padding;
    }
    return fromModel(relativeTo, padding);
  }

  private static int fixDefault(int value) {
    return value == Integer.MIN_VALUE ? 0 : value;
  }

  private static final int WRAP_CONTENT = 0 << 30;

  public boolean calculateWrapSize(@NotNull Dimension wrapSize, @Nullable Rectangle bounds) {
    if (wrapSize.width == -1 || wrapSize.height == -1) {
      try {
        Object viewObject = myViewInfo.getViewObject();
        Class<?> viewClass = viewObject.getClass();

        viewClass.getMethod("forceLayout").invoke(viewObject);
        viewClass.getMethod("measure", int.class, int.class).invoke(viewObject, WRAP_CONTENT, WRAP_CONTENT);

        if (wrapSize.width == -1) {
          wrapSize.width = (Integer)viewClass.getMethod("getMeasuredWidth").invoke(viewObject);
        }
        if (wrapSize.height == -1) {
          wrapSize.height = (Integer)viewClass.getMethod("getMeasuredHeight").invoke(viewObject);
        }

        return true;
      }
      catch (Throwable e) {
        if (bounds != null) {
          if (wrapSize.width == -1) {
            wrapSize.width = bounds.width;
          }
          if (wrapSize.height == -1) {
            wrapSize.height = bounds.height;
          }
        }
      }
    }

    return false;
  }

  @Nullable
  public Dimension calculateWrapSize(EditableArea area) {
    if (myViewInfo != null) {
      Dimension dimension = new Dimension(-1, -1);
      boolean measured = calculateWrapSize(dimension, null);
      if (measured) {
        return dimension;
      }
    }
    RadComponent parent = getParent();
    if (!(parent instanceof RadViewComponent)) {
      return null;
    }
    XmlTag parentTag = ((RadViewComponent)parent).getTag();
    if (parentTag != null) {
      RenderTask task = AndroidDesignerUtils.createRenderTask(area);
      if (task == null) {
        return null;
      }

      ViewInfo viewInfo = task.measureChild(getTag(), new RenderTask.AttributeFilter() {
        @Override
        public String getAttribute(@NotNull XmlTag n, @Nullable String namespace, @NotNull String localName) {
          if ((ATTR_LAYOUT_WIDTH.equals(localName) || ATTR_LAYOUT_HEIGHT.equals(localName)) && ANDROID_URI.equals(namespace)) {
            return VALUE_WRAP_CONTENT;
          } else if (ATTR_LAYOUT_WEIGHT.equals(localName) && ANDROID_URI.equals(namespace)) {
            return ""; // unset
          }

          return null; // use default
        }
      });
      if (viewInfo != null) {
        viewInfo = RenderService.getSafeBounds(viewInfo);
        return new Dimension(viewInfo.getRight() - viewInfo.getLeft(), viewInfo.getBottom() - viewInfo.getTop());
      }
    }

    return null;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @NotNull
  @Override
  public List<RadComponent> getChildren() {
    return myChildren;
  }

  @Override
  public boolean isBackground() {
    // In Android layouts there are two levels of parents; at the top level
    // there is the "Device Screen", which is not deletable.
    // Below that is the root layout, which we want to consider as the background,
    // such that a marquee selection within the layout drag will select its children.
    // This will also make select all operate on the children of the layout.
    RadComponent parent = getParent();
    if (parent != null) {
      if (parent.getParent() == null && !parent.getMetaModel().isTag(VIEW_MERGE)) {
        IncludeReference includeContext = parent.getClientProperty(ATTR_RENDER_IN);
        if (includeContext != null && includeContext != IncludeReference.NONE) {
          return false;
        }
        return true;
      }
    }

    return parent == null;
  }

  @Override
  public boolean canDelete() {
    // Can't delete root component (isBackground specifically returns false to let
    // you select these so we can't rely on just isBackground() as before)
    RadComponent parent = getParent();
    if (parent == null || parent.getParent() == null && !parent.getMetaModel().isTag(VIEW_MERGE)) {
      return false;
    }
    return super.canDelete();
  }

  @Override
  public void delete() throws Exception {
    if (getParent() != null) {
      removeFromParent();
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myTag.delete();
      }
    });
  }

  @Override
  public List<Property> getProperties() {
    return myProperties;
  }

  public void setProperties(List<Property> properties) {
    myProperties = properties;
  }

  @Override
  public List<Property> getInplaceProperties() throws Exception {
    List<Property> properties = super.getInplaceProperties();
    Property idProperty = PropertyTable.findProperty(myProperties, "id");
    if (idProperty != null) {
      properties.add(idProperty);
    }
    return properties;
  }

  @Override
  public void copyTo(Element parent) throws Exception {
    // skip root
    if (getParent() != null) {
      Element component = new Element("component");
      component.setAttribute("tag", myTag.getName());

      XmlAttribute[] attributes = myTag.getAttributes();
      if (attributes.length > 0) {
        Element properties = new Element("properties");
        component.addContent(properties);

        Map<String, Element> namespaces = new HashMap<String, Element>();

        for (XmlAttribute attribute : attributes) {
          String namespace = attribute.getNamespacePrefix();
          if (namespace.length() == 0) {
            properties.setAttribute(attribute.getName(), attribute.getValue());
          }
          else {
            Element element = namespaces.get(namespace);
            if (element == null) {
              element = new Element(namespace);
              namespaces.put(namespace, element);
            }

            element.setAttribute(attribute.getLocalName(), attribute.getValue());
          }
        }

        for (Element element : namespaces.values()) {
          properties.addContent(element);
        }
      }

      parent.addContent(component);
      parent = component;
    }

    for (RadComponent child : myChildren) {
      child.copyTo(parent);
    }
  }

  @Override
  public boolean isSameType(@NotNull RadComponent other) {
    if (myTag != null) {
      if (!(other instanceof RadViewComponent)) {
        return false;
      }
      RadViewComponent otherView = (RadViewComponent)other;
      if (otherView.myTag == null) {
        return false;
      }
      return myTag.getName().equals(otherView.myTag.getName());
    }
    return super.isSameType(other);
  }

  @Override
  public RadComponent morphingTo(MetaModel target) throws Exception {
    return new ComponentMorphingTool(this, this, target, null).result();
  }

  /** Sets the palette item this component was initially created from */
  public void setInitialPaletteItem(PaletteItem paletteItem) {
    myPaletteItem = paletteItem;
  }

  /**
   * The palette item this component was initially created from, if known.
   * This will be null for widgets created through other means (pasting, typing code, etc)
   * or after an IDE restart.
   */
  @Nullable
  public PaletteItem getInitialPaletteItem() {
    return myPaletteItem;
  }

  /**
   * Like {@link #toModel(java.awt.Component, java.awt.Rectangle)}, but
   * also translates from pixels in the source, to device independent pixels
   * in the model.
   * <p>
   * A lot of client code needs to compute model dp by doing arithmetic on
   * bounds in the tool (which are scaled). By first computing to model
   * pixels (with {@link #toModel(java.awt.Component, java.awt.Rectangle)},
   * and <b>then</b> computing the dp from there, you introduce two levels
   * of rounding.
   * <p>
   * This method performs both computations in a single go which reduces
   * the amount of rounding error.
   */
  public Rectangle toModelDp(int dpi, @NotNull Component source, @NotNull Rectangle rectangle) {
    Component nativeComponent = getNativeComponent();
    Rectangle bounds = nativeComponent == source
                       ? rectangle : SwingUtilities.convertRectangle(source, rectangle, nativeComponent);

    // To convert px to dpi, dp = px * 160 / dpi
    double x = 160 * bounds.x;
    double y = 160 * bounds.y;
    double w = 160 * bounds.width;
    double h = 160 * bounds.height;

    if (nativeComponent != source && nativeComponent instanceof TransformedComponent) {
      TransformedComponent transform = (TransformedComponent)nativeComponent;
      x -= transform.getShiftX();
      y -= transform.getShiftY();
      double zoom = transform.getScale();
      if (zoom != 1) {
        x /= zoom;
        y /= zoom;
        w /= zoom;
        h /= zoom;
      }
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    double dpiDouble = dpi;
    return new Rectangle(
      (int)(x / dpiDouble),
      (int)(y / dpiDouble),
      (int)(w / dpiDouble),
      (int)(h / dpiDouble));
  }

  /**
   * Like {@link #toModel(java.awt.Component, java.awt.Point)}, but
   * also translates from pixels in the source, to device independent pixels
   * in the model.
   * <p>
   * A lot of client code needs to compute model dp by doing arithmetic on
   * bounds in the tool (which are scaled). By first computing to model
   * pixels (with {@link #toModel(java.awt.Component, java.awt.Point)},
   * and <b>then</b> computing the dp from there, you introduce two levels
   * of rounding.
   * <p>
   * This method performs both computations in a single go which reduces
   * the amount of rounding error.
   */
  public Point toModelDp(int dpi, @NotNull Component source, @NotNull Point point) {
    Component nativeComponent = getNativeComponent();
    Point bounds = nativeComponent == source
                       ? point : SwingUtilities.convertPoint(source, point, nativeComponent);

    // To convert px to dpi, dp = px * 160 / dpi
    double x = 160 * bounds.x;
    double y = 160 * bounds.y;

    if (nativeComponent != source && nativeComponent instanceof TransformedComponent) {
      TransformedComponent transform = (TransformedComponent)nativeComponent;
      x -= transform.getShiftX();
      y -= transform.getShiftY();
      double zoom = transform.getScale();
      if (zoom != 1) {
        x /= zoom;
        y /= zoom;
      }
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    double dpiDouble = dpi;
    return new Point(
      (int)(x / dpiDouble),
      (int)(y / dpiDouble));
  }

  /**
   * Like {@link #toModel(java.awt.Component, java.awt.Dimension)}, but
   * also translates from pixels in the source, to device independent pixels
   * in the model.
   * <p>
   * A lot of client code needs to compute model dp by doing arithmetic on
   * bounds in the tool (which are scaled). By first computing to model
   * pixels (with {@link #toModel(java.awt.Component, java.awt.Dimension)},
   * and <b>then</b> computing the dp from there, you introduce two levels
   * of rounding.
   * <p>
   * This method performs both computations in a single go which reduces
   * the amount of rounding error.
   */
  public Dimension toModelDp(int dpi, @NotNull Component source, @NotNull Dimension size) {
    Component nativeComponent = getNativeComponent();
    size = new Dimension(size);

    // To convert px to dpi, dp = px * 160 / dpi
    double w = 160 * size.width;
    double h = 160 * size.height;

    if (nativeComponent != source && nativeComponent instanceof ScalableComponent) {
      ScalableComponent scalableComponent = (ScalableComponent)nativeComponent;
      double zoom = scalableComponent.getScale();
      if (zoom != 1) {
        w /= zoom;
        h /= zoom;
      }
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    double dpiDouble = dpi;
    return new Dimension(
      (int)(w / dpiDouble),
      (int)(h / dpiDouble));
  }

  public Insets fromModel(@NotNull Component target, @NotNull Insets insets) {
    if (insets.isEmpty()) {
      return insets;
    }

    Component nativeComponent = getNativeComponent();
    if (target != nativeComponent && nativeComponent instanceof ScalableComponent) {
      ScalableComponent scalableComponent = (ScalableComponent)nativeComponent;
      double zoom = scalableComponent.getScale();
      if (zoom != 1) {
        return new Insets((int)(insets.left * zoom), (int)(insets.top * zoom), (int)(insets.right * zoom),
                           (int)(insets.bottom * zoom));
      }
    }

    return insets;
  }

  public Insets toModel(@NotNull Component source, @NotNull Insets insets) {
    if (insets.isEmpty()) {
      return insets;
    }

    Component nativeComponent = getNativeComponent();

    if (source != nativeComponent && nativeComponent instanceof ScalableComponent) {
      ScalableComponent scalableComponent = (ScalableComponent)nativeComponent;
      double zoom = scalableComponent.getScale();
      if (zoom != 1) {
        return new Insets((int)(insets.left / zoom), (int)(insets.top / zoom), (int)(insets.right / zoom),
                           (int)(insets.bottom / zoom));
      }
    }

    return insets;
  }

  /**
   * Returns the bounds of this {@linkplain RadViewComponent} in the model, minus any
   * padding (if any).
   * <p/>
   * Caller should <b>not</b> modify this rectangle.
   *
   * @return the padded bounds of this {@linkplain RadViewComponent} in the model coordinate system
   *         (e.g. unaffected by a view zoom for example)
   */
  public Rectangle getPaddedBounds() {
    Rectangle bounds = getBounds();

    Insets padding = getPadding();
    if (padding == Insets.NONE) {
      return bounds;
    }

    return new Rectangle(bounds.x + padding.left,
                         bounds.y + padding.top,
                         Math.max(0, bounds.width - padding.left - padding.right),
                         Math.max(0, bounds.height - padding.top - padding.bottom));
  }

  /**
   * Like {@link #getBounds(java.awt.Component)}, but rather than applying to the
   * bounds of the view, it applies to the padded bounds (e.g. with insets applied).
   *
   * @param relativeTo the component whose coordinate system the model bounds should
   *                   be shifted and scaled into
   * @return the padded bounds of this {@linkplain RadComponent} in the given coordinate system
   */
  public Rectangle getPaddedBounds(Component relativeTo) {
    return fromModel(relativeTo, getPaddedBounds());
  }

  /** Extracts the {@link RadViewComponent} elements from the list */
  @NotNull
  @SuppressWarnings("unchecked")
  public static List<RadViewComponent> getViewComponents(@NotNull List<? extends RadComponent> components) {
    for (RadComponent component : components) {
      if (!(component instanceof RadViewComponent)) {
        List<RadViewComponent> newList = new ArrayList<RadViewComponent>(components.size() - 1);
        for (RadComponent c : components) {
          if (c instanceof RadViewComponent) {
            newList.add((RadViewComponent)c);
          }
        }
        return newList;
      }
    }

    return (List<RadViewComponent>)(List)components;
  }

  /** Adds in actions for this component into the given popup context menu
   *  Return true if anything was added.
   */
  public boolean addPopupActions(@NotNull AndroidDesignerEditorPanel designer,
                                 @NotNull DefaultActionGroup beforeGroup,
                                 @NotNull DefaultActionGroup afterGroup,
                                 @Nullable JComponent shortcuts,
                                 @NotNull List<RadComponent> selection) {
    return false;
  }

  // Coordinate transformations.
  // These are like the implementations in RadVisualComponent (the parent class), except
  // that instead of looking for a ScalableComponent it checks for its sub-interface,
  // TransformedComponent, which adds in translation as well as part of the transform.

  @Override
  public Rectangle fromModel(@NotNull Component target, @NotNull Rectangle bounds) {
    Component nativeComponent = getNativeComponent();
    if (target != nativeComponent && nativeComponent instanceof TransformedComponent) {
      TransformedComponent transform = (TransformedComponent)nativeComponent;
      int shiftX = transform.getShiftX();
      int shiftY = transform.getShiftY();
      double zoom = transform.getScale();
      if (zoom != 1 || shiftX != 0 || shiftY != 0) {
        bounds = new Rectangle(bounds);
        bounds.x *= zoom;
        bounds.y *= zoom;
        bounds.width *= zoom;
        bounds.height *= zoom;
        bounds.x += shiftX;
        bounds.y += shiftY;
      }
    }

    return nativeComponent == target
           ? new Rectangle(bounds) :
           SwingUtilities.convertRectangle(nativeComponent, bounds, target);
  }

  @Override
  public Rectangle toModel(@NotNull Component source, @NotNull Rectangle rectangle) {
    Component nativeComponent = getNativeComponent();
    Rectangle bounds = nativeComponent == source
                       ? new Rectangle(rectangle) : SwingUtilities.convertRectangle(source, rectangle, nativeComponent);

    if (nativeComponent != source && nativeComponent instanceof TransformedComponent) {
      TransformedComponent transform = (TransformedComponent)nativeComponent;
      bounds.x -= transform.getShiftX();
      bounds.y -= transform.getShiftY();
      double zoom = transform.getScale();
      if (zoom != 1) {
        bounds = new Rectangle(bounds);
        bounds.x /= zoom;
        bounds.y /= zoom;
        bounds.width /= zoom;
        bounds.height /= zoom;
      }
    }

    return bounds;
  }

  @Override
  public Point fromModel(@NotNull Component target, @NotNull Point point) {
    Component nativeComponent = getNativeComponent();
    if (target != nativeComponent && nativeComponent instanceof TransformedComponent) {
      TransformedComponent transform = (TransformedComponent)nativeComponent;
      int shiftX = transform.getShiftX();
      int shiftY = transform.getShiftY();
      double zoom = transform.getScale();
      if (zoom != 1 || shiftX != 0 || shiftY != 0) {
        point = new Point(point);
        point.x *= zoom;
        point.y *= zoom;
        point.x += shiftX;
        point.y += shiftY;
      }
    }

    return nativeComponent == target
           ? new Point(point) :
           SwingUtilities.convertPoint(nativeComponent, point, target);
  }

  @Override
  public Point toModel(@NotNull Component source, @NotNull Point point) {
    Component nativeComponent = getNativeComponent();
    Point p = nativeComponent == source
              ? new Point(point) : SwingUtilities.convertPoint(source, point, nativeComponent);

    if (nativeComponent != source && nativeComponent instanceof TransformedComponent) {
      TransformedComponent transform = (TransformedComponent)nativeComponent;
      p.x -= transform.getShiftX();
      p.y -= transform.getShiftY();
      double zoom = transform.getScale();
      if (zoom != 1) {
        p = new Point(p);
        p.x /= zoom;
        p.y /= zoom;
      }
    }

    return p;
  }

  @Override
  public Point convertPoint(Component relativeFrom, int x, int y) {
    Component nativeComponent = getNativeComponent();
    Point p = nativeComponent == relativeFrom ? new Point(x, y) : SwingUtilities.convertPoint(relativeFrom, x, y, nativeComponent);

    if (nativeComponent != relativeFrom && nativeComponent instanceof TransformedComponent) {
      TransformedComponent transform = (TransformedComponent)nativeComponent;
      p.x -= transform.getShiftX();
      p.y -= transform.getShiftY();
      double zoom = transform.getScale();
      if (zoom != 1) {
        p.x /= zoom;
        p.y /= zoom;
      }
    }

    return p;
  }
}
