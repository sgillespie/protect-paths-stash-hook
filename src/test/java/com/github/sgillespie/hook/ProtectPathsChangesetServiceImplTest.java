package com.github.sgillespie.hook;

import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.content.*;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.user.Permission;
import com.atlassian.stash.user.PermissionService;
import com.atlassian.stash.user.StashAuthenticationContext;
import com.atlassian.stash.user.StashUser;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageImpl;
import com.atlassian.stash.util.PageRequest;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ProtectPathsChangesetServiceImplTest {
    private ProtectPathsChangesetService protectPathsChangesetService;
    private String fromHash;
    private String toHash;
    private String refId;

    @Mock
    private CommitService commitService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private StashAuthenticationContext stashAuthenticationContext;
    @Mock
    SettingsFactoryService settingsFactoryService;
    @Mock
    Repository repository;
    @Mock
    Settings settings;
    @Mock
    private StashUser user;
    @Mock
    private Change change;


    @Before
    public void setUp() {
        initMocks(this);

        // Mock some stuff
        when(user.getName()).thenReturn("user");
        when(stashAuthenticationContext.getCurrentUser()).thenReturn(user);
        when(settingsFactoryService.getFilterType((Settings) anyObject())).thenReturn(FilterType.ALL);

        Changeset changeSet = mock(Changeset.class);
        when(changeSet.getId()).thenReturn("CHANGESET-ID");
        Page<Changeset> changeSets = new PageImpl<>(ProtectPathsChangesetServiceImpl.PAGE_REQUEST,
                1, asList(changeSet), true);
        when(commitService.getChangesetsBetween((ChangesetsBetweenRequest) anyObject(),
                (PageRequest) anyObject())).thenReturn(changeSets);

        DetailedChangeset detailedChangeset = mock(DetailedChangeset.class);
        Page<DetailedChangeset> detailedChangesets = new PageImpl<>(
                ProtectPathsRepositoryHook.PAGE_REQUEST, 1, asList(detailedChangeset), true);
        when(commitService.getDetailedChangesets((DetailedChangesetsRequest) anyObject(),
                (PageRequest) anyObject())).thenReturn(detailedChangesets);

        when(change.getPath()).thenReturn(new SimplePath("x/y/z"));
        Page<Change> changes = new PageImpl<>(ProtectPathsRepositoryHook.PAGE_REQUEST, 1, asList(change), true);
        when((Page<Change>) detailedChangeset.getChanges()).thenReturn(changes);

        fromHash = "FROM-HASH";
        toHash = "TO-HASH";
        refId = "refs/heads/master";
        protectPathsChangesetService = new ProtectPathsChangesetServiceImpl(
                commitService, permissionService, stashAuthenticationContext, settingsFactoryService);
    }

    @Test
    public void adminUserWithRestrictedPathsChangeSetShouldBeValid() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));

        assertThat(isValidChangeset(true, "x/y/z"), is(TRUE));
        assertThat(isValidChangeset(true, "z/y/x"), is(TRUE));
    }

    @Test
    public void nonAdminUserWithNonRestrictedPathsChangeSetShouldBeValid() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));

        assertThat(isValidChangeset(false, "a/b/c"), is(TRUE));
    }

    @Test
    public void nonAdminUserWithRestrictedPathsChangeSetShouldBeInvalid() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));

        assertThat(isValidChangeset(false, "x/y/z"), is(FALSE));
        assertThat(isValidChangeset(false, "z/y/x"), is(FALSE));
    }

    @Test
    public void nonAdminUserWithRestrictedPathsAndAllFilterChangeSetShouldBeInvalid() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.ALL);

        assertThat(isValidChangeset(false, "x/y/z"), is(FALSE));
        assertThat(isValidChangeset(false, "z/y/x"), is(FALSE));
    }

    @Test
    public void nonAdminUserWithRestrictedPathsAndIncludedBranchChangeSetShouldBeInvalid() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.INCLUDE);
        when(settingsFactoryService.getBranchFilters((Settings)anyObject()))
                .thenReturn(asList("branch-1", "branch-2"));
        refId = "refs/heads/branch-2";

        assertThat(isValidChangeset(false, "x/y/z"), is(FALSE));
        assertThat(isValidChangeset(false, "z/y/x"), is(FALSE));
    }

    @Test
    public void nonAdminUserWithRestrictedPathsAndUnincludedBranchChangeSetShouldBeValid() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.INCLUDE);
        when(settingsFactoryService.getBranchFilters((Settings)anyObject()))
                .thenReturn(asList("branch-1", "branch-2"));
        refId = "refs/heads/branch-3";

        assertThat(isValidChangeset(false, "x/y/z"), is(TRUE));
        assertThat(isValidChangeset(false, "z/y/x"), is(TRUE));
    }

    @Test
    public void nonAdminUserWithRestrictedPathsAndExcludedBranchChangeSetShouldBeValid() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.EXCLUDE);
        when(settingsFactoryService.getBranchFilters((Settings)anyObject()))
                .thenReturn(asList("branch-1", "branch-2"));
        refId = "refs/heads/branch-1";

        assertThat(isValidChangeset(false, "x/y/z"), is(TRUE));
        assertThat(isValidChangeset(false, "z/y/x"), is(TRUE));
    }

    @Test
    public void nonAdminUserWithRestrictedPathsAndUnexcludedBranchChangeSetShouldBeInvalid() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.EXCLUDE);
        when(settingsFactoryService.getBranchFilters((Settings)anyObject()))
                .thenReturn(asList("branch-1", "branch-2"));
        refId = "refs/heads/branch-3";

        assertThat(isValidChangeset(false, "x/y/z"), is(FALSE));
        assertThat(isValidChangeset(false, "z/y/x"), is(FALSE));
    }

    @Test
    public void excludedUserWithRestrictedPathsChangeSetShouldBeValid() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getExcludedUsers((Settings)anyObject()))
                .thenReturn(asList("excluded-user-1", "excluded-user-2"));
        when(user.getName()).thenReturn("excluded-user-1");

        assertThat(isValidChangeset(false, "x/y/z"), is(TRUE));
        assertThat(isValidChangeset(false, "z/y/x"), is(TRUE));
    }

    @Test
    public void unexcludedUserWithRestrictedPathsChangeSetShouldBeInvalid() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getExcludedUsers((Settings)anyObject()))
                .thenReturn(asList("excluded-user-1", "excluded-user-2"));
        when(user.getName()).thenReturn("unexcluded-user");

        assertThat(isValidChangeset(false, "x/y/z"), is(FALSE));
        assertThat(isValidChangeset(false, "z/y/x"), is(FALSE));
    }

    private Boolean isValidChangeset(Boolean isAdministrator, String path) {
        // Mock user
        when(permissionService.hasRepositoryPermission((Repository) anyObject(), eq(Permission.REPO_ADMIN)))
                .thenReturn(isAdministrator);
        when(change.getPath()).thenReturn(new SimplePath(path));

        return protectPathsChangesetService.validateChangesets(
                repository, settings, refId, fromHash, toHash).isEmpty();
    }

}
