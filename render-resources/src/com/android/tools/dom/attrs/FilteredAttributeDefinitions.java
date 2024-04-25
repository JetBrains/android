package com.android.tools.dom.attrs;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceReference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A decorator for {@link AttributeDefinitions} that applies filtering to it.
 */
public abstract class FilteredAttributeDefinitions implements AttributeDefinitions {
  private final AttributeDefinitions myWrappee;

  protected FilteredAttributeDefinitions(@NonNull AttributeDefinitions wrappee) {
    myWrappee = wrappee;
  }

  protected abstract boolean isAttributeAcceptable(@NonNull ResourceReference attr);

  @Override
  @Nullable
  public StyleableDefinition getStyleableDefinition(@NonNull ResourceReference styleable) {
    StyleableDefinition styleableDef = myWrappee.getStyleableDefinition(styleable);
    return styleableDef != null ? new MyStyleableDefinition(styleableDef) : null;
  }

  @Deprecated
  @Override
  @Nullable
  public StyleableDefinition getStyleableByName(@NonNull String name) {
    StyleableDefinition styleable = myWrappee.getStyleableByName(name);
    return styleable != null ? new MyStyleableDefinition(styleable) : null;
  }

  @Override
  @NonNull
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
  public AttributeDefinition getAttrDefinition(@NonNull ResourceReference attr) {
    AttributeDefinition attribute = myWrappee.getAttrDefinition(attr);
    return attribute != null && isAttributeAcceptable(attr) ? attribute : null;
  }

  @Deprecated
  @Override
  @Nullable
  public AttributeDefinition getAttrDefByName(@NonNull String name) {
    AttributeDefinition attribute = myWrappee.getAttrDefByName(name);
    return attribute != null && isAttributeAcceptable(attribute.getResourceReference()) ? attribute : null;
  }

  @Nullable
  @Override
  public String getAttrGroup(@NonNull ResourceReference attr) {
    return myWrappee.getAttrGroup(attr);
  }

  private class MyStyleableDefinition implements StyleableDefinition {
    private final StyleableDefinition myWrappee;

    private MyStyleableDefinition(@NonNull StyleableDefinition wrappee) {
      myWrappee = wrappee;
    }

    @Override
    @NonNull
    public ResourceReference getResourceReference() {
      return myWrappee.getResourceReference();
    }

    @Override
    @NonNull
    public String getName() {
      return myWrappee.getName();
    }

    @Override
    @NonNull
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
