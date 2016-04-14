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

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.*;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.dom.attrs.ToolsAttributeDefinitionsImpl;
import org.jetbrains.android.dom.converters.*;
import org.jetbrains.android.dom.layout.LayoutElement;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.manifest.*;
import org.jetbrains.android.dom.menu.Group;
import org.jetbrains.android.dom.menu.Menu;
import org.jetbrains.android.dom.menu.MenuItem;
import org.jetbrains.android.dom.resources.*;
import org.jetbrains.android.dom.xml.PreferenceElement;
import org.jetbrains.android.dom.xml.XmlResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.*;
import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;

/**
 * @author Eugene.Kudelevsky
 */
@SuppressWarnings({"EnumSwitchStatementWhichMissesCases"})
public class AndroidDomUtil {


  public static final StaticEnumConverter BOOLEAN_CONVERTER = new StaticEnumConverter(VALUE_TRUE, VALUE_FALSE);
  public static final Map<String, String> SPECIAL_RESOURCE_TYPES = Maps.newHashMapWithExpectedSize(20);
  private static final PackageClassConverter ACTIVITY_CONVERTER = new PackageClassConverter(AndroidUtils.ACTIVITY_BASE_CLASS_NAME);
  private static final FragmentClassConverter FRAGMENT_CLASS_CONVERTER = new FragmentClassConverter();

  private static final ToolsAttributeDefinitionsImpl TOOLS_ATTRIBUTE_DEFINITIONS = new ToolsAttributeDefinitionsImpl();

  // List of available font families extracted from framework's fonts.xml
  // Used to provide completion for values of android:fontFamily attribute
  // https://android.googlesource.com/platform/frameworks/base/+/android-6.0.0_r5/data/fonts/fonts.xml
  public static final List<String> AVAILABLE_FAMILIES = ImmutableList
    .of("sans-serif", "sans-serif-condensed", "serif", "monospace", "serif-monospace", "casual", "cursive", "sans-serif-smallcaps");

  static {
    // This section adds additional resource type registrations where the attrs metadata is lacking. For
    // example, attrs_manifest.xml tells us that the android:icon attribute can be a reference, but not
    // that it's a reference to a drawable.
    addSpecialResourceType(ResourceType.STRING.getName(), ATTR_LABEL, "description", ATTR_TITLE);
    addSpecialResourceType(ResourceType.DRAWABLE.getName(), ATTR_ICON);
    addSpecialResourceType(ResourceType.STYLE.getName(), ATTR_THEME);
    addSpecialResourceType(ResourceType.ANIM.getName(), "animation");
    addSpecialResourceType(ResourceType.ID.getName(), ATTR_ID, ATTR_LAYOUT_TO_RIGHT_OF, ATTR_LAYOUT_TO_LEFT_OF, ATTR_LAYOUT_ABOVE,
                           ATTR_LAYOUT_BELOW, ATTR_LAYOUT_ALIGN_BASELINE, ATTR_LAYOUT_ALIGN_LEFT, ATTR_LAYOUT_ALIGN_TOP,
                           ATTR_LAYOUT_ALIGN_RIGHT, ATTR_LAYOUT_ALIGN_BOTTOM, ATTR_LAYOUT_ALIGN_START, ATTR_LAYOUT_ALIGN_END,
                           ATTR_LAYOUT_TO_START_OF, ATTR_LAYOUT_TO_END_OF);
  }

  private AndroidDomUtil() {
  }

  @Nullable
  public static String getResourceType(@NotNull AttributeFormat format) {
    switch (format) {
      case Color:
        return ResourceType.COLOR.getName();
      case Dimension:
        return ResourceType.DIMEN.getName();
      case String:
        return ResourceType.STRING.getName();
      case Float:
      case Integer:
        return ResourceType.INTEGER.getName();
      case Fraction:
        return ResourceType.FRACTION.getName();
      case Boolean:
        return ResourceType.BOOL.getName();
      default:
        return null;
    }
  }

  @Nullable
  public static ResolvingConverter<String> getStringConverter(@NotNull AttributeFormat format, @NotNull String[] values) {
    switch (format) {
      case Enum:
        return new StaticEnumConverter(values);
      case Boolean:
        return BOOLEAN_CONVERTER;
      case Integer:
        return IntegerConverter.INSTANCE;
      case Dimension:
        return DimensionConverter.INSTANCE;
      default:
        return null;
    }
  }

  @Nullable
  public static ResourceReferenceConverter getResourceReferenceConverter(@NotNull AttributeDefinition attr) {
    boolean containsReference = false;
    boolean containsNotReference = false;
    Set<String> resourceTypes = new HashSet<String>();
    Set<AttributeFormat> formats = attr.getFormats();
    for (AttributeFormat format : formats) {
      if (format == AttributeFormat.Reference) {
        containsReference = true;
      }
      else {
        containsNotReference = true;
      }
      String type = getResourceType(format);
      if (type != null) {
        resourceTypes.add(type);
      }
    }
    String specialResourceType = getSpecialResourceType(attr.getName());
    if (specialResourceType != null) {
      resourceTypes.add(specialResourceType);
    }
    if (containsReference) {
      if (resourceTypes.contains(ResourceType.COLOR.getName())) {
        resourceTypes.add(ResourceType.DRAWABLE.getName());
      }
      if (resourceTypes.contains(ResourceType.DRAWABLE.getName())) {
        resourceTypes.add(ResourceType.MIPMAP.getName());
      }
      if (resourceTypes.size() == 0) {
        resourceTypes.addAll(AndroidResourceUtil.getNames(AndroidResourceUtil.REFERRABLE_RESOURCE_TYPES));
      }
    }
    if (resourceTypes.size() > 0) {
      final ResourceReferenceConverter converter = new ResourceReferenceConverter(resourceTypes, attr);
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

    if (!SdkConstants.NS_RESOURCES.equals(attrName.getNamespaceKey())) {
      return null;
    }

    final XmlTag xmlTag = context.getXmlTag();
    if (xmlTag == null) {
      return null;
    }

    final String localName = attrName.getLocalName();
    final String tagName = xmlTag.getName();

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
    ResourceReferenceConverter resConverter = getResourceReferenceConverter(attr);
    if (formats.contains(AttributeFormat.Flag)) {
      if (resConverter != null) {
        compositeBuilder.addConverter(new LightFlagConverter(values));
      }
      return new FlagConverter(compositeBuilder.build(), values);
    }

    if (resConverter == null && formats.contains(AttributeFormat.Enum)) {
      resConverter = new ResourceReferenceConverter(Collections.singletonList(ResourceType.INTEGER.getName()), attr);
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
  @Nullable
  public static String getSpecialResourceType(String attrName) {
    String type = SPECIAL_RESOURCE_TYPES.get(attrName);
    if (type != null) return type;
    if (attrName.endsWith("Animation")) return "anim";
    return null;
  }

  // for special cases
  static void addSpecialResourceType(String type, String... attrs) {
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
  public static Activity getActivityDomElementByClass(@NotNull List<Activity> activities, PsiClass c) {
    for (Activity activity : activities) {
      PsiClass activityClass = activity.getActivityClass().getValue();
      if (c.getManager().areElementsEquivalent(c, activityClass)) {
        return activity;
      }
    }
    return null;
  }

  public static String[] getStaticallyDefinedSubtags(@NotNull AndroidDomElement element) {
    if (element instanceof ManifestElement) {
      return AndroidManifestUtils.getStaticallyDefinedSubtags((ManifestElement)element);
    }
    if (element instanceof LayoutViewElement) {
      return new String[] {VIEW_INCLUDE, REQUEST_FOCUS, TAG};
    }
    if (element instanceof LayoutElement) {
      return new String[]{REQUEST_FOCUS};
    }
    if (element instanceof Group || element instanceof StringArray || element instanceof IntegerArray || element instanceof Style) {
      return new String[]{TAG_ITEM};
    }
    if (element instanceof MenuItem) {
      return new String[]{TAG_MENU};
    }
    if (element instanceof Menu) {
      return new String[]{TAG_ITEM, TAG_GROUP};
    }
    if (element instanceof Attr) {
      return new String[]{TAG_ENUM, TAG_FLAG};
    }
    if (element instanceof DeclareStyleable) {
      return new String[]{TAG_ATTR};
    }
    if (element instanceof Resources) {
      return new String[]{"string", "drawable", "dimen", "color", "style", "string-array", "integer-array", "array", "plurals",
        "declare-styleable", "integer", "bool", "attr", "item", "eat-comment"};
    }
    if (element instanceof StyledText) {
      // TODO: The documentation suggests that the allowed tags are <u>, <b> and <i>:
      //   http://developer.android.com/guide/topics/resources/string-resource.html#FormattingAndStyling
      // However, the full set of tags accepted by Html.fromHtml is much larger. Therefore,
      // instead consider *any* element nested inside a <string> definition to be a markup
      // element. See frameworks/base/core/java/android/text/Html.java and look for
      // HtmlToSpannedConverter#handleStartTag.
      return new String[]{"b", "i", "u"};
    }
    if (element instanceof PreferenceElement) {
      return new String[]{"intent", "extra"};
    }

    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Nullable
  public static AttributeDefinition getAttributeDefinition(@NotNull AndroidFacet facet, @NotNull XmlAttribute attribute) {
    String localName = attribute.getLocalName();
    String namespace = attribute.getNamespace();
    boolean isFramework = namespace.equals(ANDROID_URI);
    if (!isFramework && TOOLS_URI.equals(namespace)) {
      // Treat tools namespace attributes as aliases for Android namespaces: see http://tools.android.com/tips/layout-designtime-attributes
      isFramework = true;

      // However, there are some attributes with other meanings: http://tools.android.com/tech-docs/tools-attributes
      // Filter some of these out such that they are not treated as the (unrelated but identically named) platform attributes
      AttributeDefinition toolsAttr = TOOLS_ATTRIBUTE_DEFINITIONS.getAttrDefByName(localName);
      if (toolsAttr != null) {
        return toolsAttr;
      }
    }

    ResourceManager manager = facet.getResourceManager(isFramework ? SYSTEM_RESOURCE_PACKAGE : null);
    if (manager != null) {
      AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
      if (attrDefs != null) {
        return attrDefs.getAttrDefByName(localName);
      }
    }
    return null;
  }

  @NotNull
  public static Collection<String> removeUnambiguousNames(@NotNull Map<String, PsiClass> viewClassMap) {
    final Map<String, String> class2Name = new HashMap<String, String>();

    for (String tagName : viewClassMap.keySet()) {
      final PsiClass viewClass = viewClassMap.get(tagName);
      if (!AndroidUtils.isAbstract(viewClass)) {
        final String qName = viewClass.getQualifiedName();
        final String prevTagName = class2Name.get(qName);

        if (prevTagName == null || tagName.indexOf('.') == -1) {
          class2Name.put(qName, tagName);
        }
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

    final ResourceValue resValue = attribute.getValue();
    if (resValue == null || (localOnly && resValue.getNamespace() != null)) {
      return null;
    }

    final XmlAttributeValue value = attribute.getXmlAttributeValue();
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
    final AndroidFacet facet = AndroidFacet.getInstance(aClass);
    if (facet == null) {
      return null;
    }

    final boolean isActivity = isInheritor(aClass, AndroidUtils.ACTIVITY_BASE_CLASS_NAME);
    final boolean isService = isInheritor(aClass, AndroidUtils.SERVICE_CLASS_NAME);
    final boolean isReceiver = isInheritor(aClass, AndroidUtils.RECEIVER_CLASS_NAME);
    final boolean isProvider = isInheritor(aClass, AndroidUtils.PROVIDER_CLASS_NAME);

    if (!isActivity && !isService && !isReceiver && !isProvider) {
      return null;
    }
    final Manifest manifest = facet.getManifest();
    if (manifest == null) {
      return null;
    }

    final Application application = manifest.getApplication();
    if (application == null) {
      return null;
    }

    if (isActivity) {
      for (Activity activity : application.getActivities()) {
        final AndroidAttributeValue<PsiClass> activityClass = activity.getActivityClass();
        if (activityClass.getValue() == aClass) {
          return activityClass;
        }
      }
    }
    else if (isService) {
      for (Service service : application.getServices()) {
        final AndroidAttributeValue<PsiClass> serviceClass = service.getServiceClass();
        if (serviceClass.getValue() == aClass) {
          return serviceClass;
        }
      }
    }
    else if (isReceiver) {
      for (Receiver receiver : application.getReceivers()) {
        final AndroidAttributeValue<PsiClass> receiverClass = receiver.getReceiverClass();
        if (receiverClass.getValue() == aClass) {
          return receiverClass;
        }
      }
    }
    else {
      for (Provider provider : application.getProviders()) {
        final AndroidAttributeValue<PsiClass> providerClass = provider.getProviderClass();
        if (providerClass.getValue() == aClass) {
          return providerClass;
        }
      }
    }
    return null;
  }

  public static boolean isInheritor(@NotNull PsiClass aClass, @NotNull String baseClassQName) {
    final Project project = aClass.getProject();
    final PsiClass baseClass = JavaPsiFacade.getInstance(project).findClass(baseClassQName, aClass.getResolveScope());
    return baseClass != null && aClass.isInheritor(baseClass, true);
  }
}
