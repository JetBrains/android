package com.android.tools.dom.attrs;

import com.android.ide.common.rendering.api.ResourceReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A decorator for {@link AttributeDefinitions} that applies filtering to it.
 */
public abstract class FilteredAttributeDefinitions implements AttributeDefinitions {
  private final AttributeDefinitions myWrappee;

  protected FilteredAttributeDefinitions(@NotNull AttributeDefinitions wrappee) {
    myWrappee = wrappee;
  }

  protected abstract boolean isAttributeAcceptable(@NotNull ResourceReference attr);

  @Override
  @Nullable
  public StyleableDefinition getStyleableDefinition(@NotNull ResourceReference styleable) {
    StyleableDefinition styleableDef = myWrappee.getStyleableDefinition(styleable);
    return styleableDef != null ? new MyStyleableDefinition(styleableDef) : null;
  }

  @Deprecated
  @Override
  @Nullable
  public StyleableDefinition getStyleableByName(@NotNull String name) {
    StyleableDefinition styleable = myWrappee.getStyleableByName(name);
    return styleable != null ? new MyStyleableDefinition(styleable) : null;
  }

  @Override
  @NotNull
  public Set<ResourceReference> getAttrs() {
    Set<ResourceReference> result = new HashSet<>();

    for (ResourceReference attrRef : myWrappee.getAttrs()) {
      if (isAttributeAcceptable(attrRef)) {
        result.add(attrRef);
      }
    }
    return result;
  }

  @Nullable
  @Override
  public AttributeDefinition getAttrDefinition(@NotNull ResourceReference attr) {
    AttributeDefinition attribute = myWrappee.getAttrDefinition(attr);
    return attribute != null && isAttributeAcceptable(attr) ? attribute : null;
  }

  @Deprecated
  @Override
  @Nullable
  public AttributeDefinition getAttrDefByName(@NotNull String name) {
    AttributeDefinition attribute = myWrappee.getAttrDefByName(name);
    return attribute != null && isAttributeAcceptable(attribute.getResourceReference()) ? attribute : null;
  }

  @Nullable
  @Override
  public String getAttrGroup(@NotNull ResourceReference attr) {
    return myWrappee.getAttrGroup(attr);
  }

  private class MyStyleableDefinition implements StyleableDefinition {
    private final StyleableDefinition myWrappee;

    private MyStyleableDefinition(@NotNull StyleableDefinition wrappee) {
      myWrappee = wrappee;
    }

    @Override
    @NotNull
    public ResourceReference getResourceReference() {
      return myWrappee.getResourceReference();
    }

    @Override
    @NotNull
    public String getName() {
      return myWrappee.getName();
    }

    @Override
    @NotNull
    public List<AttributeDefinition> getAttributes() {
      List<AttributeDefinition> result = new ArrayList<>();

      for (AttributeDefinition definition : myWrappee.getAttributes()) {
        if (isAttributeAcceptable(definition.getResourceReference())) {
          result.add(definition);
        }
      }
      return result;
    }
  }
}
