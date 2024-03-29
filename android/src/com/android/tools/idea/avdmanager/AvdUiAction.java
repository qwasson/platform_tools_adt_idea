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

import com.android.sdklib.internal.avd.AvdInfo;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.util.Map;

/**
 * Abstract base class for AVD editing actions
 */
public abstract class AvdUiAction implements Action {
  @NotNull private final AvdInfoProvider myAvdInfoProvider;
  @NotNull private final String myText;
  @NotNull private final String myDescription;
  @NotNull private final Icon myIcon;
  private Map<String, Object> myData = Maps.newHashMap();

  public interface AvdInfoProvider {
    @Nullable
    AvdInfo getAvdInfo();
    void refreshAvds();
  }

  public AvdUiAction(@NotNull AvdInfoProvider avdInfoProvider, @NotNull String text, @NotNull String description, @NotNull Icon icon) {
    myAvdInfoProvider = avdInfoProvider;
    myText = text;
    myDescription = description;
    myIcon = icon;
  }

  @Override
  public Object getValue(String key) {
    return myData.get(key);
  }

  @Override
  public void putValue(String key, Object value) {
    myData.put(key, value);
  }

  @Override
  @Deprecated
  public void setEnabled(boolean b) {

  }

  @Override
  public abstract boolean isEnabled();

  @Override
  public void addPropertyChangeListener(PropertyChangeListener listener) {

  }

  @Override
  public void removePropertyChangeListener(PropertyChangeListener listener) {

  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  protected AvdInfo getAvdInfo() {
    return myAvdInfoProvider.getAvdInfo();
  }

  protected void refreshAvds() {
    myAvdInfoProvider.refreshAvds();
  }
}
