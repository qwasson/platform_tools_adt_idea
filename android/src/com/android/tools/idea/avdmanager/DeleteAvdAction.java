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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.popup.PopupComponent;
import com.intellij.ui.popup.PopupDispatcher;
import com.intellij.util.ui.ConfirmationDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Delete an AVD with confirmation.
 */
public class DeleteAvdAction extends AvdUiAction {
  private static final Logger LOG = Logger.getInstance(DeleteAvdAction.class);

  public DeleteAvdAction(AvdInfoProvider avdInfoProvider) {
    super(avdInfoProvider, "Delete", "Delete this AVD", AllIcons.Actions.Delete);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    AvdInfo info = getAvdInfo();
    if (info == null) {
      return;
    }
    if (info.isRunning()) {
      JOptionPane.showMessageDialog(null, "The selected AVD is currently running in the Emulator. " +
                                          "Please exit the emulator instance and try deleting again.",
                                    "Cannot Delete A Running AVD", JOptionPane.ERROR_MESSAGE);
      return;
    }
    int result = JOptionPane.showConfirmDialog(null, "Do you really want to delete AVD " + info.getName() + "?",
                                               "Confirm Deletion", JOptionPane.YES_NO_OPTION);
    if (result == JOptionPane.YES_OPTION) {
      AvdManagerConnection.deleteAvd(info);
      refreshAvds();
    }
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
