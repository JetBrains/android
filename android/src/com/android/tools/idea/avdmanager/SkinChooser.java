/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.tools.idea.concurrency.FutureUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Combobox that populates itself with the skins used by existing devices. Also allows adding a
 * new skin by browsing.
 */
public class SkinChooser extends ComboboxWithBrowseButton implements ItemListener, ItemSelectable {
  private static final File LOADING_SKINS = new File("_loading_skins");

  @VisibleForTesting
  static final File FAILED_TO_LOAD_SKINS = new File("_failed_to_load_skins");

  private final @NotNull Supplier<@NotNull ListenableFuture<@NotNull Collection<@NotNull Path>>> myUpdateSkins;
  private final @NotNull Executor myDeviceSkinUpdaterServiceExecutor;
  private final @NotNull Executor myEdtExecutor;
  private List<ItemListener> myListeners = new ArrayList<>();

  SkinChooser(@Nullable Project project, boolean includeSdkHandlerSkins) {
    this(project,
         updateSkins(includeSdkHandlerSkins),
         DeviceSkinUpdaterService.getInstance().getExecutor(),
         EdtExecutorService.getInstance());
  }

  private static @NotNull Supplier<@NotNull ListenableFuture<@NotNull Collection<@NotNull Path>>> updateSkins(boolean includeSdkHandlerSkins) {
    if (includeSdkHandlerSkins) {
      return DeviceSkinUpdaterService.getInstance()::updateSkinsIncludingSdkHandlerOnes;
    }

    return DeviceSkinUpdaterService.getInstance()::updateSkinsExcludingSdkHandlerOnes;
  }

  @VisibleForTesting
  SkinChooser(@Nullable Project project,
              @NotNull Supplier<@NotNull ListenableFuture<@NotNull Collection<@NotNull Path>>> updateSkins,
              @NotNull Executor deviceSkinUpdaterServiceExecutor,
              @NotNull Executor edtExecutor) {
    myUpdateSkins = updateSkins;
    myDeviceSkinUpdaterServiceExecutor = deviceSkinUpdaterServiceExecutor;
    myEdtExecutor = edtExecutor;

    getComboBox().setRenderer(new ColoredListCellRenderer<File>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<@NotNull ? extends File> list,
                                           @Nullable File skin,
                                           int index,
                                           boolean selected,
                                           boolean focused) {
        if (FileUtil.filesEqual(skin, LOADING_SKINS)) {
          append("Loading skins...");
          return;
        }

        if (FileUtil.filesEqual(skin, FAILED_TO_LOAD_SKINS)) {
          append("Failed to load skins");
          return;
        }

        if (skin == null) {
          skin = AvdWizardUtils.NO_SKIN;
        }

        String skinPath = skin.getPath();
        if (FileUtil.filesEqual(skin, AvdWizardUtils.NO_SKIN)) {
          append("No Skin");
        }
        else if (skinPath.contains("/sdk/platforms/")) {
          append(skinPath.replaceAll(".*/sdk/platforms/(.*)/skins/(.*)", "$2 ($1)"));
        }
        else if (skinPath.contains("/sdk/system-images/")) {
          append(skinPath.replaceAll(".*/sdk/system-images/(.*)/(.*)/(.*)/skins/(.*)", "$4 ($1 $3)"));
        }
        else {
          append(skin.getName());
        }
      }
    });
    FileChooserDescriptor skinChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    addBrowseFolderListener("Select Custom Skin", "Select the directory containing your custom skin definition", project,
                            skinChooserDescriptor, new TextComponentAccessor<JComboBox>() {
        @Override
        public String getText(JComboBox component) {
          return ((File)component.getSelectedItem()).getPath();
        }

        @Override
        public void setText(JComboBox component, @NotNull String text) {
          loadSkins(skins -> {
            File skin = new File(text);
            skins.add(skin);

            setItems(skins);
            getComboBox().setSelectedItem(skin);
          });
        }
      });
    getComboBox().addItemListener(this);
    setTextFieldPreferredWidth(20);

    loadSkins(this::setItems);
  }

  private void loadSkins(@NotNull Consumer<@NotNull List<@NotNull File>> consumer) {
    setEnabled(false);
    setItems(Collections.singletonList(LOADING_SKINS));

    @SuppressWarnings("UnstableApiUsage")
    ListenableFuture<Collection<Path>> future = Futures.transform(myUpdateSkins.get(),
                                                                  SkinChooser::transform,
                                                                  myDeviceSkinUpdaterServiceExecutor);

    FutureUtils.addCallback(future, myEdtExecutor, new FutureCallback<Collection<Path>>() {
      @Override
      public void onSuccess(@Nullable Collection<@NotNull Path> skins) {
        assert skins != null;
        consumer.accept(ContainerUtil.map(skins, Path::toFile));

        setEnabled(true);
      }

      @Override
      public void onFailure(@NotNull Throwable throwable) {
        Logger.getInstance(SkinChooser.class).warn(throwable);
        setItems(Collections.singletonList(FAILED_TO_LOAD_SKINS));
      }
    });
  }

  private static @NotNull Collection<@NotNull Path> transform(@NotNull Collection<@NotNull Path> paths) {
    List<Path> transformed = new ArrayList<>(1 + paths.size());
    transformed.add(Paths.get(AvdManagerUtils.NO_SKIN));

    paths.stream()
      .filter(Files::exists)
      .distinct()
      .sorted()
      .forEach(transformed::add);

    return transformed;
  }

  @VisibleForTesting
  final Object getItems() {
    JComboBox<File> comboBox = getComboBox();

    return IntStream.range(0, comboBox.getItemCount())
      .mapToObj(comboBox::getItemAt)
      .collect(Collectors.toList());
  }

  private void setItems(List<File> items) {
    getComboBox().setModel(new CollectionComboBoxModel<>(items));
  }

  @Override
  @SuppressWarnings("EmptyMethod")
  public final JComboBox<File> getComboBox() {
    // noinspection unchecked
    return super.getComboBox();
  }

  @Override
  public void itemStateChanged(ItemEvent e) {
    ItemEvent newEvent = new ItemEvent(this, e.getID(), e.getItem(), e.getStateChange());
    for (ItemListener listener : myListeners) {
      listener.itemStateChanged(newEvent);
    }
  }

  @Override
  public Object[] getSelectedObjects() {
    return getComboBox().getSelectedObjects();
  }

  @Override
  public void addItemListener(ItemListener l) {
    getComboBox().addItemListener(l);
  }

  @Override
  public void removeItemListener(ItemListener l) {
    getComboBox().removeItemListener(l);
  }
}
