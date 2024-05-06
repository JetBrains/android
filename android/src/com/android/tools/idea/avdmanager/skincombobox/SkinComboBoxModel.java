/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.skincombobox;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.AbstractListModel;
import javax.swing.MutableComboBoxModel;
import org.jetbrains.annotations.NotNull;

public final class SkinComboBoxModel extends AbstractListModel<Skin> implements MutableComboBoxModel<Skin> {
  @NotNull
  private List<Skin> mySkins;

  @NotNull
  private Object mySelectedSkin;

  @NotNull
  private final Callable<Collection<Skin>> myCollect;

  @NotNull
  private final Function<SkinComboBoxModel, FutureCallback<Collection<Skin>>> myNewMerge;

  SkinComboBoxModel(@NotNull Callable<Collection<Skin>> collect) {
    this(collect, Merge::new);
  }

  @VisibleForTesting
  SkinComboBoxModel(@NotNull Collection<Skin> skins) {
    this(skins, List::of, model -> null);
  }

  @VisibleForTesting
  public SkinComboBoxModel(@NotNull Callable<Collection<Skin>> collect,
                           @NotNull Function<SkinComboBoxModel, FutureCallback<Collection<Skin>>> newMerge) {
    this(List.of(NoSkin.INSTANCE), collect, newMerge);
  }

  private SkinComboBoxModel(@NotNull Collection<Skin> skins,
                            @NotNull Callable<Collection<Skin>> collect,
                            @NotNull Function<SkinComboBoxModel, FutureCallback<Collection<Skin>>> newMerge) {
    mySkins = new ArrayList<>(skins);
    mySelectedSkin = NoSkin.INSTANCE;
    myCollect = collect;
    myNewMerge = newMerge;
  }

  void load() {
    var future = Futures.submit(myCollect, AppExecutorUtil.getAppExecutorService());
    Futures.addCallback(future, myNewMerge.apply(this), EdtExecutorService.getInstance());
  }

  @VisibleForTesting
  static final class Merge implements FutureCallback<Collection<Skin>> {
    @NotNull
    private final SkinComboBoxModel myModel;

    @VisibleForTesting
    Merge(@NotNull SkinComboBoxModel model) {
      myModel = model;
    }

    @Override
    public void onSuccess(@NotNull Collection<Skin> skins) {
      var map = Stream.concat(myModel.mySkins.stream(), skins.stream()).collect(Collectors.toMap(Skin::path, skin -> skin, Skin::merge));

      myModel.mySkins = map.values().stream()
        .sorted()
        .collect(Collectors.toList());

      myModel.fireContentsChanged(myModel, 0, myModel.mySkins.size() - 1);
    }

    @Override
    public void onFailure(@NotNull Throwable throwable) {
      Logger.getInstance(SkinComboBoxModel.class).warn(throwable);
    }
  }

  @NotNull
  Skin getSkin(@NotNull Path path) {
    return mySkins.stream()
      .filter(skin -> skin.path().equals(path))
      .findFirst()
      .orElse(new DefaultSkin(path));
  }

  @VisibleForTesting
  @NotNull
  Object getSkins() {
    return mySkins;
  }

  @Override
  public int getSize() {
    return mySkins.size();
  }

  @NotNull
  @Override
  public Skin getElementAt(int index) {
    return mySkins.get(index);
  }

  @Override
  public void addElement(@NotNull Skin skin) {
    if (mySkins.contains(skin)) {
      return;
    }

    mySkins.add(skin);
    mySkins.sort(null);

    var index = mySkins.indexOf(skin);
    fireIntervalAdded(this, index, index);
  }

  @Override
  public void removeElement(@NotNull Object skin) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void insertElementAt(@NotNull Skin skin, int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeElementAt(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setSelectedItem(@NotNull Object selectedSkin) {
    assert selectedSkin instanceof Skin skin && mySkins.contains(skin);

    mySelectedSkin = selectedSkin;
    fireContentsChanged(this, -1, -1);
  }

  @NotNull
  @Override
  public Object getSelectedItem() {
    return mySelectedSkin;
  }
}
