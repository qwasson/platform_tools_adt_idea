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
package com.android.tools.idea.wizard;

import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.android.tools.idea.wizard.DynamicWizardStepTest.DummyDynamicWizardStep;

/**
 * Tests for {@link DynamicWizardPath}
 */
public class DynamicWizardPathTest extends TestCase {

  DummyDynamicWizardPath myPath;
  DummyDynamicWizardStep myStep1;
  DummyDynamicWizardStep myStep2;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myPath = new DummyDynamicWizardPath("TestPath");
    myStep1 = new DummyDynamicWizardStep("TestStep1");
    myStep2 = new DummyDynamicWizardStep("TestStep2");
  }

  public void testAddStep() throws Exception {
    assertEquals(0, myPath.getVisibleStepCount());
    assertEquals(0, myPath.mySteps.size());

    myPath.addStep(myStep1);
    assertEquals(1, myPath.getVisibleStepCount());
    assertEquals(1, myPath.mySteps.size());

    myPath.addStep(myStep2);
    assertEquals(2, myPath.getVisibleStepCount());
    assertEquals(2, myPath.mySteps.size());
  }

  public void testGetStepCount() throws Exception {
    myPath.addStep(myStep1);
    myPath.addStep(myStep2);
    myPath.onPathStarted(true);

    assertEquals(2, myPath.getVisibleStepCount());
    assertFalse(myPath.hasPrevious());

    myStep1.myState.put(myStep1.VISIBLE_KEY, false);
    assertTrue(myPath.hasNext());
    assertEquals(1, myPath.getVisibleStepCount());
    assertFalse(myPath.hasPrevious());

    myStep2.myState.put(myStep2.VISIBLE_KEY, false);
    assertFalse(myPath.hasNext());
    assertEquals(0, myPath.getVisibleStepCount());
    assertFalse(myPath.hasPrevious());

    myStep2.myState.put(myStep2.VISIBLE_KEY, true);
    assertTrue(myPath.hasNext());
    assertTrue(myPath.canGoNext());
    myPath.next();
    assertEquals(1, myPath.getVisibleStepCount());
    assertFalse(myPath.hasPrevious());

    myStep1.myState.put(myStep1.VISIBLE_KEY, true);
    assertEquals(2, myPath.getVisibleStepCount());
    assertTrue(myPath.hasPrevious());
  }

  public void testNavigation() throws Exception {
    myPath.addStep(myStep1);
    myPath.addStep(myStep2);

    myPath.onPathStarted(true);

    assertTrue(myPath.canGoNext());
    assertTrue(myPath.canGoPrevious()); // We can still walk backwards out of the path as far as the path is concerned

    // Advance to the second step
    assertEquals(myStep2, myPath.next());

    assertTrue(myPath.canGoNext());
    assertTrue(myPath.canGoPrevious());

    // Go back to the first step
    assertEquals(myStep1, myPath.previous());

    assertTrue(myPath.canGoNext());
    assertTrue(myPath.canGoPrevious());

    // While still on the first step, hide the second step
    myStep2.myState.put(myStep2.VISIBLE_KEY, false);
    assertFalse(myStep2.isStepVisible());

    assertTrue(myPath.canGoNext());
    assertTrue(myPath.canGoPrevious());

    // Show the second step, but make the first step invalid
    myStep2.myState.put(myStep2.VISIBLE_KEY, true);
    assertTrue(myStep2.isStepVisible());
    myStep1.myState.put(myStep1.VALID_KEY, false);
    assertFalse(myStep1.canGoNext());

    assertFalse(myPath.canGoNext());
    assertTrue(myPath.canGoPrevious());

    // Calls to next should return the current step since it's invalid
    assertEquals(myStep1, myPath.next());
  }

  public static class DummyDynamicWizardPath extends DynamicWizardPath {

    protected final ScopedStateStore.Key<Boolean> VISIBLE_KEY;
    protected final ScopedStateStore.Key<Boolean> REQUIRED_KEY;
    protected final ScopedStateStore.Key<String> DERIVED_KEY;
    protected final ScopedStateStore.Key<Boolean> VALID_KEY;
    private String myName;

    public DummyDynamicWizardPath(@NotNull String name) {
      myName = name;
      VALID_KEY = myState.createKey(getPathName() + ":inputValue", Boolean.class);
      DERIVED_KEY = myState.createKey(getPathName() + ":derivedValue", String.class);
      VISIBLE_KEY = myState.createKey(getPathName() + ":isVisible", Boolean.class);
      REQUIRED_KEY = myState.createKey(getPathName() + ":isRequired", Boolean.class);
    }

    @Override
    protected void init() {
      // Do nothing
    }

    @Override
    public void deriveValues(Set<ScopedStateStore.Key> modified) {
      myState.put(DERIVED_KEY, "derived!");
    }

    @Override
    public boolean validate() {
      Boolean valid = myState.get(VALID_KEY);
      String derivedString = myState.get(DERIVED_KEY);
      if (valid != null) {
        return valid;
      } else {
        return derivedString != null && "derived!".equals(derivedString);
      }
    }

    @Override
    public boolean isPathVisible() {
      Boolean visible = myState.get(VISIBLE_KEY);
      return visible == null || visible;
    }

    @Override
    public boolean isPathRequired() {
      Boolean required = myState.get(REQUIRED_KEY);
      return required == null || required;
    }

    @NotNull
    @Override
    public String getPathName() {
      return myName;
    }

    @Override
    public boolean performFinishingActions() {
      return true;
    }
  }
}
