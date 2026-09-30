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
import com.intellij.java.execution.JavaExecutionUtil;
import consulo.ui.ValueComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

// Author: dyoma

public class JUnitConfigurationModel {
    public static final int ALL_IN_PACKAGE = 0;
    public static final int CLASS = 1;
    public static final int METHOD = 2;
    public static final int PATTERN = 3;
    public static final int DIR = 4;
    public static final int CATEGORY = 5;
    public static final int UNIQUE_ID = 6;
    public static final int BY_SOURCE_POSITION = 7;
    public static final int BY_SOURCE_CHANGES = 8;

    private static final List<String> ourTestObjects;

    static {
        ourTestObjects = Arrays.asList(JUnitConfiguration.TEST_PACKAGE, JUnitConfiguration.TEST_CLASS, JUnitConfiguration.TEST_METHOD, JUnitConfiguration.TEST_PATTERN, JUnitConfiguration
            .TEST_DIRECTORY, JUnitConfiguration.TEST_CATEGORY, JUnitConfiguration.TEST_UNIQUE_ID, JUnitConfiguration.BY_SOURCE_POSITION, JUnitConfiguration.BY_SOURCE_CHANGES);
    }

    private JUnitConfigurable<?> myListener;
    private int myType = -1;
    @SuppressWarnings("unchecked")
    private final ValueComponent<String>[] myJUnitFields = new ValueComponent[6];

    public boolean setType(int type) {
        if (type == myType) {
            return false;
        }
        if (type < 0 || type >= ourTestObjects.size()) {
            type = CLASS;
        }
        myType = type;
        fireTypeChanged(type);
        return true;
    }

    public int getType() {
        return myType;
    }

    private void fireTypeChanged(int newType) {
        myListener.onTypeChanged(newType);
    }

    public void setListener(JUnitConfigurable<?> listener) {
        myListener = listener;
    }

    public void setJUnitField(int i, ValueComponent<String> field) {
        myJUnitFields[i] = field;
    }

    public void apply(JUnitConfiguration configuration) {
        boolean shouldUpdateName = configuration.isGeneratedName();
        applyTo(configuration.getPersistentData());
        if (shouldUpdateName && !JavaExecutionUtil.isNewName(configuration.getName())) {
            configuration.setGeneratedName();
        }
    }

    private void applyTo(JUnitConfiguration.Data data) {
        String testObject = getTestObject();
        data.TEST_OBJECT = testObject;
        if (testObject != JUnitConfiguration.TEST_PACKAGE && testObject != JUnitConfiguration.TEST_PATTERN && testObject != JUnitConfiguration.TEST_DIRECTORY && testObject != JUnitConfiguration
            .TEST_CATEGORY && testObject != JUnitConfiguration.BY_SOURCE_CHANGES) {
            data.METHOD_NAME = getJUnitTextValue(METHOD);

            String className = getJUnitTextValue(CLASS);
            if (!className.equals(toPresentableClassName(data.getMainClassName()))) {
                data.MAIN_CLASS_NAME = className;
                data.PACKAGE_NAME = StringUtil.getPackageName(className);
            }
        }
        else if (testObject != JUnitConfiguration.BY_SOURCE_CHANGES) {
            if (testObject == JUnitConfiguration.TEST_PACKAGE) {
                data.PACKAGE_NAME = getJUnitTextValue(ALL_IN_PACKAGE);
            }
            else if (testObject == JUnitConfiguration.TEST_DIRECTORY) {
                data.setDirName(getJUnitTextValue(DIR));
            }
            else if (testObject == JUnitConfiguration.TEST_CATEGORY) {
                data.setCategoryName(getJUnitTextValue(CATEGORY));
            }
            else {
                LinkedHashSet<String> set = new LinkedHashSet<>();
                String[] patterns = getJUnitTextValue(PATTERN).split("\\|\\|");
                for (String pattern : patterns) {
                    if (pattern.length() > 0) {
                        set.add(pattern);
                    }
                }
                data.setPatterns(set);
            }
            data.MAIN_CLASS_NAME = "";
            data.METHOD_NAME = "";
        }
    }

    private static String toPresentableClassName(@Nullable String className) {
        return className == null ? "" : className.replace('$', '.');
    }

    private String getTestObject() {
        return ourTestObjects.get(myType);
    }

    private String getJUnitTextValue(int index) {
        return StringUtil.notNullize(myJUnitFields[index].getValue());
    }

    @RequiredUIAccess
    public void reset(JUnitConfiguration configuration) {
        JUnitConfiguration.Data data = configuration.getPersistentData();
        setTestType(data.TEST_OBJECT);
        setJUnitTextValue(ALL_IN_PACKAGE, data.getPackageName());
        setJUnitTextValue(CLASS, toPresentableClassName(data.getMainClassName()));
        setJUnitTextValue(METHOD, data.getMethodNameWithSignature());
        setJUnitTextValue(PATTERN, data.getPatternPresentation());
        setJUnitTextValue(DIR, data.getDirName());
        setJUnitTextValue(CATEGORY, data.getCategory());
    }

    @RequiredUIAccess
    private void setJUnitTextValue(int index, @Nullable String text) {
        myJUnitFields[index].setValue(StringUtil.notNullize(text));
    }

    private void setTestType(String testObject) {
        setType(ourTestObjects.indexOf(testObject));
    }
}
