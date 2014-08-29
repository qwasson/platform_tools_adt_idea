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
package com.android.tools.idea.gradle.service.notification.errors;

import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.easymock.EasyMock.createMock;

public class ClassLoadingErrorHandlerTest extends TestCase {
  private ExternalSystemException myError;
  private NotificationData myNotification;
  private Project myProject;

  private ClassLoadingErrorHandler myErrorHandler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myError = new ExternalSystemException("Test");
    myNotification = new NotificationData("Test", "Testing", NotificationCategory.ERROR, NotificationSource.PROJECT_SYNC);
    myProject = createMock(Project.class);
    myErrorHandler = new ClassLoadingErrorHandler();
  }

  public void testHandleErrorWhenClassNotLoaded() throws Exception {
    assertQuickFixHyperlinksAreDisplayed("Unable to load class 'java.util.List'");
  }

  public void testHandleErrorWhenMethodNotFound() throws Exception {
    assertQuickFixHyperlinksAreDisplayed("Unable to find method 'org.slf4j.spi.LocationAwareLogger.log'");
  }

  public void testHandleErrorWhenClassCannotBeCast() throws Exception {
    assertQuickFixHyperlinksAreDisplayed("Cause: org.slf4j.impl.JDK14LoggerFactory cannot be cast to ch.qos.logback.classic.LoggerContext");
  }

  private void assertQuickFixHyperlinksAreDisplayed(@NotNull String errorMsg) {
    List<String> message = Lists.newArrayList(errorMsg);
    assertTrue(myErrorHandler.handleError(message, myError, myNotification, myProject));
    String notification = myNotification.getMessage();
    assertTrue(notification.contains("Re-download dependencies and sync project"));
    assertTrue(notification.contains("Stop Gradle daemons and sync project"));
  }
}
