/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.variant.view;

import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link BuildVariantView}.
 */
public class BuildVariantViewTest extends AndroidTestCase {
  private Listener myListener;
  private BuildVariantUpdater myUpdater;
  private BuildVariantView myView;
  private List<AndroidFacet> myAndroidFacets;
  private String myBuildVariantName;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myUpdater = createMock(BuildVariantUpdater.class);
    myView = BuildVariantView.getInstance(getProject());
    myView.setUpdater(myUpdater);
    myListener = new Listener();
    myView.addListener(myListener);
    myAndroidFacets = Collections.singletonList(myFacet);
    myBuildVariantName = "debug";
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void testSelectVariantWithSuccessfulUpdate() {
    expect(myUpdater.updateModule(getProject(), myModule.getName(), myBuildVariantName)).andStubReturn(myAndroidFacets);
    replay(myUpdater);

    myView.selectVariant(myModule, myBuildVariantName);
    assertTrue(myListener.myWasCalled);

    verify(myUpdater);
  }

  public void testSelectVariantWithFailedUpdate() {
    List<AndroidFacet> facets = Collections.emptyList();
    expect(myUpdater.updateModule(getProject(), myModule.getName(), myBuildVariantName)).andStubReturn(facets);
    replay(myUpdater);

    myView.selectVariant(myModule, myBuildVariantName);
    assertFalse(myListener.myWasCalled);

    verify(myUpdater);
  }

  private static class Listener implements BuildVariantView.BuildVariantSelectionChangeListener {
    boolean myWasCalled;

    @Override
    public void buildVariantSelected(@NotNull List<AndroidFacet> updatedFacets) {
      myWasCalled = true;
    }
  }
}
