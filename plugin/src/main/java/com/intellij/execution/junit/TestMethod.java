// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.junit;

import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.configurations.JavaRunConfigurationModule;
import com.intellij.java.execution.impl.junit.RefactoringListeners;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.execution.ExecutionBundle;
import consulo.execution.ProgramRunnerUtil;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.RuntimeConfigurationWarning;
import consulo.execution.action.PsiLocation;
import consulo.execution.configuration.RuntimeConfigurationError;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.language.editor.refactoring.event.RefactoringElementAdapter;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.event.UndoRefactoringElementListener;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPackage;
import consulo.process.ExecutionException;
import consulo.util.lang.Comparing;

import jakarta.annotation.Nonnull;

class TestMethod extends TestObject {
    public TestMethod(JUnitConfiguration configuration, ExecutionEnvironment environment) {
        super(configuration, environment);
    }

    @Override
    protected OwnJavaParameters createJavaParameters() throws ExecutionException {
        OwnJavaParameters javaParameters = createDefaultJavaParameters();
        JUnitConfigurationOptions data = getConfiguration().getOptions();
        javaParameters.getProgramParametersList().add(getRuntimeMainClassName() + "," + data.getMethodName());
        return javaParameters;
    }

    protected OwnJavaParameters createDefaultJavaParameters() throws ExecutionException {
        return super.createJavaParameters();
    }

    @Override
    public String suggestActionName() {
        return ProgramRunnerUtil.shortenName(getConfiguration().getOptions().getMethodName(), 2) + "()";
    }

    @Override
    public RefactoringElementListener getListener(PsiElement element, final JUnitConfiguration configuration) {
        if (element instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) element;
            if (!method.getName().equals(configuration.getMethodNameWithoutSignature())) {
                return null;
            }
            //noinspection ConstantConditions
            if (!method.getContainingClass().equals(configuration.myClass.getPsiElement())) {
                return null;
            }
            class Listener extends RefactoringElementAdapter implements UndoRefactoringElementListener {
                @Override
                public void elementRenamedOrMoved(@Nonnull PsiElement newElement) {
                    boolean generatedName = configuration.isGeneratedName();
                    configuration.setTestMethod(PsiLocation.fromPsiElement((PsiMethod) newElement));
                    if (generatedName) {
                        configuration.setGeneratedName();
                    }
                }

                @Override
                public void undoElementMovedOrRenamed(@Nonnull PsiElement newElement, @Nonnull String oldQualifiedName) {
                    int methodIdx = oldQualifiedName.indexOf("#") + 1;
                    if (methodIdx <= 0 || methodIdx >= oldQualifiedName.length()) {
                        return;
                    }
                    boolean generatedName = configuration.isGeneratedName();
                    configuration.getOptions().setMethodName(oldQualifiedName.substring(methodIdx));
                    if (generatedName) {
                        configuration.setGeneratedName();
                    }
                }
            }
            return new Listener();
        }
        else {
            return RefactoringListeners.getClassOrPackageListener(element, configuration.myClass);
        }
    }


    @Override
    public boolean isConfiguredByElement(JUnitConfiguration configuration, PsiClass testClass, PsiMethod testMethod, PsiPackage testPackage, PsiDirectory testDir) {
        if (testMethod == null) {
            return false;
        }
        if (testClass == null) {
            return false;
        }
        JUnitConfigurationOptions data = configuration.getOptions();
    /*final PsiClass containingClass = testMethod.getContainingClass();
    if (testClass == null && (containingClass == null || !containingClass.hasModifierProperty(PsiModifier.ABSTRACT))) return false;

    if (containingClass != null && containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return Comparing.equal(testMethod.getName(), data.getMethodName());
    }*/
        return Comparing.equal(JavaExecutionUtil.getRuntimeQualifiedName(testClass), data.getMainClassName()) && Comparing.equal(JUnitConfiguration.getMethodPresentation(testMethod), data.getMethodName());
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        super.checkConfiguration();
        JavaRunConfigurationModule configurationModule = getConfiguration().getConfigurationModule();
        JUnitConfigurationOptions data = getConfiguration().getOptions();
        String testClass = data.getMainClassName();
        PsiClass psiClass = configurationModule.checkModuleAndClassName(testClass, ExecutionBundle.message("no.test.class.specified.error.text"));

        String methodName = getConfiguration().getMethodNameWithoutSignature();
        String methodNameWithSignature = data.getMethodName();
        if (methodName == null || methodName.trim().length() == 0) {
            throw new RuntimeConfigurationError(ExecutionBundle.message("method.name.not.specified.error.message"));
        }
        JUnitUtil.TestMethodFilter filter = new JUnitUtil.TestMethodFilter(psiClass);
        boolean found = false;
        boolean testAnnotated = false;
        for (PsiMethod method : psiClass.findMethodsByName(methodName, true)) {
            if (filter.value(method) && Comparing.equal(methodNameWithSignature, JUnitConfiguration.getMethodPresentation(method))) {
                found = true;
            }
            if (JUnitUtil.isTestAnnotated(method)) {
                testAnnotated = true;
            }
        }
        if (!found) {
            throw new RuntimeConfigurationWarning(ExecutionBundle.message("test.method.doesnt.exist.error.message", methodName));
        }

        if (!AnnotationUtil.isAnnotated(psiClass, JUnitUtil.RUN_WITH, AnnotationUtil.CHECK_HIERARCHY) && !testAnnotated) {
            PsiClass testCaseClass = JUnitUtil.getTestCaseClass(configurationModule.getModule());
            if (testCaseClass != null && !psiClass.isInheritor(testCaseClass, true)) {
                throw new RuntimeConfigurationError(ExecutionBundle.message("class.isnt.inheritor.of.testcase.error.message", testClass));
            }
        }
    }
}
