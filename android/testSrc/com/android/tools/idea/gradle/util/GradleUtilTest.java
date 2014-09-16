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
package com.android.tools.idea.gradle.util;

import com.android.sdklib.repository.FullRevision;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static com.android.SdkConstants.*;

/**
 * Tests for {@link GradleUtil}.
 */
public class GradleUtilTest extends TestCase {
  private File myTempDir;

  @Override
  protected void tearDown() throws Exception {
    if (myTempDir != null) {
      FileUtil.delete(myTempDir);
    }
    super.tearDown();
  }

  public void testGetGradleInvocationJvmArgWithNullBuildMode() {
    assertNull(GradleUtil.getGradleInvocationJvmArg(null));
  }

  public void testGetGradleInvocationJvmArgWithAssembleTranslateBuildMode() {
    assertEquals("-DenableTranslation=true", GradleUtil.getGradleInvocationJvmArg(BuildMode.ASSEMBLE_TRANSLATE));
  }

  public void testGetGradleWrapperPropertiesFilePath() throws IOException {
    myTempDir = Files.createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    FileUtilRt.createIfNotExists(wrapper);
    GradleUtil.updateGradleDistributionUrl("1.6", wrapper);

    Properties properties = PropertiesUtil.getProperties(wrapper);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("http://services.gradle.org/distributions/gradle-1.6-all.zip", distributionUrl);
  }

  public void testLeaveGradleWrapperAloneBin() throws IOException {
    // Ensure that if we already have the right version, we don't replace a -bin.zip with a -all.zip
    myTempDir = Files.createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    Files.write("#Wed Apr 10 15:27:10 PDT 2013\n" +
                "distributionBase=GRADLE_USER_HOME\n" +
                "distributionPath=wrapper/dists\n" +
                "zipStoreBase=GRADLE_USER_HOME\n" +
                "zipStorePath=wrapper/dists\n" +
                "distributionUrl=http\\://services.gradle.org/distributions/gradle-1.9-bin.zip", wrapper, Charsets.UTF_8);
    GradleUtil.updateGradleDistributionUrl("1.9", wrapper);

    Properties properties = PropertiesUtil.getProperties(wrapper);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("http://services.gradle.org/distributions/gradle-1.9-bin.zip", distributionUrl);
  }

  public void testLeaveGradleWrapperAloneAll() throws IOException {
    // Ensure that if we already have the right version, we don't replace a -all.zip with a -bin.zip
    myTempDir = Files.createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    Files.write("#Wed Apr 10 15:27:10 PDT 2013\n" +
                "distributionBase=GRADLE_USER_HOME\n" +
                "distributionPath=wrapper/dists\n" +
                "zipStoreBase=GRADLE_USER_HOME\n" +
                "zipStorePath=wrapper/dists\n" +
                "distributionUrl=http\\://services.gradle.org/distributions/gradle-1.9-all.zip", wrapper, Charsets.UTF_8);
    GradleUtil.updateGradleDistributionUrl("1.9", wrapper);

    Properties properties = PropertiesUtil.getProperties(wrapper);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("http://services.gradle.org/distributions/gradle-1.9-all.zip", distributionUrl);
  }

  public void testReplaceGradleWrapper() throws IOException {
    // Test that when we replace to a new version we use -all.zip
    myTempDir = Files.createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    Files.write("#Wed Apr 10 15:27:10 PDT 2013\n" +
                "distributionBase=GRADLE_USER_HOME\n" +
                "distributionPath=wrapper/dists\n" +
                "zipStoreBase=GRADLE_USER_HOME\n" +
                "zipStorePath=wrapper/dists\n" +
                "distributionUrl=http\\://services.gradle.org/distributions/gradle-1.9-bin.zip", wrapper, Charsets.UTF_8);
    GradleUtil.updateGradleDistributionUrl("1.6", wrapper);

    Properties properties = PropertiesUtil.getProperties(wrapper);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("http://services.gradle.org/distributions/gradle-1.6-all.zip", distributionUrl);
  }

  public void testUpdateGradleDistributionUrl() {
    myTempDir = Files.createTempDir();
    File wrapperPath = GradleUtil.getGradleWrapperPropertiesFilePath(myTempDir);

    List<String> expected = Lists.newArrayList(FileUtil.splitPath(myTempDir.getPath()));
    expected.addAll(FileUtil.splitPath(FD_GRADLE_WRAPPER));
    expected.add(FN_GRADLE_WRAPPER_PROPERTIES);

    assertEquals(expected, FileUtil.splitPath(wrapperPath.getPath()));
  }

  public void testGetPathSegments() {
    List<String> pathSegments = GradleUtil.getPathSegments("foo:bar:baz");
    assertEquals(Lists.newArrayList("foo", "bar", "baz"), pathSegments);
  }

  public void testGetPathSegmentsWithEmptyString() {
    List<String> pathSegments = GradleUtil.getPathSegments("");
    assertEquals(0, pathSegments.size());
  }

  public void testGetGradleBuildFilePath() {
    myTempDir = Files.createTempDir();
    File buildFilePath = GradleUtil.getGradleBuildFilePath(myTempDir);
    assertEquals(new File(myTempDir, FN_BUILD_GRADLE), buildFilePath);
  }

  public void testGetGradleVersionFromJarUsingGradleLibraryJar() {
    File jarFile = new File("gradle-core-2.0.jar");
    FullRevision gradleVersion = GradleUtil.getGradleVersionFromJar(jarFile);
    assertNotNull(gradleVersion);
    assertEquals(FullRevision.parseRevision("2.0"), gradleVersion);
  }

  public void testGetGradleVersionFromJarUsingGradleLibraryJarWithoutVersion() {
    File jarFile = new File("gradle-core-two.jar");
    FullRevision gradleVersion = GradleUtil.getGradleVersionFromJar(jarFile);
    assertNull(gradleVersion);
  }

  public void testGetGradleVersionFromJarUsingNonGradleLibraryJar() {
    File jarFile = new File("ant-1.9.3.jar");
    FullRevision gradleVersion = GradleUtil.getGradleVersionFromJar(jarFile);
    assertNull(gradleVersion);
  }

  public void testGetAndroidGradleModelVersion() throws IOException {
    String contents ="buildscript {\n" +
                     "    repositories {\n" +
                     "        jcenter()\n" +
                     "    }\n" +
                     "    dependencies {\n" +
                     "        classpath 'com.android.tools.build:gradle:0.13.0'\n" +
                     "    }\n" +
                     "}";
    FullRevision revision = GradleUtil.getResolvedAndroidGradleModelVersion(contents);
    assertNotNull(revision);
    assertEquals("0.13.0", revision.toString());
  }

  public void testGetAndroidGradleModelVersionWithPlusInMicro() throws IOException {
    String contents ="buildscript {\n" +
                     "    repositories {\n" +
                     "        jcenter()\n" +
                     "    }\n" +
                     "    dependencies {\n" +
                     "        classpath 'com.android.tools.build:gradle:0.13.+'\n" +
                     "    }\n" +
                     "}";
    FullRevision revision = GradleUtil.getResolvedAndroidGradleModelVersion(contents);
    assertNotNull(revision);
    assertEquals("0.13.0", revision.toString());
  }

  public void testGetAndroidGradleModelVersionWithPlusNotation() throws IOException {
    String contents ="buildscript {\n" +
                     "    repositories {\n" +
                     "        jcenter()\n" +
                     "    }\n" +
                     "    dependencies {\n" +
                     "        classpath 'com.android.tools.build:gradle:+'\n" +
                     "    }\n" +
                     "}";
    FullRevision revision = GradleUtil.getResolvedAndroidGradleModelVersion(contents);
    assertNotNull(revision);
  }
}
