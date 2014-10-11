package com.github.sgillespie.hook;


import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.content.*;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.hook.repository.RepositoryMergeRequestCheckContext;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestRef;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;

import com.atlassian.stash.scm.pull.MergeRequest;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.user.Permission;
import com.atlassian.stash.user.PermissionService;
import com.atlassian.stash.user.StashAuthenticationContext;
import com.atlassian.stash.user.StashUser;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageImpl;
import com.atlassian.stash.util.PageRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static java.util.Arrays.asList;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * Created by sgillespie on 9/19/14.
 */
public class ProtectPathsRepositoryHookTest {
    private ProtectPathsRepositoryHook protectPathsRepositoryHook;
    private StringWriter hookResponseErr;

    @Mock
    private CommitService commitService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private StashAuthenticationContext stashAuthenticationContext;
    @Mock
    private StashUser user;
    @Mock
    private HookResponse hookResponse;
    @Mock
    private RefChange refChange;
    @Mock
    private RepositoryHookContext repositoryHookContext;
    @Mock
    private Change change;
    @Mock
    private SettingsFactoryService settingsFactoryService;
    @Mock
    private RepositoryMergeRequestCheckContext mergeRequestCheckContext;
    @Mock
    private MergeRequest mergeRequest;
    @Mock
    private PullRequestRef pullRequestFromRef;
    @Mock
    private PullRequestRef pullRequestToRef;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Mock some stuff
        when(user.getName()).thenReturn("user");
        when(stashAuthenticationContext.getCurrentUser()).thenReturn(user);

        Repository repository = mock(Repository.class);
        when(repositoryHookContext.getRepository()).thenReturn(repository);
        when(refChange.getFromHash()).thenReturn("FROM-HASH");
        when(refChange.getToHash()).thenReturn("TO-HASH");
        when(refChange.getRefId()).thenReturn("refs/heads/master");

        when(settingsFactoryService.getFilterType((Settings) anyObject())).thenReturn(FilterType.ALL);

        Changeset changeSet = mock(Changeset.class);
        when(changeSet.getId()).thenReturn("CHANGESET-ID");
        Page<Changeset> changeSets = new PageImpl<Changeset>(ProtectPathsRepositoryHook.PAGE_REQUEST,
                1, asList(changeSet), true);
        when(commitService.getChangesetsBetween((ChangesetsBetweenRequest) anyObject(),
                (PageRequest) anyObject())).thenReturn(changeSets);

        DetailedChangeset detailedChangeset = mock(DetailedChangeset.class);
        Page<DetailedChangeset> detailedChangesets = new PageImpl<DetailedChangeset>(
                ProtectPathsRepositoryHook.PAGE_REQUEST, 1, asList(detailedChangeset), true);
        when(commitService.getDetailedChangesets((DetailedChangesetsRequest) anyObject(),
                (PageRequest) anyObject())).thenReturn(detailedChangesets);

        when(change.getPath()).thenReturn(new SimplePath("x/y/z"));
        Page<Change> changes = new PageImpl<Change>(ProtectPathsRepositoryHook.PAGE_REQUEST, 1, asList(change), true);
        when((Page<Change>) detailedChangeset.getChanges()).thenReturn(changes);

        hookResponseErr = new StringWriter();
        when(hookResponse.err()).thenReturn(new PrintWriter(hookResponseErr));

        when(mergeRequestCheckContext.getMergeRequest()).thenReturn(mergeRequest);
        PullRequest pullRequest = mock(PullRequest.class);
        when(mergeRequest.getPullRequest()).thenReturn(pullRequest);
        when(pullRequest.getFromRef()).thenReturn(pullRequestFromRef);
        when(pullRequestFromRef.getRepository()).thenReturn(repository);
        when(pullRequestFromRef.getLatestChangeset()).thenReturn("FROM-HASH");
        when(pullRequestToRef.getRepository()).thenReturn(repository);
        when(pullRequest.getToRef()).thenReturn(pullRequestToRef);
        when(pullRequestFromRef.getLatestChangeset()).thenReturn("TO-HASH");

        protectPathsRepositoryHook = new ProtectPathsRepositoryHook(
                commitService, permissionService, stashAuthenticationContext, settingsFactoryService);
    }

    @Test
    public void adminUserShouldBeAbleToPush() {
        assertThat(canPush(true, "x/y/z"), is(Boolean.TRUE));
    }

    @Test
    public void adminUserShouldBeAbleToMerge() {
        merge(true, "x/y/z");
        verify(mergeRequest, never()).veto(anyString(), anyString());
    }

    @Test
    public void nonAdminUserShouldBeAbleToPushToNonRestrictedPaths() {
        assertThat(canPush(false, "x/y/z"), is(Boolean.TRUE));
    }

    @Test
    public void nonAdminUserShouldBeAbleToMergeNonRestrictedPaths() {
        merge(false, "x/y/z");
        verify(mergeRequest, never()).veto(anyString(), anyString());
    }

    @Test
    public void nonAdminUserShouldNotBeAbleToPushToRestrictedPaths() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        assertThat(canPush(false, "x/y/z"), is(Boolean.FALSE));
        assertThat(canPush(false, "z/y/x"), is(Boolean.FALSE));
    }

    @Test
    public void nonAdminUserShouldNotBeAbleToMergeRestrictedPaths() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        merge(false, "x/y/z");
        merge(false, "z/y/x");
        verify(mergeRequest, times(2)).veto(anyString(), anyString());
    }

    @Test
    public void adminUserShouldBeAbleToPushToRestrictedPaths() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        assertThat(canPush(true, "x/y/z"), is(Boolean.TRUE));
        assertThat(canPush(true, "z/y/x"), is(Boolean.TRUE));
    }

    @Test
    public void adminUserShouldBeAbleToMergeRestrictedPaths() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        merge(true, "x/y/z");
        merge(true, "z/y/z");
        verify(mergeRequest, never()).veto(anyString(), anyString());
    }

    @Test
    public void nonAdminShouldNotBeAbleToPushAllFilter() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.ALL);
        assertThat(canPush(false, "x/y/z"), is(Boolean.FALSE));
        assertThat(canPush(false, "z/y/x"), is(Boolean.FALSE));
    }

    @Test
    public void nonAdminShouldNotBeAbleToMergeAllFilter() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.ALL);
        merge(false, "x/y/z");
        merge(false, "z/y/x");
        verify(mergeRequest, times(2)).veto(anyString(), anyString());
    }

    @Test
    public void nonAdminShouldNotBeAbleToPushToIncludedBranch() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.INCLUDE);
        when(settingsFactoryService.getBranchFilters((Settings)anyObject()))
                .thenReturn(asList("branch-1", "branch-2"));
        when(refChange.getRefId()).thenReturn("refs/heads/branch-2");

        assertThat(canPush(false, "x/y/z"), is(Boolean.FALSE));
        assertThat(canPush(false, "z/y/x"), is(Boolean.FALSE));
    }

    @Test
    public void nonAdminShouldNotBeAbleToMergeToIncludedBranch() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.INCLUDE);
        when(settingsFactoryService.getBranchFilters((Settings)anyObject()))
                .thenReturn(asList("branch-1", "branch-2"));
        when(pullRequestToRef.getId()).thenReturn("refs/heads/branch-2");

        merge(false, "x/y/z");
        merge(false, "z/y/x");

        verify(mergeRequest, times(2)).veto(anyString(), anyString());
    }

    @Test
    public void nonAdminShouldBeAbleToPushToNotIncludedBranch() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.INCLUDE);
        when(settingsFactoryService.getBranchFilters((Settings)anyObject()))
                .thenReturn(asList("branch-1", "branch-2"));
        when(refChange.getRefId()).thenReturn("refs/heads/branch-3");

        assertThat(canPush(false, "x/y/z"), is(Boolean.TRUE));
        assertThat(canPush(false, "z/y/x"), is(Boolean.TRUE));
    }

    @Test
    public void nonAdminShouldBeAbleToMergeNotIncludedBranch() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.INCLUDE);
        when(settingsFactoryService.getBranchFilters((Settings)anyObject()))
                .thenReturn(asList("branch-1", "branch-2"));
        when(pullRequestToRef.getId()).thenReturn("refs/heads/branch-3");

        merge(false, "x/y/z");
        merge(false, "z/y/x");

        verify(mergeRequest, times(0)).veto(anyString(), anyString());
    }

    @Test
    public void nonAdminShouldBeAbleToPushToExcludedBranch() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.EXCLUDE);
        when(settingsFactoryService.getBranchFilters((Settings)anyObject()))
                .thenReturn(asList("branch-1", "branch-2"));
        when(refChange.getRefId()).thenReturn("refs/heads/branch-1");

        assertThat(canPush(false, "x/y/z"), is(Boolean.TRUE));
        assertThat(canPush(false, "z/y/x"), is(Boolean.TRUE));
    }

    @Test
    public void nonAdminShouldBeAbleToMergeExcludedBranch() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.EXCLUDE);
        when(settingsFactoryService.getBranchFilters((Settings)anyObject()))
                .thenReturn(asList("branch-1", "branch-2"));
        when(pullRequestToRef.getId()).thenReturn("refs/heads/branch-1");

        merge(false, "x/y/z");
        merge(false, "z/y/x");

        verify(mergeRequest, never()).veto(anyString(), anyString());
    }

    @Test
    public void nonAdminShouldNotBeAbleToPushToNotExcludedBranch() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.EXCLUDE);
        when(settingsFactoryService.getBranchFilters((Settings)anyObject()))
                .thenReturn(asList("branch-1", "branch-2"));
        when(refChange.getRefId()).thenReturn("refs/heads/branch-3");

        assertThat(canPush(false, "x/y/z"), is(Boolean.FALSE));
        assertThat(canPush(false, "z/y/x"), is(Boolean.FALSE));
    }

    @Test
    public void nonAdminShouldNotBeNotAbleToMergeExcludedBranch() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.EXCLUDE);
        when(settingsFactoryService.getBranchFilters((Settings)anyObject()))
                .thenReturn(asList("branch-1", "branch-2"));
        when(pullRequestToRef.getId()).thenReturn("refs/heads/branch-3");

        merge(false, "x/y/z");
        merge(false, "z/y/x");

        verify(mergeRequest, times(2)).veto(anyString(), anyString());
    }

    @Test
    public void excludedUserShouldBeAbleToPushRestrictedPaths() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.ALL);
        when(settingsFactoryService.getExcludedUsers((Settings)anyObject()))
                .thenReturn(asList("excluded-user-1", "excluded-user-2"));
        when(user.getName()).thenReturn("excluded-user-1");

        assertThat(canPush(false, "x/y/z"), is(Boolean.TRUE));
        assertThat(canPush(false, "z/y/x"), is(Boolean.TRUE));
    }

    @Test
    public void excludedUserShouldBeAbleToMergeRestrictedPaths() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));
        when(settingsFactoryService.getFilterType((Settings)anyObject())).thenReturn(FilterType.ALL);
        when(settingsFactoryService.getExcludedUsers((Settings)anyObject()))
                .thenReturn(asList("excluded-user-1", "excluded-user-2"));
        when(user.getName()).thenReturn("excluded-user-1");

        merge(false, "x/y/z");
        merge(false, "z/y/x");

        verify(mergeRequest, never()).veto(anyString(), anyString());
    }

    @Test
    public void failedPushShouldContainErrorMessage() {
        when(settingsFactoryService.getPathPatterns((Settings)anyObject())).thenReturn(asList("x/y/z", "z/y/x"));

        assertThat(canPush(false, "x/y/z"), is(Boolean.FALSE));
        assertThat(hookResponseErr.toString().length() == 0, equalTo(Boolean.FALSE));
    }

    private boolean canPush(Boolean isAdministrator, String path) {
        // Mock user
        when(permissionService.hasRepositoryPermission((Repository) anyObject(), eq(Permission.REPO_ADMIN)))
                .thenReturn(isAdministrator);
        when(change.getPath()).thenReturn(new SimplePath(path));

        return protectPathsRepositoryHook.onReceive(repositoryHookContext,
                asList(refChange), hookResponse);
    }

    private void merge(Boolean isAdministrator, String path) {
        when(permissionService.hasRepositoryPermission((Repository) anyObject(), eq(Permission.REPO_ADMIN)))
                .thenReturn(isAdministrator);
        protectPathsRepositoryHook.check(mergeRequestCheckContext);
    }
}