/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.common.settings;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.common.settings.Property.Getter;
import com.google.idea.common.settings.Property.Setter;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * A setting descriptor for an {@link AutoConfigurable}.
 *
 * <p>Describes how to represent the setting in a UI, and retrieve and update its value. This is an
 * immutable value class. It is safe to create static instances; this will not create any Swing
 * components or access/modify setting state.
 */
@AutoValue
public abstract class ConfigurableSetting<ValueT, ComponentT extends SettingComponent<ValueT>> {

  abstract SearchableText searchableText();

  /** Returns the UI label of this setting. */
  public final String label() {
    return searchableText().label();
  }

  /** Returns a {@link Property} for accessing and modifying the setting. */
  abstract Property<ValueT> setting();

  abstract Optional<Supplier<Boolean>> hideCondition();

  abstract ComponentFactory<ComponentT> componentFactory();

  /** Creates a {@link SettingComponent} for representing this setting in a UI. */
  final ComponentT createComponent() {
    ComponentT component = componentFactory().createComponent(label());
    hideCondition().ifPresent(hide -> component.setEnabledAndVisible(!hide.get()));
    return component;
  }

  /** A factory for creating {@link SettingComponent SettingComponents}. */
  @FunctionalInterface
  public interface ComponentFactory<ComponentT extends SettingComponent<?>> {
    ComponentT createComponent(String label);
  }

  /**
   * Creates a {@link ConfigurableSetting} with the given text, setting property, condition for
   * hiding, and {@link ComponentFactory}.
   */
  public static <ValueT, ComponentT extends SettingComponent<ValueT>>
      ConfigurableSetting<ValueT, ComponentT> create(
          SearchableText searchableText,
          Property<ValueT> settingProperty,
          @Nullable Supplier<Boolean> hideCondition,
          ComponentFactory<ComponentT> componentFactory) {
    return new AutoValue_ConfigurableSetting<>(
        searchableText, settingProperty, Optional.ofNullable(hideCondition), componentFactory);
  }

  /**
   * Returns a builder for creating a {@link ConfigurableSetting}.
   *
   * @param settingsProvider provides the object containing the setting to represent
   */
  public static <SettingsT> Builder<SettingsT> builder(Supplier<SettingsT> settingsProvider) {
    return new Builder<>(settingsProvider);
  }

  // Split builder to support generic type inference.
  // This allows fluent chained calls to determine ValueT and ComponentT,
  // so the caller doesn't have to declare the type up-front on #builder.
  private abstract static class AbstractBuilder<SettingsT, BuilderT> {

    final SearchableText.Builder searchableTextBuilder;
    final Supplier<SettingsT> settingsProvider;

    @Nullable Supplier<Boolean> hideCondition;

    AbstractBuilder(Supplier<SettingsT> settingsProvider) {
      this.searchableTextBuilder = SearchableText.builder();
      this.settingsProvider = settingsProvider;
      this.hideCondition = null;
    }

    AbstractBuilder(AbstractBuilder<SettingsT, ?> other) {
      this.searchableTextBuilder = other.searchableTextBuilder.build().toBuilder();
      this.settingsProvider = other.settingsProvider;
      this.hideCondition = other.hideCondition;
    }

    abstract BuilderT self();

    /** Sets the UI label for this setting. */
    @CanIgnoreReturnValue
    public BuilderT label(String label) {
      searchableTextBuilder.setLabel(label);
      return self();
    }

    /**
     * Adds search terms, allowing this setting to be a result for the given strings, even if they
     * don't appear in the user-visible label.
     */
    @CanIgnoreReturnValue
    public BuilderT addTags(String... tags) {
      searchableTextBuilder.addTags(tags);
      return self();
    }

    /**
     * Sets a condition for hiding and disabling this setting in the UI.
     *
     * <p>The condition will only be checked when the UI is created, not on subsequent updates.
     *
     * @throws IllegalStateException if {@link #hideIf} or {@link #showIf} has already been called.
     *     Only one condition is supported.
     */
    @CanIgnoreReturnValue
    public BuilderT hideIf(Supplier<Boolean> hideCondition) {
      checkState(this.hideCondition == null, "hideIf/showIf can only be called once");
      this.hideCondition = hideCondition;
      return self();
    }

    /**
     * Sets a condition for showing and enabling this setting in the UI.
     *
     * <p>The condition will only be checked when the UI is created, not on subsequent updates.
     *
     * @throws IllegalStateException if {@link #hideIf} or {@link #showIf} has already been called.
     *     Only one condition is supported.
     */
    public BuilderT showIf(Supplier<Boolean> showCondition) {
      return hideIf(() -> !showCondition.get());
    }
  }

  /** A builder for {@link ConfigurableSetting}. */
  public static final class Builder<SettingsT>
      extends AbstractBuilder<SettingsT, Builder<SettingsT>> {

    private Builder(Supplier<SettingsT> settingsProvider) {
      super(settingsProvider);
    }

    @Override
    Builder<SettingsT> self() {
      return this;
    }

    /** Sets the {@link Getter} used to retrieve the setting value. */
    public <ValueT> TypedBuilder<SettingsT, ValueT> getter(Getter<SettingsT, ValueT> getter) {
      return new TypedBuilder<SettingsT, ValueT>(this).getter(getter);
    }

    /** Sets the {@link Setter} used to update the setting value. */
    public <ValueT> TypedBuilder<SettingsT, ValueT> setter(Setter<SettingsT, ValueT> setter) {
      return new TypedBuilder<SettingsT, ValueT>(this).setter(setter);
    }
  }

  /** A builder for {@link ConfigurableSetting}. */
  public static final class TypedBuilder<SettingsT, ValueT>
      extends AbstractBuilder<SettingsT, TypedBuilder<SettingsT, ValueT>> {

    private Getter<SettingsT, ValueT> getter;
    private Setter<SettingsT, ValueT> setter;

    private TypedBuilder(AbstractBuilder<SettingsT, ?> other) {
      super(other);
    }

    @Override
    TypedBuilder<SettingsT, ValueT> self() {
      return this;
    }

    /** Sets the {@link Getter} used to retrieve the setting value. */
    @CanIgnoreReturnValue
    public TypedBuilder<SettingsT, ValueT> getter(Getter<SettingsT, ValueT> getter) {
      this.getter = getter;
      return self();
    }

    /** Sets the {@link Setter} used to update the setting value. */
    @CanIgnoreReturnValue
    public TypedBuilder<SettingsT, ValueT> setter(Setter<SettingsT, ValueT> setter) {
      this.setter = setter;
      return self();
    }

    /**
     * Sets the {@link ComponentFactory} used to create the UI component, and returns the built
     * {@link ConfigurableSetting}.
     */
    public <ComponentT extends SettingComponent<ValueT>>
        ConfigurableSetting<ValueT, ComponentT> componentFactory(
            ComponentFactory<ComponentT> componentFactory) {

      return create(
          searchableTextBuilder.build(),
          Property.create(settingsProvider, getter, setter),
          hideCondition,
          componentFactory);
    }
  }
}
