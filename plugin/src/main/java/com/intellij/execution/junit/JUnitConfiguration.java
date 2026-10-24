/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.execution.junit;

import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.configurations.JavaRunConfigurationModule;
import com.intellij.java.execution.impl.JavaRunConfigurationExtensionManager;
import com.intellij.java.execution.impl.JavaTestConfigurationBase;
import com.intellij.java.execution.impl.RunConfigurationExtension;
import com.intellij.java.execution.impl.junit.RefactoringListeners;
import com.intellij.java.execution.impl.junit2.info.MethodLocation;
import com.intellij.java.language.impl.codeInsight.MetaAnnotationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.rt.execution.junit.RepeatCount;
import consulo.execution.ExecutionBundle;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.action.Location;
import consulo.execution.action.PsiLocation;
import consulo.execution.action.RunConfigurationProducer;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.RuntimeConfigurationError;
import consulo.execution.configuration.fragment.EnvironmentFragmentState;
import consulo.execution.configuration.fragment.ProgramParametersFragmentState;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.WorkingDirectoryFragmentState;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ExecutionEnvironmentBuilder;
import consulo.execution.test.TestSearchScope;
import consulo.execution.test.sm.runner.SMTRunnerConsoleProperties;
import consulo.java.execution.configurations.fragment.JreFragmentState;
import consulo.java.execution.configurations.fragment.VmParametersFragmentState;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPackage;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class JUnitConfiguration extends JavaTestConfigurationBase {
    public static final String DEFAULT_PACKAGE_NAME = ExecutionBundle.message("default.package.presentable.name");

    @NonNls
    public static final String TEST_CLASS = "class";
    @NonNls
    public static final String TEST_PACKAGE = "package";
    @NonNls
    public static final String TEST_DIRECTORY = "directory";
    @NonNls
    public static final String TEST_CATEGORY = "category";
    @NonNls
    public static final String TEST_METHOD = "method";
    @NonNls
    public static final String TEST_UNIQUE_ID = "uniqueId";
    @NonNls
    public static final String BY_SOURCE_POSITION = "source location";
    @NonNls
    public static final String BY_SOURCE_CHANGES = "changes";

    //fork modes
    @NonNls
    public static final String FORK_NONE = "none";
    @NonNls
    public static final String FORK_METHOD = "method";
    @NonNls
    public static final String FORK_KLASS = "class";
    @NonNls
    public static final String FORK_REPEAT = "repeat";
    // See #26522
    @NonNls
    public static final String JUNIT_START_CLASS = "com.intellij.rt.execution.junit.JUnitStarter";
    @NonNls
    public static final String TEST_PATTERN = "pattern";

    private static final String LEGACY_PATTERNS = "patterns";
    private static final String LEGACY_PATTERN = "pattern";
    private static final String LEGACY_TEST_CLASS = "testClass";
    private static final String LEGACY_SEARCH_SCOPE = "TEST_SEARCH_SCOPE";

    final RefactoringListeners.Accessor<PsiJavaPackage> myPackage = new RefactoringListeners.Accessor<PsiJavaPackage>() {
        @Override
        public void setName(String qualifiedName) {
            boolean generatedName = isGeneratedName();
            getOptions().setPackageName(qualifiedName);
            if (generatedName) {
                setGeneratedName();
            }
        }

        @Override
        public PsiJavaPackage getPsiElement() {
            String qualifiedName = getOptions().getPackageName();
            return JavaPsiFacade.getInstance(getProject()).findPackage(qualifiedName);
        }

        @Override
        public void setPsiElement(PsiJavaPackage psiPackage) {
            setName(psiPackage.getQualifiedName());
        }
    };
    final RefactoringListeners.Accessor<PsiClass> myClass = new RefactoringListeners.Accessor<PsiClass>() {
        @Override
        public void setName(@Nonnull String qualifiedName) {
            boolean generatedName = isGeneratedName();
            getOptions().setMainClassName(qualifiedName);
            if (generatedName) {
                setGeneratedName();
            }
        }

        @Override
        public PsiClass getPsiElement() {
            return getConfigurationModule().findClass(getOptions().getMainClassName());
        }

        @Override
        public void setPsiElement(PsiClass psiClass) {
            Module originalModule = getConfigurationModule().getModule();
            setMainClass(psiClass);
            restoreOriginalModule(originalModule);
        }
    };

    final RefactoringListeners.Accessor<PsiClass> myCategory = new RefactoringListeners.Accessor<PsiClass>() {
        @Override
        public void setName(@Nonnull String qualifiedName) {
            setCategory(qualifiedName);
        }

        @Override
        public PsiClass getPsiElement() {
            return getConfigurationModule().findClass(getOptions().getCategoryName());
        }

        @Override
        public void setPsiElement(PsiClass psiClass) {
            setCategory(JavaExecutionUtil.getRuntimeQualifiedName(psiClass));
        }
    };

    public JUnitConfiguration(String name, Project project, ConfigurationFactory configurationFactory) {
        super(name, new JavaRunConfigurationModule(project, false), configurationFactory);
    }

    @Override
    protected Class<JUnitConfigurationOptions> getOptionsClass() {
        return JUnitConfigurationOptions.class;
    }

    @Override
    public JUnitConfigurationOptions getOptions() {
        return (JUnitConfigurationOptions) super.getOptions();
    }

    @Override
    protected void convertLegacyState(LegacyRunConfigurationConverter converter) {
        super.convertLegacyState(converter);

        JUnitConfigurationLegacyState old = converter.readXml(JUnitConfigurationLegacyState.class);
        Element element = converter.getElement();

        JUnitConfigurationOptions options = new JUnitConfigurationOptions();
        options.setTestObject(old.TEST_OBJECT);
        options.setPackageName(old.PACKAGE_NAME);
        options.setMainClassName(old.MAIN_CLASS_NAME);
        options.setMethodName(old.METHOD_NAME);
        options.setSearchScope(readLegacySearchScope(element));

        Set<String> patterns = new LinkedHashSet<>();
        Element patternsElement = element.getChild(LEGACY_PATTERNS);
        if (patternsElement != null) {
            for (Element patternElement : patternsElement.getChildren(LEGACY_PATTERN)) {
                String testClass = patternElement.getAttributeValue(LEGACY_TEST_CLASS);
                if (testClass != null) {
                    patterns.add(testClass);
                }
            }
        }
        options.setPatterns(new ArrayList<>(patterns));

        Element forkModeElement = element.getChild("fork_mode");
        if (forkModeElement != null) {
            options.setForkMode(forkModeElement.getAttributeValue("value"));
        }

        String count = element.getAttributeValue("repeat_count");
        if (count != null) {
            try {
                options.setRepeatCount(Integer.parseInt(count));
            }
            catch (NumberFormatException e) {
                options.setRepeatCount(1);
            }
        }
        options.setRepeatMode(element.getAttributeValue("repeat_mode"));

        Element dirNameElement = element.getChild("dir");
        if (dirNameElement != null) {
            String dirName = dirNameElement.getAttributeValue("value");
            options.setDirName(dirName == null ? null : FileUtil.toSystemDependentName(dirName));
        }

        Element categoryNameElement = element.getChild("category");
        if (categoryNameElement != null) {
            options.setCategoryName(categoryNameElement.getAttributeValue("value"));
        }

        Element idsElement = element.getChild("uniqueIds");
        if (idsElement != null) {
            List<String> ids = new ArrayList<>();
            for (Element uniqueIdElement : idsElement.getChildren("uniqueId")) {
                String id = uniqueIdElement.getAttributeValue("value");
                if (id != null) {
                    ids.add(id);
                }
            }
            options.setUniqueIds(ids);
        }
        converter.setOptions(options);

        converter.addFragment(new ProgramParametersFragmentState(old.PARAMETERS));
        converter.addFragment(new WorkingDirectoryFragmentState(old.WORKING_DIRECTORY));
        converter.addFragment(new EnvironmentFragmentState(converter.readEnvs(), old.PASS_PARENT_ENVS));
        converter.addFragment(new VmParametersFragmentState(old.VM_PARAMETERS));
        if (old.ALTERNATIVE_JRE_PATH_ENABLED) {
            converter.addFragment(new JreFragmentState(old.ALTERNATIVE_JRE_PATH));
        }
    }

    private static String readLegacySearchScope(Element element) {
        for (Element option : element.getChildren("option")) {
            if (!LEGACY_SEARCH_SCOPE.equals(option.getAttributeValue("name"))) {
                continue;
            }

            Element value = option.getChild("value");
            if (value == null) {
                return TestSearchScope.MODULE_WITH_DEPENDENCIES.toString();
            }

            String name = value.getAttributeValue("defaultName");
            if ("singleModule".equals(name)) {
                return TestSearchScope.SINGLE_MODULE.toString();
            }
            if ("moduleWithDependencies".equals(name)) {
                return TestSearchScope.MODULE_WITH_DEPENDENCIES.toString();
            }
            return TestSearchScope.WHOLE_PROJECT.toString();
        }
        return TestSearchScope.MODULE_WITH_DEPENDENCIES.toString();
    }

    @Override
    public List<RunConfigurationFragment<?>> createFragments() {
        return List.of(
            new JUnitTestTargetFragment(),
            new JUnitForkModeFragment(),
            new JUnitRepeatFragment()
        );
    }

    @Override
    public TestObject getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment env) throws ExecutionException {
        return TestObject.fromString(getOptions().getTestObject(), this, env);
    }

    @Override
    public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
        RefactoringElementListener listener = getTestObject().getListener(element, this);
        return RunConfigurationExtension.wrapRefactoringElementListener(element, this, listener);
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        getTestObject().checkConfiguration();
        JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
    }

    @Override
    public Collection<Module> getValidModules() {
        String testObject = getOptions().getTestObject();
        if (TEST_PACKAGE.equals(testObject) || TEST_PATTERN.equals(testObject)) {
            return Arrays.asList(ModuleManager.getInstance(getProject()).getModules());
        }
        try {
            getTestObject().checkConfiguration();
        }
        catch (RuntimeConfigurationError e) {
            return Arrays.asList(ModuleManager.getInstance(getProject()).getModules());
        }
        catch (RuntimeConfigurationException e) {
            //ignore
        }

        return JavaRunConfigurationModule.getModulesForClass(getProject(), getOptions().getMainClassName());
    }

    @Override
    public String suggestedName() {
        String repeat;
        switch (getRepeatMode()) {
            case RepeatCount.UNLIMITED:
            case RepeatCount.UNTIL_FAILURE:
                repeat = " [*]";
                break;
            case RepeatCount.N:
                repeat = " [" + getRepeatCount() + "]";
                break;
            default:
                repeat = "";
        }
        return getGeneratedName() + repeat;
    }

    @Override
    public String getActionName() {
        return getTestObject().suggestActionName();
    }

    @Override
    public String getRunClass() {
        String testObject = getOptions().getTestObject();
        return !Comparing.strEqual(testObject, TEST_CLASS) && !Comparing.strEqual(testObject, TEST_METHOD) ? null : getOptions().getMainClassName();
    }

    @Override
    public String getPackage() {
        return !Comparing.strEqual(getOptions().getTestObject(), TEST_PACKAGE) ? null : getOptions().getPackageName();
    }

    @Override
    public void beClassConfiguration(PsiClass testClass) {
        if (FORK_KLASS.equals(getForkMode())) {
            setForkMode(FORK_NONE);
        }
        setMainClass(testClass);
        getOptions().setTestObject(TEST_CLASS);
        setGeneratedName();
    }

    @Override
    public boolean isConfiguredByElement(PsiElement element) {
        PsiClass testClass = JUnitUtil.getTestClass(element);
        PsiMethod testMethod = JUnitUtil.getTestMethod(element, false);
        PsiPackage testPackage;
        if (element instanceof PsiPackage) {
            testPackage = (PsiPackage) element;
        }
        else if (element instanceof PsiDirectory) {
            testPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory) element));
        }
        else {
            testPackage = null;
        }
        PsiDirectory testDir = element instanceof PsiDirectory ? (PsiDirectory) element : null;

        return getTestObject().isConfiguredByElement(this, testClass, testMethod, testPackage, testDir);
    }

    @Override
    public TestSearchScope getTestSearchScope() {
        String name = getOptions().getSearchScope();
        if (TestSearchScope.WHOLE_PROJECT.toString().equals(name)) {
            return TestSearchScope.WHOLE_PROJECT;
        }
        if (TestSearchScope.SINGLE_MODULE.toString().equals(name)) {
            return TestSearchScope.SINGLE_MODULE;
        }
        return TestSearchScope.MODULE_WITH_DEPENDENCIES;
    }

    public void setTestSearchScope(TestSearchScope scope) {
        getOptions().setSearchScope(scope.toString());
    }

    public void beFromSourcePosition(PsiLocation<PsiMethod> sourceLocation) {
        setTestMethod(sourceLocation);
        getOptions().setTestObject(BY_SOURCE_POSITION);
    }

    public void setMainClass(PsiClass testClass) {
        boolean shouldUpdateName = isGeneratedName();
        setModule(applyMainClass(testClass));
        if (shouldUpdateName) {
            setGeneratedName();
        }
    }

    public void setCategory(String categoryName) {
        boolean shouldUpdateName = isGeneratedName();
        getOptions().setCategoryName(categoryName);
        if (shouldUpdateName) {
            setGeneratedName();
        }
    }

    @Override
    public void beMethodConfiguration(Location<PsiMethod> methodLocation) {
        setForkMode(FORK_NONE);
        setModule(setTestMethod(methodLocation));
        setGeneratedName();
    }

    /**
     * Points the configuration to the method of the location and its class.
     *
     * @return the module of the test class
     */
    @Nullable
    public Module setTestMethod(Location<PsiMethod> methodLocation) {
        PsiMethod method = methodLocation.getPsiElement();
        getOptions().setMethodName(getMethodPresentation(method));
        getOptions().setTestObject(TEST_METHOD);
        return applyMainClass(methodLocation instanceof MethodLocation ? ((MethodLocation) methodLocation).getContainingClass() : method.getContainingClass());
    }

    @Nullable
    private Module applyMainClass(PsiClass testClass) {
        JUnitConfigurationOptions options = getOptions();
        options.setMainClassName(JavaExecutionUtil.getRuntimeQualifiedName(testClass));
        PsiPackage containingPackage = JUnitUtil.getContainingPackage(testClass);
        options.setPackageName(containingPackage != null ? containingPackage.getQualifiedName() : "");
        return JavaExecutionUtil.findModule(testClass);
    }

    /**
     * The test method name without the parameter signature.
     */
    public String getMethodNameWithoutSignature() {
        String signature = getOptions().getMethodName();
        int paramsIdx = signature.lastIndexOf("(");
        return paramsIdx > -1 ? signature.substring(0, paramsIdx) : signature;
    }

    public Set<String> getPatterns() {
        return new LinkedHashSet<>(getOptions().getPatterns());
    }

    public void setPatterns(Collection<String> patterns) {
        getOptions().setPatterns(new ArrayList<>(new LinkedHashSet<>(patterns)));
    }

    public String getPatternPresentation() {
        return StringUtil.join(getOptions().getPatterns(), "||");
    }

    public static String getMethodPresentation(PsiMethod method) {
        if (method.getParameterList().getParametersCount() > 0 && MetaAnnotationUtil.isMetaAnnotated(method, JUnitUtil.TEST5_ANNOTATIONS)) {
            return method.getName() + "(" + StringUtil.join(method.getParameterList().getParameters(), param ->
            {
                PsiType type = TypeConversionUtil.erasure(param.getType());
                return type != null ? type.accept(createSignatureVisitor()) : "";
            }, ",") + ")";
        }
        else {
            return method.getName();
        }
    }

    private static PsiTypeVisitor<String> createSignatureVisitor() {
        return new PsiTypeVisitor<String>() {
            @Override
            public String visitPrimitiveType(PsiPrimitiveType primitiveType) {
                return primitiveType.getCanonicalText();
            }

            @Override
            public String visitClassType(PsiClassType classType) {
                PsiClass aClass = classType.resolve();
                if (aClass == null) {
                    return "";
                }
                return ClassUtil.getJVMClassName(aClass);
            }

            @Override
            public String visitArrayType(PsiArrayType arrayType) {
                PsiType componentType = arrayType.getComponentType();
                String typePresentation = componentType.accept(this);
                if (componentType instanceof PsiClassType) {
                    typePresentation = "L" + typePresentation + ";";
                }
                return "[" + typePresentation;
            }
        };
    }

    private String getGeneratedName() {
        JUnitConfigurationOptions options = getOptions();
        String testObject = options.getTestObject();
        if (TEST_PACKAGE.equals(testObject) || TEST_DIRECTORY.equals(testObject)) {
            if (getTestSearchScope() == TestSearchScope.WHOLE_PROJECT) {
                return ExecutionBundle.message("default.junit.config.name.whole.project");
            }
            String moduleName = getConfigurationModule().getModuleName();
            String packageName = TEST_PACKAGE.equals(testObject) ? options.getPackageName() : StringUtil.getShortName(options.getDirName(), '/');
            if (packageName.length() == 0) {
                if (moduleName.length() > 0) {
                    return ExecutionBundle.message("default.junit.config.name.all.in.module", moduleName);
                }
                return DEFAULT_PACKAGE_NAME;
            }
            if (moduleName.length() > 0) {
                return ExecutionBundle.message("default.junit.config.name.all.in.package.in.module", packageName, moduleName);
            }
            return packageName;
        }
        if (TEST_PATTERN.equals(testObject)) {
            List<String> patterns = options.getPatterns();
            int size = patterns.size();
            if (size == 0) {
                return "Temp suite";
            }
            String fqName = patterns.get(0);
            String firstName = fqName.contains("*") ? fqName : StringUtil.getShortName(fqName.contains("(") ? StringUtil.getPackageName(fqName, '(') : fqName);
            return firstName + (size > 1 ? " and " + (size - 1) + " more" : "");
        }
        if (TEST_CATEGORY.equals(testObject)) {
            String categoryName = options.getCategoryName();
            return "@Category(" + (StringUtil.isEmpty(categoryName) ? "Invalid" : categoryName) + ")";
        }
        if (TEST_UNIQUE_ID.equals(testObject)) {
            List<String> uniqueIds = options.getUniqueIds();
            return !uniqueIds.isEmpty() ? StringUtil.join(uniqueIds, " ") : "Temp suite";
        }
        String className = JavaExecutionUtil.getPresentableClassName(options.getMainClassName());
        if (TEST_METHOD.equals(testObject)) {
            return className + '.' + getMethodNameWithoutSignature();
        }

        return className;
    }

    @Override
    @Nonnull
    public Module[] getModules() {
        if (TEST_PACKAGE.equals(getOptions().getTestObject()) && getTestSearchScope() == TestSearchScope.WHOLE_PROJECT) {
            return Module.EMPTY_ARRAY;
        }
        return super.getModules();
    }

    public TestObject getTestObject() {
        ExecutionEnvironment environment = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), this).build();
        TestObject testObject = TestObject.fromString(getOptions().getTestObject(), this, environment);
        return testObject == null ? new UnknownTestTarget(this, environment) : testObject;
    }

    public String getForkMode() {
        return getOptions().getForkMode();
    }

    public void setForkMode(@Nonnull String forkMode) {
        getOptions().setForkMode(forkMode);
    }

    @Override
    public boolean collectOutputFromProcessHandler() {
        return false;
    }

    @Override
    public void bePatternConfiguration(List<PsiClass> classes, PsiMethod method) {
        JUnitConfigurationOptions options = getOptions();
        options.setTestObject(TEST_PATTERN);
        LinkedHashSet<String> patterns = new LinkedHashSet<>();
        String methodSufiix;
        if (method != null) {
            options.setMethodName(getMethodPresentation(method));
            methodSufiix = "," + options.getMethodName();
        }
        else {
            methodSufiix = "";
        }
        for (PsiClass pattern : classes) {
            patterns.add(JavaExecutionUtil.getRuntimeQualifiedName(pattern) + methodSufiix);
        }
        setPatterns(patterns);
        Module module = RunConfigurationProducer.getInstance(PatternConfigurationProducer.class).findModule(this, getConfigurationModule().getModule(), patterns);
        if (module == null) {
            setTestSearchScope(TestSearchScope.WHOLE_PROJECT);
            setModule(null);
        }
        else {
            setModule(module);
        }
        setGeneratedName();
    }

    public int getRepeatCount() {
        return getOptions().getRepeatCount();
    }

    public void setRepeatCount(int repeatCount) {
        getOptions().setRepeatCount(repeatCount);
    }

    public String getRepeatMode() {
        return getOptions().getRepeatMode();
    }

    public void setRepeatMode(String repeatMode) {
        getOptions().setRepeatMode(repeatMode);
    }

    @Override
    public SMTRunnerConsoleProperties createTestConsoleProperties(Executor executor) {
        return new JUnitConsoleProperties(this, executor);
    }

    @Nonnull
    @Override
    public String getFrameworkPrefix() {
        return "j";
    }
}
