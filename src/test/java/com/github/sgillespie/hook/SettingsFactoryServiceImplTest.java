package com.github.sgillespie.hook;

import com.atlassian.stash.setting.Settings;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.github.sgillespie.hook.SettingsFactoryServiceImpl.*;
import static java.util.Arrays.asList;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

/**
 * Created by sgillespie on 10/10/14.
 */
public class SettingsFactoryServiceImplTest {
    private SettingsFactoryService settingsFactoryServiceImpl;

    @Mock
    private Settings settings;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        settingsFactoryServiceImpl = new SettingsFactoryServiceImpl();
    }

    @Test
    public void getPathPatternsEmptyShouldReturnEmptyList() {
        when(settings.getString(KEY_PATH_PATTERNS)).thenReturn("");
        assertThat(settingsFactoryServiceImpl.getPathPatterns(settings), empty());
    }

    @Test
    public void getPathPatternsWithOnePatternShouldReturnSingleton() {
        when(settings.getString(KEY_PATH_PATTERNS)).thenReturn("pattern1");
        assertThat(settingsFactoryServiceImpl.getPathPatterns(settings), equalTo(asList("pattern1")));
    }

    @Test
    public void getPathPatternsWithMultiplePatternsShouldReturnAllPatterns() {
        when(settings.getString(KEY_PATH_PATTERNS)).thenReturn("pattern1 pattern2");
        assertThat(settingsFactoryServiceImpl.getPathPatterns(settings), equalTo((asList("pattern1", "pattern2"))));
    }

    @Test
    public void getPathPatternsWithNewlineShouldReturnAllPatterns() {
        when(settings.getString(KEY_PATH_PATTERNS)).thenReturn("pattern1\n pattern2");
        assertThat(settingsFactoryServiceImpl.getPathPatterns(settings), equalTo((asList("pattern1", "pattern2"))));
    }

    @Test
    public void getFilterTypeWithValidTypeShouldReturnType() {
        when(settings.getString(eq(KEY_FILTER_TYPE), eq("ALL"))).thenReturn("ALL");
        assertThat(settingsFactoryServiceImpl.getFilterType(settings), is(FilterType.ALL));
    }

    @Test
    public void getBranchFiltersEmptyShouldReturnEmptyList() {
        when(settings.getString(KEY_BRANCH_FILTERS)).thenReturn("");
        assertThat(settingsFactoryServiceImpl.getBranchFilters(settings), empty());
    }

    @Test
    public void getBranchFiltersWithOnePatternReturnsSingleton() {
        when(settings.getString(KEY_BRANCH_FILTERS)).thenReturn("pattern1");
        assertThat(settingsFactoryServiceImpl.getBranchFilters(settings), equalTo(asList("pattern1")));
    }

    @Test
    public void getBranchFiltersWithMultiplePatternsShouldReturnAllPatterns() {
        when(settings.getString(KEY_BRANCH_FILTERS)).thenReturn("pattern1 pattern2");
        assertThat(settingsFactoryServiceImpl.getBranchFilters(settings), equalTo(asList("pattern1", "pattern2")));
    }

    @Test
    public void getExcludedUsersEmptyShouldReturnEmptyList() {
        when(settings.getString(KEY_EXCLUDED_USERS)).thenReturn("");
        assertThat(settingsFactoryServiceImpl.getExcludedUsers(settings), empty());
    }

    @Test
    public void getExcludedUsersWithOnePatternReturnsSingleton() {
        when(settings.getString(KEY_EXCLUDED_USERS)).thenReturn("pattern1");
        assertThat(settingsFactoryServiceImpl.getExcludedUsers(settings), equalTo(asList("pattern1")));
    }

    @Test
    public void getExcludedUsersWithMultiplePatternShouldReturnAllPatterns() {
        when(settings.getString(KEY_EXCLUDED_USERS)).thenReturn("pattern1 pattern2");
        assertThat(settingsFactoryServiceImpl.getExcludedUsers(settings), equalTo(asList("pattern1", "pattern2")));
    }
}
