/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.execution.junit;

import com.intellij.execution.junit.testDiscovery.TestBySource;
import com.intellij.execution.junit.testDiscovery.TestsByChanges;
import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.impl.JavaTestFrameworkRunnableState;
import com.intellij.java.execution.impl.TestClassCollector;
import com.intellij.java.execution.impl.testframework.SearchForTestsTask;
import com.intellij.java.execution.impl.util.JavaParametersUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.junit5.JUnit5IdeaTestRunner;
import com.intellij.rt.execution.junit.IDEAJUnitListener;
import com.intellij.rt.execution.junit.JUnitStarter;
import com.intellij.rt.execution.junit.RepeatCount;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import consulo.application.ReadAction;
import consulo.execution.CantRunException;
import consulo.execution.ExecutionBundle;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.executor.Executor;
import consulo.execution.process.ProcessTerminatedListener;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.test.SourceScope;
import consulo.execution.test.TestSearchScope;
import consulo.execution.util.ProgramParametersUtil;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.junit.JUnitListener;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.GlobalSearchScopesCore;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.cmd.ParametersList;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.io.ClassPathUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import consulo.virtualFileSystem.util.PathsList;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public abstract class TestObject extends JavaTestFrameworkRunnableState<JUnitConfiguration>
{
	protected static final Logger LOG = Logger.getInstance(TestObject.class);

	private static final String MESSAGE = ExecutionBundle.message("configuration.not.speficied.message");
	@NonNls
	private static final String JUNIT_TEST_FRAMEWORK_NAME = "JUnit";

	private final JUnitConfiguration myConfiguration;
	protected File myListenersFile;

	public static TestObject fromString(final String id, final JUnitConfiguration configuration, @Nonnull ExecutionEnvironment environment)
	{
		if(JUnitConfiguration.TEST_METHOD.equals(id))
		{
			return new TestMethod(configuration, environment);
		}
		if(JUnitConfiguration.TEST_CLASS.equals(id))
		{
			return new TestClass(configuration, environment);
		}
		if(JUnitConfiguration.TEST_PACKAGE.equals(id))
		{
			return new TestPackage(configuration, environment);
		}
		if(JUnitConfiguration.TEST_DIRECTORY.equals(id))
		{
			return new TestDirectory(configuration, environment);
		}
		if(JUnitConfiguration.TEST_CATEGORY.equals(id))
		{
			return new TestCategory(configuration, environment);
		}
		if(JUnitConfiguration.TEST_PATTERN.equals(id))
		{
			return new TestsPattern(configuration, environment);
		}
		if(JUnitConfiguration.TEST_UNIQUE_ID.equals(id))
		{
			return new TestUniqueId(configuration, environment);
		}
		if(JUnitConfiguration.BY_SOURCE_POSITION.equals(id))
		{
			return new TestBySource(configuration, environment);
		}
		if(JUnitConfiguration.BY_SOURCE_CHANGES.equals(id))
		{
			return new TestsByChanges(configuration, environment);
		}
		LOG.info(MESSAGE + id);
		return null;
	}

	public Module[] getModulesToCompile()
	{
		final SourceScope sourceScope = getSourceScope();
		return sourceScope != null ? sourceScope.getModulesToCompile() : Module.EMPTY_ARRAY;
	}

	protected TestObject(JUnitConfiguration configuration, ExecutionEnvironment environment)
	{
		super(environment);
		myConfiguration = configuration;
	}

	public abstract String suggestActionName();

	public abstract RefactoringElementListener getListener(PsiElement element, JUnitConfiguration configuration);

	public abstract boolean isConfiguredByElement(JUnitConfiguration configuration, PsiClass testClass, PsiMethod testMethod, PsiPackage testPackage, PsiDirectory testDir);

	public void checkConfiguration() throws RuntimeConfigurationException
	{
		JavaParametersUtil.checkAlternativeJRE(getConfiguration());
		ProgramParametersUtil.checkWorkingDirectoryExist(getConfiguration(), getConfiguration().getProject(), getConfiguration().getConfigurationModule().getModule());
	}

	@Nullable
	public SourceScope getSourceScope()
	{
		return SourceScope.modules(getConfiguration().getModules());
	}

	@Override
	protected void configureRTClasspath(OwnJavaParameters javaParameters) throws CantRunException
	{
		javaParameters.getClassPath().add(ClassPathUtil.getJarPathForClass(JUnitStarter.class));

		//include junit5 listeners for the case custom junit 5 engines would be detected on runtime
		javaParameters.getClassPath().add(getJUnit5RtFile());

		String preferredRunner = getRunner();
		if(JUnitStarter.JUNIT5_PARAMETER.equals(preferredRunner))
		{
			final Project project = getConfiguration().getProject();
			GlobalSearchScope globalSearchScope = getScopeForJUnit(getConfiguration().getConfigurationModule().getModule(), project);
			appendJUnit5LauncherClasses(javaParameters, project, globalSearchScope);
		}
	}

	public static File getJUnit5RtFile()
	{
		return new File(ClassPathUtil.getJarPathForClass(JUnit5IdeaTestRunner.class));
	}

	@Override
	protected OwnJavaParameters createJavaParameters() throws ExecutionException
	{
		OwnJavaParameters javaParameters = super.createJavaParameters();
		javaParameters.setMainClass(JUnitConfiguration.JUNIT_START_CLASS);
		javaParameters.getProgramParametersList().add(JUnitStarter.IDE_VERSION + JUnitStarter.VERSION);

		final StringBuilder buf = new StringBuilder();
		collectListeners(javaParameters, buf, JUnitListener.class, "\n");
		if(buf.length() > 0)
		{
			try
			{
				myListenersFile = FileUtil.createTempFile("junit_listeners_", "", true);
				javaParameters.getProgramParametersList().add("@@" + myListenersFile.getPath());
				FileUtil.writeToFile(myListenersFile, buf.toString().getBytes(StandardCharsets.UTF_8));
			}
			catch(IOException e)
			{
				LOG.error(e);
			}
		}

		String preferredRunner = getRunner();
		if(preferredRunner != null)
		{
			javaParameters.getProgramParametersList().add(preferredRunner);
		}

		return javaParameters;
	}

	public static void appendJUnit5LauncherClasses(OwnJavaParameters javaParameters, Project project, GlobalSearchScope globalSearchScope) throws CantRunException
	{
		final PathsList classPath = javaParameters.getClassPath();
		JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
		PsiClass classFromCommon = DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() -> psiFacade.findClass("org.junit.platform.commons.JUnitException", globalSearchScope));
		String launcherVersion = ObjectUtil.notNull(getVersion(classFromCommon), "1.0.0");
		if(!hasPackageWithDirectories(psiFacade, "org.junit.platform.launcher", globalSearchScope))
		{
			// FIXME [VISTALL] depedency to Jar Repositories in IDEA Java Impl, and we dont merge that
			// downloadDependenciesWhenRequired(project, classPath, new RepositoryLibraryProperties("org.junit.platform", "junit-platform-launcher", launcherVersion));
		}

		//add standard engines only if no engine api is present
		if(!hasPackageWithDirectories(psiFacade, "org.junit.platform.engine", globalSearchScope))
		{
			if(!hasPackageWithDirectories(psiFacade, "org.junit.jupiter.engine", globalSearchScope) && hasPackageWithDirectories(psiFacade, JUnitUtil.TEST5_PACKAGE_FQN, globalSearchScope))
			{
				PsiClass testAnnotation = DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() -> psiFacade.findClass(JUnitUtil.TEST5_ANNOTATION, globalSearchScope));
				String version = ObjectUtil.notNull(getVersion(testAnnotation), "5.0.0");
				// FIXME [VISTALL] depedency to Jar Repositories in IDEA Java Impl, and we dont merge that
				// downloadDependenciesWhenRequired(project, classPath, new RepositoryLibraryProperties("org.junit.jupiter", "junit-jupiter-engine", version));
			}

			if(!hasPackageWithDirectories(psiFacade, "org.junit.vintage", globalSearchScope) && hasPackageWithDirectories(psiFacade, "junit.framework", globalSearchScope))
			{
				String version = "4.12." + StringUtil.getShortName(launcherVersion);
				// FIXME [VISTALL] depedency to Jar Repositories in IDEA Java Impl, and we dont merge that
				// downloadDependenciesWhenRequired(project, classPath, new RepositoryLibraryProperties("org.junit.vintage", "junit-vintage-engine", version));
			}
		}
	}

	private static String getVersion(PsiClass classFromCommon)
	{
		VirtualFile virtualFile = PsiUtilCore.getVirtualFile(classFromCommon);
		if(virtualFile == null)
		{
			return null;
		}
		ProjectFileIndex index = ProjectFileIndex.getInstance(classFromCommon.getProject());
		VirtualFile root = index.getClassRootForFile(virtualFile);
		if(root != null && root.getFileSystem() instanceof ArchiveFileSystem)
		{
			VirtualFile manifestFile = root.findFileByRelativePath(JarFile.MANIFEST_NAME);
			if(manifestFile == null)
			{
				return null;
			}

			try (final InputStream inputStream = manifestFile.getInputStream())
			{
				return new Manifest(inputStream).getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
			}
			catch(IOException e)
			{
				return null;
			}
		}
		return null;
	}

	// FIXME [VISTALL] depedency to Jar Repositories in IDEA Java Impl, and we dont merge that
	/*private static void downloadDependenciesWhenRequired(Project project, PathsList classPath, RepositoryLibraryProperties properties) throws CantRunException
	{
		Collection<OrderRoot> roots = JarRepositoryManager.loadDependenciesModal(project, properties, false, false, null, null);
		if(roots.isEmpty())
		{
			throw new CantRunException("Failed to resolve " + properties.getMavenId());
		}
		for(OrderRoot root : roots)
		{
			if(root.getType() == OrderRootType.CLASSES)
			{
				classPath.add(root.getFile());
			}
		}
	}*/

	private static boolean hasPackageWithDirectories(JavaPsiFacade psiFacade, String packageQName, GlobalSearchScope globalSearchScope)
	{
		PsiPackage aPackage = psiFacade.findPackage(packageQName);
		return aPackage != null && aPackage.getDirectories(globalSearchScope).length > 0;
	}

	private static GlobalSearchScope getScopeForJUnit(@Nullable Module module, Project project)
	{
		return module != null ? GlobalSearchScope.moduleRuntimeScope(module, true) : GlobalSearchScope.allScope(project);
	}

	public static GlobalSearchScope getScopeForJUnit(JUnitConfiguration configuration)
	{
		return getScopeForJUnit(configuration.getConfigurationModule().getModule(), configuration.getProject());
	}

	@Nonnull
	protected ProcessHandler createHandler(Executor executor) throws ExecutionException
	{
		appendForkInfo(executor);
		final String repeatMode = getConfiguration().getRepeatMode();
		if(!RepeatCount.ONCE.equals(repeatMode))
		{
			final int repeatCount = getConfiguration().getRepeatCount();
			final String countString = RepeatCount.N.equals(repeatMode) && repeatCount > 0 ? RepeatCount.getCountString(repeatCount) : repeatMode;
			getJavaParameters().getProgramParametersList().add(countString);
		}

		final ProcessHandler processHandler = ProcessHandlerBuilder.create(createCommandLine()).killable().build();
		ProcessTerminatedListener.attach(processHandler);
		final SearchForTestsTask searchForTestsTask = createSearchingForTestsTask();
		if(searchForTestsTask != null)
		{
			searchForTestsTask.attachTaskToProcess(processHandler);
		}
		return processHandler;
	}

	@Override
	protected boolean isIdBasedTestTree()
	{
		return JUnitStarter.JUNIT5_PARAMETER.equals(getRunner());
	}

	@Nonnull
	@Override
	protected String getForkMode()
	{
		return getConfiguration().getForkMode();
	}

	protected <T> void addClassesListToJavaParameters(Collection<? extends T> elements,
			Function<T, String> nameFunction,
			String packageName,
			boolean createTempFile,
			OwnJavaParameters javaParameters) throws CantRunException
	{
		try
		{
			if(createTempFile)
			{
				createTempFiles(javaParameters);
			}

			final Map<Module, List<String>> perModule = forkPerModule() ? new TreeMap<>((o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true)) : null;

			final List<String> testNames = new ArrayList<>();

			if(elements.isEmpty() && perModule != null)
			{
				final SourceScope sourceScope = getSourceScope();
				Project project = getConfiguration().getProject();
				if(sourceScope != null && packageName != null && JUnitStarter.JUNIT5_PARAMETER.equals(getRunner()))
				{
					final PsiPackage aPackage = JavaPsiFacade.getInstance(getConfiguration().getProject()).findPackage(packageName);
					if(aPackage != null)
					{
						final TestSearchScope scope = getScope();
						if(scope != null)
						{
							final GlobalSearchScope configurationSearchScope = GlobalSearchScopesCore.projectTestScope(project).intersectWith(sourceScope.getGlobalSearchScope());
							final PsiDirectory[] directories = aPackage.getDirectories(configurationSearchScope);
							for(PsiDirectory directory : directories)
							{
								Module module = ModuleUtilCore.findModuleForFile(directory.getVirtualFile(), project);
								if(module != null)
								{
									perModule.put(module, Collections.emptyList());
								}
							}
						}
					}
				}
			}

			for(final T element : elements)
			{
				final String name = nameFunction.apply(element);
				if(name == null)
				{
					continue;
				}

				final PsiElement psiElement = retrievePsiElement(element);
				if(perModule != null && psiElement != null)
				{
					final Module module = ModuleUtilCore.findModuleForPsiElement(psiElement);
					if(module != null)
					{
						List<String> list = perModule.get(module);
						if(list == null)
						{
							list = new ArrayList<>();
							perModule.put(module, list);
						}
						list.add(name);
					}
				}
				else
				{
					testNames.add(name);
				}
			}
			final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
			if(perModule != null)
			{
				for(List<String> perModuleClasses : perModule.values())
				{
					Collections.sort(perModuleClasses);
					testNames.addAll(perModuleClasses);
				}
			}
			else if(JUnitConfiguration.TEST_PACKAGE.equals(data.TEST_OBJECT))
			{
				Collections.sort(testNames); //sort tests in FQN order
			}

			final String category = JUnitConfiguration.TEST_CATEGORY.equals(data.TEST_OBJECT) ? data.getCategory() : "";
			final String filters = JUnitConfiguration.TEST_PATTERN.equals(data.TEST_OBJECT) ? data.getPatternPresentation() : "";
			JUnitStarter.printClassesList(testNames, packageName, category, filters, myTempFile);

			writeClassesPerModule(packageName, javaParameters, perModule);
		}
		catch(IOException e)
		{
			LOG.error(e);
		}
	}

	protected PsiElement retrievePsiElement(Object element)
	{
		return element instanceof PsiElement ? (PsiElement) element : null;
	}

	@Override
	protected void deleteTempFiles()
	{
		super.deleteTempFiles();
		if(myListenersFile != null)
		{
			FileUtil.delete(myListenersFile);
		}
	}

	@Nonnull
	protected String getFrameworkName()
	{
		return JUNIT_TEST_FRAMEWORK_NAME;
	}

	@Nonnull
	protected String getFrameworkId()
	{
		return "junit";
	}

	protected void passTempFile(ParametersList parametersList, String tempFilePath)
	{
		parametersList.add("@" + tempFilePath);
	}

	@Nonnull
	public JUnitConfiguration getConfiguration()
	{
		return myConfiguration;
	}

	@Override
	protected TestSearchScope getScope()
	{
		return getConfiguration().getPersistentData().getScope();
	}

	protected void passForkMode(String forkMode, File tempFile, OwnJavaParameters parameters) throws ExecutionException
	{
		parameters.getProgramParametersList().add("@@@" + forkMode + ',' + tempFile.getAbsolutePath());
		if(getForkSocket() != null)
		{
			// see ForkedDebuggerHelper
			parameters.getProgramParametersList().add("-debugSocket" + getForkSocket().getLocalPort());
		}
	}

	private String myRunner;

	protected String getRunner()
	{
		if(myRunner == null)
		{
			myRunner = getRunnerInner();
		}
		return myRunner;
	}

	private String getRunnerInner()
	{
		final GlobalSearchScope globalSearchScope = getScopeForJUnit(myConfiguration);
		JUnitConfiguration.Data data = myConfiguration.getPersistentData();
		Project project = myConfiguration.getProject();
		boolean isMethodConfiguration = JUnitConfiguration.TEST_METHOD.equals(data.TEST_OBJECT);
		boolean isClassConfiguration = JUnitConfiguration.TEST_CLASS.equals(data.TEST_OBJECT);
		final PsiClass psiClass = isMethodConfiguration || isClassConfiguration ? JavaExecutionUtil.findMainClass(project, data.getMainClassName(), globalSearchScope) : null;
		if(psiClass != null)
		{
			if(JUnitUtil.isJUnit5TestClass(psiClass, false))
			{
				return JUnitStarter.JUNIT5_PARAMETER;
			}

			if(isClassConfiguration || JUnitUtil.isJUnit4TestClass(psiClass))
			{
				return JUnitStarter.JUNIT4_PARAMETER;
			}

			final String methodName = data.getMethodName();
			final PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);
			for(PsiMethod method : methods)
			{
				if(JUnitUtil.isTestAnnotated(method))
				{
					return JUnitStarter.JUNIT4_PARAMETER;
				}
			}
			return JUnitStarter.JUNIT3_PARAMETER;
		}
		return JUnitUtil.isJUnit5(globalSearchScope, project) || isCustomJUnit5(globalSearchScope) ? JUnitStarter.JUNIT5_PARAMETER : null;
	}

	private boolean isCustomJUnit5(GlobalSearchScope globalSearchScope)
	{
		Project project = myConfiguration.getProject();
		JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
		if(DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() ->
		{
			@Nullable PsiClass testEngine = ReadAction.compute(() -> psiFacade.findClass(JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE, globalSearchScope));
			return testEngine;
		}) == null)
		{
			return false;
		}

		ClassLoader loader = TestClassCollector.createUsersClassLoader(myConfiguration);
		try
		{
			ServiceLoader<?> serviceLoader = ServiceLoader.load(Class.forName(JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE, false, loader), loader);
			for(Object engine : serviceLoader)
			{
				String engineClassName = engine.getClass().getName();
				if(!"org.junit.jupiter.engine.JupiterTestEngine".equals(engineClassName) && !"org.junit.vintage.engine.VintageTestEngine".equals(engineClassName))
				{
					return true;
				}
			}
			return false;
		}
		catch(Throwable e)
		{
			return false;
		}
	}
}
