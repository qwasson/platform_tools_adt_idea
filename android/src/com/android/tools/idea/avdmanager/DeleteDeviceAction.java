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

import com.android.sdklib.devices.Device;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Action to delete a selected {@link Device}.
 */
public class DeleteDeviceAction extends DeviceUiAction {
  public DeleteDeviceAction(@NotNull DeviceProvider provider) {
    super(provider, "Delete");
  }

  @Override
  public boolean isEnabled() {
    Device device = myProvider.getDevice();
    return device != null && DeviceManagerConnection.isUserDevice(device);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    Device device = myProvider.getDevice();
    int result = JOptionPane
      .showConfirmDialog(null, "Do you really want to delete Device " + device.getDisplayName() + "?",
                         "Confirm Deletion", JOptionPane.YES_NO_OPTION);
    if (result == JOptionPane.YES_OPTION) {
      DeviceManagerConnection.deleteDevice(device);
      myProvider.refreshDevices();
    }
  }
}
