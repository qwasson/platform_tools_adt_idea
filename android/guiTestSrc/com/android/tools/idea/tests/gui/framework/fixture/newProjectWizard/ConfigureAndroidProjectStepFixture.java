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
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import com.android.tools.idea.wizard.LabelWithEditLink;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

import static org.fest.assertions.Assertions.assertThat;

public class ConfigureAndroidProjectStepFixture extends AbstractWizardStepFixture {
  protected ConfigureAndroidProjectStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(robot, target);
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture enterApplicationName(@NotNull String text) {
    JTextField textField = findTextFieldWithLabel("Application name:");
    replaceText(textField, text);
    return this;
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture enterCompanyDomain(@NotNull String text) {
    JTextField textField = findTextFieldWithLabel("Company Domain:");
    replaceText(textField, text);
    return this;
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture enterPackageName(@NotNull String text) {
    LabelWithEditLink link = robot.finder().findByType(target, LabelWithEditLink.class);

    JBLabel editLabel = robot.finder().find(link, new GenericTypeMatcher<JBLabel>(JBLabel.class) {
      @Override
      protected boolean isMatching(JBLabel label) {
        return "<html><a>Edit</a></html>".equals(label.getText());
      }
    });
    robot.click(editLabel);

    final JTextField textField = robot.finder().findByType(link, JTextField.class);
    Pause.pause(new Condition("'Package name' field is visible") {
      @Override
      public boolean test() {
        return textField.isShowing();
      }
    });
    replaceText(textField, text);

    // click "Done"
    robot.click(editLabel);
    return this;
  }

  @NotNull
  public File getLocationInFileSystem() {
    final TextFieldWithBrowseButton locationField = robot.finder().findByType(target, TextFieldWithBrowseButton.class);
    return GuiActionRunner.execute(new GuiQuery<File>() {
      @Override
      protected File executeInEDT() throws Throwable {
        String location = locationField.getText();
        assertThat(location).isNotNull().isNotEmpty();
        return new File(location);
      }
    });
  }
}
