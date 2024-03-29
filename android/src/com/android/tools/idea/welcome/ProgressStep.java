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
package com.android.tools.idea.welcome;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Wizard step with progress bar and "more details" button.
 */
public abstract class ProgressStep extends FirstRunWizardStep {
  private JPanel myRoot;
  private JProgressBar myProgressBar;
  private JButton myShowDetailsButton;
  private JLabel myLabel;

  public ProgressStep(@NotNull String title, @NotNull String label) {
    super(title);
    setComponent(myRoot);
    myLabel.setText(label);
  }

  @Override
  public void init() {

  }

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myShowDetailsButton;
  }
}
