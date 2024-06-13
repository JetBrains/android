// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.InterpolatedText;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.ext.RawText;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.util.TypeReference;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.PropertyTransform;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import java.util.regex.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.FAKE;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.*;
import static com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelSemanticsDescription.CREATE_WITH_VALUE;

public class GradlePropertyModelImpl implements GradlePropertyModel {
  private static final Logger LOG = Logger.getInstance(GradlePropertyModelImpl.class);

  @Nullable protected GradleDslElement myElement;
  @Nullable protected GradleDslElement myDefaultElement;
  @NotNull protected GradleDslElement myPropertyHolder;

  // The list of transforms to be checked for this property model. Only the first transform that has its PropertyTransform#condition
  // return true will be used.
  @NotNull
  private List<PropertyTransform> myTransforms = new ArrayList<>();

  // The following properties should always be kept up to date with the values given by myElement.getElementType(), myElement.getName()
  // and myElement.getModelProperty().
  @NotNull private final PropertyType myPropertyType;
  @NotNull protected String myName;
  @Nullable protected ModelPropertyDescription myPropertyDescription;

  public GradlePropertyModelImpl(@NotNull GradleDslElement element) {
    myElement = element;

    GradleDslElement parent = element.getParent();
    if (parent == null) {
      assert (element instanceof GradleDslFile);
      parent = element;
    }
    assert (parent instanceof GradlePropertiesDslElement ||
            parent instanceof GradleDslMethodCall) : "Property found to be invalid, this should never happen!";
    myPropertyHolder = parent;

    myPropertyType = myElement.getElementType();
    myName = myElement.getName();
    myPropertyDescription = myElement.getModelProperty();
  }

  // Used to create an empty property with no backing element.
  public GradlePropertyModelImpl(@NotNull GradleDslElement holder, @NotNull PropertyType type, @NotNull String name) {
    myPropertyHolder = holder;
    myPropertyType = type;
    myName = name;
    myPropertyDescription = null; // TODO(xof): this does not actually mean (yet, during transition) that this is not a model property
  }

  public GradlePropertyModelImpl(@NotNull GradleDslElement holder, @NotNull PropertyType type, @NotNull ModelPropertyDescription description) {
    this(holder, type, description.name);
    myPropertyDescription = description;
  }

  public void addTransform(@NotNull PropertyTransform transform) {
    myTransforms.add(0, transform);
  }

  @Nullable
  public GradleDslElement getDefaultElement() {
    return myDefaultElement;
  }

  public void setDefaultElement(@NotNull GradleDslElement defaultElement) {
    myDefaultElement = defaultElement;
  }

  @Override
  @NotNull
  public ValueType getValueType() {
    return extractAndGetValueType(getElement());
  }

  @Override
  @NotNull
  public PropertyType getPropertyType() {
    GradleDslElement element = getElement();
    return element == null ? myPropertyType : element.getElementType();
  }

  @Override
  @Nullable
  public <T> T getValue(@NotNull TypeReference<T> typeReference) {
    return extractValue(typeReference, true);
  }

  @Override
  public <T> T getRawValue(@NotNull TypeReference<T> typeReference) {
    return extractValue(typeReference, false);
  }

  @Nullable
  private static GradleDslElement maybeGetInnerReferenceModel(@NotNull GradleDslElement element) {
    if (extractAndGetValueType(element) == LIST && element instanceof GradleDslExpressionList) {
      GradleDslExpressionList list = (GradleDslExpressionList)element;
      if (list.getExpressions().size() == 1) {
        GradleDslExpression expression = list.getElementAt(0);
        if (expression instanceof GradleDslLiteral && ((GradleDslLiteral)expression).isReference()) {
          GradleDslLiteral reference = (GradleDslLiteral)expression;
          GradleReferenceInjection injection = reference.getReferenceInjection();
          if (injection != null) {
            return injection.getToBeInjected();
          }
        }
      }
    }
    return null;
  }

  /**
   * Given a value of type {@link ValueType.INTERPOLATED} , get the element value as a {@link InterpolatedText} when possible.
   * If it is not possible to do so (Ex: injected text isn't valid), then, we return a {@link RawText} object instead to represent
   * the value. This is because in such cases, we will not be able to provide any DSL syntax interoperability support to the value, so we will
   * treat it as a {@link RawText}.
   * @return Object to represent the most suitable value, or null if none is possible.
   */
  @Nullable
  private Object getInterpolatedValueIfPossible() {
    GradleDslSimpleExpression interpolated = (GradleDslSimpleExpression)getElement();
    PsiElement expression = interpolated.getExpression();
    if (expression == null) return null;

    // An Interpolated Text is a list of InterpolatedTextItem.
    List<InterpolatedText.InterpolatedTextItem> interpolationElements = new ArrayList<>();

    // Go through all the psiElements and treat them based on their type.
    for (PsiElement child : expression.getChildren()) {
      if (child.getText().startsWith("$")) { // This is an injected string.
        // TODO(b/173698662): improve the regexp patterns for complex injections.
        Matcher wrappedValueMatcher = getElement().getDslFile().getParser().getPatternForWrappedVariables().matcher(child.getText());  // Ex:  ${abc}
        Matcher unwrappedValueMatcher = getElement().getDslFile().getParser().getPatternForUnwrappedVariables().matcher(child.getText());  //Ex: $abc
        String injectedText = null;
        if (wrappedValueMatcher.find()) {
          injectedText = wrappedValueMatcher.group(1);
        } else if (unwrappedValueMatcher.find()) {
          injectedText = unwrappedValueMatcher.group(1);
        }
        // The injection doesn't match the pattern of injections we support, hence, we won't be able to extract a InterpolatedText object.
        if (injectedText == null) {
          return interpolated.getReferenceText() != null ?
                 new RawText(interpolated.getReferenceText(), interpolated.getReferenceText()) : null;
        }
        // The injectedReference should be resolvable (i.e. Resolves to a referenceTo), otherwise treat the expression as a RawText.
        ReferenceTo injectionReference = ReferenceTo.createReferenceFromText(injectedText, this);
        // If the injectedText is not resolvable, then treat the expression as a RawText.
        if (injectionReference == null) {
          return interpolated.getReferenceText() != null ?
                 new RawText(interpolated.getReferenceText(), interpolated.getReferenceText()) : null;
        }
        // Otherwise, we have now an InterpolatedTextItem (simpleText, InjectedReference).
        else {
          interpolationElements.add(new InterpolatedText.InterpolatedTextItem(injectionReference));
        }
      } else {
        // This is a simple text value.
        interpolationElements.add(new InterpolatedText.InterpolatedTextItem(child.getText()));
      }
    }
    return new InterpolatedText(interpolationElements);
  }

  @NotNull
  private Map<String, GradlePropertyModel> getMap(boolean resolved) {
    GradleDslElement element = getElement();
    if (element == null) {
      return ImmutableMap.of();
    }

    GradleDslExpressionMap map;
    GradleDslElement innerElement = maybeGetInnerReferenceModel(element);
    // If we have a single reference it will be parsed as a list with one element.
    // we need to make sure that this actually gets resolved to the correct map.
    if (resolved && innerElement instanceof GradleDslExpressionMap) {
      map = (GradleDslExpressionMap)innerElement;
    }
    else {
      assert element instanceof GradleDslExpressionMap;
      map = (GradleDslExpressionMap)element;
    }

    return map.getPropertyElements(GradleDslExpression.class).stream()
      .collect(Collectors.toMap(e -> e.getName() ,e -> new GradlePropertyModelImpl(e), (u, v) -> v, LinkedHashMap::new));
  }

  @NotNull
  private List<GradlePropertyModel> getList(boolean resolved) {
    GradleDslElement element = getElement();
    if (element == null) {
      return ImmutableList.of();
    }

    assert element instanceof GradleDslExpressionList;

    GradleDslExpressionList list = (GradleDslExpressionList)element;
    // If the list contains a single reference, that is also to a list. Follow it and return the
    // resulting list. Only do this if the resolved value is requested.
    if (resolved) {
      GradleDslElement innerElement = maybeGetInnerReferenceModel(element);
      if (innerElement instanceof GradleDslExpressionList) {
        list = (GradleDslExpressionList)innerElement;
      }
    }

    return ContainerUtil.map(list.getExpressions(), e -> new GradlePropertyModelImpl(e));
  }

  @Override
  @NotNull
  public String getName() {
    GradleDslElement element = getElement();

    if (element != null && element.getParent() instanceof GradleDslExpressionList) {
      GradleDslExpressionList list = (GradleDslExpressionList)element.getParent();
      int index = list.findIndexOf(element);
      if (index != -1) {
        // This is the case if the element is a FakeElement
        return String.valueOf(index);
      }
    }

    return element == null ? myName : element.getName();
  }

  @Override
  @NotNull
  public List<GradlePropertyModel> getDependencies() {
    return new ArrayList<>(dependencies());
  }

  @Override
  @NotNull
  public String getFullyQualifiedName() {
    GradleDslElement element = getRawElement();

    if (element != null && element.getParent() instanceof GradleDslExpressionList) {
      GradleDslExpressionList list = (GradleDslExpressionList)element.getParent();
      return element.getParent().getQualifiedName() + "[" + String.valueOf(list.findIndexOf(element)) + "]";
    }

    return element == null ? myPropertyHolder.getQualifiedName() + "." + getName() : element.getQualifiedName();
  }

  @Override
  @NotNull
  public VirtualFile getGradleFile() {
    return myPropertyHolder.getDslFile().getFile();
  }

  @Override
  public void setValue(@NotNull Object value) {
    if (value instanceof List) {
      setListValue((List<GradlePropertyModel>) value);
    }
    else if (value instanceof Map) {
      setMapValue((Map<String,GradlePropertyModel>)value);
    }
    else {
      GradleDslExpression newElement;
      if (myPropertyDescription == null) {
        newElement = getTransform().bind(myPropertyHolder, myElement, value, getName());
      }
      else {
        newElement = getTransform().bind(myPropertyHolder, myElement, value, myPropertyDescription);
      }
      if (newElement != null) {
        bindToNewElement(newElement);
      }
    }
  }

  private void setMapValue(Map<String,GradlePropertyModel> value) {
    convertToEmptyMap();
    for (Map.Entry<String,GradlePropertyModel> e : value.entrySet()) {
      GradlePropertyModel newValueModel = getMapValue(e.getKey());
      Object newValue = e.getValue().getValue(OBJECT_TYPE);
      if (newValue != null && newValueModel != null) {
        newValueModel.setValue(newValue);
      }
    }
  }

  @Override
  @NotNull
  public GradlePropertyModel convertToEmptyMap() {
    makeEmptyMap();
    return this;
  }

  @Override
  @Nullable
  public GradlePropertyModel getMapValue(@NotNull String key) {
    ValueType valueType = getValueType();
    if (valueType != MAP && valueType != NONE) {
      LOG.warn(new IllegalStateException(
        "getMapValue \"" + key + "\" called on a non-map model of type " + valueType + "."));
      return null;
    }
    else if (valueType == NONE || myElement == null) {
      makeEmptyMap();
    }

    GradleDslElement element = getTransform().transform(myElement);
    assert element instanceof GradleDslExpressionMap;

    // Does the element already exist?
    GradleDslExpressionMap map = (GradleDslExpressionMap)element;
    GradleDslElement arg = map.getPropertyElement(key);

    return arg == null ? new GradlePropertyModelImpl(element, PropertyType.DERIVED, key) : new GradlePropertyModelImpl(arg);
  }

  private void setListValue(List<GradlePropertyModel> value) {
    convertToEmptyList();
    for (GradlePropertyModel e : value) {
      GradlePropertyModel newValueModel = addListValue();
      Object newValue = e.getValue(OBJECT_TYPE);
      if (newValue != null) newValueModel.setValue(newValue);
    }
  }

  @Override
  @NotNull
  public GradlePropertyModel convertToEmptyList() {
    makeEmptyList();
    return this;
  }

  @Override
  @Nullable
  public GradlePropertyModel addListValue() {
    ValueType valueType = getValueType();
    if (valueType != LIST && valueType != NONE) {
      LOG.warn(new IllegalStateException("addListValue called on a non-list of type " + valueType + "."));
      return null;
    }
    else if (valueType == NONE || myElement == null) {
      makeEmptyList();
    }

    GradleDslElement element = getTransform().transform(myElement);
    assert element instanceof GradleDslExpressionList;

    return addListValueAt(((GradleDslExpressionList)element).getExpressions().size());
  }

  @Override
  @Nullable
  public GradlePropertyModel addListValueAt(int index) {
    ValueType valueType = getValueType();
    if (valueType != LIST && valueType != NONE) {
      LOG.warn(new IllegalStateException("addListValueAt called on a non-list of type " + valueType + "."));
      return null;
    }
    else if (valueType == NONE || myElement == null) {
      makeEmptyList();
    }

    GradleDslElement element = getTransform().transform(myElement);
    if (!(element instanceof GradleDslExpressionList)) {
      LOG.warn(new IllegalStateException("element is not a GradleDslExpressionList " + element));
      return null;
    }

    GradleDslExpressionList list = (GradleDslExpressionList)element;
    if (index > list.getPropertyElements(GradleDslExpression.class).size()) {
      LOG.warn(new IllegalStateException("attempting to add an element past the end of the list " + list));
      return null;
    }

    // Unlike maps, we don't create a placeholder element. This is since we need to retain and update order in the list.
    // This would be hard to create an intuitive api to do this, so instead we always create an empty string as the new item.
    GradleDslLiteral literal = new GradleDslLiteral(element, GradleNameElement.empty());
    literal.setValue("");

    list.addNewExpression(literal, index);

    return new GradlePropertyModelImpl(literal);
  }

  @Override
  @Nullable
  public GradlePropertyModel getListValue(@NotNull Object value) {
    ValueType valueType = getValueType();
    if (valueType != LIST && valueType != NONE) {
      LOG.warn(new IllegalStateException("getListValue called on a non-list of type " + valueType + "."));
      return null;
    }

    List<GradlePropertyModel> list = getValue(LIST_TYPE);
    if (list == null) {
      return null;
    }
    return list.stream().filter(e -> {
      Object v = e.getValue(OBJECT_TYPE);
      return v != null && v.equals(value);
    }).findFirst().orElse(null);
  }

  @Override
  public void rewrite() {
    GradleDslElement element = getElement();
    if (element == null || myElement == null) return;
    GradleDslExpression newElement;
    if (!(myElement instanceof GradleDslExpression)) {
      LOG.warn(new IllegalStateException("Called rewrite on a non-Expression: " + myElement));
      return;
    }
    ValueType valueType = getValueType();
    if (valueType == ValueType.LIST) {
      setListValue(getValue(LIST_TYPE));
    }
    else if (valueType == ValueType.MAP) {
      setMapValue(getValue(MAP_TYPE));
    }
    else {
      if (myPropertyDescription == null) {
        newElement = getTransform().bind(myPropertyHolder, myElement, getValue(OBJECT_TYPE), getName());
      }
      else {
        newElement = getTransform().bind(myPropertyHolder, myElement, getValue(OBJECT_TYPE), myPropertyDescription);
      }
      if (newElement == myElement) {
        newElement = newElement.copy();
      }
      bindToNewElement(newElement);
    }
  }

  @Override
  public void delete() {
    GradleDslElement element = getElement();
    if (element == null || myElement == null) {
      // Nothing to delete.
      return;
    }

    myElement = getTransform().delete(myPropertyHolder, myElement, element);
  }

  @Override
  @NotNull
  public ResolvedPropertyModelImpl resolve() {
    return new ResolvedPropertyModelImpl(this);
  }

  @NotNull
  @Override
  public GradlePropertyModel getUnresolvedModel() {
    return this;
  }

  @Nullable
  @Override
  public PsiElement getPsiElement() {
    GradleDslElement element = getElement();
    if (element == null) {
      return null;
    }
    return element.getPsiElement();
  }

  @Nullable
  @Override
  public PsiElement getExpressionPsiElement() {
    return getExpressionPsiElement(false);
  }

  @Nullable
  @Override
  public PsiElement getFullExpressionPsiElement() {
    return getExpressionPsiElement(true);
  }

  @Nullable
  private PsiElement getExpressionPsiElement(boolean fullExpression) {
    // We don't use the transform here
    GradleDslElement element = fullExpression ? myElement : getElement();
    if (element instanceof GradleDslExpression) {
      return ((GradleDslExpression)element).getExpression();
    }

    return element == null ? null : element.getPsiElement();
  }

  @Override
  public void rename(@NotNull String name) {
    rename(Arrays.asList(name));
  }

  @Override
  public void rename(@NotNull List<String> name) {
    // If we have no backing element then just alter the name that we will change.
    if (myElement == null) {
      myName = GradleNameElement.join(name);
      return;
    }

    GradleDslElement element = getElement();
    if (element == null) {
      return;
    }

    GradleDslElement parent = element.getParent();

    // Check that the element should actually be renamed.
    if (parent instanceof GradleDslExpressionList || parent instanceof GradleDslMethodCall) {
      LOG.warn(new UnsupportedOperationException("Can't rename list values: " + element + " in " + parent + "."));
      return;
    }

    element.rename(name);
    // myName needs to be consistent with the elements name.
    myName = myElement.getName();
  }

  @Override
  public boolean isModified() {
    GradleDslElement element = myElement;
    if (element != null) {
      if (element instanceof FakeElement) {
        // FakeElements need special handling as they are not connected to the tree doe findOriginalElement will be null.
        return isFakeElementModified((FakeElement)element);
      }

      GradleDslElement originalElement = findOriginalElement(myPropertyHolder, element);
      if (originalElement == null) return true;
      GradleDslElement transformedElement = getElement();
      if (transformedElement == null) return true;
      // There are two distinct ways that a model element (after transform) can correspond to an original transformed element.
      // The first is that myElement holds properties (at some depth), and that the original transformed element has been replaced
      // in that properties element...
      GradleDslElement transformedOriginalElement = findOriginalElement(getHolder(), transformedElement);
      if (transformedOriginalElement == null) {
        // ... while the second is that the element has been replaced wholesale in its own holder, for example because of a change
        // of syntactic form (from method call to literal, or from literal to infix expression).  In that case we must look for the
        // original transformed element in our original element.
        transformedOriginalElement = getTransformFor(originalElement).transform(originalElement);
      }
      if (transformedOriginalElement == null) return true;
      return PropertyUtil.isModelElementModified(originalElement, element, transformedOriginalElement, transformedElement);
    }

    GradlePropertiesDslElement holder;
    if (myPropertyHolder instanceof GradleDslMethodCall) {
      holder = ((GradleDslMethodCall)myPropertyHolder).getArgumentsElement();
    }
    else {
      holder = (GradlePropertiesDslElement)myPropertyHolder;
    }

    GradleDslElement originalElement = holder.getOriginalElementForNameAndType(getName(), myPropertyType);
    GradleDslElement holderOriginalElement = findOriginalElement(holder.getParent(), holder);
    // For a property element to be modified : it should either be under a modified state itself, or should have made a modification
    // to the original state of the dsl tree.
    return originalElement != null && (originalElement.isModified() || isElementModified(holderOriginalElement, holder));
  }

  @Override
  public String toString() {
    return getValue(STRING_TYPE);
  }

  @Nullable
  @Override
  public String valueAsString() {
    return getValue(STRING_TYPE);
  }

  @NotNull
  @Override
  public String forceString() {
    String s = toString();
    assert s != null;
    return s;
  }

  @Nullable
  @Override
  public Integer toInt() {
    return getValue(INTEGER_TYPE);
  }

  @Nullable
  @Override
  public BigDecimal toBigDecimal() {
    return getValue(BIG_DECIMAL_TYPE);
  }

  @Nullable
  @Override
  public Boolean toBoolean() {
    return getValue(BOOLEAN_TYPE);
  }

  @Nullable
  @Override
  public List<GradlePropertyModel> toList() {
    return getValue(LIST_TYPE);
  }

  @Nullable
  @Override
  public Map<String, GradlePropertyModel> toMap() {
    return getValue(MAP_TYPE);
  }

  private static ValueType extractAndGetValueType(@Nullable GradleDslElement element) {
    if (element == null) {
      return NONE;
    }

    if (element instanceof GradleDslExpressionMap) {
      return MAP;
    }
    else if (element instanceof GradleDslExpressionList) {
      return LIST;
    }
    else if (element instanceof GradleDslSimpleExpression && ((GradleDslSimpleExpression)element).isInterpolated()) {
      return INTERPOLATED;
    }
    else if (element instanceof GradleDslSimpleExpression && ((GradleDslSimpleExpression)element).isReference()) {
      return REFERENCE;
    }
    else if ((element instanceof GradleDslMethodCall &&
              (element.getExternalSyntax() == ASSIGNMENT || element.getElementType() == PropertyType.DERIVED)) ||
             element instanceof GradleDslUnknownElement) {
      // This check ensures that methods we care about, i.e targetSdkVersion(12) are not classed as unknown.
      return UNKNOWN;
    }
    else if (element instanceof GradleDslSimpleExpression) {
      GradleDslSimpleExpression expression = (GradleDslSimpleExpression)element;
      Object value = expression.getValue();
      if (value instanceof Boolean) {
        return BOOLEAN;
      }
      else if (value instanceof Integer) {
        return INTEGER;
      }
      else if (value instanceof String) {
        return STRING;
      }
      else if (value instanceof BigDecimal) {
        return BIG_DECIMAL;
      }
      else if (value == null) {
        return NONE;
      }
      else {
        return UNKNOWN;
      }
    }
    else {
      // We should not be trying to create properties based of other elements.
      return UNKNOWN;
    }
  }

  @Nullable
  private <T> T extractValue(@NotNull TypeReference<T> typeReference, boolean resolved) {
    GradleDslElement element = getElement();
    // If we don't have an element, no value has yet been set, but we might have a default.
    if (element == null) {
      element = getDefaultElement();
    }
    // If we still don't have an element, we have no value.
    if (element == null) {
      return null;
    }

    ValueType valueType = getValueType();
    Object value;
    if (valueType == MAP) {
      value = getMap(resolved);
    }
    else if (valueType == LIST) {
      value = getList(resolved);
    }
    else if (valueType == REFERENCE) {
      // For references only display the reference text for both resolved and unresolved values.
      // Users should follow the reference to obtain the value.
      GradleDslSimpleExpression ref = (GradleDslSimpleExpression)element;
      String refText = ref.getReferenceText();
      if (typeReference.getType() == Object.class || typeReference.getType() == ReferenceTo.class) {
        if (refText != null) {
          ReferenceTo referenceVal = ReferenceTo.createReferenceFromText(refText, this);
          value = referenceVal != null ? typeReference.castTo(referenceVal) : typeReference.castTo(new RawText(refText, refText));
        } else {
          value = null;
        }
      }
      else {
        value = refText == null ? null : typeReference.castTo(refText);
      }
    }
    else if (valueType == INTERPOLATED && (typeReference == INTERPOLATED_TEXT_TYPE || typeReference == OBJECT_TYPE)) {
      // when extracting a value for the type INTERPOLATED_TEXT_TYPE or OBJECT_TYPE, return InterpolatedText object.
      // Otherwise, return the expression value when resolved=true, or the unresolvedValue when resolved=false.
      Object extractedDslObject = getInterpolatedValueIfPossible();
      value = extractedDslObject == null ? null : typeReference.castTo(extractedDslObject);
    }
    else if (valueType == UNKNOWN) {
      // If its a GradleDslBlockElement use the name, otherwise use the psi text. This prevents is dumping the whole
      // elements block as a string value.
      if (!(element instanceof GradleDslBlockElement)) {
        PsiElement psiElement = element instanceof GradleDslSettableExpression
                                ? ((GradleDslSettableExpression)element).getCurrentElement()
                                : element.getPsiElement();
        if (psiElement == null) {
          return null;
        }
        value = GradleDslElementImpl.getPsiText(psiElement);
      }
      else {
        value = element.getFullName();
      }
    }
    else {
      GradleDslSimpleExpression expression = (GradleDslSimpleExpression)element;

      value = resolved ? expression.getValue() : expression.getUnresolvedValue();
    }

    if (value == null) {
      return null;
    }

    T result = typeReference.castTo(value);
    // Attempt to cast to a string if requested. But only do this for unresolved values.
    if (result == null && typeReference.getType().equals(String.class)) {
      result = typeReference.castTo(value.toString());
    }

    return result;
  }

  private void makeEmptyMap() {
    if (myPropertyDescription == null) {
      bindToNewElement(getTransform().bindMap(myPropertyHolder, myElement, getName()));
    }
    else {
      bindToNewElement(getTransform().bindMap(myPropertyHolder, myElement, myPropertyDescription));
    }
  }

  private void makeEmptyList() {
    if (myPropertyDescription == null) {
      bindToNewElement(getTransform().bindList(myPropertyHolder, myElement, getName()));
    }
    else {
      bindToNewElement(getTransform().bindList(myPropertyHolder, myElement, myPropertyDescription));
    }
  }

  private void bindToNewElement(@NotNull GradleDslExpression newElement) {
    if (newElement == myElement) {
      // No need to bind
      return;
    }

    // if (myElement != null && myElement.getElementType() == FAKE) { ... return }
    // TODO Need to reinstate support of ignoring transformation for fake elements.
    // Effectively need to bring back support/ignore cases like artifact.classifier().convertToEmptyMap().
    // b/268355590 - perhaps this should be done by introducing fake artifact elements
    // through transforms rather than directly.

    GradleDslElement element = getTransform().replace(myPropertyHolder, myElement, newElement, getName());
    if (element != null) {
      GradleDslElement copyFrom =
        (myElement != null && myElement.getElementType() == FAKE) ? ((FakeElement)myElement).getRealExpression() : myElement;
      element.setElementType(myPropertyType);
      ModelEffectDescription effect = element.getModelEffect();
      if (effect == null || effect.semantics != CREATE_WITH_VALUE) {
        if (copyFrom != null) {
          element.setExternalSyntax(copyFrom.getExternalSyntax());
        }
        // TODO(b/148657110): This is necessary until models store the properties they're associated with: for now, the models
        //  have only names while the Dsl elements are annotated with model effect / properties.
        if (copyFrom != null) {
          element.setModelEffect(copyFrom.getModelEffect());
        }
      }
      // We need to ensure the parent will be modified so this change takes effect.
      element.setModified();
      myElement = element;
    }
  }

  /**
   * This method has package visibility so that subclasses of {@link ResolvedPropertyModelImpl} can access the element to
   * extract custom types.
   */
  @Nullable
  public GradleDslElement getElement() {
    return getTransform().transform(myElement);
  }

  @Override
  public @NotNull GradleDslElement getHolder() {
    GradleDslElement element = getElement();
    if (element == null) return myPropertyHolder;
    GradleDslElement parent = element.getParent();
    if (parent == null) return myPropertyHolder;
    return parent;
  }

  @Override
  @Nullable
  public GradleDslElement getRawElement() {
    return myElement;
  }

  @Override
  @NotNull
  public GradleDslElement getRawPropertyHolder() {
    return myPropertyHolder;
  }

  @NotNull
  protected PropertyTransform getTransform() {
    for (PropertyTransform transform : myTransforms) {
      if (transform.test(myElement, myPropertyHolder)) {
        return transform;
      }
    }
    return DEFAULT_TRANSFORM;
  }

  protected PropertyTransform getTransformFor(@Nullable GradleDslElement element) {
    for (PropertyTransform transform : myTransforms) {
      if (transform.test(element, myPropertyHolder)) {
        return transform;
      }
    }
    return DEFAULT_TRANSFORM;
  }

  @NotNull
  List<GradlePropertyModelImpl> dependencies() {
    GradleDslElement element = getElement();
    if (element == null) {
      return Collections.emptyList();
    }

    return element.getResolvedVariables().stream()
      .map(injection -> {
        GradleDslElement injected = injection.getToBeInjected();
        return injected != null ? new GradlePropertyModelImpl(injected) : null;
      }).filter(Objects::nonNull).collect(
        Collectors.toList());
  }
}
