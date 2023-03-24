/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.dom;

import com.android.sdklib.SdkVersionInfo;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.icons.ImageDataByPathLoaderKt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DefinesXml;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.dom.DomElementXmlDescriptor;
import icons.StudioIcons;
import org.jetbrains.android.dom.drawable.CustomDrawableDomElement;
import org.jetbrains.android.dom.drawable.CustomDrawableElementDescriptor;
import org.jetbrains.android.dom.layout.*;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.dom.xml.PreferenceElement;
import org.jetbrains.android.dom.xml.PreferenceElementDescriptor;
import org.jetbrains.android.facet.AndroidClassesForXmlUtilKt;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

import static com.android.SdkConstants.CLASS_DRAWABLE;

public class AndroidDomElementDescriptorProvider implements XmlElementDescriptorProvider {
  private static final Map<String, Ref<Icon>> ourViewTagName2Icon = ContainerUtil.createSoftMap();

  @Nullable
  private static XmlElementDescriptor getDescriptor(@NotNull DomElement domElement, @NotNull XmlTag tag) {
    AndroidFacet facet = AndroidFacet.getInstance(domElement);
    if (facet == null) return null;

    final DefinesXml definesXml = domElement.getAnnotation(DefinesXml.class);
    if (definesXml != null) {
      return new AndroidXmlTagDescriptor(new DomElementXmlDescriptor(domElement));
    }
    final PsiElement parent = tag.getParent();
    if (parent instanceof XmlTag) {
      final XmlElementDescriptor parentDescriptor = ((XmlTag)parent).getDescriptor();

      if (parentDescriptor instanceof AndroidXmlTagDescriptor) {
        XmlElementDescriptor domDescriptor = parentDescriptor.getElementDescriptor(tag, (XmlTag)parent);
        if (domDescriptor != null) {
          return new AndroidXmlTagDescriptor(domDescriptor);
        }
      }
    }
    return null;
  }

  @Override
  public XmlElementDescriptor getDescriptor(XmlTag tag) {
    DomElement element = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
    if (element instanceof LayoutElement) {
      return createLayoutElementDescriptor((LayoutElement)element, tag);
    }
    if (element instanceof PreferenceElement) {
      return createPreferenceElementDescriptor((PreferenceElement)element, tag);
    }
    if (element instanceof DataBindingElement) {
      return null;
    }
    if (element instanceof CustomDrawableDomElement) {
      return createDrawableElementDescriptor((CustomDrawableDomElement)element, tag);
    }
    if (element instanceof AndroidDomElement) {
      return getDescriptor(element, tag);
    }
    return null;
  }

  @Nullable
  public static PreferenceElementDescriptor createPreferenceElementDescriptor(@NotNull PreferenceElement element, @NotNull XmlTag tag) {
    AndroidFacet facet = AndroidFacet.getInstance(element);
    if (facet == null) return null;
    String baseClass = AndroidXmlResourcesUtil.PreferenceSource.getPreferencesSource(tag, facet).getQualifiedBaseClass();
    String baseGroupClass = AndroidXmlResourcesUtil.PreferenceSource.getPreferencesSource(tag, facet).getQualifiedGroupClass();
    final PsiClass preferenceClass = AndroidClassesForXmlUtilKt.findClassValidInXMLByName(facet, tag.getName(), baseClass);
    return new PreferenceElementDescriptor(preferenceClass, new DomElementXmlDescriptor(element), baseGroupClass);
  }

  @Nullable
  public static CustomDrawableElementDescriptor createDrawableElementDescriptor(@NotNull CustomDrawableDomElement element,
                                                                                @NotNull XmlTag tag) {
    AndroidFacet facet = AndroidFacet.getInstance(element);
    if (facet == null) return null;
    final PsiClass preferenceClass = AndroidClassesForXmlUtilKt.findClassValidInXMLByName(facet, tag.getName(), CLASS_DRAWABLE);
    return new CustomDrawableElementDescriptor(preferenceClass, new DomElementXmlDescriptor(element));
  }

  @Nullable
  public static LayoutElementDescriptor createLayoutElementDescriptor(@NotNull LayoutElement element, @NotNull XmlTag tag) {
    if (element instanceof View) {
      PsiClass viewClass = ((View)element).getViewClass().getValue();
      return new LayoutViewElementDescriptor(viewClass, (View)element);
    }
    if (element instanceof LayoutViewElement) {
      AndroidFacet facet = AndroidFacet.getInstance(element);
      if (facet == null) return null;
      final PsiClass viewClass = AndroidClassesForXmlUtilKt.findViewValidInXMLByName(facet, tag.getName());
      return new LayoutViewElementDescriptor(viewClass, (LayoutViewElement)element);
    }
    return new LayoutElementDescriptor(new DomElementXmlDescriptor(element));
  }

  @Nullable
  public static Icon getIconForViewTag(@NotNull String tagName) {
    return getIconForView(tagName);
  }

  @Nullable
  private static Icon getIconForView(@NotNull String keyName) {
    synchronized (ourViewTagName2Icon) {
      if (ourViewTagName2Icon.isEmpty()) {
        final Map<String, Icon> map = getInitialViewTagName2IconMap();

        for (Map.Entry<String, Icon> entry : map.entrySet()) {
          ourViewTagName2Icon.put(entry.getKey(), Ref.create(entry.getValue()));
        }
      }
      Ref<Icon> iconRef = ourViewTagName2Icon.get(keyName);

      if (iconRef == null) {
        // Find icons from StudioIcons.LayoutEditor.Palette first, then AndroidIcons.Views.
        Icon icon = ImageDataByPathLoaderKt.getReflectiveIcon("StudioIcons.LayoutEditor.Palette." + convertToPaletteIconName(keyName), StudioIcons.class.getClassLoader());
        iconRef = Ref.create(icon);
        ourViewTagName2Icon.put(keyName, iconRef);
      }
      return iconRef.get();
    }
  }

  /**
   * Utility function to convert tagName (e.g. TextView, CheckBox, etc.) to the icon name of {@link StudioIcons.LayoutEditor.Palette}.
   * The keys in {@link StudioIcons} are always upper case with underline.
   *
   * @param tagName the name of the widget tag.
   * @return the icon name matches the format of {@link StudioIcons}.
   */
  @NotNull
  private static String convertToPaletteIconName(@NotNull String tagName) {
    return StringUtil.toUpperCase(SdkVersionInfo.camelCaseToUnderlines(tagName));
  }

  @NotNull
  private static Map<String, Icon> getInitialViewTagName2IconMap() {
    final HashMap<String, Icon> map = new HashMap<>();
    map.put("ActionMenuView", StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ);
    map.put("ContentFrameLayout", StudioIcons.LayoutEditor.Palette.FRAME_LAYOUT);
    map.put("FitWindowsLinearLayout", StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ);
    map.put("FragmentContainerView", StudioIcons.LayoutEditor.Palette.FRAME_LAYOUT);
    map.put("ImageFilterButton", StudioIcons.LayoutEditor.Palette.IMAGE_BUTTON);
    map.put("ImageFilterView", StudioIcons.LayoutEditor.Palette.IMAGE_VIEW);
    // The default icon for LinearLayout is horizontal version.
    map.put("LinearLayout", StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ);
    map.put("MockView", StudioIcons.LayoutEditor.Palette.PLACEHOLDER);
    map.put("MotionLayout", StudioIcons.LayoutEditor.Motion.MOTION_LAYOUT);
    map.put("RecyclerViewImpl", StudioIcons.LayoutEditor.Palette.RECYCLER_VIEW);
    map.put("SlidingTabIndicator", StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ);
    map.put("ViewPager2", StudioIcons.LayoutEditor.Palette.VIEW_PAGER);
    map.put("ViewStubCompat", StudioIcons.LayoutEditor.Palette.VIEW_STUB);

    return map;
  }
}
