package org.jetbrains.android.dom.drawable;

import com.intellij.util.xml.Convert;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.AndroidResourceType;
import org.jetbrains.android.dom.Styleable;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.resources.ResourceValue;

import java.util.List;

public interface ListItemBase extends DrawableDomElement {
  @Convert(ResourceReferenceConverter.class)
  @AndroidResourceType("drawable")
  AndroidAttributeValue<ResourceValue> getDrawable();

  // See android.graphics.drawable.Drawable.createFromXmlInner
  List<DrawableSelector> getSelectors();
  List<AnimatedStateListTransition> getAnimatedSelectors();
  List<LevelList> getLevelLists();
  List<LayerList> getLayerLists();

  @Styleable("LayerDrawable")
  List<LayerList> getTransitions();

  List<Ripple> getRipples(); // API 21
  List<ColorDrawable> getColors();
  List<Shape> getShapes();
  // Being considered:
  //List<Vector> getVectors();
  List<Scale> getScales();
  List<Clip> getClips();
  List<Rotate> getRotates();
  List<AnimatedRotate> getAnimatedRotates();
  List<AnimationList> getAnimationLists();

  List<Inset> getInsets();
  List<BitmapElement> getBitmaps();
  List<NinePatchElement> getNinePatches();
}
