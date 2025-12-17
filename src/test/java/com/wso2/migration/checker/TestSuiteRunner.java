package com.wso2.migration. checker;

import org.junit. platform.suite.api.*;

/**
 * Test suite runner for executing specific test suites.
 */
public class TestSuiteRunner {

    /**
     * Runs all infrastructure tests (Suite A).
     */
    @Suite
    @SuiteDisplayName("Suite A:  Infrastructure Tests")
    @SelectPackages("com.wso2.migration.checker. suite")
    @IncludeTags(TestConstants.TAG_INFRASTRUCTURE)
    public static class SuiteARunner {}

    /**
     * Runs all MySQL functional tests (Suite B).
     */
    @Suite
    @SuiteDisplayName("Suite B:  MySQL Functional Tests")
    @SelectPackages("com.wso2.migration.checker.suite")
    @IncludeTags(TestConstants.TAG_MYSQL_FUNCTIONAL)
    public static class SuiteBRunner {}

    /**
     * Runs smoke tests only.
     */
    @Suite
    @SuiteDisplayName("Smoke Tests")
    @SelectPackages("com.wso2.migration.checker.suite")
    @IncludeTags(TestConstants.TAG_SMOKE)
    public static class SmokeTestRunner {}

    /**
     * Runs all test suites.
     */
    @Suite
    @SuiteDisplayName("All Test Suites")
    @SelectPackages("com.wso2.migration.checker.suite")
    @IncludeTags({TestConstants.TAG_INFRASTRUCTURE, TestConstants.TAG_MYSQL_FUNCTIONAL})
    public static class AllTestsRunner {}

    /**
     * Runs table-related tests only.
     */
    @Suite
    @SuiteDisplayName("Table Tests Only")
    @SelectPackages("com.wso2.migration. checker.suite")
    @IncludeTags(TestConstants. TAG_TABLES)
    public static class TableTestsRunner {}

    /**
     * Runs constraint-related tests only.
     */
    @Suite
    @SuiteDisplayName("Constraint Tests Only")
    @SelectPackages("com.wso2.migration.checker.suite")
    @IncludeTags(TestConstants.TAG_CONSTRAINTS)
    public static class ConstraintTestsRunner {}

    /**
     * Runs stored routine tests only.
     */
    @Suite
    @SuiteDisplayName("Routine Tests Only")
    @SelectPackages("com.wso2.migration.checker.suite")
    @IncludeTags(TestConstants.TAG_ROUTINES)
    public static class RoutineTestsRunner {}
}