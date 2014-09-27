package com.github.sgillespie.hook;


import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.content.*;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.user.Permission;
import com.atlassian.stash.user.PermissionService;
import com.atlassian.stash.user.StashAuthenticationContext;
import com.atlassian.stash.user.StashUser;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageImpl;
import com.atlassian.stash.util.PageRequest;
import com.github.sgillespie.hook.RestrictPathsRepositoryHook;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openqa.selenium.lift.WebDriverTestContext;

import java.io.PrintWriter;
import java.io.StringWriter;

import static java.util.Arrays.asList;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by sgillespie on 9/19/14.
 */
public class TestRestrictPathsRepositoryHook {
    private RestrictPathsRepositoryHook restrictPathsRepositoryHook;
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
    private Settings settings;

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

        when(repositoryHookContext.getSettings()).thenReturn(settings);
        when(settings.getString(anyString(), anyString())).thenReturn("");
        when(settings.getString(eq("filterType"), anyString())).thenReturn("ALL");

        Changeset changeSet = mock(Changeset.class);
        when(changeSet.getId()).thenReturn("CHANGESET-ID");
        Page<Changeset> changeSets = new PageImpl<Changeset>(RestrictPathsRepositoryHook.PAGE_REQUEST,
                1, asList(changeSet), true);
        when(commitService.getChangesetsBetween((ChangesetsBetweenRequest) Matchers.anyObject(),
                (PageRequest) Matchers.anyObject())).thenReturn(changeSets);

        DetailedChangeset detailedChangeset = mock(DetailedChangeset.class);
        Page<DetailedChangeset> detailedChangesets = new PageImpl<DetailedChangeset>(
                RestrictPathsRepositoryHook.PAGE_REQUEST, 1, asList(detailedChangeset), true);
        when(commitService.getDetailedChangesets((DetailedChangesetsRequest) Matchers.anyObject(),
                (PageRequest) Matchers.anyObject())).thenReturn(detailedChangesets);

        when(change.getPath()).thenReturn(new SimplePath("x/y/z"));
        Page<Change> changes = new PageImpl<Change>(RestrictPathsRepositoryHook.PAGE_REQUEST, 1, asList(change), true);
        when((Page<Change>) detailedChangeset.getChanges()).thenReturn(changes);

        hookResponseErr = new StringWriter();
        when(hookResponse.err()).thenReturn(new PrintWriter(hookResponseErr));

        restrictPathsRepositoryHook = new RestrictPathsRepositoryHook(
                commitService, permissionService, stashAuthenticationContext);
    }

    @Test
    public void adminUserShouldBeAbleToPush() {
        assertThat(canPush(true, "x/y/z"), is(Boolean.TRUE));
    }

    @Test
    public void nonAdminUserShouldBeAbleToPushToNonRestrictedPaths() {
        assertThat(canPush(false, "x/y/z"), is(Boolean.TRUE));
    }

    @Test
    public void nonAdminUserShouldNotBeAbleToPushToRestrictedPaths() {
        when(settings.getString(eq("restrictedPaths"), anyString())).thenReturn("x/y/z z/y/x");
        assertThat(canPush(false, "x/y/z"), is(Boolean.FALSE));
        assertThat(canPush(false, "z/y/x"), is(Boolean.FALSE));
    }

    @Test
    public void adminUserShouldBeAbleToPushToRestrictedPaths() {
        when(settings.getString(eq("restrictedPaths"), anyString())).thenReturn("x/y/z z/y/x");
        assertThat(canPush(true, "x/y/z"), is(Boolean.TRUE));
        assertThat(canPush(true, "z/y/x"), is(Boolean.TRUE));
    }

    @Test
    public void nonAdminShouldNotBeAbleToPushAllFilter() {
        when(settings.getString(eq("restrictedPaths"), anyString())).thenReturn("x/y/z z/y/x");
        when(settings.getString(eq("filterType"), anyString())).thenReturn("ALL");
        assertThat(canPush(false, "x/y/z"), is(Boolean.FALSE));
        assertThat(canPush(false, "z/y/x"), is(Boolean.FALSE));
    }

    @Test
    public void nonAdminShouldNotBeAbleToPushToIncludedBranch() {
        when(settings.getString(eq("restrictedPaths"), anyString())).thenReturn("x/y/z z/y/x");
        when(settings.getString(eq("filterType"), anyString())).thenReturn("INCLUDE");
        when(settings.getString(eq("branchFilter"), anyString())).thenReturn("branch-1 branch-2");
        when(refChange.getRefId()).thenReturn("refs/heads/branch-2");

        assertThat(canPush(false, "x/y/z"), is(Boolean.FALSE));
        assertThat(canPush(false, "z/y/x"), is(Boolean.FALSE));
    }

    @Test
    public void nonAdminShouldBeAbleToPushToNotIncludedBranch() {
        when(settings.getString(eq("restrictedPaths"), anyString())).thenReturn("x/y/z z/y/x");
        when(settings.getString(eq("filterType"), anyString())).thenReturn("INCLUDE");
        when(settings.getString(eq("branchFilter"), anyString())).thenReturn("branch-1 branch-2");
        when(refChange.getRefId()).thenReturn("refs/heads/branch-3");

        assertThat(canPush(false, "x/y/z"), is(Boolean.TRUE));
        assertThat(canPush(false, "z/y/x"), is(Boolean.TRUE));
    }

    @Test
    public void nonAdminShouldBeAbleToPushToExcludedBranch() {
        when(settings.getString(eq("restrictedPaths"), anyString())).thenReturn("x/y/z z/y/x");
        when(settings.getString(eq("filterType"), anyString())).thenReturn("EXCLUDE");
        when(settings.getString(eq("branchFilter"), anyString())).thenReturn("branch-1 branch-2");
        when(refChange.getRefId()).thenReturn("refs/heads/branch-1");

        assertThat(canPush(false, "x/y/z"), is(Boolean.TRUE));
        assertThat(canPush(false, "z/y/x"), is(Boolean.TRUE));
    }

    @Test
    public void nonAdminShouldNotBeAbleToPushToNotExcludedBranch() {
        when(settings.getString(eq("restrictedPaths"), anyString())).thenReturn("x/y/z z/y/x");
        when(settings.getString(eq("filterType"), anyString())).thenReturn("EXCLUDE");
        when(settings.getString(eq("branchFilter"), anyString())).thenReturn("branch-1 branch-2");
        when(refChange.getRefId()).thenReturn("refs/heads/branch-3");

        assertThat(canPush(false, "x/y/z"), is(Boolean.FALSE));
        assertThat(canPush(false, "z/y/x"), is(Boolean.FALSE));
    }

    @Test
    public void excludedUserShouldBeAbleToPushRestrictedPaths() {
        when(settings.getString(eq("restrictedPaths"), anyString())).thenReturn("x/y/z z/y/x");
        when(settings.getString(eq("filterType"), anyString())).thenReturn("ALL");
        when(settings.getString(eq("excludedUsers"), anyString())).thenReturn("excluded-user-1 excluded-user-2");
        when(user.getName()).thenReturn("excluded-user-1");

        assertThat(canPush(false, "x/y/z"), is(Boolean.TRUE));
        assertThat(canPush(false, "z/y/x"), is(Boolean.TRUE));
    }

    @Test
    public void errorMessageShouldMatch() {
        when(settings.getString(eq("restrictedPaths"), anyString())).thenReturn("x/y/z");

        String expectedErr = "Push rejected!\n\n" +
                "There are changes to restricted paths.\n" +
                "Only repository administrators can push to master.\n";
        assertThat(canPush(false, "x/y/z"), is(Boolean.FALSE));
        assertThat(hookResponseErr.toString(), equalTo(expectedErr));
    }

    private boolean canPush(Boolean isAdministrator, String path) {
        // Mock user
        when(permissionService.hasRepositoryPermission((Repository) Matchers.anyObject(), eq(Permission.REPO_ADMIN)))
                .thenReturn(isAdministrator);
        when(change.getPath()).thenReturn(new SimplePath(path));

        return restrictPathsRepositoryHook.onReceive(repositoryHookContext,
                asList(refChange), hookResponse);
    }
}