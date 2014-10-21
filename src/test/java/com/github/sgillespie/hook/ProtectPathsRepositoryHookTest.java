package com.github.sgillespie.hook;


import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.hook.repository.RepositoryMergeRequestCheckContext;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestRef;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.pull.MergeRequest;
import com.atlassian.stash.setting.Settings;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;

import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class ProtectPathsRepositoryHookTest {
    private ProtectPathsRepositoryHook protectPathsRepositoryHook;

    @Mock
    private ProtectPathsChangesetService protectPathsChangesetService;
    @Mock
    private HookResponse hookResponse;
    @Mock
    private RefChange refChange;
    @Mock
    private RepositoryHookContext repositoryHookContext;
    @Mock
    private RepositoryMergeRequestCheckContext mergeRequestCheckContext;
    @Mock
    private MergeRequest mergeRequest;
    @Mock
    private PullRequestRef pullRequestFromRef;
    @Mock
    private PullRequestRef pullRequestToRef;
    @Mock
    private Repository repository;
    @Mock
    private Settings settings;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        Repository repository = mock(Repository.class);
        when(repositoryHookContext.getRepository()).thenReturn(repository);

        PrintWriter printWriter = new PrintWriter(new StringWriter());
        when(hookResponse.err()).thenReturn(printWriter);

        when(refChange.getFromHash()).thenReturn("FROM-HASH");
        when(refChange.getToHash()).thenReturn("TO-HASH");
        when(refChange.getRefId()).thenReturn("refs/heads/master");

        when(mergeRequestCheckContext.getMergeRequest()).thenReturn(mergeRequest);
        PullRequest pullRequest = mock(PullRequest.class);
        when(mergeRequest.getPullRequest()).thenReturn(pullRequest);
        when(pullRequest.getFromRef()).thenReturn(pullRequestFromRef);
        when(pullRequestFromRef.getRepository()).thenReturn(repository);
        when(pullRequestFromRef.getLatestChangeset()).thenReturn("FROM-HASH");
        when(pullRequestToRef.getRepository()).thenReturn(repository);
        when(pullRequestToRef.getId()).thenReturn("refs/heads/master");
        when(pullRequest.getToRef()).thenReturn(pullRequestToRef);
        when(pullRequestToRef.getLatestChangeset()).thenReturn("TO-HASH");

        protectPathsRepositoryHook = new ProtectPathsRepositoryHook(protectPathsChangesetService);
    }

    @Test
    public void validChangeSetShouldPush() {
        when(protectPathsChangesetService.validateChangesets((Repository)anyObject(), (Settings)anyObject(),
                eq("refs/heads/master"), eq("FROM-HASH"), eq("TO-HASH"))).thenReturn(new ArrayList<String>());
        assertThat(push(), is(TRUE));
    }

    @Test
    public void invalidChangeSetShoudNotPush() {
        when(protectPathsChangesetService.validateChangesets((Repository)anyObject(), (Settings)anyObject(),
                eq("refs/heads/master"), eq("FROM-HASH"), eq("TO-HASH"))).thenReturn(asList("error!"));
        assertThat(push(), is(Boolean.FALSE));
    }

    @Test
    public void validChangeSetShouldMerge() {
        when(protectPathsChangesetService.validateChangesets((Repository)anyObject(), (Settings)anyObject(),
                anyString(), anyString(), anyString())).thenReturn(new ArrayList<String>());
        merge();
        verify(mergeRequestCheckContext.getMergeRequest(), never()).veto(anyString(), anyString());
    }

    @Test
    public void validChangeSetShouldNotMerge() {
        when(protectPathsChangesetService.validateChangesets((Repository)anyObject(), (Settings)anyObject(),
                eq("refs/heads/master"), eq("TO-HASH"), eq("FROM-HASH"))).thenReturn(asList("error!"));
        merge();
        verify(mergeRequestCheckContext.getMergeRequest(), times(1)).veto(anyString(), anyString());
    }

    private boolean push() {
        return protectPathsRepositoryHook.onReceive(repositoryHookContext, Arrays.asList(refChange), hookResponse);
    }

    private void merge() {
        protectPathsRepositoryHook.check(mergeRequestCheckContext);
    }
}