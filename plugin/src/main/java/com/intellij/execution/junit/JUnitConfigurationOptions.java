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

import com.dslplatform.json.CompiledJson;
import com.intellij.rt.execution.junit.RepeatCount;
import consulo.execution.configuration.RunConfigurationOptions;
import consulo.execution.test.TestSearchScope;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.AbstractCollection;
import consulo.util.xml.serializer.annotation.Attribute;
import consulo.util.xml.serializer.annotation.Property;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What a {@link JUnitConfiguration} runs: the test kind with its target, the search scope, the fork mode and the repeat mode.
 * String getters return an empty string for an unset value.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
@CompiledJson
public class JUnitConfigurationOptions extends RunConfigurationOptions {
    public static final String ALL_CHANGE_LISTS = "All";

    private String myTestObject = JUnitConfiguration.TEST_CLASS;
    private @Nullable String myPackageName;
    private @Nullable String myMainClassName;
    private @Nullable String myMethodName;
    private @Nullable String myDirName;
    private @Nullable String myCategoryName;
    private @Nullable String myChangeList;
    private String mySearchScope = TestSearchScope.MODULE_WITH_DEPENDENCIES.toString();
    private String myForkMode = JUnitConfiguration.FORK_NONE;
    private String myRepeatMode = RepeatCount.ONCE;
    private int myRepeatCount = 1;
    private List<String> myPatterns = new ArrayList<>();
    private List<String> myUniqueIds = new ArrayList<>();

    /**
     * One of the {@code TEST_*} or {@code BY_SOURCE_*} kinds of {@link JUnitConfiguration}.
     */
    @Attribute("testKind")
    public String getTestObject() {
        return myTestObject;
    }

    public void setTestObject(@Nullable String testObject) {
        myTestObject = StringUtil.isEmptyOrSpaces(testObject) ? JUnitConfiguration.TEST_CLASS : testObject;
    }

    @Attribute("package")
    public String getPackageName() {
        return StringUtil.notNullize(myPackageName);
    }

    public void setPackageName(@Nullable String packageName) {
        myPackageName = StringUtil.nullize(packageName, true);
    }

    /**
     * The runtime qualified name of the test class.
     */
    @Attribute("class")
    public String getMainClassName() {
        return StringUtil.notNullize(myMainClassName);
    }

    public void setMainClassName(@Nullable String mainClassName) {
        myMainClassName = StringUtil.nullize(mainClassName, true);
    }

    /**
     * The test method name, followed by its JVM parameter signature in parentheses for parameterized JUnit 5 methods.
     */
    @Attribute("method")
    public String getMethodName() {
        return StringUtil.notNullize(myMethodName);
    }

    public void setMethodName(@Nullable String methodName) {
        myMethodName = StringUtil.nullize(methodName, true);
    }

    @Attribute("directory")
    public String getDirName() {
        return StringUtil.notNullize(myDirName);
    }

    public void setDirName(@Nullable String dirName) {
        myDirName = StringUtil.nullize(dirName, true);
    }

    /**
     * The qualified name of the JUnit 4 category interface.
     */
    @Attribute("category")
    public String getCategoryName() {
        return StringUtil.notNullize(myCategoryName);
    }

    public void setCategoryName(@Nullable String categoryName) {
        myCategoryName = StringUtil.nullize(categoryName, true);
    }

    /**
     * The name of the change list whose changes select the tests, {@link #ALL_CHANGE_LISTS} for all of them.
     */
    @Attribute("changeList")
    public String getChangeList() {
        return myChangeList == null ? ALL_CHANGE_LISTS : myChangeList;
    }

    public void setChangeList(@Nullable String changeList) {
        myChangeList = ALL_CHANGE_LISTS.equals(changeList) ? null : StringUtil.nullize(changeList, true);
    }

    /**
     * The name of the {@link TestSearchScope} for package, pattern and category tests.
     */
    @Attribute("searchScope")
    public String getSearchScope() {
        return mySearchScope;
    }

    public void setSearchScope(@Nullable String searchScope) {
        mySearchScope = StringUtil.isEmptyOrSpaces(searchScope) ? TestSearchScope.MODULE_WITH_DEPENDENCIES.toString() : searchScope;
    }

    /**
     * One of {@link JUnitConfiguration#FORK_NONE}, {@link JUnitConfiguration#FORK_METHOD} or {@link JUnitConfiguration#FORK_KLASS}.
     */
    @Attribute("forkMode")
    public String getForkMode() {
        return myForkMode;
    }

    public void setForkMode(@Nullable String forkMode) {
        myForkMode = StringUtil.isEmptyOrSpaces(forkMode) ? JUnitConfiguration.FORK_NONE : forkMode;
    }

    /**
     * One of {@link RepeatCount#REPEAT_TYPES}.
     */
    @Attribute("repeatMode")
    public String getRepeatMode() {
        return myRepeatMode;
    }

    public void setRepeatMode(@Nullable String repeatMode) {
        myRepeatMode = StringUtil.isEmptyOrSpaces(repeatMode) ? RepeatCount.ONCE : repeatMode;
    }

    @Attribute("repeatCount")
    public int getRepeatCount() {
        return myRepeatCount;
    }

    public void setRepeatCount(int repeatCount) {
        myRepeatCount = repeatCount;
    }

    /**
     * Test classes or class patterns, optionally followed by {@code ,method}, of a pattern test.
     */
    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false, elementTag = "pattern", elementValueAttribute = "testClass")
    public List<String> getPatterns() {
        return myPatterns;
    }

    public void setPatterns(List<String> patterns) {
        myPatterns = new ArrayList<>(patterns);
    }

    /**
     * JUnit 5 unique ids of a unique id test.
     */
    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false, elementTag = "uniqueId", elementValueAttribute = "value")
    public List<String> getUniqueIds() {
        return myUniqueIds;
    }

    public void setUniqueIds(List<String> uniqueIds) {
        myUniqueIds = new ArrayList<>(uniqueIds);
    }
}
