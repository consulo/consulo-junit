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

package com.intellij.execution.junit2.configuration;

import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestClassFilter;
import com.intellij.java.execution.impl.MethodListDlg;
import com.intellij.java.execution.impl.testDiscovery.TestDiscoveryExtension;
import com.intellij.java.execution.impl.ui.ClassBrowser;
import com.intellij.java.execution.impl.ui.CommonJavaParametersLayout;
import com.intellij.java.execution.impl.ui.UnifiedConfigurationModuleSelector;
import com.intellij.java.execution.impl.ui.UnifiedJrePathEditor;
import com.intellij.java.execution.impl.ui.UnifiedShortenCommandLineModeCombo;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.ui.JavaReferenceEditorUtil;
import com.intellij.java.language.impl.ui.PackageChooser;
import com.intellij.java.language.impl.ui.PackageChooserFactory;
import com.intellij.java.language.psi.JavaCodeFragment;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.util.ClassFilter;
import com.intellij.rt.execution.junit.RepeatCount;
import consulo.application.concurrent.coroutine.ReadLock;
import consulo.configurable.ConfigurationException;
import consulo.document.Document;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.test.SourceScope;
import consulo.execution.test.TestSearchScope;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.java.execution.localize.JavaExecutionLocalize;
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
import consulo.ui.IntBox;
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
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.image.Image;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.ui.util.FormBuilder;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.util.concurrent.coroutine.CoroutineScope;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.LocalChangeList;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class JUnitConfigurable<T extends JUnitConfiguration> extends SettingsEditor<T> {
    private static final int[][] ourEnabledFields = {
        {JUnitConfigurationModel.ALL_IN_PACKAGE},
        {JUnitConfigurationModel.CLASS},
        {JUnitConfigurationModel.CLASS, JUnitConfigurationModel.METHOD},
        {JUnitConfigurationModel.PATTERN},
        {JUnitConfigurationModel.DIR},
        {JUnitConfigurationModel.CATEGORY},
        {},
        {JUnitConfigurationModel.CLASS, JUnitConfigurationModel.METHOD},
        {}
    };
    private static final List<String> FORK_MODE_ALL = List.of(
        JUnitConfiguration.FORK_NONE,
        JUnitConfiguration.FORK_METHOD,
        JUnitConfiguration.FORK_KLASS
    );
    private static final List<String> FORK_MODE = List.of(
        JUnitConfiguration.FORK_NONE,
        JUnitConfiguration.FORK_METHOD
    );
    private static final String ALL_CHANGE_LISTS = "All";

    private final Project myProject;

    private @Nullable JUnitParametersLayout myLayout;

    public JUnitConfigurable(Project project) {
        myProject = project;
    }

    @Override
    @RequiredUIAccess
    protected Component createUIComponent() {
        JUnitParametersLayout layout = new JUnitParametersLayout();
        myLayout = layout;
        layout.build();
        layout.initialize();
        return layout.getComponent();
    }

    @Override
    @RequiredUIAccess
    protected void resetEditorFrom(T configuration) {
        JUnitParametersLayout layout = myLayout;
        if (layout != null) {
            layout.reset(configuration);
        }
    }

    @Override
    @RequiredUIAccess
    protected void applyEditorTo(T configuration) throws ConfigurationException {
        JUnitParametersLayout layout = myLayout;
        if (layout != null) {
            layout.apply(configuration);
        }
    }

    @RequiredUIAccess
    public void onTypeChanged(int newType) {
        JUnitParametersLayout layout = myLayout;
        if (layout != null) {
            layout.onTypeChanged(newType);
        }
    }

    private static LocalizeValue getTypeName(int type) {
        return switch (type) {
            case JUnitConfigurationModel.ALL_IN_PACKAGE -> JUnitLocalize.junitConfigurationKindAllInPackage();
            case JUnitConfigurationModel.DIR -> JUnitLocalize.junitConfigurationKindAllInDirectory();
            case JUnitConfigurationModel.PATTERN -> JUnitLocalize.junitConfigurationKindByPattern();
            case JUnitConfigurationModel.CLASS -> JUnitLocalize.junitConfigurationKindClass();
            case JUnitConfigurationModel.METHOD -> JUnitLocalize.junitConfigurationKindMethod();
            case JUnitConfigurationModel.CATEGORY -> JUnitLocalize.junitConfigurationKindCategory();
            case JUnitConfigurationModel.UNIQUE_ID -> JUnitLocalize.junitConfigurationKindByUniqueId();
            case JUnitConfigurationModel.BY_SOURCE_POSITION -> JUnitLocalize.junitConfigurationKindBySourcePosition();
            case JUnitConfigurationModel.BY_SOURCE_CHANGES -> JUnitLocalize.junitConfigurationKindBySourceChanges();
            default -> LocalizeValue.empty();
        };
    }

    private static LocalizeValue getForkModeName(@Nullable String forkMode) {
        if (JUnitConfiguration.FORK_METHOD.equals(forkMode)) {
            return JUnitLocalize.junitConfigurationForkModeMethod();
        }
        if (JUnitConfiguration.FORK_KLASS.equals(forkMode)) {
            return JUnitLocalize.junitConfigurationForkModeClass();
        }
        return JUnitLocalize.junitConfigurationForkModeNone();
    }

    private static LocalizeValue getRepeatModeName(@Nullable String repeatMode) {
        if (RepeatCount.N.equals(repeatMode)) {
            return JUnitLocalize.junitConfigurationRepeatModeNTimes();
        }
        if (RepeatCount.UNTIL_FAILURE.equals(repeatMode)) {
            return JUnitLocalize.junitConfigurationRepeatModeUntilFailure();
        }
        if (RepeatCount.UNLIMITED.equals(repeatMode)) {
            return JUnitLocalize.junitConfigurationRepeatModeUntilStopped();
        }
        return JUnitLocalize.junitConfigurationRepeatModeOnce();
    }

    private record Row(Label label, Component field) {
        @RequiredUIAccess
        void setVisible(boolean visible) {
            label.setVisible(visible);
            field.setVisible(visible);
        }

        @RequiredUIAccess
        void setEnabled(boolean enabled) {
            label.setEnabled(enabled);
            field.setEnabled(enabled);
        }
    }

    private class JUnitParametersLayout extends CommonJavaParametersLayout<T> {
        private final JUnitConfigurationModel myModel = new JUnitConfigurationModel();
        private final UnifiedConfigurationModuleSelector myModuleSelector;
        private final UnifiedJrePathEditor myJrePathEditor;
        private final UnifiedShortenCommandLineModeCombo myShortenCommandLineModeCombo;

        private final ComboBox<Integer> myTypeChooser;
        private final EditorBox myPackageField;
        private final FileChooserTextBoxBuilder.Controller myDirField;
        private final TextBox myPatternField;
        private final EditorBox myClassField;
        private final EditorBox myMethodField;
        private final EditorBox myCategoryField;
        private final TextBox myUniqueIdField;
        private final ComboBox<String> myChangeListBox;
        private final RadioGroup<TestSearchScope> myScopeGroup;
        private final VerticalLayout myScopesLayout;

        private final MutableFlatDataModel<String> myForkModel = FlatDataModel.of(new ArrayList<>(FORK_MODE_ALL));
        private final ComboBox<String> myForkBox;
        private final ComboBox<String> myRepeatBox;
        private final IntBox myRepeatCountBox;

        private final Row[] myTestLocations = new Row[6];
        private @Nullable Row myUniqueIdRow;
        private @Nullable Row myChangeListRow;
        private @Nullable Row myScopesRow;

        @RequiredUIAccess
        private JUnitParametersLayout() {
            super(myProject.getApplication().getInstance(DialogService.class));

            myModuleSelector = new UnifiedConfigurationModuleSelector(myProject, JavaExecutionLocalize.runConfigurationModuleNone());
            myModuleSelector.addValueListener(this::setModuleContext);
            myJrePathEditor = new UnifiedJrePathEditor(JUnitConfigurable.this);
            myShortenCommandLineModeCombo = new UnifiedShortenCommandLineModeCombo(myProject, myJrePathEditor, myModuleSelector);

            List<Integer> types = new ArrayList<>(List.of(
                JUnitConfigurationModel.ALL_IN_PACKAGE,
                JUnitConfigurationModel.DIR,
                JUnitConfigurationModel.PATTERN,
                JUnitConfigurationModel.CLASS,
                JUnitConfigurationModel.METHOD,
                JUnitConfigurationModel.CATEGORY,
                JUnitConfigurationModel.UNIQUE_ID
            ));
            if (TestDiscoveryExtension.TESTDISCOVERY_ENABLED) {
                types.add(JUnitConfigurationModel.BY_SOURCE_POSITION);
                types.add(JUnitConfigurationModel.BY_SOURCE_CHANGES);
            }
            myTypeChooser = ComboBox.create(types);
            myTypeChooser.setTextRenderer(type -> type == null ? LocalizeValue.empty() : getTypeName(type));

            myPackageField = createReferenceField(false, JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);

            FileChooserDescriptor dirDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
            dirDescriptor.setHideIgnored(false);
            myDirField = FileChooserTextBoxBuilder.create(myProject).fileChooserDescriptor(dirDescriptor).build();

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
            changeLists.add(ALL_CHANGE_LISTS);
            for (LocalChangeList changeList : ChangeListManager.getInstance(myProject).getChangeLists()) {
                changeLists.add(changeList.getName());
            }
            myChangeListBox = ComboBox.create(changeLists);
            myChangeListBox.setTextRenderer(name -> ALL_CHANGE_LISTS.equals(name)
                ? JUnitLocalize.junitConfigurationChangeListAll()
                : LocalizeValue.ofNullable(name));

            myScopeGroup = RadioGroup.create();
            myScopesLayout = VerticalLayout.create(Space.NONE);
            myScopesLayout.add(myScopeGroup.newButton(ExecutionLocalize.junitConfigurationInWholeProjectRadio(), TestSearchScope.WHOLE_PROJECT));
            myScopesLayout.add(myScopeGroup.newButton(ExecutionLocalize.junitConfigurationInSingleModuleRadio(), TestSearchScope.SINGLE_MODULE));
            myScopesLayout.add(myScopeGroup.newButton(
                ExecutionLocalize.junitConfigurationAcrossModuleDependenciesRadio(),
                TestSearchScope.MODULE_WITH_DEPENDENCIES
            ));
            myScopeGroup.setValue(TestSearchScope.WHOLE_PROJECT, false);

            myForkBox = ComboBox.create(myForkModel);
            myForkBox.setTextRenderer(JUnitConfigurable::getForkModeName);

            myRepeatBox = ComboBox.create(RepeatCount.REPEAT_TYPES);
            myRepeatBox.setTextRenderer(JUnitConfigurable::getRepeatModeName);
            myRepeatBox.setValue(RepeatCount.ONCE, false);

            myRepeatCountBox = IntBox.create(1).withRange(1, Integer.MAX_VALUE);
            myRepeatCountBox.setEnabled(false);

            if (!myProject.getApplication().isUnifiedApplication()) {
                installChooserActions(classBrowser);
            }
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
            return myModuleSelector.findClass(className);
        }

        @RequiredUIAccess
        private void installChooserActions(TestClassBrowser classBrowser) {
            installAction(myPackageField, "JUnitConfigurablePackage", DumbAwareAction.create(
                JUnitLocalize.junitConfigurationChoosePackageAction(),
                LocalizeValue.empty(),
                PlatformIconGroup.nodesPackage(),
                e -> {
                    PackageChooser chooser = myProject.getInstance(PackageChooserFactory.class).create();
                    List<PsiJavaPackage> packages = chooser.showAndSelect();
                    PsiPackage aPackage = packages == null || packages.isEmpty() ? null : packages.getFirst();
                    if (aPackage != null) {
                        myPackageField.setValue(aPackage.getQualifiedName());
                    }
                }
            ));

            installAction(myClassField, "JUnitConfigurableClass", createClassAction(
                ExecutionLocalize.chooseTestClassDialogTitle(),
                PlatformIconGroup.nodesClass(),
                () -> classBrowser.chooseClass(myClassField.getValue()),
                myClassField::setValue
            ));

            installAction(myMethodField, "JUnitConfigurableMethod", DumbAwareAction.create(
                JUnitLocalize.junitConfigurationChooseMethodAction(),
                LocalizeValue.empty(),
                PlatformIconGroup.nodesMethod(),
                e -> {
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
            installAction(myPatternField, "JUnitConfigurablePattern", createClassAction(
                JUnitLocalize.junitConfigurationAddTestClassAction(),
                PlatformIconGroup.generalAdd(),
                () -> patternBrowser.chooseClass(null),
                className -> {
                    String text = StringUtil.notNullize(myPatternField.getValue());
                    myPatternField.setValue(text + (text.isEmpty() ? "" : "||") + className);
                }
            ));

            CategoryBrowser categoryBrowser = new CategoryBrowser();
            installAction(myCategoryField, "JUnitConfigurableCategory", createClassAction(
                JUnitLocalize.categoryInterfaceDialogTitle(),
                PlatformIconGroup.nodesClass(),
                () -> categoryBrowser.chooseClass(myCategoryField.getValue()),
                myCategoryField::setValue
            ));
        }

        private AnAction createClassAction(
            LocalizeValue text,
            Image icon,
            Supplier<@Nullable String> chooser,
            Consumer<String> consumer
        ) {
            return DumbAwareAction.create(text, LocalizeValue.empty(), icon, e -> {
                String className = chooser.get();
                if (className != null) {
                    consumer.accept(className);
                }
            });
        }

        @RequiredUIAccess
        private <C extends Component & HasSuffixComponent> void installAction(C field, String place, AnAction action) {
            ActionToolbar toolbar = ActionToolbarFactory.getInstance()
                .createActionToolbar(place, ActionGroup.newImmutableBuilder().add(action).build(), ActionToolbar.Style.INPLACE);
            toolbar.setTargetUIComponent(field);
            toolbar.updateActionsAsync();
            field.setSuffixComponent(toolbar.getUIComponent());
        }

        @RequiredUIAccess
        private Row addRow(FormBuilder builder, LocalizeValue text, Component field) {
            Label label = Label.create(text);
            builder.addLabeled(label, field);
            return new Row(label, field);
        }

        @Override
        @RequiredUIAccess
        protected void addBefore(FormBuilder builder) {
            builder.addLabeled(ExecutionLocalize.junitConfigurationConfigureJunitTestKindLabel(), myTypeChooser);

            myTestLocations[JUnitConfigurationModel.ALL_IN_PACKAGE] =
                addRow(builder, ExecutionLocalize.junitConfigurationPackageLabel(), myPackageField);
            myTestLocations[JUnitConfigurationModel.DIR] =
                addRow(builder, JUnitLocalize.junitConfigurationDirectoryLabel(), myDirField.getComponent());
            myTestLocations[JUnitConfigurationModel.PATTERN] =
                addRow(builder, JUnitLocalize.junitConfigurationPatternLabel(), myPatternField);
            myTestLocations[JUnitConfigurationModel.CLASS] =
                addRow(builder, ExecutionLocalize.junitConfigurationClassLabel(), myClassField);
            myTestLocations[JUnitConfigurationModel.METHOD] =
                addRow(builder, ExecutionLocalize.junitConfigurationMethodLabel(), myMethodField);
            myTestLocations[JUnitConfigurationModel.CATEGORY] =
                addRow(builder, JUnitLocalize.junitConfigurationCategoryLabel(), myCategoryField);
            myUniqueIdRow = addRow(builder, JUnitLocalize.junitConfigurationUniqueIdLabel(), myUniqueIdField);
            myChangeListRow = addRow(builder, JUnitLocalize.junitConfigurationChangeListLabel(), myChangeListBox);
            myScopesRow = addRow(builder, ExecutionLocalize.junitConfigurationSearchForTestsLabel(), myScopesLayout);

            super.addBefore(builder);
        }

        @Override
        @RequiredUIAccess
        protected void addAfter(FormBuilder builder) {
            builder.addLabeled(
                JavaExecutionLocalize.applicationConfigurationUseClasspathAndJdkOfModuleLabel(),
                myModuleSelector.getComponent()
            );
            builder.addLabeled(JavaExecutionLocalize.runConfigurationJreLabel(), myJrePathEditor.getComponent());
            builder.addLabeled(
                JavaExecutionLocalize.applicationConfigurationShortenCommandLineLabel(),
                myShortenCommandLineModeCombo.getComponent()
            );
            builder.addLabeled(JUnitLocalize.junitConfigurationForkModeLabel(), myForkBox);
            builder.addLabeled(JUnitLocalize.junitConfigurationRepeatLabel(), myRepeatBox);
            builder.addLabeled(JUnitLocalize.junitConfigurationRepeatCountLabel(), myRepeatCountBox);
        }

        @RequiredUIAccess
        private void initialize() {
            myModel.setJUnitField(JUnitConfigurationModel.ALL_IN_PACKAGE, myPackageField);
            myModel.setJUnitField(JUnitConfigurationModel.CLASS, myClassField);
            myModel.setJUnitField(JUnitConfigurationModel.METHOD, myMethodField);
            myModel.setJUnitField(JUnitConfigurationModel.PATTERN, myPatternField);
            myModel.setJUnitField(JUnitConfigurationModel.DIR, myDirField.getComponent());
            myModel.setJUnitField(JUnitConfigurationModel.CATEGORY, myCategoryField);
            myModel.setListener(JUnitConfigurable.this);

            myTypeChooser.addValueListener(event -> {
                Integer type = event.getValue();
                if (type != null) {
                    myModel.setType(type);
                }
            });
            myRepeatBox.addValueListener(event -> {
                myRepeatCountBox.setEnabled(RepeatCount.N.equals(event.getValue()));
                if (myModel.getType() == JUnitConfigurationModel.CLASS) {
                    setForkModes(getForkModesBasedOnRepeat(), myForkBox.getValue());
                }
            });
            myScopeGroup.addValueListener(scope -> onScopeChanged());

            myModel.setType(JUnitConfigurationModel.CLASS);
        }

        @RequiredUIAccess
        private void onTypeChanged(int newType) {
            myTypeChooser.setValue(newType, false);

            int[] enabledFields = newType < ourEnabledFields.length ? ourEnabledFields[newType] : new int[0];
            for (int i = 0; i < myTestLocations.length; i++) {
                myTestLocations[i].setEnabled(contains(enabledFields, i));
            }

            if (isModuleOptional(newType)) {
                onScopeChanged();
            }
            else {
                myModuleSelector.getComponent().setEnabled(true);
            }

            changePanel(newType);
        }

        private boolean contains(int[] values, int value) {
            for (int each : values) {
                if (each == value) {
                    return true;
                }
            }
            return false;
        }

        private boolean isModuleOptional(int type) {
            return type == JUnitConfigurationModel.ALL_IN_PACKAGE
                || type == JUnitConfigurationModel.PATTERN
                || type == JUnitConfigurationModel.CATEGORY
                || type == JUnitConfigurationModel.UNIQUE_ID;
        }

        @RequiredUIAccess
        private void onScopeChanged() {
            boolean wholeProject = isModuleOptional(myModel.getType()) && myScopeGroup.getValue() == TestSearchScope.WHOLE_PROJECT;
            myModuleSelector.getComponent().setEnabled(!wholeProject);
            if (wholeProject) {
                myModuleSelector.setSelectedModule(null);
            }
        }

        @RequiredUIAccess
        private void changePanel(int type) {
            String forkMode = myForkBox.getValue();
            if (forkMode == null) {
                forkMode = JUnitConfiguration.FORK_NONE;
            }

            boolean method = type == JUnitConfigurationModel.METHOD || type == JUnitConfigurationModel.BY_SOURCE_POSITION;

            myTestLocations[JUnitConfigurationModel.ALL_IN_PACKAGE].setVisible(type == JUnitConfigurationModel.ALL_IN_PACKAGE);
            myTestLocations[JUnitConfigurationModel.DIR].setVisible(type == JUnitConfigurationModel.DIR);
            myTestLocations[JUnitConfigurationModel.PATTERN].setVisible(type == JUnitConfigurationModel.PATTERN);
            myTestLocations[JUnitConfigurationModel.CLASS].setVisible(type == JUnitConfigurationModel.CLASS || method);
            myTestLocations[JUnitConfigurationModel.METHOD].setVisible(method || type == JUnitConfigurationModel.PATTERN);
            myTestLocations[JUnitConfigurationModel.CATEGORY].setVisible(type == JUnitConfigurationModel.CATEGORY);
            setRowVisible(myUniqueIdRow, type == JUnitConfigurationModel.UNIQUE_ID);
            setRowVisible(myChangeListRow, type == JUnitConfigurationModel.BY_SOURCE_CHANGES);
            setRowVisible(
                myScopesRow,
                type == JUnitConfigurationModel.ALL_IN_PACKAGE
                    || type == JUnitConfigurationModel.PATTERN
                    || type == JUnitConfigurationModel.CATEGORY
            );

            if (method) {
                myForkBox.setEnabled(false);
                myForkBox.setValue(JUnitConfiguration.FORK_NONE);
            }
            else if (type == JUnitConfigurationModel.CLASS) {
                myForkBox.setEnabled(true);
                setForkModes(
                    getForkModesBasedOnRepeat(),
                    !JUnitConfiguration.FORK_KLASS.equals(forkMode) ? forkMode : JUnitConfiguration.FORK_METHOD
                );
            }
            else {
                myForkBox.setEnabled(true);
                setForkModes(FORK_MODE_ALL, forkMode);
            }
        }

        @RequiredUIAccess
        private void setRowVisible(@Nullable Row row, boolean visible) {
            if (row != null) {
                row.setVisible(visible);
            }
        }

        private List<String> getForkModesBasedOnRepeat() {
            return RepeatCount.ONCE.equals(myRepeatBox.getValue()) ? FORK_MODE : FORK_MODE_ALL;
        }

        @RequiredUIAccess
        private void setForkModes(List<String> modes, @Nullable String selected) {
            if (!modes.equals(toList(myForkModel))) {
                myForkModel.replaceAll(modes);
            }
            myForkBox.setValue(selected != null && modes.contains(selected) ? selected : JUnitConfiguration.FORK_NONE);
        }

        private List<String> toList(MutableFlatDataModel<String> model) {
            List<String> items = new ArrayList<>(model.getSize());
            for (String item : model) {
                items.add(item);
            }
            return items;
        }

        @Override
        @RequiredUIAccess
        public void apply(T configuration) {
            super.apply(configuration);

            configuration.setRepeatMode(myRepeatBox.getValue());
            Integer repeatCount = myRepeatCountBox.getValue();
            configuration.setRepeatCount(repeatCount == null ? 1 : repeatCount);

            myModel.apply(configuration);

            JUnitConfiguration.Data data = configuration.getPersistentData();
            data.setUniqueIds(StringUtil.notNullize(myUniqueIdField.getValue()).split(" "));
            data.setChangeList(myChangeListBox.getValue());

            myModuleSelector.applyTo(configuration);

            TestSearchScope scope = myScopeGroup.getValue();
            data.setScope(scope == null ? TestSearchScope.WHOLE_PROJECT : scope);

            configuration.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
            configuration.setAlternativeJrePathEnabled(myJrePathEditor.isAlternativeJreSelected());
            configuration.setForkMode(myForkBox.getValue());
            configuration.setShortenCommandLine(myShortenCommandLineModeCombo.getSelectedItem());
        }

        @Override
        @RequiredUIAccess
        public void reset(T configuration) {
            super.reset(configuration);

            int count = configuration.getRepeatCount();
            myRepeatCountBox.setValue(Math.max(1, count));
            myRepeatBox.setValue(configuration.getRepeatMode());
            myRepeatCountBox.setEnabled(RepeatCount.N.equals(configuration.getRepeatMode()));

            myModuleSelector.reset(configuration);
            setModuleContext(myModuleSelector.getModule());

            JUnitConfiguration.Data data = configuration.getPersistentData();
            TestSearchScope scope = data.getScope();
            myScopeGroup.setValue(
                scope == TestSearchScope.SINGLE_MODULE || scope == TestSearchScope.MODULE_WITH_DEPENDENCIES
                    ? scope
                    : TestSearchScope.WHOLE_PROJECT,
                false
            );

            myModel.reset(configuration);

            String changeList = data.getChangeList();
            myChangeListBox.setValue(changeList == null ? ALL_CHANGE_LISTS : changeList);
            String[] ids = data.getUniqueIds();
            myUniqueIdField.setValue(ids != null ? StringUtil.join(ids, " ") : "");

            myJrePathEditor.setByName(configuration.isAlternativeJrePathEnabled() ? configuration.getAlternativeJrePath() : null);
            setForkModes(toList(myForkModel), configuration.getForkMode());
            myShortenCommandLineModeCombo.setSelectedItem(configuration.getShortenCommandLine());

            onScopeChanged();
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
                return myModuleSelector.findClass(className);
            }

            @Override
            protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
                Module module = myModuleSelector.getModule();
                if (module == null) {
                    throw NoFilterException.moduleDoesntExist(myModuleSelector);
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
                return myModuleSelector.findClass(className);
            }

            @Override
            protected ClassFilter.ClassFilterWithScope getFilter() {
                Module module = myModuleSelector.getModule();
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
}
