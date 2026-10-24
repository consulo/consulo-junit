/*
 * Copyright 2013-2026 consulo.io
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

import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.impl.MethodListDlg;
import com.intellij.java.execution.impl.testDiscovery.TestDiscoveryExtension;
import com.intellij.java.execution.impl.ui.ClassBrowser;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.ui.JavaReferenceEditorUtil;
import com.intellij.java.language.impl.ui.PackageChooser;
import com.intellij.java.language.impl.ui.PackageChooserFactory;
import com.intellij.java.language.psi.JavaCodeFragment;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.util.ClassFilter;
import consulo.application.concurrent.coroutine.ReadLock;
import consulo.disposer.Disposable;
import consulo.document.Document;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.configuration.fragment.StandardRunConfigurationFragmentOrder;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.test.SourceScope;
import consulo.execution.test.TestSearchScope;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.junit.localize.JUnitLocalize;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.ui.EditorBox;
import consulo.language.editor.ui.EditorBoxBuilderFactory;
import consulo.language.editor.ui.awt.TextFieldCompletionProvider;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.HasSuffixComponent;
import consulo.ui.Label;
import consulo.ui.RadioGroup;
import consulo.ui.Space;
import consulo.ui.TextBox;
import consulo.ui.UIAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionToolbar;
import consulo.ui.ex.action.ActionToolbarFactory;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.FormBuilder;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.util.concurrent.coroutine.CoroutineScope;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.LocalChangeList;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The test kind of a {@link JUnitConfiguration} with its target (package, directory, pattern, class, method, category,
 * unique id or change list) and the search scope.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
class JUnitTestTargetFragment implements RunConfigurationFragment<JUnitConfiguration> {
    @Override
    public String getId() {
        return "junit.target";
    }

    @Override
    public LocalizeValue getName() {
        return JUnitLocalize.runConfigurationFragmentTestKind();
    }

    @Override
    public int getOrder() {
        return StandardRunConfigurationFragmentOrder.FIRST;
    }

    @Override
    public boolean isRemovable() {
        return false;
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<JUnitConfiguration> createEditor(Project project, Disposable parent) {
        return new Editor(project, parent);
    }

    private static List<String> getTypes() {
        List<String> types = new ArrayList<>(List.of(
            JUnitConfiguration.TEST_PACKAGE,
            JUnitConfiguration.TEST_DIRECTORY,
            JUnitConfiguration.TEST_PATTERN,
            JUnitConfiguration.TEST_CLASS,
            JUnitConfiguration.TEST_METHOD,
            JUnitConfiguration.TEST_CATEGORY,
            JUnitConfiguration.TEST_UNIQUE_ID
        ));
        if (TestDiscoveryExtension.TESTDISCOVERY_ENABLED) {
            types.add(JUnitConfiguration.BY_SOURCE_POSITION);
            types.add(JUnitConfiguration.BY_SOURCE_CHANGES);
        }
        return types;
    }

    private static LocalizeValue getTypeName(String type) {
        return switch (type) {
            case JUnitConfiguration.TEST_PACKAGE -> JUnitLocalize.junitConfigurationKindAllInPackage();
            case JUnitConfiguration.TEST_DIRECTORY -> JUnitLocalize.junitConfigurationKindAllInDirectory();
            case JUnitConfiguration.TEST_PATTERN -> JUnitLocalize.junitConfigurationKindByPattern();
            case JUnitConfiguration.TEST_CLASS -> JUnitLocalize.junitConfigurationKindClass();
            case JUnitConfiguration.TEST_METHOD -> JUnitLocalize.junitConfigurationKindMethod();
            case JUnitConfiguration.TEST_CATEGORY -> JUnitLocalize.junitConfigurationKindCategory();
            case JUnitConfiguration.TEST_UNIQUE_ID -> JUnitLocalize.junitConfigurationKindByUniqueId();
            case JUnitConfiguration.BY_SOURCE_POSITION -> JUnitLocalize.junitConfigurationKindBySourcePosition();
            case JUnitConfiguration.BY_SOURCE_CHANGES -> JUnitLocalize.junitConfigurationKindBySourceChanges();
            default -> LocalizeValue.of(type);
        };
    }

    private static boolean isMethodType(String type) {
        return JUnitConfiguration.TEST_METHOD.equals(type) || JUnitConfiguration.BY_SOURCE_POSITION.equals(type);
    }

    private static boolean isScopeShown(String type) {
        return JUnitConfiguration.TEST_PACKAGE.equals(type)
            || JUnitConfiguration.TEST_PATTERN.equals(type)
            || JUnitConfiguration.TEST_CATEGORY.equals(type);
    }

    private static String toPresentableClassName(String className) {
        return className.replace('$', '.');
    }

    private record Row(Label label, Component field) {
        @RequiredUIAccess
        static Row add(FormBuilder builder, LocalizeValue text, Component field) {
            Label label = Label.create(text);
            builder.addLabeled(label, field);
            return new Row(label, field);
        }

        @RequiredUIAccess
        void setVisible(boolean visible) {
            label.setVisible(visible);
            field.setVisible(visible);
        }
    }

    private static final class Editor implements RunConfigurationFragmentEditor<JUnitConfiguration> {
        private final Project myProject;
        private final JUnitModuleHolder myModuleHolder;

        private final ComboBox<String> myTypeChooser;
        private final EditorBox myPackageField;
        private final FileChooserTextBoxBuilder.Controller myDirField;
        private final TextBox myPatternField;
        private final EditorBox myClassField;
        private final EditorBox myMethodField;
        private final EditorBox myCategoryField;
        private final TextBox myUniqueIdField;
        private final ComboBox<String> myChangeListBox;
        private final RadioGroup<TestSearchScope> myScopeGroup;
        private final Component myForm;

        private final Row myPackageRow;
        private final Row myDirRow;
        private final Row myPatternRow;
        private final Row myClassRow;
        private final Row myMethodRow;
        private final Row myCategoryRow;
        private final Row myUniqueIdRow;
        private final Row myChangeListRow;
        private final Row myScopeRow;

        @RequiredUIAccess
        private Editor(Project project, Disposable parent) {
            myProject = project;
            myModuleHolder = new JUnitModuleHolder(project);

            myTypeChooser = ComboBox.create(getTypes());
            myTypeChooser.setTextRenderer(type -> type == null ? LocalizeValue.empty() : getTypeName(type));

            myPackageField = createReferenceField(false, JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);

            FileChooserDescriptor dirDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
            dirDescriptor.setHideIgnored(false);
            myDirField = FileChooserTextBoxBuilder.create(project)
                .uiDisposable(parent)
                .fileChooserDescriptor(dirDescriptor)
                .build();

            myPatternField = TextBox.create();

            TestClassBrowser classBrowser = new TestClassBrowser();
            myClassField = createReferenceField(true, (declaration, place) -> {
                try {
                    if (declaration instanceof PsiClass psiClass
                        && (classBrowser.getFilter().isAccepted(psiClass)
                        || classBrowser.findClass(psiClass.getQualifiedName()) != null && place.getParent() != null)) {
                        return JavaCodeFragment.VisibilityChecker.Visibility.VISIBLE;
                    }
                }
                catch (ClassBrowser.NoFilterException e) {
                    return JavaCodeFragment.VisibilityChecker.Visibility.NOT_VISIBLE;
                }
                return JavaCodeFragment.VisibilityChecker.Visibility.NOT_VISIBLE;
            });

            myMethodField = createMethodField();

            myCategoryField = createReferenceField(
                true,
                (declaration, place) -> declaration instanceof PsiClass
                    ? JavaCodeFragment.VisibilityChecker.Visibility.VISIBLE
                    : JavaCodeFragment.VisibilityChecker.Visibility.NOT_VISIBLE
            );

            myUniqueIdField = TextBox.create();

            List<String> changeLists = new ArrayList<>();
            changeLists.add(JUnitConfigurationOptions.ALL_CHANGE_LISTS);
            if (!project.isDefault()) {
                for (LocalChangeList changeList : ChangeListManager.getInstance(project).getChangeLists()) {
                    changeLists.add(changeList.getName());
                }
            }
            myChangeListBox = ComboBox.create(changeLists);
            myChangeListBox.setTextRenderer(name -> JUnitConfigurationOptions.ALL_CHANGE_LISTS.equals(name)
                ? JUnitLocalize.junitConfigurationChangeListAll()
                : LocalizeValue.ofNullable(name));
            myChangeListBox.setValue(JUnitConfigurationOptions.ALL_CHANGE_LISTS, false);

            myScopeGroup = RadioGroup.create();
            VerticalLayout scopes = VerticalLayout.create(Space.NONE);
            scopes.add(myScopeGroup.newButton(ExecutionLocalize.junitConfigurationInWholeProjectRadio(), TestSearchScope.WHOLE_PROJECT));
            scopes.add(myScopeGroup.newButton(ExecutionLocalize.junitConfigurationInSingleModuleRadio(), TestSearchScope.SINGLE_MODULE));
            scopes.add(myScopeGroup.newButton(
                ExecutionLocalize.junitConfigurationAcrossModuleDependenciesRadio(),
                TestSearchScope.MODULE_WITH_DEPENDENCIES
            ));
            myScopeGroup.setValue(TestSearchScope.WHOLE_PROJECT, false);

            FormBuilder builder = FormBuilder.create();
            myPackageRow = Row.add(builder, ExecutionLocalize.junitConfigurationPackageLabel(), myPackageField);
            myDirRow = Row.add(builder, JUnitLocalize.junitConfigurationDirectoryLabel(), myDirField.getComponent());
            myPatternRow = Row.add(builder, JUnitLocalize.junitConfigurationPatternLabel(), myPatternField);
            myClassRow = Row.add(builder, ExecutionLocalize.junitConfigurationClassLabel(), myClassField);
            myMethodRow = Row.add(builder, ExecutionLocalize.junitConfigurationMethodLabel(), myMethodField);
            myCategoryRow = Row.add(builder, JUnitLocalize.junitConfigurationCategoryLabel(), myCategoryField);
            myUniqueIdRow = Row.add(builder, JUnitLocalize.junitConfigurationUniqueIdLabel(), myUniqueIdField);
            myChangeListRow = Row.add(builder, JUnitLocalize.junitConfigurationChangeListLabel(), myChangeListBox);
            myScopeRow = Row.add(builder, ExecutionLocalize.junitConfigurationSearchForTestsLabel(), scopes);
            myForm = builder.build();

            if (!project.getApplication().isUnifiedApplication()) {
                installChooserActions(classBrowser);
            }

            myTypeChooser.addValueListener(event -> {
                String type = event.getValue();
                if (type != null) {
                    updateRows(type);
                }
            });
            myTypeChooser.setValue(JUnitConfiguration.TEST_CLASS, false);
            updateRows(JUnitConfiguration.TEST_CLASS);
        }

        @RequiredUIAccess
        @Override
        public void addRows(RunConfigurationFragmentLayout layout) {
            layout.addLabeled(ExecutionLocalize.junitConfigurationConfigureJunitTestKindLabel(), myTypeChooser);
            layout.addUnlabeled(myForm);
        }

        @RequiredUIAccess
        @Override
        public void reset(JUnitConfiguration target) {
            myModuleHolder.setModule(target.getConfigurationModule().getModule());

            JUnitConfigurationOptions options = target.getOptions();
            String type = options.getTestObject();
            myTypeChooser.setValue(type, false);
            myPackageField.setValue(options.getPackageName());
            myDirField.setValue(options.getDirName(), false);
            myPatternField.setValue(target.getPatternPresentation(), false);
            myClassField.setValue(toPresentableClassName(options.getMainClassName()));
            myMethodField.setValue(options.getMethodName());
            myCategoryField.setValue(options.getCategoryName());
            myUniqueIdField.setValue(StringUtil.join(options.getUniqueIds(), " "), false);
            myChangeListBox.setValue(options.getChangeList(), false);
            TestSearchScope scope = target.getTestSearchScope();
            myScopeGroup.setValue(
                scope == TestSearchScope.SINGLE_MODULE || scope == TestSearchScope.MODULE_WITH_DEPENDENCIES ? scope : TestSearchScope.WHOLE_PROJECT,
                false
            );
            updateRows(type);
        }

        @RequiredUIAccess
        @Override
        public void apply(JUnitConfiguration target) {
            myModuleHolder.setModule(target.getConfigurationModule().getModule());

            boolean generatedName = target.isGeneratedName();
            JUnitConfigurationOptions options = target.getOptions();
            String type = getType();
            options.setTestObject(type);
            if (JUnitConfiguration.TEST_PACKAGE.equals(type)
                || JUnitConfiguration.TEST_PATTERN.equals(type)
                || JUnitConfiguration.TEST_DIRECTORY.equals(type)
                || JUnitConfiguration.TEST_CATEGORY.equals(type)) {
                if (JUnitConfiguration.TEST_PACKAGE.equals(type)) {
                    options.setPackageName(getText(myPackageField.getValue()));
                }
                else if (JUnitConfiguration.TEST_DIRECTORY.equals(type)) {
                    options.setDirName(getText(myDirField.getValue()));
                }
                else if (JUnitConfiguration.TEST_CATEGORY.equals(type)) {
                    options.setCategoryName(getText(myCategoryField.getValue()));
                }
                else {
                    Set<String> patterns = new LinkedHashSet<>();
                    for (String pattern : getText(myPatternField.getValue()).split("\\|\\|")) {
                        if (!pattern.isEmpty()) {
                            patterns.add(pattern);
                        }
                    }
                    target.setPatterns(patterns);
                }
                options.setMainClassName("");
                options.setMethodName("");
            }
            else if (!JUnitConfiguration.BY_SOURCE_CHANGES.equals(type)) {
                options.setMethodName(getText(myMethodField.getValue()));

                String className = getText(myClassField.getValue());
                if (!className.equals(toPresentableClassName(options.getMainClassName()))) {
                    options.setMainClassName(className);
                    options.setPackageName(StringUtil.getPackageName(className));
                }
            }

            List<String> uniqueIds = new ArrayList<>();
            for (String id : getText(myUniqueIdField.getValue()).split(" ")) {
                if (!id.isEmpty()) {
                    uniqueIds.add(id);
                }
            }
            options.setUniqueIds(uniqueIds);
            options.setChangeList(myChangeListBox.getValue());

            TestSearchScope scope = myScopeGroup.getValue();
            target.setTestSearchScope(scope == null ? TestSearchScope.WHOLE_PROJECT : scope);

            if (generatedName && !JavaExecutionUtil.isNewName(target.getName())) {
                target.setGeneratedName();
            }
        }

        private String getType() {
            String type = myTypeChooser.getValue();
            return type == null ? JUnitConfiguration.TEST_CLASS : type;
        }

        private static String getText(@Nullable String value) {
            return StringUtil.notNullize(value).trim();
        }

        @RequiredUIAccess
        private void updateRows(String type) {
            boolean method = isMethodType(type);
            myPackageRow.setVisible(JUnitConfiguration.TEST_PACKAGE.equals(type));
            myDirRow.setVisible(JUnitConfiguration.TEST_DIRECTORY.equals(type));
            myPatternRow.setVisible(JUnitConfiguration.TEST_PATTERN.equals(type));
            myClassRow.setVisible(JUnitConfiguration.TEST_CLASS.equals(type) || method);
            myMethodRow.setVisible(method);
            myCategoryRow.setVisible(JUnitConfiguration.TEST_CATEGORY.equals(type));
            myUniqueIdRow.setVisible(JUnitConfiguration.TEST_UNIQUE_ID.equals(type));
            myChangeListRow.setVisible(JUnitConfiguration.BY_SOURCE_CHANGES.equals(type));
            myScopeRow.setVisible(isScopeShown(type));
        }

        @RequiredUIAccess
        private EditorBox createReferenceField(boolean classesAccepted, JavaCodeFragment.VisibilityChecker visibilityChecker) {
            EditorBox field = myProject.getApplication().getInstance(EditorBoxBuilderFactory.class).create(myProject).build();
            if (myProject.isDefault()) {
                return field;
            }

            CoroutineScope.launchAsync(
                myProject.coroutineContext(),
                () -> Coroutine
                    .first(ReadLock.<Void, @Nullable Document>apply(
                        ignored -> JavaReferenceEditorUtil.createDocument("", myProject, classesAccepted, visibilityChecker)
                    ))
                    .then(UIAction.<@Nullable Document, Void>apply(document -> {
                        if (document != null) {
                            String text = StringUtil.notNullize(field.getValue());
                            field.setDocument(document, JavaFileType.INSTANCE);
                            field.setValue(text);
                        }
                        return null;
                    }))
            );
            return field;
        }

        @RequiredUIAccess
        private EditorBox createMethodField() {
            TextFieldCompletionProvider completionProvider = new TextFieldCompletionProvider() {
                @Override
                public void addCompletionVariants(String text, int offset, String prefix, CompletionResultSet result) {
                    PsiClass testClass = findTestClass();
                    if (testClass == null) {
                        return;
                    }

                    JUnitUtil.TestMethodFilter filter = new JUnitUtil.TestMethodFilter(testClass);
                    for (PsiMethod psiMethod : testClass.getAllMethods()) {
                        if (filter.value(psiMethod)) {
                            result.addElement(LookupElementBuilder.create(psiMethod.getName()));
                        }
                    }
                }
            };

            return myProject.getApplication()
                .getInstance(EditorBoxBuilderFactory.class)
                .create(myProject)
                .completion(completionProvider)
                .build();
        }

        private @Nullable PsiClass findTestClass() {
            String className = StringUtil.notNullize(myClassField.getValue());
            if (StringUtil.isEmptyOrSpaces(className)) {
                return null;
            }
            return myModuleHolder.findClass(className);
        }

        @RequiredUIAccess
        private void installChooserActions(TestClassBrowser classBrowser) {
            installAction(myPackageField, "JUnitConfigurationPackage", new ChooserAction(
                myProject,
                JUnitLocalize.junitConfigurationChoosePackageAction(),
                PlatformIconGroup.nodesPackage(),
                () -> {
                    PackageChooser chooser = myProject.getInstance(PackageChooserFactory.class).create();
                    List<PsiJavaPackage> packages = chooser.showAndSelect();
                    PsiPackage aPackage = packages == null || packages.isEmpty() ? null : packages.getFirst();
                    if (aPackage != null) {
                        myPackageField.setValue(aPackage.getQualifiedName());
                    }
                }
            ));

            installAction(myClassField, "JUnitConfigurationClass", createClassAction(
                ExecutionLocalize.chooseTestClassDialogTitle(),
                PlatformIconGroup.nodesClass(),
                () -> classBrowser.chooseClass(myClassField.getValue()),
                myClassField::setValue
            ));

            installAction(myMethodField, "JUnitConfigurationMethod", new ChooserAction(
                myProject,
                JUnitLocalize.junitConfigurationChooseMethodAction(),
                PlatformIconGroup.nodesMethod(),
                () -> {
                    PsiClass testClass = findTestClass();
                    if (testClass == null) {
                        return;
                    }

                    MethodListDlg dialog =
                        new MethodListDlg(testClass, new JUnitUtil.TestMethodFilter(testClass), (JComponent) TargetAWT.to(myMethodField));
                    if (dialog.showAndGet()) {
                        PsiMethod method = dialog.getSelected();
                        if (method != null) {
                            myMethodField.setValue(method.getName());
                        }
                    }
                }
            ));

            TestClassBrowser patternBrowser = new TestClassBrowser() {
                @Override
                protected ClassFilter.ClassFilterWithScope getFilter() {
                    return TestClassFilter.create(SourceScope.wholeProject(getProject()), null);
                }

                @Override
                protected void onClassChoosen(PsiClass psiClass) {
                }
            };
            installAction(myPatternField, "JUnitConfigurationPattern", createClassAction(
                JUnitLocalize.junitConfigurationAddTestClassAction(),
                PlatformIconGroup.generalAdd(),
                () -> patternBrowser.chooseClass(null),
                className -> {
                    String text = StringUtil.notNullize(myPatternField.getValue());
                    myPatternField.setValue(text + (text.isEmpty() ? "" : "||") + className);
                }
            ));

            CategoryBrowser categoryBrowser = new CategoryBrowser();
            installAction(myCategoryField, "JUnitConfigurationCategory", createClassAction(
                JUnitLocalize.categoryInterfaceDialogTitle(),
                PlatformIconGroup.nodesClass(),
                () -> categoryBrowser.chooseClass(myCategoryField.getValue()),
                myCategoryField::setValue
            ));
        }

        private AnAction createClassAction(LocalizeValue text, Image icon, Supplier<@Nullable String> chooser, Consumer<String> consumer) {
            return new ChooserAction(myProject, text, icon, () -> {
                String className = chooser.get();
                if (className != null) {
                    consumer.accept(className);
                }
            });
        }

        @RequiredUIAccess
        private <C extends Component & HasSuffixComponent> void installAction(C field, String place, AnAction action) {
            ActionToolbar toolbar = myProject.getApplication()
                .getInstance(ActionToolbarFactory.class)
                .createActionToolbar(place, ActionGroup.newImmutableBuilder().add(action).build(), ActionToolbar.Style.INPLACE);
            toolbar.setTargetUIComponent(field);
            toolbar.updateActionsAsync();
            field.setSuffixComponent(toolbar.getUIComponent());
        }

        private class TestClassBrowser extends ClassBrowser {
            TestClassBrowser() {
                super(myProject, ExecutionLocalize.chooseTestClassDialogTitle().get());
            }

            @Override
            protected void onClassChoosen(PsiClass psiClass) {
                PsiPackage aPackage = JUnitUtil.getContainingPackage(psiClass);
                if (aPackage != null) {
                    myPackageField.setValue(aPackage.getQualifiedName());
                }
            }

            @Override
            protected PsiClass findClass(String className) {
                return myModuleHolder.findClass(className);
            }

            @Override
            protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
                Module module = myModuleHolder.getModule();
                if (module == null) {
                    throw NoFilterException.moduleDoesntExist(myModuleHolder);
                }
                return TestClassFilter.create(SourceScope.modulesWithDependencies(new Module[]{module}), module);
            }
        }

        private class CategoryBrowser extends ClassBrowser {
            CategoryBrowser() {
                super(myProject, JUnitLocalize.categoryInterfaceDialogTitle().get());
            }

            @Override
            protected PsiClass findClass(String className) {
                return myModuleHolder.findClass(className);
            }

            @Override
            protected ClassFilter.ClassFilterWithScope getFilter() {
                Module module = myModuleHolder.getModule();
                GlobalSearchScope scope = module == null
                    ? GlobalSearchScope.allScope(myProject)
                    : GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
                return new ClassFilter.ClassFilterWithScope() {
                    @Override
                    public GlobalSearchScope getScope() {
                        return scope;
                    }

                    @Override
                    public boolean isAccepted(PsiClass aClass) {
                        return true;
                    }
                };
            }
        }
    }

    private static final class ChooserAction extends DumbAwareAction implements AnActionWithSyncUpdate {
        private final Project myProject;
        private final Runnable myChooser;

        private ChooserAction(Project project, LocalizeValue text, Image icon, @RequiredUIAccess Runnable chooser) {
            super(text, LocalizeValue.empty(), icon);
            myProject = project;
            myChooser = chooser;
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(!myProject.isDefault());
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(AnActionEvent e) {
            myChooser.run();
        }
    }
}
