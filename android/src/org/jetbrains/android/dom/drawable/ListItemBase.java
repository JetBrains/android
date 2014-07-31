package org.jetbrains.android.dom.drawable;

import com.intellij.util.xml.Convert;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.AndroidResourceType;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.resources.ResourceValue;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public interface ListItemBase extends DrawableDomElement {
  @Convert(ResourceReferenceConverter.class)
  @AndroidResourceType("drawable")
  AndroidAttributeValue<ResourceValue> getDrawable();

  // See android.graphics.drawable.Drawable.createFromXmlInner
  List<DrawableSelector> getSelectors();
  List<AnimatedStateListTransition> getAnimatedSelectors();
  List<LevelList> getLevelLists();
  List<LayerList> getLayerLists();
  List<LayerList> getTransitions();
  List<Ripple> getRipples(); // API 21
  List<ColorDrawable> getColors();
  List<Shape> getShapes();
  // Being considered:
  //List<Vector> getVectors();
  List<InsetOrClipOrScale> getScales();
  List<InsetOrClipOrScale> getClips();
  List<InsetOrClipOrScale> getRotates();
  List<InsetOrClipOrScale> getAnimatedRotates();
  List<AnimationList> getAnimationLists();
  List<InsetOrClipOrScale> getInsets();
  List<BitmapOrNinePatchElement> getBitmaps();
  List<BitmapOrNinePatchElement> getNinePatches();
}
