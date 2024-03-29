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

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.*;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;

public class AvdListDialogTest extends GuiTestCase {
  @Test
  @IdeGuiTest
  public void testCreateAvd() throws Exception {
    IdeFrameFixture ideFrame = openSimpleApplication();
    AvdManagerDialogFixture avdManagerDialog = ideFrame.invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();

    ChooseDeviceDefinitionStepFixture chooseDeviceDefinitionStep = avdEditWizard.getChooseDeviceDefinitionStep();
    chooseDeviceDefinitionStep.enterSearchTerm("Nexus").selectDeviceByName("Nexus 7");
    avdEditWizard.clickNext();

    ChooseSystemImageStepFixture chooseSystemImageStep = avdEditWizard.getChooseSystemImageStep();
    chooseSystemImageStep.selectSystemImage("KitKat", "19", "x86", "Android 4.4.2");
    avdEditWizard.clickNext();

    ConfigureAvdOptionsStepFixture configureAvdOptionsStep = avdEditWizard.getConfigureAvdOptionsStep();
    configureAvdOptionsStep.showAdvancedSettings();
    configureAvdOptionsStep.setFrontCamera("Emulated");
    configureAvdOptionsStep.setScaleFactor("1dp on device = 1px on screen").setUseHostGpu(true);
    avdEditWizard.clickFinish();
    avdManagerDialog.close();

    // Ensure the AVD was created
    avdManagerDialog.selectAvdByName("Nexus 7 2013");
    // Then clean it up
    avdManagerDialog.deleteAvdByName("Nexus 7 2013");
  }

  @Test
  @IdeGuiTest
  public void testEditAvd() throws Exception {
    IdeFrameFixture ideFrame = openSimpleApplication();
    makeNexus5(ideFrame);
    AvdManagerDialogFixture avdManagerDialog = ideFrame.invokeAvdManager();
    AvdEditWizardFixture avdEditWizardFixture = avdManagerDialog.editAvdWithName("Nexus 5");
    ConfigureAvdOptionsStepFixture configureAvdOptionsStep = avdEditWizardFixture.getConfigureAvdOptionsStep();

    configureAvdOptionsStep.showAdvancedSettings();
    configureAvdOptionsStep.setUseHostGpu(true);
    
    avdEditWizardFixture.clickFinish();
    avdManagerDialog.close();

    removeNexus5(ideFrame);
  }

  public static void makeNexus5(@NotNull IdeFrameFixture ideFrame) throws Exception {
    AvdManagerDialogFixture avdManagerDialog = ideFrame.invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();

    ChooseDeviceDefinitionStepFixture chooseDeviceDefinitionStep = avdEditWizard.getChooseDeviceDefinitionStep();
    chooseDeviceDefinitionStep.selectDeviceByName("Nexus 5");
    avdEditWizard.clickNext();

    ChooseSystemImageStepFixture chooseSystemImageStep = avdEditWizard.getChooseSystemImageStep();
    chooseSystemImageStep.selectSystemImage("KitKat", "19", "x86", "Android 4.4.2");
    avdEditWizard.clickNext();
    avdEditWizard.clickFinish();
    avdManagerDialog.close();
  }


  public static void removeNexus5(@NotNull IdeFrameFixture ideFrame) {
    AvdManagerDialogFixture avdManagerDialog = ideFrame.invokeAvdManager();
    avdManagerDialog.deleteAvdByName("Nexus 5");
  }
}