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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ACCESSIBILITY_TRAVERSAL_AFTER;
import static com.android.SdkConstants.ATTR_ACCESSIBILITY_TRAVERSAL_BEFORE;
import static com.android.SdkConstants.ATTR_AUTOFILL_HINTS;
import static com.android.SdkConstants.ATTR_CHECKED_BUTTON;
import static com.android.SdkConstants.ATTR_CHECKED_CHIP;
import static com.android.SdkConstants.ATTR_CONSTRAINT_SET_END;
import static com.android.SdkConstants.ATTR_CONSTRAINT_SET_START;
import static com.android.SdkConstants.ATTR_DERIVE_CONSTRAINTS_FROM;
import static com.android.SdkConstants.ATTR_FONT_FAMILY;
import static com.android.SdkConstants.ATTR_HIDE_MOTION_SPEC;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_LABEL_FOR;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ATTR_LAYOUT_ABOVE;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_BEHAVIOR;
import static com.android.SdkConstants.ATTR_LAYOUT_BELOW;
import static com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_END_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_END_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_MANAGER;
import static com.android.SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_START_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_START_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_START_OF;
import static com.android.SdkConstants.ATTR_LISTITEM;
import static com.android.SdkConstants.ATTR_MENU;
import static com.android.SdkConstants.ATTR_MOTION_TARGET_ID;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_ON_CLICK;
import static com.android.SdkConstants.ATTR_SHOW_MOTION_SPEC;
import static com.android.SdkConstants.ATTR_SRC;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.ATTR_TITLE;
import static com.android.SdkConstants.CONSTRAINT_REFERENCED_IDS;
import static com.android.AndroidXConstants.COORDINATOR_LAYOUT;
import static com.android.SdkConstants.FRAGMENT_CONTAINER_VIEW;
import static com.android.AndroidXConstants.RECYCLER_VIEW;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.SdkConstants.VIEW_FRAGMENT;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.support.AndroidxName;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.XmlName;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import com.android.tools.dom.attrs.AttributeDefinition;
import com.android.tools.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.ToolsAttributeDefinitionsImpl;
import org.jetbrains.android.dom.converters.AndroidConstraintIdsConverter;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
import org.jetbrains.android.dom.converters.AutoFillHintsConverter;
import org.jetbrains.android.dom.converters.ColorConverter;
import org.jetbrains.android.dom.converters.CompositeConverter;
import org.jetbrains.android.dom.converters.DimensionConverter;
import org.jetbrains.android.dom.converters.FlagConverter;
import org.jetbrains.android.dom.converters.FloatConverter;
import org.jetbrains.android.dom.converters.FragmentClassConverter;
import org.jetbrains.android.dom.converters.IntegerConverter;
import org.jetbrains.android.dom.converters.OnClickConverter;
import org.jetbrains.android.dom.converters.PackageClassConverter;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.converters.StaticEnumConverter;
import org.jetbrains.android.dom.converters.StringResourceAdapterConverter;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.manifest.Action;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.Category;
import org.jetbrains.android.dom.manifest.IntentFilter;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.Provider;
import org.jetbrains.android.dom.manifest.Receiver;
import org.jetbrains.android.dom.manifest.Service;
import org.jetbrains.android.dom.menu.MenuItem;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.xml.XmlResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.MigrateToAndroidxUtil;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@SuppressWarnings({"EnumSwitchStatementWhichMissesCases"})
public class AndroidDomUtil {
  private static final Logger LOG = Logger.getInstance(AndroidDomUtil.class);

  private static final AndroidxName RECYCLER_VIEW_LAYOUT_MANAGER_NAME =
    new AndroidxName("android.support.v7.widget.RecyclerView$LayoutManager", "androidx.recyclerview.widget.RecyclerView$LayoutManager");
  private static final String[] RECYCLER_VIEW_LAYOUT_MANAGER_NAMES =
    {RECYCLER_VIEW_LAYOUT_MANAGER_NAME.oldName(), RECYCLER_VIEW_LAYOUT_MANAGER_NAME.newName()};
  private static final String[] RECYCLER_VIEW_LAYOUT_MANAGER_BASE_PACKAGES =
    {"android.support.v7.widget.", "androidx.recyclerview.widget."};
  private static final AndroidxName COORDINATOR_LAYOUT_BEHAVIOR_NAME =
    new AndroidxName("android.support.design.widget.CoordinatorLayout$Behavior", "androidx.coordinatorlayout.widget.CoordinatorLayout$Behavior");
  private static final String[] COORDINATOR_LAYOUT_BEHAVIOR_NAMES =
    {COORDINATOR_LAYOUT_BEHAVIOR_NAME.oldName(), COORDINATOR_LAYOUT_BEHAVIOR_NAME.newName()};

  public static final StaticEnumConverter BOOLEAN_CONVERTER = new StaticEnumConverter(VALUE_TRUE, VALUE_FALSE);
  private static final Multimap<String, ResourceType> SPECIAL_RESOURCE_TYPES = ArrayListMultimap.create();
  private static final PackageClassConverter ACTIVITY_CONVERTER = new PackageClassConverter(AndroidUtils.ACTIVITY_BASE_CLASS_NAME);

  private static final Converter RECYCLER_VIEW_LAYOUT_MANAGER_CONVERTER =
    new StringResourceAdapterConverter(
      new PackageClassConverter.Builder()
        .useManifestBasePackage(true)
        .withExtraBasePackages(RECYCLER_VIEW_LAYOUT_MANAGER_BASE_PACKAGES)
        .completeLibraryClasses(true)
        .withExtendClassNames(RECYCLER_VIEW_LAYOUT_MANAGER_NAMES)
        .build());

  private static final Converter COORDINATOR_LAYOUT_BEHAVIOR_CONVERTER =
    new StringResourceAdapterConverter(
      new PackageClassConverter.Builder()
        .useManifestBasePackage(true)
        .completeLibraryClasses(true)
        .withExtendClassNames(COORDINATOR_LAYOUT_BEHAVIOR_NAMES)
        .build());

  private static final FragmentClassConverter FRAGMENT_CLASS_CONVERTER = new FragmentClassConverter();

  private static final AndroidConstraintIdsConverter CONSTRAINT_REFERENCED_IDS_CONVERTER = new AndroidConstraintIdsConverter();

  private static final ToolsAttributeDefinitionsImpl TOOLS_ATTRIBUTE_DEFINITIONS = new ToolsAttributeDefinitionsImpl();

  private static final AutoFillHintsConverter AUTOFILL_HINTS_CONVERTER = new AutoFillHintsConverter();

  // List of available font families extracted from framework's fonts.xml
  // Used to provide completion for values of android:fontFamily attribute
  // https://android.googlesource.com/platform/frameworks/base/+/android-6.0.0_r5/data/fonts/fonts.xml
  public static final List<String> AVAILABLE_FAMILIES = ImmutableList
      .of("sans-serif", "sans-serif-thin", "sans-serif-light", "sans-serif-medium", "sans-serif-black",
          "sans-serif-condensed", "sans-serif-condensed-light", "sans-serif-condensed-medium",
          "serif", "monospace", "serif-monospace", "casual", "cursive", "sans-serif-smallcaps");

  static {
    // This section adds additional resource type registrations where the attrs metadata is lacking. For
    // example, attrs_manifest.xml tells us that the android:icon attribute can be a reference, but not
    // that it's a reference to a drawable.
    addSpecialResourceType(ResourceType.STRING, ATTR_LABEL, "description", ATTR_TITLE);
    addSpecialResourceType(ResourceType.DRAWABLE, ATTR_ICON, ATTR_SRC);
    addSpecialResourceType(ResourceType.COLOR, ATTR_SRC);
    addSpecialResourceType(ResourceType.STYLE, ATTR_THEME, ATTR_STYLE);
    addSpecialResourceType(ResourceType.ANIM, "animation", ATTR_SHOW_MOTION_SPEC, ATTR_HIDE_MOTION_SPEC);
    addSpecialResourceType(ResourceType.ID, ATTR_ID, ATTR_LAYOUT_TO_RIGHT_OF, ATTR_LAYOUT_TO_LEFT_OF, ATTR_LAYOUT_ABOVE,
                           ATTR_LAYOUT_BELOW, ATTR_LAYOUT_ALIGN_BASELINE, ATTR_LAYOUT_ALIGN_LEFT, ATTR_LAYOUT_ALIGN_TOP,
                           ATTR_LAYOUT_ALIGN_RIGHT, ATTR_LAYOUT_ALIGN_BOTTOM, ATTR_LAYOUT_ALIGN_START, ATTR_LAYOUT_ALIGN_END,
                           ATTR_LAYOUT_TO_START_OF, ATTR_LAYOUT_TO_END_OF, ATTR_CHECKED_BUTTON, ATTR_ACCESSIBILITY_TRAVERSAL_BEFORE,
                           ATTR_ACCESSIBILITY_TRAVERSAL_AFTER, ATTR_LABEL_FOR, ATTR_CHECKED_CHIP,
                           ATTR_LAYOUT_LEFT_TO_LEFT_OF, ATTR_LAYOUT_LEFT_TO_RIGHT_OF, ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
                           ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
                           ATTR_LAYOUT_BOTTOM_TO_TOP_OF, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF,
                           ATTR_LAYOUT_START_TO_END_OF, ATTR_LAYOUT_START_TO_START_OF, ATTR_LAYOUT_END_TO_START_OF,
                           ATTR_LAYOUT_END_TO_END_OF, ATTR_CONSTRAINT_SET_START, ATTR_CONSTRAINT_SET_END, ATTR_DERIVE_CONSTRAINTS_FROM,
                           ATTR_MOTION_TARGET_ID);
    addSpecialResourceType(ResourceType.LAYOUT, ATTR_LISTITEM, ATTR_LAYOUT);
    addSpecialResourceType(ResourceType.FONT, ATTR_FONT_FAMILY);
    addSpecialResourceType(ResourceType.MENU, ATTR_MENU);

    // Nav editor
    addSpecialResourceType(ResourceType.ID, NavigationSchema.ATTR_DESTINATION);
  }

  private AndroidDomUtil() {
  }

  @Nullable
  public static ResourceType getResourceType(@NotNull AttributeFormat format) {
    switch (format) {
      case COLOR:
        return ResourceType.COLOR;
      case DIMENSION:
        return ResourceType.DIMEN;
      case STRING:
        return ResourceType.STRING;
      case FLOAT:
      case INTEGER:
        return ResourceType.INTEGER;
      case FRACTION:
        return ResourceType.FRACTION;
      case BOOLEAN:
        return ResourceType.BOOL;
      default:
        return null;
    }
  }

  @Nullable
  public static ResolvingConverter<String> getStringConverter(@NotNull AttributeFormat format, @NotNull String[] values) {
    switch (format) {
      case ENUM:
        return new StaticEnumConverter(values);
      case BOOLEAN:
        return BOOLEAN_CONVERTER;
      case INTEGER:
        return IntegerConverter.INSTANCE;
      case DIMENSION:
        return DimensionConverter.INSTANCE;
      case COLOR:
        return ColorConverter.INSTANCE;
      case FLOAT:
        return FloatConverter.INSTANCE;
      default:
        return null;
    }
  }

  @Nullable
  public static ResourceReferenceConverter getResourceReferenceConverter(@NotNull AttributeDefinition attr) {
    boolean containsReference = false;
    boolean containsNotReference = false;
    Set<ResourceType> resourceTypes = EnumSet.noneOf(ResourceType.class);
    Set<AttributeFormat> formats = attr.getFormats();
    for (AttributeFormat format : formats) {
      if (format == AttributeFormat.REFERENCE) {
        containsReference = true;
      }
      else {
        containsNotReference = true;
      }
      ResourceType type = getResourceType(format);
      if (type != null) {
        resourceTypes.add(type);
      }
    }
    resourceTypes.addAll(getSpecialResourceTypes(attr.getName()));
    if (containsReference) {
      if (resourceTypes.contains(ResourceType.COLOR)) {
        resourceTypes.add(ResourceType.DRAWABLE);
      }
      if (resourceTypes.contains(ResourceType.DRAWABLE)) {
        resourceTypes.add(ResourceType.MIPMAP);
      }
      if (resourceTypes.isEmpty()) {
        resourceTypes.addAll(ResourceType.REFERENCEABLE_TYPES);
      }
    }
    if (!resourceTypes.isEmpty()) {
      ResourceReferenceConverter converter = new ResourceReferenceConverter(resourceTypes, attr);
      converter.setAllowLiterals(containsNotReference);
      return converter;
    }
    return null;
  }

  @Nullable
  public static Converter getSpecificConverter(@NotNull XmlName attrName, DomElement context) {
    if (context == null) {
      return null;
    }

    XmlTag xmlTag = context.getXmlTag();
    if (xmlTag == null) {
      return null;
    }

    String localName = attrName.getLocalName();
    String tagName = xmlTag.getName();

    if (tagName.equals(FRAGMENT_CONTAINER_VIEW) && localName.equals(ATTR_NAME)) {
      return FRAGMENT_CLASS_CONVERTER;
    }

    if (ANDROID_URI.equals(attrName.getNamespaceKey())) {
      // Framework attributes:
      if (context instanceof XmlResourceElement) {
        if ("configure".equals(localName) && "appwidget-provider".equals(tagName)) {
          return ACTIVITY_CONVERTER;
        }
        else if (VIEW_FRAGMENT.equals(localName)) {
          return FRAGMENT_CLASS_CONVERTER;
        }
      }
      else if (context instanceof LayoutViewElement || context instanceof MenuItem) {
        if (ATTR_ON_CLICK.equals(localName)) {
          return context instanceof LayoutViewElement
                 ? OnClickConverter.CONVERTER_FOR_LAYOUT
                 : OnClickConverter.CONVERTER_FOR_MENU;
        }
        else if (ATTR_AUTOFILL_HINTS.equals(localName)) {
          return AUTOFILL_HINTS_CONVERTER;
        }
      }
    }
    else {
      // TODO: This should be duplicated to handle the similar classes from the new support packages
      // RecyclerView:
      if (localName.equals(ATTR_LAYOUT_MANAGER) && isInheritor(xmlTag, RECYCLER_VIEW)) {
        return RECYCLER_VIEW_LAYOUT_MANAGER_CONVERTER;
      }
      else if (localName.equals(ATTR_LAYOUT_BEHAVIOR)) {
        // app:layout_behavior attribute is used by CoordinatorLayout children to specify their behaviour
        // when scrolling.
        // https://developer.android.com/reference/android/support/design/widget/CoordinatorLayout
        XmlTag parentTag = xmlTag.getParentTag();
        if (parentTag != null && isInheritor(parentTag, COORDINATOR_LAYOUT)) {
            return COORDINATOR_LAYOUT_BEHAVIOR_CONVERTER;
        }
      } else if (localName.equals(CONSTRAINT_REFERENCED_IDS)) {
        return CONSTRAINT_REFERENCED_IDS_CONVERTER;
      }
    }

    return null;
  }

  @Nullable
  public static ResolvingConverter getConverter(@NotNull AttributeDefinition attr) {
    Set<AttributeFormat> formats = attr.getFormats();

    CompositeConverter.Builder compositeBuilder = new CompositeConverter.Builder();
    String[] values = attr.getValues();
    boolean containsUnsupportedFormats = false;

    if ("fontFamily".equals(attr.getName())) {
      compositeBuilder.addConverter(new StaticEnumConverter(AVAILABLE_FAMILIES).setContainsAllValues(false));
    }
    for (AttributeFormat format : formats) {
      ResolvingConverter<String> converter = getStringConverter(format, values);
      if (converter != null) {
        compositeBuilder.addConverter(converter);
      }
      else {
        containsUnsupportedFormats = true;
      }
    }
    if (formats.contains(AttributeFormat.FLAGS)) {
      return new FlagConverter(compositeBuilder.build(), values);
    }

    ResourceReferenceConverter resConverter = getResourceReferenceConverter(attr);
    if (resConverter == null && formats.contains(AttributeFormat.ENUM)) {
      resConverter = new ResourceReferenceConverter(EnumSet.of(ResourceType.INTEGER), attr);
      resConverter.setQuiet(true);
    }
    ResolvingConverter<String> stringConverter = compositeBuilder.build();
    if (resConverter != null) {
      resConverter.setAdditionalConverter(stringConverter, containsUnsupportedFormats);
      return resConverter;
    }
    return stringConverter;
  }

  /** A "special" resource type is just additional information we've manually added about an attribute
   * name that augments what attrs.xml and attrs_manifest.xml tell us about the attributes */
  @NotNull
  public static Collection<ResourceType> getSpecialResourceTypes(String attrName) {
    Collection<ResourceType> type = SPECIAL_RESOURCE_TYPES.get(attrName);
    if (!type.isEmpty()) return type;
    if (attrName.endsWith("Animation")) return ImmutableList.of(ResourceType.ANIM);
    return ImmutableList.of();
  }

  @TestOnly
  @NotNull
  public static Stream<String> getSpecialAttributeNamesByType(@NotNull ResourceType type) {
    return SPECIAL_RESOURCE_TYPES.entries().stream().filter(entry -> entry.getValue() == type).map(entry -> entry.getKey());
  }

  // for special cases
  static void addSpecialResourceType(ResourceType type, String... attrs) {
    for (String attr : attrs) {
      SPECIAL_RESOURCE_TYPES.put(attr, type);
    }
  }

  public static boolean containsAction(@NotNull IntentFilter filter, @NotNull String name) {
    for (Action action : filter.getActions()) {
      if (name.equals(action.getName().getValue())) {
        return true;
      }
    }
    return false;
  }

  public static boolean containsCategory(@NotNull IntentFilter filter, @NotNull String name) {
    for (Category category : filter.getCategories()) {
      if (name.equals(category.getName().getValue())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static AttributeDefinition getAttributeDefinition(@NotNull AndroidFacet facet, @NotNull XmlAttribute attribute) {
    String localName = attribute.getLocalName();

    ResourceNamespace namespace = null;
    String namespaceUri = attribute.getNamespace();
    if (!namespaceUri.isEmpty()) {
      namespace = ResourceNamespace.fromNamespaceUri(namespaceUri);
      if (namespace == null) {
        return null;
      }
    }

    if (namespace != null) {
      if (namespace.equals(ResourceNamespace.TOOLS)) {
        // Treat tools namespaceUri attributes as aliases for Android namespaces:
        // see https://developer.android.com/studio/write/tool-attributes.html#design-time_view_attributes
        namespace = ResourceNamespace.ANDROID;

        // However, there are some attributes with other meanings: https://developer.android.com/studio/write/tool-attributes.html
        // Filter some of these out such that they are not treated as the (unrelated but identically named) platform attributes
        AttributeDefinition toolsAttr =
            TOOLS_ATTRIBUTE_DEFINITIONS.getAttrDefinition(ResourceReference.attr(ResourceNamespace.TOOLS, localName));
        if (toolsAttr != null) {
          return toolsAttr;
        }
      }

      ResourceManager manager = ModuleResourceManagers.getInstance(facet).getResourceManager(null);
      if (manager != null) {
        AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
        if (attrDefs != null) {
          AttributeDefinition definition = attrDefs.getAttrDefinition(ResourceReference.attr(namespace, localName));
          if (definition != null) {
            return definition;
          }
        }
      }
    }

    return null;
  }

  @NotNull
  public static Collection<String> removeUnambiguousNames(@NotNull Map<String, PsiClass> viewClassMap) {
    Map<String, String> class2Name = new HashMap<>();

    for (String tagName : viewClassMap.keySet()) {
      PsiClass viewClass = viewClassMap.get(tagName);
      String qName = viewClass.getQualifiedName();
      String prevTagName = class2Name.get(qName);

      if (prevTagName == null || tagName.indexOf('.') < 0) {
        class2Name.put(qName, tagName);
      }
    }
    return class2Name.values();
  }

  @Nullable
  public static AndroidResourceReferenceBase getAndroidResourceReference(@Nullable GenericAttributeValue<ResourceValue> attribute,
                                                                         boolean localOnly) {
    if (attribute == null) {
      return null;
    }

    ResourceValue resValue = attribute.getValue();
    if (resValue == null || (localOnly && resValue.getPackage() != null)) {
      return null;
    }

    XmlAttributeValue value = attribute.getXmlAttributeValue();
    if (value == null) {
      return null;
    }

    for (PsiReference reference : value.getReferences()) {
      if (reference instanceof AndroidResourceReferenceBase) {
        return (AndroidResourceReferenceBase)reference;
      }
    }
    return null;
  }

  @Nullable
  public static AndroidAttributeValue<PsiClass> findComponentDeclarationInManifest(@NotNull PsiClass aClass) {
    AndroidFacet facet = AndroidFacet.getInstance(aClass);
    if (facet == null) {
      return null;
    }

    boolean isActivity = InheritanceUtil.isInheritor(aClass, AndroidUtils.ACTIVITY_BASE_CLASS_NAME);
    boolean isService = InheritanceUtil.isInheritor(aClass, AndroidUtils.SERVICE_CLASS_NAME);
    boolean isReceiver = InheritanceUtil.isInheritor(aClass, AndroidUtils.RECEIVER_CLASS_NAME);
    boolean isProvider = InheritanceUtil.isInheritor(aClass, AndroidUtils.PROVIDER_CLASS_NAME);

    if (!isActivity && !isService && !isReceiver && !isProvider) {
      return null;
    }
    Manifest manifest = Manifest.getMainManifest(facet);
    if (manifest == null) {
      return null;
    }

    Application application = manifest.getApplication();
    if (application == null) {
      return null;
    }

    if (isActivity) {
      for (Activity activity : application.getActivities()) {
        AndroidAttributeValue<PsiClass> activityClass = activity.getActivityClass();
        if (activityClass.getValue() == aClass) {
          return activityClass;
        }
      }
    }
    else if (isService) {
      for (Service service : application.getServices()) {
        AndroidAttributeValue<PsiClass> serviceClass = service.getServiceClass();
        if (serviceClass.getValue() == aClass) {
          return serviceClass;
        }
      }
    }
    else if (isReceiver) {
      for (Receiver receiver : application.getReceivers()) {
        AndroidAttributeValue<PsiClass> receiverClass = receiver.getReceiverClass();
        if (receiverClass.getValue() == aClass) {
          return receiverClass;
        }
      }
    }
    else {
      for (Provider provider : application.getProviders()) {
        AndroidAttributeValue<PsiClass> providerClass = provider.getProviderClass();
        if (providerClass.getValue() == aClass) {
          return providerClass;
        }
      }
    }
    return null;
  }

  private static boolean isInheritor(@NotNull XmlTag tag, @NotNull AndroidxName baseClass) {
    String qualifiedName = tag.getName();
    if (StringUtil.isEmpty(qualifiedName)) {
      return false;
    }

    PsiClass tagClass = JavaPsiFacade.getInstance(tag.getProject()).findClass(qualifiedName, tag.getResolveScope());
    return InheritanceUtil.isInheritor(tagClass, MigrateToAndroidxUtil.getNameInProject(baseClass, tag.getProject()));
  }
}
