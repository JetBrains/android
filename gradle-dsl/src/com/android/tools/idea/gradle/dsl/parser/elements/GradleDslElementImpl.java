/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.model.GradleSettingsModelImpl;
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference;
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter.Kind;
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.ModificationAware;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile;
import com.android.tools.idea.gradle.dsl.parser.semantics.DescribedGradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.followElement;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.isNonExpressionPropertiesElement;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.isPropertiesElementOrMap;
import static com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.METHOD;
import static com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter.Kind.GROOVY;
import static com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter.Kind.KOTLIN;
import static com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement.*;
import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT;
import static com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement.getStandardProjectKey;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public abstract class GradleDslElementImpl implements GradleDslElement, ModificationAware {
  @NotNull private static final String SINGLE_QUOTES = "\'";
  @NotNull private static final String DOUBLE_QUOTES = "\"";
  @NotNull protected GradleNameElement myName;

  @Nullable protected GradleDslElement myParent;

  @NotNull protected List<GradlePropertiesDslElement> myHolders = new ArrayList<>();

  @NotNull private final GradleDslFile myDslFile;

  @Nullable private PsiElement myPsiElement;

  @Nullable private GradleDslClosure myClosureElement;
  @Nullable private GradleDslClosure myUnsavedClosure;

  private long myLastCommittedModificationCount;
  private long myModificationCount;

  /**
   * Represents the expressed syntax of this element (if from the parser), defaulting to METHOD.
   */
  @NotNull protected ExternalNameSyntax mySyntax;

  @NotNull private PropertyType myElementType;

  @NotNull protected final List<GradleReferenceInjection> myDependencies = new ArrayList<>();
  @NotNull protected final List<GradleReferenceInjection> myDependents = new ArrayList<>();

  @Nullable private ModelEffectDescription myModelEffectDescription;

  /**
   * Creates an instance of a {@link GradleDslElement}
   *
   * @param parent     the parent {@link GradleDslElement} of this element. The parent element should always be a not-null value except if
   *                   this element is the root element, i.e a {@link GradleDslFile}.
   * @param psiElement the {@link PsiElement} of this dsl element.
   * @param name       the name of this element.
   */
  protected GradleDslElementImpl(@Nullable GradleDslElement parent, @Nullable PsiElement psiElement, @NotNull GradleNameElement name) {
    assert parent != null || this instanceof GradleDslFile;

    myParent = parent;
    myPsiElement = psiElement;
    myName = name;


    if (parent == null) {
      myDslFile = (GradleDslFile)this;
    }
    else {
      myDslFile = parent.getDslFile();
    }

    mySyntax = METHOD;
    // Default to DERIVED, this is overwritten in the parser if required for the given element type.
    myElementType = DERIVED;
  }

  @Override
  public void setParsedClosureElement(@NotNull GradleDslClosure closureElement) {
    myClosureElement = closureElement;
  }

  @Override
  public void setNewClosureElement(@Nullable GradleDslClosure closureElement) {
    myUnsavedClosure = closureElement;
    setModified();
  }

  @Override
  @Nullable
  public GradleDslClosure getUnsavedClosure() {
    return myUnsavedClosure;
  }

  @Override
  @Nullable
  public GradleDslClosure getClosureElement() {
    return myUnsavedClosure == null ? myClosureElement : myUnsavedClosure;
  }

  @Override
  @NotNull
  public String getName() {
    return myModelEffectDescription == null ? myName.name() : myModelEffectDescription.property.name;
  }

  @Override
  @NotNull
  public String getQualifiedName() {
    // Don't include the name of the parent if this element is a direct child of the file.
    if (myParent == null || myParent instanceof GradleDslFile) {
      return GradleNameElement.escape(getName());
    }

    String ourName = getName();
    return myParent.getQualifiedName() + (ourName.isEmpty() ? "" : "." + GradleNameElement.escape(ourName));
  }

  @Override
  @NotNull
  public String getFullName() {
    if (myModelEffectDescription == null) {
      return myName.fullName();
    }
    else {
      List<String> parts = myName.qualifyingParts();
      parts.add(getName());
      return GradleNameElement.createNameFromParts(parts);
    }
  }

  @Override
  @NotNull
  public GradleNameElement getNameElement() {
    return myName;
  }

  @Override
  public void setNameElement(@NotNull GradleNameElement name) {
    myName = name;
  }

  @Override
  public void rename(@NotNull String newName) {
    rename(Arrays.asList(newName));
  }

  @Override
  public void rename(@NotNull List<String> hierarchicalName) {
    myName.rename(hierarchicalName);
    setModified();

    // If we are a GradleDslSimpleExpression we need to ensure our dependencies are correct.
    if (!(this instanceof GradleDslSimpleExpression)) {
      return;
    }

    List<GradleReferenceInjection> dependents = getDependents();
    unregisterAllDependants();

    reorder();

    // The property we renamed could have been shadowing another one. Attempt to re-resolve all dependents.
    dependents.forEach(e -> e.getOriginElement().resolve());

    // The new name could also create new dependencies, we need to make sure to resolve them.
    getDslFile().getContext().getDependencyManager().resolveWith(this);
  }

  @Override
  @Nullable
  public GradleDslElement getParent() {
    return myParent;
  }

  @Override
  public void setParent(@NotNull GradleDslElement parent) {
    myParent = parent;
  }

  @Override
  @NotNull
  public List<GradlePropertiesDslElement> getHolders() {
    return myHolders;
  }

  @Override
  public void addHolder(@NotNull GradlePropertiesDslElement holder) {
    myHolders.add(holder);
  }

  @Override
  @Nullable
  public PsiElement getPsiElement() {
    return myPsiElement;
  }

  @Override
  public void setPsiElement(@Nullable PsiElement psiElement) {
    myPsiElement = psiElement;
  }

  @Override
  @NotNull
  public ExternalNameSyntax getExternalSyntax() {
    return mySyntax;
  }

  @Override
  public void setExternalSyntax(@NotNull ExternalNameSyntax syntax) {
    mySyntax = syntax;
  }

  @Override
  @NotNull
  public PropertyType getElementType() {
    return myElementType;
  }

  @Override
  public void setElementType(@NotNull PropertyType propertyType) {
    myElementType = propertyType;
  }

  @Override
  @NotNull
  public GradleDslFile getDslFile() {
    return myDslFile;
  }

  @Override
  @NotNull
  public List<GradleReferenceInjection> getResolvedVariables() {
    ImmutableList.Builder<GradleReferenceInjection> resultBuilder = ImmutableList.builder();
    for (GradleDslElement child : getChildren()) {
      resultBuilder.addAll(child.getResolvedVariables());
    }
    return resultBuilder.build();
  }

  @Override
  @Nullable
  public GradleDslElement requestAnchor(@NotNull GradleDslElement element) {
    return null;
  }

  @Override
  @Nullable
  public GradleDslElement getAnchor() {
    return myParent == null ? null : myParent.requestAnchor(this);
  }

  @Override
  @Nullable
  public PsiElement create() {
    return myDslFile.getWriter().createDslElement(this);
  }

  @Override
  @Nullable
  public PsiElement move() {
    return myDslFile.getWriter().moveDslElement(this);
  }

  @Override
  public void delete() {
    this.getDslFile().getWriter().deleteDslElement(this);
  }

  @Override
  public void setModified() {
    modify();
    if (myParent != null) {
      myParent.setModified();
      if (this instanceof DescribedGradlePropertiesDslElement && myParent instanceof GradlePropertiesDslElement) {
        // If we modify a previously-applied internal node of the Dsl tree, in particular when adding an element to it or any of its
        // children, we must change its state from APPLIED to reflect the fact that the new element will have a physical existence
        // in the file, and can no longer be represented as merely the effect of transclusion.
        ((GradlePropertiesDslElement)myParent).updateAppliedState(this);
      }
    }
  }

  @Override
  public boolean isModified() {
    return getLastCommittedModificationCount() != getModificationCount();
  }

  @Override
  public boolean isBlockElement() {
    return false;
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    return true;
  }

  @Override
  @NotNull
  public abstract Collection<GradleDslElement> getChildren();

  @Override
  public final void applyChanges() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    apply();
    commit();
  }

  protected abstract void apply();

  @Override
  public final void resetState() {
    reset();
    commit();
  }

  protected abstract void reset();

  @Override
  @NotNull
  public List<GradleDslElement> getContainedElements(boolean includeProperties) {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Map<String, GradleDslElement> getInScopeElements() {
    Map<String, GradleDslElement> results = new LinkedHashMap<>();

    if (isNonExpressionPropertiesElement(this)) {
      GradlePropertiesDslElement thisElement = (GradlePropertiesDslElement)this;
      results.putAll(thisElement.getVariableElements());
    }

    // Trace parents finding any variable elements present.
    GradleDslElement currentElement = this;
    while (currentElement != null && currentElement.getParent() != null) {
      currentElement = currentElement.getParent();
      if (isNonExpressionPropertiesElement(currentElement)) {
        GradlePropertiesDslElement element = (GradlePropertiesDslElement)currentElement;
        results.putAll(element.getVariableElements());
      }
    }

    // Get Ext properties from the GradleDslFile, and the EXT properties from the buildscript.
    if (currentElement instanceof GradleBuildFile) {
      GradleBuildFile file = (GradleBuildFile)currentElement;
      while (file != null) {
        ExtDslElement ext = file.getPropertyElement(ExtDslElement.EXT);
        if (ext != null) {
          results.putAll(ext.getPropertyElements());
        }
        // Add properties files properties
        GradleDslFile propertiesFile = file.getPropertiesFile();
        if (propertiesFile != null) {
          // Only properties with no qualifier are picked up by build scripts.
          Map<String, GradleDslElement> filteredProperties =
            propertiesFile.getPropertyElements().entrySet().stream().filter(entry -> !entry.getKey().contains("."))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
          results.putAll(filteredProperties);
        }
        // Add BuildScriptExt properties.
        BuildScriptDslElement buildScriptElement = file.getPropertyElement(BUILDSCRIPT);
        if (buildScriptElement != null) {
          ExtDslElement buildScriptExt = buildScriptElement.getPropertyElement(ExtDslElement.EXT);
          if (buildScriptExt != null) {
            results.putAll(buildScriptExt.getPropertyElements());
          }
        }

        file = file.getParentModuleBuildFile();
      }
    }

    return results;
  }

  @Override
  @NotNull
  public <T extends BuildModelNotification> T notification(@NotNull NotificationTypeReference<T> type) {
    return getDslFile().getContext().getNotificationForType(myDslFile, type);
  }

  @Override
  public void registerDependent(@NotNull GradleReferenceInjection injection) {
    assert injection.isResolved() && injection.getToBeInjected() == this;
    myDependents.add(injection);
  }

  @Override
  public void unregisterDependent(@NotNull GradleReferenceInjection injection) {
    assert injection.isResolved() && injection.getToBeInjected() == this;
    assert myDependents.contains(injection);
    myDependents.remove(injection);
  }

  @Override
  public void unregisterAllDependants() {
    // We need to create a new array to avoid concurrent modification exceptions.
    myDependents.forEach(e -> {
      // Break the dependency.
      e.resolveWith(null);
      // Register with DependencyManager
      getDslFile().getContext().getDependencyManager().registerUnresolvedReference(e);
    });
    myDependents.clear();
  }

  @Override
  @NotNull
  public List<GradleReferenceInjection> getDependents() {
    return new ArrayList<>(myDependents);
  }

  @Override
  @NotNull
  public List<GradleReferenceInjection> getDependencies() {
    return new ArrayList<>(myDependencies);
  }

  @Override
  public void updateDependenciesOnAddElement(@NotNull GradleDslElement newElement) {
    newElement.resolve();
    newElement.getDslFile().getContext().getDependencyManager().resolveWith(newElement);
  }

  @Override
  public void updateDependenciesOnReplaceElement(@NotNull GradleDslElement oldElement, @NotNull GradleDslElement newElement) {
    // Switch dependents to point to the new element.
    List<GradleReferenceInjection> injections = oldElement.getDependents();
    oldElement.unregisterAllDependants();
    injections.forEach(e -> e.resolveWith(newElement));
    // Register all the dependents with this new element.
    injections.forEach(newElement::registerDependent);

    // Go though our dependencies and unregister us as a dependent.
    oldElement.getResolvedVariables().forEach(e -> {
      GradleDslElement toBeInjected = e.getToBeInjected();
      if (toBeInjected != null) {
        toBeInjected.unregisterDependent(e);
      }
    });
  }

  @Override
  public void updateDependenciesOnRemoveElement(@NotNull GradleDslElement oldElement) {
    List<GradleReferenceInjection> dependents = oldElement.getDependents();
    oldElement.unregisterAllDependants();

    // The property we remove could have been shadowing another one. Attempt to re-resolve all dependents.
    dependents.forEach(e -> e.getOriginElement().resolve());

    // Go though our dependencies and unregister us as a dependent.
    oldElement.getResolvedVariables().forEach(e -> {
      GradleDslElement toBeInjected = e.getToBeInjected();
      if (toBeInjected != null) {
        toBeInjected.unregisterDependent(e);
      }
    });
  }

  @Override
  public void addDependency(@NotNull GradleReferenceInjection injection) {
    myDependencies.add(injection);
  }

  @Override
  public void resolve() {
  }

  protected void reorder() {
    if (myParent instanceof ExtDslElement) {
      ((ExtDslElement)myParent).reorderAndMaybeGetNewIndex(this);
    }
  }

  @Override
  public long getModificationCount() {
    return myModificationCount;
  }

  public long getLastCommittedModificationCount() {
    return myLastCommittedModificationCount;
  }

  @Override
  public void modify() {
    HashSet<GradleDslElement> visited = new HashSet<>();
    modify(visited);
  }

  protected void modify(Set<GradleDslElement> visited) {
    myModificationCount++;
    visited.add(this);
    myDependents.forEach(e -> { if (!visited.contains(e.getOriginElement())) e.getOriginElement().modify(visited); });
  }

  public void commit() {
    myLastCommittedModificationCount = myModificationCount;
  }

  @Nullable
  public static String getPsiText(@NotNull PsiElement psiElement) {
    return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> psiElement.getText());
  }

  @Override
  public boolean isNewEmptyBlockElement() {
    if (myPsiElement != null) {
      return false;
    }

    if (!isBlockElement() || !isInsignificantIfEmpty()) {
      return false;
    }

    Collection<GradleDslElement> children = getContainedElements(true);
    if (children.isEmpty()) {
      return true;
    }

    for (GradleDslElement child : children) {
      if (!child.isNewEmptyBlockElement()) {
        return false;
      }
    }

    return true;
  }

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, ExternalToModelMap.empty, ExternalToModelMap.empty);
  }

  protected final @NotNull ExternalToModelMap getExternalToModelMap(
    @NotNull GradleDslNameConverter converter,
    ExternalToModelMap groovy,
    ExternalToModelMap kts
  ) {
    Kind kind = converter.getKind();
    if (kind == GROOVY) return groovy;
    if (kind == KOTLIN) return kts;
    return ExternalToModelMap.empty;
  }

  @Nullable
  @Override
  public ModelEffectDescription getModelEffect() {
    return myModelEffectDescription;
  }

  @Override
  public void setModelEffect(@Nullable ModelEffectDescription effect) {
    myModelEffectDescription = effect;
  }

  @Nullable
  @Override
  public ModelPropertyDescription getModelProperty() {
    return myModelEffectDescription == null ? null : myModelEffectDescription.property;
  }

  @Nullable
  public static GradleDslElement dereference(@NotNull GradleDslElement element, @NotNull String index) {
    if (element instanceof GradleDslExpressionList) {
      int offset;
      try {
        offset = Integer.parseInt(index);
      }
      catch (NumberFormatException e) {
        return null;
      }

      GradleDslExpressionList list = (GradleDslExpressionList)element;
      if (list.getExpressions().size() <= offset) {
        return null;
      }
      return list.getExpressions().get(offset);
    }
    else if (element instanceof GradleDslExpressionMap) {
      GradleDslExpressionMap map = (GradleDslExpressionMap)element;
      index = stripQuotes(index);

      return map.getPropertyElement(index);
    }
    GradleDslElement value = followElement(element);
    if (value == null) {
      return null;
    }
    else if (value != element) {
      return dereference(value, index);
    }
    else {
      return null;
    }
  }

  @Nullable
  private static GradleDslElement extractElementFromProperties(@NotNull GradlePropertiesDslElement properties,
                                                               @NotNull String name,
                                                               GradleDslNameConverter converter,
                                                               boolean sameScope,
                                                               @Nullable GradleDslElement childElement,
                                                               boolean includeSelf) {
    // First check if any indexing has been done.
    Matcher indexMatcher = GradleNameElement.INDEX_PATTERN.matcher(name);

    // If the index matcher doesn't give us anything, just attempt to find the property on the element;
    if (!indexMatcher.find()) {
      ModelPropertyDescription property = converter.modelDescriptionForParent(name, properties);
      String modelName = property == null ? name : property.name;

      return sameScope
             ? properties.getElementBefore(childElement, modelName, includeSelf)
             : properties.getPropertyElementBefore(childElement, modelName, includeSelf);
    }

    // We have some index present, find the element we need to index. The first match, the property, is always the whole match.
    String elementName = indexMatcher.group(0);
    if (elementName == null || elementName.isEmpty()) {
      return null;
    }
    ModelPropertyDescription property = converter.modelDescriptionForParent(elementName, properties);
    String modelName = property == null ? elementName : property.name;

    GradleDslElement element =
      sameScope
      ? properties.getElementBefore(childElement, modelName, includeSelf)
      : properties.getPropertyElementBefore(childElement, modelName, includeSelf);

    // Construct a list of all of the index parts
    Deque<String> indexParts = new ArrayDeque<>();
    while (indexMatcher.find()) {
      // second and subsequent matches of INDEX_PATTERN should have .group(0) being "[...]", and .group(1) the text inside the brackets.
      // If not then we might be dealing with invalid syntax and should not resolve to an element.
      String match = indexMatcher.group(1);
      if (match == null) return null;
      indexParts.add(match);
    }

    // Go through each index and search for the element.
    while (!indexParts.isEmpty()) {
      String index = indexParts.pop();
      // Ensure the element is not null
      if (element == null) {
        return null;
      }

      // Get the type of the element and ensure the index is compatible, e.g numerical index for a list.
      element = dereference(element, index);
    }

    return element;
  }

  @Nullable
  private static GradleBuildFile findBuildFile(GradleBuildFile rootModuleBuildFile, File moduleDirectory) {
    if (filesEqual(rootModuleBuildFile.getDirectoryPath(), moduleDirectory)) {
      return rootModuleBuildFile;
    }

    for (GradleBuildFile buildFile : rootModuleBuildFile.getChildModuleBuildFiles()) {
      if (filesEqual(buildFile.getDirectoryPath(), moduleDirectory)) {
        return buildFile;
      }
      GradleBuildFile childBuildFile = findBuildFile(buildFile, moduleDirectory);
      if (childBuildFile != null) {
        return buildFile;
      }
    }
    return null;
  }

  @Nullable
  private static GradleDslElement resolveReferenceOnPropertiesElement(@NotNull GradlePropertiesDslElement properties,
                                                                      @NotNull List<String> nameParts,
                                                                      GradleDslNameConverter converter,
                                                                      @NotNull List<GradleDslElement> trace) {
    int traceIndex = trace.size() - 1;
    // Go through each of the parts and extract the elements from each of them.
    GradleDslElement element;
    for (int i = 0; i < nameParts.size() - 1; i++) {
      // Only look for variables on the first iteration, otherwise only properties should be accessible.
      element = extractElementFromProperties(properties, nameParts.get(i), converter, i == 0, traceIndex < 0 ? null : trace.get(traceIndex--),
                                             traceIndex >= 0);
      if (element == null) return null;
      element = followElement(element);

      // All elements we find must be GradlePropertiesDslElement on all but the last iteration.
      if (!isPropertiesElementOrMap(element)) {
        return null;
      }
      // isPropertiesElementOrMap should always return false when is not an instance of GradlePropertiesDslElement.
      //noinspection ConstantConditions
      properties = (GradlePropertiesDslElement)element;
    }

    return extractElementFromProperties(properties, nameParts.get(nameParts.size() - 1), converter, nameParts.size() == 1,
                                        traceIndex < 0 ? null : trace.get(traceIndex--), traceIndex >= 0);
  }

  @Nullable
  private static GradleDslElement resolveReferenceOnElement(@NotNull GradleDslElement element,
                                                            @NotNull List<String> nameParts,
                                                            GradleDslNameConverter converter,
                                                            boolean resolveWithOrder,
                                                            boolean checkExt,
                                                            int ignoreParentNumber) {
    // We need to keep track of the last element we saw to ensure we only check items BEFORE the one we are resolving.
    Stack<GradleDslElement> elementTrace = new Stack<>();
    if (resolveWithOrder) {
      elementTrace.push(element);
    }
    // Make sure we don't check any nested scope for the element.
    while (ignoreParentNumber-- > 0 && element != null && !(element instanceof GradleDslFile) && !(element instanceof BuildScriptDslElement)) {
      element = element.getParent();
    }
    while (element != null) {
      GradleDslElement lastElement = elementTrace.isEmpty() ? null : elementTrace.peek();
      if (isPropertiesElementOrMap(element)) {
        GradleDslElement propertyElement = resolveReferenceOnPropertiesElement((GradlePropertiesDslElement)element, nameParts,
                                                                               converter, elementTrace);
        if (propertyElement != null) {
          return propertyElement;
        }

        // If it is then we have already checked the ExtElement of this object.
        if (!(lastElement instanceof ExtDslElement) && checkExt) {
          GradleDslElement extElement =
            ((GradlePropertiesDslElement)element).getPropertyElementBefore(lastElement, EXT.name, false);
          if (extElement instanceof ExtDslElement) {
            GradleDslElement extPropertyElement =
              resolveReferenceOnPropertiesElement((ExtDslElement)extElement, nameParts, converter, elementTrace);
            if (extPropertyElement != null) {
              return extPropertyElement;
            }
          }
        }

        if (!(lastElement instanceof BuildScriptDslElement)) {
          GradleDslElement bsDslElement =
            ((GradlePropertiesDslElement)element).getPropertyElementBefore(element, BUILDSCRIPT.name, false);
          if (bsDslElement instanceof BuildScriptDslElement) {
            GradleDslElement bsElement =
              resolveReferenceOnElement(bsDslElement, nameParts, converter, true /* Must be true or we just jump between buildscript -> parent */,
                                        false, -1);
            if (bsElement != null) {
              return bsElement;
            }
          }
        }
      }

      if (resolveWithOrder) {
        elementTrace.push(element);
      }

      // Don't resolve up the parents for BuildScript elements.
      if (element instanceof BuildScriptDslElement) {
        return null;
      }
      element = element.getParent();
    }

    return null;
  }

  @Nullable
  private static GradleDslElement resolveReferenceInPropertiesFile(@NotNull GradleBuildFile buildDslFile, @NotNull String referenceText) {
    GradleDslFile propertiesDslFile = buildDslFile.getPropertiesFile();
    return propertiesDslFile != null ? propertiesDslFile.getPropertyElement(referenceText) : null;
  }

  private static @Nullable GradleDslElement resolveReferenceInVersionCatalogs(@NotNull GradleBuildFile buildFile, @NotNull String text) {
    List<String> referenceParts = GradleNameElement.split(text);
    if (referenceParts.size() < 2) return null;
    String catalog = referenceParts.get(0);
    GradleDslElement result1;
    GradleDslElement result2;
    for (GradleVersionCatalogFile versionCatalogFile : buildFile.getVersionCatalogFiles()) {
      if (!catalog.equals(versionCatalogFile.getCatalogName())) continue;
      result1 = versionCatalogFile;
      result2 = versionCatalogFile.getElement("libraries");
      for (String part : referenceParts.subList(1, referenceParts.size())) {
        result1 = (result1 instanceof GradlePropertiesDslElement) ? ((GradlePropertiesDslElement)result1).getElement(part) : null;
        result2 = (result2 instanceof GradlePropertiesDslElement) ? ((GradlePropertiesDslElement)result2).getElement(part) : null;
      }
      if (result2 != null) return result2;
      if (result1 != null) return result1;
    }
    return null;
  }

  @Nullable
  private static GradleDslElement resolveReferenceInParentModules(
    @NotNull GradleBuildFile buildFile,
    @NotNull List<String> referenceText,
    GradleDslNameConverter converter
  ) {
    GradleBuildFile parentBuildFile = buildFile.getParentModuleBuildFile();
    while (parentBuildFile != null) {
      ExtDslElement extDslElement = parentBuildFile.getPropertyElement(EXT);
      if (extDslElement != null) {
        GradleDslElement extPropertyElement = resolveReferenceOnPropertiesElement(extDslElement, referenceText, converter, new Stack<>());
        if (extPropertyElement != null) {
          return extPropertyElement;
        }
      }

      BuildScriptDslElement bsDslElement = parentBuildFile.getPropertyElement(BUILDSCRIPT);
      if (bsDslElement != null) {
        GradleDslElement bsElement = resolveReferenceOnElement(bsDslElement, referenceText, converter, false, true, -1);
        if (bsElement != null) {
          return bsElement;
        }
      }

      if (parentBuildFile.getParentModuleBuildFile() == null) {
        // This is the root project build.gradle file and the root project's gradle.properties file is already considered in
        // resolveReferenceInSameModule method.
        return null;
      }

      GradleDslElement propertyElement = resolveReferenceInPropertiesFile(parentBuildFile, String.join(".", referenceText));
      if (propertyElement != null) {
        return propertyElement;
      }

      parentBuildFile = parentBuildFile.getParentModuleBuildFile();
    }
    return null;
  }

  @Nullable
  private static GradleDslElement resolveReferenceInSameModule(@NotNull GradleDslElement startElement,
                                                               @NotNull List<String> referenceText,
                                                               GradleDslNameConverter converter,
                                                               boolean resolveWithOrder) {
    GradleDslElement element;

    // References within Version Catalog files can be version.ref or bundle reference to libraries
    if (startElement.getDslFile() instanceof GradleVersionCatalogFile && referenceText.size() == 1) {
      GradleDslExpressionMap versions = startElement.getDslFile().getPropertyElement("versions", GradleDslExpressionMap.class);
      if (versions != null) {
        element = resolveReferenceOnElement(versions, referenceText, converter, false, false, -1);
        if (element != null) {
          return element;
        }
      }
      else {
        GradleDslExpressionMap libraries = startElement.getDslFile().getPropertyElement("libraries", GradleDslExpressionMap.class);
        if (libraries != null) {
          element = resolveReferenceOnElement(libraries, referenceText, converter, false, false, -1);
          if (element != null) {
            return element;
          }
        }
      }
    }

    // Try to resolve in the build.gradle file the startElement belongs to.
    element =
      resolveReferenceOnElement(startElement, referenceText, converter, resolveWithOrder, true, startElement.getNameElement().fullNameParts().size());
    if (element != null) {
      return element;
    }

    // Join the text before looking in the properties files.
    String text = String.join(".", referenceText);

    // TODO: Add support to look at <GRADLE_USER_HOME>/gradle.properties before looking at this module's gradle.properties file.

    // Try to resolve in the gradle.properties file of the startElement's module.
    GradleDslFile dslFile = startElement.getDslFile();
    if (dslFile instanceof GradleBuildFile) {
      GradleBuildFile buildFile = (GradleBuildFile)dslFile;
      GradleDslElement propertyElement = resolveReferenceInPropertiesFile(buildFile, text);
      if (propertyElement != null) {
        return propertyElement;
      }

      // Ensure we check the buildscript as well.
      BuildScriptDslElement bsDslElement = buildFile.getPropertyElement(BUILDSCRIPT);
      if (bsDslElement != null) {
        GradleDslElement bsElement = resolveReferenceOnElement(bsDslElement, referenceText, converter, false, true, -1);
        if (bsElement != null) {
          return bsElement;
        }
      }

      GradleBuildFile rootProjectBuildFile = buildFile;
      while (true) {
        GradleBuildFile parentModuleDslFile = rootProjectBuildFile.getParentModuleBuildFile();
        if (parentModuleDslFile == null) {
          break;
        }
        rootProjectBuildFile = parentModuleDslFile;
      }

      GradleDslElement versionCatalogElement = resolveReferenceInVersionCatalogs(rootProjectBuildFile, text);
      if (versionCatalogElement != null) return versionCatalogElement;

      if (buildFile == rootProjectBuildFile) {
        return null; // This is the root project build.gradle file and there is no further path to look up.
      }

      // Try to resolve in the root project gradle.properties file.
      return resolveReferenceInPropertiesFile(rootProjectBuildFile, text);
    }
    return null;
  }

  @Nullable
  @Override
  public GradleDslElement resolveExternalSyntaxReference(@NotNull String referenceText, boolean resolveWithOrder) {
    GradleDslElement searchStartElement = this;
    if (searchStartElement.getDslFile() instanceof GradleVersionCatalogFile)
        if(referenceText.startsWith("versions.")) {
          referenceText = "\"" + referenceText.substring("versions.".length()) + "\"";
        } else if(referenceText.startsWith("libraries.")) {
          referenceText = "\"" + referenceText.substring("libraries.".length()) + "\"";
        }
    GradleDslParser parser = getDslFile().getParser();
    referenceText = parser.convertReferenceText(searchStartElement, referenceText);

    return resolveInternalSyntaxReference(referenceText, resolveWithOrder);
  }

  @Override
  public @Nullable GradleDslElement resolveExternalSyntaxReference(@NotNull PsiElement psiElement, boolean resolveWithOrder) {
    GradleDslElement searchStartElement = this;
    GradleDslParser parser = getDslFile().getParser();
    String referenceText = parser.convertReferencePsi(searchStartElement, psiElement);

    return resolveInternalSyntaxReference(referenceText, resolveWithOrder);
  }

  @Nullable
  @Override
  public GradleDslElement resolveInternalSyntaxReference(@NotNull String referenceText, boolean resolveWithOrder) {
    GradleDslElement searchStartElement = this;
    GradleDslParser parser = getDslFile().getParser();

    boolean withinBuildscript = false;
    GradleDslElement element = this;
    while (element != null) {
      element = element.getParent();
      if (element instanceof BuildScriptDslElement) {
        withinBuildscript = true;
        break;
      }
    }

    List<String> referenceTextSegments = GradleNameElement.split(referenceText);
    int index = 0;
    int segmentCount = referenceTextSegments.size();
    for (; index < segmentCount; index++) {
      // Resolve the project reference elements like parent, rootProject etc.
      GradleBuildFile buildFile = resolveProjectReference(searchStartElement, referenceTextSegments.get(index));
      if (buildFile == null) {
        break;
      }
      // start the search for our element at the top-level of the Dsl file (but see below for buildscript handling)
      searchStartElement = buildFile;
    }

    /* For a project with the below hierarchy ...

    | <GRADLE_USER_HOME>/gradle.properties
    | RootProject
    | - - build.gradle
    | - - gradle.properties
    | - - FirstLevelChildProject
    | - - - - build.gradle
    | - - - - gradle.properties
    | - - - - SecondLevelChildProject
    | - - - - - - build.gradle
    | - - - - - - gradle.properties
    | - - - - - - ThirdLevelChildProject
    | - - - - - - - - build.gradle
    | - - - - - - - - gradle.properties

    the resolution path for a property defined in ThirdLevelChildProject's build.gradle file will be ...

      1. ThirdLevelChildProject/build.gradle
      2. <GRADLE_USER_HOME>/gradle.properties
      3. ThirdLevelChildProject/gradle.properties
      4. RootProject/gradle.properties
      5. SecondLevelChildProject/build.gradle
      6. SecondLevelChildProject/gradle.properties
      7. FirstLevelChildProject/build.gradle
      8. FirstLevelChildProject/gradle.properties
      9. RootProject/build.gradle
    */

    GradleDslElement resolvedElement = null;
    GradleDslFile dslFile = searchStartElement.getDslFile();
    if (index >= segmentCount) {
      // the reference text is fully resolved by now. ex: if the while text itself is "rootProject" etc.
      resolvedElement = searchStartElement;
    }
    else {
      // Search in the file that searchStartElement belongs to.
      referenceTextSegments = referenceTextSegments.subList(index, segmentCount);
      // if we are resolving in the general context of buildscript { } within the same module, then build code external to the
      // buildscript block will not yet have run: restrict search to the buildscript element (which should exist)
      if (dslFile == searchStartElement  && withinBuildscript) {
        searchStartElement = dslFile.getPropertyElement(BUILDSCRIPT);
      }
      if (searchStartElement != null) {
        resolvedElement = resolveReferenceInSameModule(searchStartElement, referenceTextSegments, parser, resolveWithOrder);
      }
    }

    if (resolvedElement == null && dslFile instanceof GradleBuildFile) {
      GradleBuildFile buildFile = (GradleBuildFile)dslFile;
      // Now look in the parent projects ext blocks.
      resolvedElement = resolveReferenceInParentModules(buildFile, referenceTextSegments, parser);
    }

    return resolvedElement;
  }

  @Nullable
  private static GradleBuildFile resolveProjectReference(GradleDslElement startElement, @NotNull String projectReference) {
    GradleDslFile dslFile = startElement.getDslFile();
    if (dslFile instanceof GradleBuildFile) {
      GradleBuildFile buildFile = (GradleBuildFile)dslFile;
      if ("project".equals(projectReference)) {
        return buildFile;
      }

      if ("parent".equals(projectReference)) {
        return buildFile.getParentModuleBuildFile();
      }

      if ("rootProject".equals(projectReference)) {
        while (buildFile != null && !filesEqual(buildFile.getDirectoryPath(), virtualToIoFile(buildFile.getProject().getBaseDir()))) {
          buildFile = buildFile.getParentModuleBuildFile();
        }
        return buildFile;
      }

      String standardProjectKey = getStandardProjectKey(projectReference);
      if (standardProjectKey != null) { // project(':project:path')
        String modulePath = standardProjectKey.substring(standardProjectKey.indexOf('\'') + 1, standardProjectKey.lastIndexOf('\''));
        VirtualFile settingFile = buildFile.tryToFindSettingsFile();
        if (settingFile == null) {
          return null;
        }
        GradleSettingsFile file = buildFile.getContext().getOrCreateSettingsFile(settingFile);
        GradleSettingsModel model = new GradleSettingsModelImpl(file);
        File moduleDirectory = model.moduleDirectory(modulePath);
        if (moduleDirectory == null) {
          return null;
        }
        while (buildFile != null && !filesEqual(buildFile.getDirectoryPath(), virtualToIoFile(buildFile.getProject().getBaseDir()))) {
          buildFile = buildFile.getParentModuleBuildFile();
        }
        if (buildFile == null) {
          return null;
        }
        return findBuildFile(buildFile, moduleDirectory); // root module dsl File.
      }
    }
    return null;
  }

  @NotNull
  private static String stripQuotes(@NotNull String index) {
    if (index.startsWith(SINGLE_QUOTES) && index.endsWith(SINGLE_QUOTES) ||
        index.startsWith(DOUBLE_QUOTES) && index.endsWith(DOUBLE_QUOTES)) {
      return index.substring(1, index.length() - 1);
    }
    return index;
  }
}
