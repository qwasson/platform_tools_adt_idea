/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.sdk;

import com.android.SdkConstants;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.sdk.Jdks;
import com.android.tools.idea.sdk.SelectSdkDialog;
import com.android.tools.idea.sdk.VersionCheck;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.android.tools.idea.startup.ExternalAnnotationsSupport;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.OSProcessManager;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.android.actions.AndroidEnableAdbServiceAction;
import org.jetbrains.android.actions.AndroidRunDdmsAction;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.logcat.AndroidToolWindowFactory;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.FD_EXTRAS;
import static com.android.SdkConstants.FD_M2_REPOSITORY;

/**
 * @author Eugene.Kudelevsky
 */
public final class AndroidSdkUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidSdkUtils");

  public static final String DEFAULT_PLATFORM_NAME_PROPERTY = "AndroidPlatformName";
  public static final String SDK_NAME_PREFIX = "Android ";
  public static final String DEFAULT_JDK_NAME = "JDK";

  private static AndroidSdkData ourSdkData;

  private AndroidSdkUtils() {
  }

  @Nullable
  private static VirtualFile getPlatformDir(@NotNull IAndroidTarget target) {
    String platformPath = target.isPlatform() ? target.getLocation() : target.getParent().getLocation();
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName((platformPath)));
  }

  @NotNull
  public static List<VirtualFile> getPlatformAndAddOnJars(@NotNull IAndroidTarget target) {
    VirtualFile platformDir = getPlatformDir(target);
    if (platformDir == null) {
      return Collections.emptyList();
    }

    VirtualFile androidJar = platformDir.findChild(SdkConstants.FN_FRAMEWORK_LIBRARY);
    if (androidJar == null) {
      return Collections.emptyList();
    }

    List<VirtualFile> result = Lists.newArrayList();

    VirtualFile androidJarRoot = JarFileSystem.getInstance().findFileByPath(androidJar.getPath() + JarFileSystem.JAR_SEPARATOR);
    if (androidJarRoot != null) {
      result.add(androidJarRoot);
    }

    IAndroidTarget.IOptionalLibrary[] libs = target.getOptionalLibraries();
    if (libs != null) {
      for (IAndroidTarget.IOptionalLibrary lib : libs) {
        VirtualFile libRoot = JarFileSystem.getInstance().findFileByPath(lib.getJarPath() + JarFileSystem.JAR_SEPARATOR);
        if (libRoot != null) {
          result.add(libRoot);
        }
      }
    }
    return result;
  }

  @NotNull
  public static List<OrderRoot> getLibraryRootsForTarget(@NotNull IAndroidTarget target,
                                                         @Nullable String sdkPath,
                                                         boolean addPlatformAndAddOnJars) {
    List<OrderRoot> result = Lists.newArrayList();

    if (addPlatformAndAddOnJars) {
      for (VirtualFile file : getPlatformAndAddOnJars(target)) {
        result.add(new OrderRoot(file, OrderRootType.CLASSES));
      }
    }
    VirtualFile platformDir = getPlatformDir(target);
    if (platformDir == null) return result;

    VirtualFile targetDir = platformDir;
    if (!target.isPlatform()) {
      targetDir = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName((target.getLocation())));
    }
    boolean docsOrSourcesFound = false;

    if (targetDir != null) {
      docsOrSourcesFound = addJavaDocAndSources(result, targetDir);
    }
    VirtualFile sdkDir = sdkPath != null ? LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName((sdkPath))) : null;
    VirtualFile sourcesDir = null;
    if (sdkDir != null) {
      docsOrSourcesFound = addJavaDocAndSources(result, sdkDir) || docsOrSourcesFound;
      sourcesDir = sdkDir.findChild(SdkConstants.FD_PKG_SOURCES);
    }

    // todo: replace it by target.getPath(SOURCES) when it'll be up to date
    if (sourcesDir != null && sourcesDir.isDirectory()) {
      VirtualFile platformSourcesDir = sourcesDir.findChild(platformDir.getName());
      if (platformSourcesDir != null && platformSourcesDir.isDirectory()) {
        result.add(new OrderRoot(platformSourcesDir, OrderRootType.SOURCES));
        docsOrSourcesFound = true;
      }
    }

    if (!docsOrSourcesFound) {
      VirtualFile f = VirtualFileManager.getInstance().findFileByUrl(AndroidSdkType.DEFAULT_EXTERNAL_DOCUMENTATION_URL);
      if (f != null) {
        result.add(new OrderRoot(f, JavadocOrderRootType.getInstance()));
      }
    }

    String resFolderPath = target.getPath(IAndroidTarget.RESOURCES);
    if (resFolderPath != null) {
      VirtualFile resFolder = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName((resFolderPath)));
      if (resFolder != null) {
        result.add(new OrderRoot(resFolder, OrderRootType.CLASSES));
      }
    }

    if (sdkPath != null) {
      // todo: check if we should do it for new android platforms (api_level >= 15)
      JarFileSystem jarFileSystem = JarFileSystem.getInstance();
      String annotationsJarPath =
        FileUtil.toSystemIndependentName(sdkPath) + AndroidCommonUtils.ANNOTATIONS_JAR_RELATIVE_PATH + JarFileSystem.JAR_SEPARATOR;
      VirtualFile annotationsJar = jarFileSystem.findFileByPath(annotationsJarPath);
      if (annotationsJar != null) {
        result.add(new OrderRoot(annotationsJar, OrderRootType.CLASSES));
      }
    }

    return result;
  }

  @Nullable
  private static VirtualFile findJavadocDir(@NotNull VirtualFile dir) {
    VirtualFile docsDir = dir.findChild(SdkConstants.FD_DOCS);
    if (docsDir != null) {
      return docsDir.findChild(SdkConstants.FD_DOCS_REFERENCE);
    }
    return null;
  }

  private static boolean addJavaDocAndSources(@NotNull List<OrderRoot> list, @NotNull VirtualFile dir) {
    boolean found = false;

    VirtualFile javadocDir = findJavadocDir(dir);
    if (javadocDir != null) {
      list.add(new OrderRoot(javadocDir, JavadocOrderRootType.getInstance()));
      found = true;
    }

    VirtualFile sourcesDir = dir.findChild(SdkConstants.FD_SOURCES);
    if (sourcesDir != null) {
      list.add(new OrderRoot(sourcesDir, OrderRootType.SOURCES));
      found = true;
    }
    return found;
  }

  public static String getPresentableTargetName(@NotNull IAndroidTarget target) {
    IAndroidTarget parentTarget = target.getParent();
    if (parentTarget != null) {
      return target.getName() + " (" + parentTarget.getVersionName() + ')';
    }
    return target.getName();
  }

  /**
   * Creates a new IDEA Android SDK. User is prompt for the paths of the Android SDK and JDK if necessary.
   *
   * @param sdkPath the path of Android SDK.
   * @return the created IDEA Android SDK, or {@null} if it was not possible to create it.
   */
  @Nullable
  public static Sdk createNewAndroidPlatform(@Nullable String sdkPath, boolean promptUser) {
    Sdk jdk = Jdks.chooseOrCreateJavaSdk();
    if (sdkPath != null && jdk != null) {
      sdkPath = FileUtil.toSystemIndependentName(sdkPath);
      IAndroidTarget target = findBestTarget(sdkPath);
      if (target != null) {
        Sdk sdk = createNewAndroidPlatform(target, sdkPath, chooseNameForNewLibrary(target), jdk, true);
        if (sdk != null) {
          return sdk;
        }
      }
    }
    String jdkPath = jdk == null ? null : jdk.getHomePath();

    return promptUser ? promptUserForSdkCreation(null, sdkPath, jdkPath) : null;
  }

  @Nullable
  private static IAndroidTarget findBestTarget(@NotNull String sdkPath) {
    AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdkPath);
    if (sdkData != null) {
      IAndroidTarget[] targets = sdkData.getTargets();
      return findBestTarget(targets);
    }
    return null;
  }

  @Nullable
  private static IAndroidTarget findBestTarget(@NotNull IAndroidTarget[] targets) {
    IAndroidTarget bestTarget = null;
    int maxApiLevel = -1;
    for (IAndroidTarget target : targets) {
      AndroidVersion version = target.getVersion();
      if (target.isPlatform() && !version.isPreview() && version.getApiLevel() > maxApiLevel) {
        bestTarget = target;
        maxApiLevel = version.getApiLevel();
      }
    }
    return bestTarget;
  }

  @Nullable
  public static Sdk createNewAndroidPlatform(@NotNull IAndroidTarget target,
                                             @NotNull String sdkPath,
                                             boolean addRoots) {
    return createNewAndroidPlatform(target, sdkPath, chooseNameForNewLibrary(target), addRoots);
  }

  @Nullable
  public static Sdk createNewAndroidPlatform(@NotNull IAndroidTarget target,
                                             @NotNull String sdkPath,
                                             @NotNull String sdkName,
                                             boolean addRoots) {
    Sdk jdk = Jdks.chooseOrCreateJavaSdk();
    if (jdk == null) {
      return null;
    }
    return createNewAndroidPlatform(target, sdkPath, sdkName, jdk, addRoots);
  }

  @Nullable
  public static Sdk createNewAndroidPlatform(@NotNull IAndroidTarget target,
                                             @NotNull String sdkPath,
                                             @NotNull String sdkName,
                                             @Nullable Sdk jdk,
                                             boolean addRoots) {
    ProjectJdkTable table = ProjectJdkTable.getInstance();
    String tmpName = SdkConfigurationUtil.createUniqueSdkName(AndroidSdkType.SDK_NAME, Arrays.asList(table.getAllJdks()));

    final Sdk sdk = table.createSdk(tmpName, AndroidSdkType.getInstance());

    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(sdkPath);
    sdkModificator.commitChanges();

    setUpSdk(sdk, sdkName, table.getAllJdks(), target, jdk, addRoots);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().addJdk(sdk);
      }
    });
    return sdk;
  }

  @NotNull
  public static String chooseNameForNewLibrary(@NotNull IAndroidTarget target) {
    if (target.isPlatform()) {
      return SDK_NAME_PREFIX + target.getVersion().toString() + " Platform";
    }
    IAndroidTarget parentTarget = target.getParent();
    if (parentTarget != null) {
      return SDK_NAME_PREFIX + parentTarget.getVersionName() + ' ' + target.getName();
    }
    return SDK_NAME_PREFIX + target.getName();
  }

  public static String getTargetPresentableName(@NotNull IAndroidTarget target) {
    return target.isPlatform() ?
           target.getName() :
           target.getName() + " (" + target.getVersionName() + ')';
  }

  public static void setUpSdk(@NotNull Sdk androidSdk,
                              @NotNull String sdkName,
                              @NotNull Sdk[] allSdks,
                              @NotNull IAndroidTarget target,
                              @Nullable Sdk jdk,
                              boolean addRoots) {
    AndroidSdkAdditionalData data = new AndroidSdkAdditionalData(androidSdk, jdk);
    data.setBuildTarget(target);

    String name = SdkConfigurationUtil.createUniqueSdkName(sdkName, Arrays.asList(allSdks));

    SdkModificator sdkModificator = androidSdk.getSdkModificator();
    findAndSetPlatformSources(target, sdkModificator);

    sdkModificator.setName(name);
    if (jdk != null) {
      //noinspection ConstantConditions
      sdkModificator.setVersionString(jdk.getVersionString());
    }
    sdkModificator.setSdkAdditionalData(data);

    if (addRoots) {
      for (OrderRoot orderRoot : getLibraryRootsForTarget(target, androidSdk.getHomePath(), true)) {
        sdkModificator.addRoot(orderRoot.getFile(), orderRoot.getType());
      }
      ExternalAnnotationsSupport.attachJdkAnnotations(sdkModificator);
    }

    sdkModificator.commitChanges();
  }

  public static void findAndSetPlatformSources(@NotNull IAndroidTarget target, @NotNull SdkModificator sdkModificator) {
    File sources = findPlatformSources(target);
    if (sources != null) {
      VirtualFile virtualFile = VfsUtil.findFileByIoFile(sources, true);
      if (virtualFile != null) {
        sdkModificator.addRoot(virtualFile, OrderRootType.SOURCES);
      }
    }
  }

  public static boolean targetHasId(@NotNull IAndroidTarget target, @NotNull String id) {
    return id.equals(target.getVersion().getApiString()) || id.equals(target.getVersionName());
  }

  @NotNull
  public static Collection<String> getAndroidSdkPathsFromExistingPlatforms() {
    List<Sdk> androidSdks = getAllAndroidSdks();
    List<String> result = Lists.newArrayList();
    for (Sdk androidSdk : androidSdks) {
      AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)androidSdk.getSdkAdditionalData();
      if (data != null) {
        AndroidPlatform androidPlatform = data.getAndroidPlatform();
        if (androidPlatform != null) {
          // Put default platforms in the list before non-default ones so they'll be looked at first.
          String sdkPath = FileUtil.toSystemIndependentName(androidPlatform.getSdkData().getLocation().getPath());
          if (result.contains(sdkPath)) continue;
          if (androidSdk.getName().startsWith(SDK_NAME_PREFIX)) {
            result.add(0, sdkPath);
          } else {
            result.add(sdkPath);
          }
        }
      }
    }
    return result;
  }

  @NotNull
  public static List<Sdk> getAllAndroidSdks() {
    List<Sdk> allSdks = ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance());
    return ObjectUtils.notNull(allSdks, Collections.<Sdk>emptyList());
  }

  private static boolean tryToSetAndroidPlatform(Module module, Sdk sdk) {
    AndroidPlatform platform = AndroidPlatform.parse(sdk);
    if (platform != null) {
      ModuleRootModificationUtil.setModuleSdk(module, sdk);
      return true;
    }
    return false;
  }

  private static void setupPlatform(@NotNull Module module) {
    String targetHashString = getTargetHashStringFromPropertyFile(module);
    if (targetHashString != null && findAndSetSdkWithHashString(module, targetHashString)) {
      return;
    }

    PropertiesComponent component = PropertiesComponent.getInstance();
    if (component.isValueSet(DEFAULT_PLATFORM_NAME_PROPERTY)) {
      String defaultPlatformName = component.getValue(DEFAULT_PLATFORM_NAME_PROPERTY);
      Sdk defaultLib = ProjectJdkTable.getInstance().findJdk(defaultPlatformName, AndroidSdkType.getInstance().getName());
      if (defaultLib != null && tryToSetAndroidPlatform(module, defaultLib)) {
        return;
      }
    }
    for (Sdk sdk : getAllAndroidSdks()) {
      AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (data != null) {
        AndroidPlatform platform = data.getAndroidPlatform();

        if (platform != null &&
            checkSdkRoots(sdk, platform.getTarget(), false) &&
            tryToSetAndroidPlatform(module, sdk)) {
          component.setValue(DEFAULT_PLATFORM_NAME_PROPERTY, sdk.getName());
          return;
        }
      }
    }
  }

  @Nullable
  public static Sdk findSuitableAndroidSdk(@NotNull String targetHashString) {
    List<Pair<Boolean, Sdk>> matchingSdks = Lists.newArrayList();

    for (Sdk sdk : getAllAndroidSdks()) {
      SdkAdditionalData originalData = sdk.getSdkAdditionalData();
      if (!(originalData instanceof AndroidSdkAdditionalData)) {
        continue;
      }
      AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)originalData;
      AndroidPlatform androidPlatform = data.getAndroidPlatform();
      if (androidPlatform == null) {
        continue;
      }
      String baseDir = androidPlatform.getSdkData().getLocation().getPath();
      String platformHashString = androidPlatform.getTarget().hashString();
      if (targetHashString.equals(platformHashString)) {
        boolean compatible = VersionCheck.isCompatibleVersion(baseDir);
        matchingSdks.add(Pair.create(compatible, sdk));
      }
    }

    for (Pair<Boolean, Sdk> sdk : matchingSdks) {
      // We try to find an SDK that matches the given platform string and has a compatible Tools version.
      if (sdk.getFirst()) {
        return sdk.getSecond();
      }
    }
    if (!matchingSdks.isEmpty()) {
      // We got here because we have SDKs but none of them have a compatible Tools version. Pick the first one.
      return matchingSdks.get(0).getSecond();
    }

    return null;
  }

  @Nullable
  private static String getTargetHashStringFromPropertyFile(@NotNull Module module) {
    Pair<String, VirtualFile> targetProp = AndroidRootUtil.getProjectPropertyValue(module, AndroidUtils.ANDROID_TARGET_PROPERTY);
    return targetProp != null ? targetProp.getFirst() : null;
  }

  private static boolean findAndSetSdkWithHashString(@NotNull Module module, @NotNull String targetHashString) {
    Pair<String, VirtualFile> sdkDirProperty = AndroidRootUtil.getPropertyValue(module, SdkConstants.FN_LOCAL_PROPERTIES, "sdk.dir");
    String sdkDir = sdkDirProperty != null ? sdkDirProperty.getFirst() : null;
    return findAndSetSdk(module, targetHashString, sdkDir);
  }

  /**
   * Finds a matching Android SDK and sets it in the given module.
   *
   * @param module           the module to set the found SDK to.
   * @param targetHashString compile target.
   * @param sdkPath          path, in the file system, of the Android SDK.
   * @return {@code true} if a matching Android SDK was found and set in the module; {@code false} otherwise.
   */
  public static boolean findAndSetSdk(@NotNull Module module, @NotNull String targetHashString, @Nullable String sdkPath) {
    if (sdkPath != null) {
      sdkPath = FileUtil.toSystemIndependentName(sdkPath);
    }

    Sdk sdk = findSuitableAndroidSdk(targetHashString);
    if (sdk != null) {
      ModuleRootModificationUtil.setModuleSdk(module, sdk);
      return true;
    }

    if (sdkPath != null && tryToCreateAndSetAndroidSdk(module, sdkPath, targetHashString)) {
      return true;
    }

    String androidHomeValue = System.getenv(SdkConstants.ANDROID_HOME_ENV);
    if (androidHomeValue != null &&
        tryToCreateAndSetAndroidSdk(module, FileUtil.toSystemIndependentName(androidHomeValue), targetHashString)) {
      return true;
    }

    for (String dir : getAndroidSdkPathsFromExistingPlatforms()) {
      if (tryToCreateAndSetAndroidSdk(module, dir, targetHashString)) {
        return true;
      }
    }
    return false;
  }

  @VisibleForTesting
  static boolean tryToCreateAndSetAndroidSdk(@NotNull Module module, @NotNull String sdkPath, @NotNull String targetHashString) {
    File path = new File(FileUtil.toSystemDependentName(sdkPath));
    Sdk sdk = tryToCreateAndroidSdk(path, targetHashString);
    if (sdk != null) {
      ModuleRootModificationUtil.setModuleSdk(module, sdk);
      return true;
    }
    return false;
  }

  @Nullable
  public static Sdk tryToCreateAndroidSdk(@NotNull File sdkPath, @NotNull String targetHashString) {
    AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdkPath);
    if (sdkData != null) {
      sdkData.getLocalSdk().clearLocalPkg(PkgType.PKG_ALL);
      IAndroidTarget target = sdkData.findTargetByHashString(targetHashString);
      if (target != null) {
        return createNewAndroidPlatform(target, sdkData.getLocation().getPath(), true);
      }
    }
    return null;
  }

  @Nullable
  private static Sdk promptUserForSdkCreation(@Nullable final IAndroidTarget target,
                                              @Nullable final String androidSdkPath,
                                              @Nullable final String jdkPath) {
    final Ref<Sdk> sdkRef = new Ref<Sdk>();
    Runnable task = new Runnable() {
      @Override
      public void run() {
        SelectSdkDialog dlg = new SelectSdkDialog(jdkPath, androidSdkPath);
        dlg.setModal(true);
        if (dlg.showAndGet()) {
          Sdk sdk = createNewAndroidPlatform(target, dlg.getAndroidHome(), dlg.getJdkHome());
          sdkRef.set(sdk);
          if (sdk != null) {
            RunAndroidSdkManagerAction.updateInWelcomePage(dlg.getContentPanel());
          }
        }
      }
    };
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      task.run();
      return sdkRef.get();
    }
    application.invokeAndWait(task, ModalityState.any());
    return sdkRef.get();
  }

  @Nullable
  private static Sdk createNewAndroidPlatform(@Nullable IAndroidTarget target, @NotNull String androidSdkPath, @NotNull String jdkPath) {
    if (!Strings.isNullOrEmpty(jdkPath)) {
      jdkPath = FileUtil.toSystemIndependentName(jdkPath);
      Sdk jdk = Jdks.createJdk(jdkPath);
      if (jdk != null) {
        androidSdkPath = FileUtil.toSystemIndependentName(androidSdkPath);
        if (target == null) {
          target = findBestTarget(androidSdkPath);
        }
        if (target != null) {
          return createNewAndroidPlatform(target, androidSdkPath, chooseNameForNewLibrary(target), jdk, true);
        }
      }
    }
    return null;
  }

  public static void setupAndroidPlatformIfNecessary(@NotNull Module module, boolean forceImportFromProperties) {
    Sdk currentSdk = ModuleRootManager.getInstance(module).getSdk();
    if (currentSdk == null || !isAndroidSdk(currentSdk)) {
      setupPlatform(module);
      return;
    }
    if (forceImportFromProperties) {
      SdkAdditionalData data = currentSdk.getSdkAdditionalData();

      if (data instanceof AndroidSdkAdditionalData) {
        AndroidPlatform platform = ((AndroidSdkAdditionalData)data).getAndroidPlatform();

        if (platform != null) {
          String targetHashString = getTargetHashStringFromPropertyFile(module);
          String currentTargetHashString = platform.getTarget().hashString();

          if (targetHashString != null && !targetHashString.equals(currentTargetHashString)) {
            findAndSetSdkWithHashString(module, targetHashString);
          }
        }
      }
    }
  }

  public static void openModuleDependenciesConfigurable(final Module module) {
    ProjectSettingsService.getInstance(module.getProject()).openModuleDependenciesSettings(module, null);
  }

  @Nullable
  public static Sdk findAppropriateAndroidPlatform(@NotNull IAndroidTarget target,
                                                   @NotNull AndroidSdkData sdkData,
                                                   boolean forMaven) {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      String homePath = sdk.getHomePath();

      if (homePath != null && isAndroidSdk(sdk)) {
        AndroidSdkData currentSdkData = AndroidSdkData.getSdkData(homePath);

        if (currentSdkData != null && currentSdkData.equals(sdkData)) {
          AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
          if (data != null) {
            IAndroidTarget currentTarget = data.getBuildTarget(currentSdkData);
            if (currentTarget != null &&
                target.hashString().equals(currentTarget.hashString()) &&
                checkSdkRoots(sdk, target, forMaven)) {
              return sdk;
            }
          }
        }
      }
    }
    return null;
  }

  private static boolean isAndroidSdk(@NotNull Sdk sdk) {
    return sdk.getSdkType().equals(AndroidSdkType.getInstance());
  }

  public static boolean checkSdkRoots(@NotNull Sdk sdk, @NotNull IAndroidTarget target, boolean forMaven) {
    String homePath = sdk.getHomePath();
    if (homePath == null) {
      return false;
    }
    AndroidSdkAdditionalData sdkAdditionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    Sdk javaSdk = sdkAdditionalData != null ? sdkAdditionalData.getJavaSdk() : null;
    if (javaSdk == null) {
      return false;
    }
    Set<VirtualFile> filesInSdk = Sets.newHashSet(sdk.getRootProvider().getFiles(OrderRootType.CLASSES));

    List<VirtualFile> platformAndAddOnJars = getPlatformAndAddOnJars(target);
    for (VirtualFile file : platformAndAddOnJars) {
      if (filesInSdk.contains(file) == forMaven) {
        return false;
      }
    }
    boolean containsJarFromJdk = false;

    for (VirtualFile file : javaSdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
      if (file.getFileType() instanceof ArchiveFileType && filesInSdk.contains(file)) {
        containsJarFromJdk = true;
      }
    }
    return containsJarFromJdk == forMaven;
  }

  @Nullable
  public static AndroidDebugBridge getDebugBridge(@NotNull Project project) {
    final List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    for (AndroidFacet facet : facets) {
      final AndroidDebugBridge debugBridge = facet.getDebugBridge();
      if (debugBridge != null) {
        return debugBridge;
      }
    }
    return null;
  }

  public static boolean activateDdmsIfNecessary(@NotNull Project project, @NotNull Computable<AndroidDebugBridge> bridgeProvider) {
    if (AndroidEnableAdbServiceAction.isAdbServiceEnabled()) {
      AndroidDebugBridge bridge = bridgeProvider.compute();
      if (bridge != null && isDdmsCorrupted(bridge)) {
        LOG.info("DDMLIB is corrupted and will be restarted");
        restartDdmlib(project);
      }
    }
    else {
      final OSProcessHandler ddmsProcessHandler = AndroidRunDdmsAction.getDdmsProcessHandler();
      if (ddmsProcessHandler != null) {
        int r = Messages.showYesNoDialog(project, "Monitor will be closed to enable ADB integration. Continue?", "ADB Integration",
                                         Messages.getQuestionIcon());
        if (r != Messages.YES) {
          return false;
        }

        Runnable destroyingRunnable = new Runnable() {
          @Override
          public void run() {
            if (!ddmsProcessHandler.isProcessTerminated()) {
              OSProcessManager.getInstance().killProcessTree(ddmsProcessHandler.getProcess());
              ddmsProcessHandler.waitFor();
            }
          }
        };
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(destroyingRunnable, "Closing Monitor", true, project)) {
          return false;
        }
        AndroidEnableAdbServiceAction.setAdbServiceEnabled(project, true);
        return true;
      }

      int result = Messages.showYesNoDialog(project, AndroidBundle.message("android.ddms.disabled.error"),
                                            AndroidBundle.message("android.ddms.disabled.dialog.title"),
                                            Messages.getQuestionIcon());
      if (result != Messages.YES) {
        return false;
      }
      AndroidEnableAdbServiceAction.setAdbServiceEnabled(project, true);
    }
    return true;
  }

  public static boolean canDdmsBeCorrupted(@NotNull AndroidDebugBridge bridge) {
    return isDdmsCorrupted(bridge) || allDevicesAreEmpty(bridge);
  }

  private static boolean allDevicesAreEmpty(@NotNull AndroidDebugBridge bridge) {
    for (IDevice device : bridge.getDevices()) {
      if (device.getClients().length > 0) {
        return false;
      }
    }
    return true;
  }

  public static boolean isDdmsCorrupted(@NotNull AndroidDebugBridge bridge) {
    // TODO: find other way to check if debug service is available

    IDevice[] devices = bridge.getDevices();
    if (devices.length > 0) {
      for (IDevice device : devices) {
        Client[] clients = device.getClients();

        if (clients.length > 0) {
          ClientData clientData = clients[0].getClientData();
          return clientData.getVmIdentifier() == null;
        }
      }
    }
    return false;
  }

  public static void restartDdmlib(@NotNull Project project) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(
      AndroidToolWindowFactory.TOOL_WINDOW_ID);
    boolean hidden = false;
    if (toolWindow != null && toolWindow.isVisible()) {
      hidden = true;
      toolWindow.hide(null);
    }
    AndroidSdkData.terminateDdmlib();
    if (hidden) {
      toolWindow.show(null);
    }
  }

  public static boolean isAndroidSdkAvailable() {
    return tryToChooseAndroidSdk() != null;
  }

  @Nullable
  public static AndroidSdkData tryToChooseAndroidSdk() {
    if (ourSdkData == null) {
      if (AndroidStudioSpecificInitializer.isAndroidStudio()) {
        File path = DefaultSdks.getDefaultAndroidHome();
        if (path != null) {
          ourSdkData = AndroidSdkData.getSdkData(path.getPath());
          if (ourSdkData != null) {
            return ourSdkData;
          }
        }
      }

      for (String s : getAndroidSdkPathsFromExistingPlatforms()) {
        ourSdkData = AndroidSdkData.getSdkData(s);
        if (ourSdkData != null) {
          break;
        }
      }
    }
    return ourSdkData;
  }

  public static void setSdkData(@Nullable AndroidSdkData data) {
    ourSdkData = data;
  }

  /** Finds the root source code folder for the given android target, if any */
  @Nullable
  public static File findPlatformSources(@NotNull IAndroidTarget target) {
    String path = target.getPath(IAndroidTarget.SOURCES);
    if (path != null) {
      File platformSource = new File(path);
      if (platformSource.isDirectory()) {
        return platformSource;
      }
    }

    return null;
  }

  @NotNull
  public static File getAndroidSupportRepositoryLocation(@NotNull File androidHome) {
    return getRepositoryLocation(androidHome, "android");
  }

  @NotNull
  public static File getGoogleRepositoryLocation(@NotNull File androidHome) {
    return getRepositoryLocation(androidHome, "google");
  }

  @NotNull
  private static File getRepositoryLocation(@NotNull File androidHome, @NotNull String extrasName) {
    return new File(androidHome, FileUtil.join(FD_EXTRAS, extrasName, FD_M2_REPOSITORY));
  }

}
