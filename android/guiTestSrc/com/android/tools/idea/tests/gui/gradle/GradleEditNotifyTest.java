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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.junit.Test;

import java.io.IOException;

public class GradleEditNotifyTest extends GuiTestCase {
  @Test
  @IdeGuiTest
  public void testEditNotify() throws IOException {
    // Edit a build.gradle file and ensure that you are immediately notified that
    // the build.gradle model is out of date
    // Regression test for https://code.google.com/p/android/issues/detail?id=75983

    IdeFrameFixture projectFrame = openSimpleApplication();
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/build.gradle");

    // When done and a successful gradle sync there should not be any messages
    projectFrame.requireEditorNotification(null);

    // Insert:
    editor.moveTo(editor.findOffset("versionCode ", null, true));
    editor.enterText("1");
    projectFrame.requireEditorNotification("Gradle files have changed since last project sync");

    // Sync
    projectFrame.clickEditorNotification("Gradle files have changed since last project sync", "Sync Now");
    projectFrame.waitForGradleProjectSyncToFinish();
    projectFrame.requireEditorNotification(null);

    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    projectFrame.requireEditorNotification("Gradle files have changed since last project sync");
  }
}
