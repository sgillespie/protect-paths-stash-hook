package com.github.sgillespie.hook;

import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.hook.repository.RepositoryMergeRequestCheck;
import com.atlassian.stash.hook.repository.RepositoryMergeRequestCheckContext;
import com.atlassian.stash.pull.PullRequestRef;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.util.PageRequest;
import com.atlassian.stash.util.PageRequestImpl;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.join;

public class ProtectPathsRepositoryHook implements PreReceiveRepositoryHook,
        RepositoryMergeRequestCheck {

    public static final PageRequest PAGE_REQUEST = new PageRequestImpl(0, PageRequest.MAX_PAGE_LIMIT);

    private final ProtectPathsChangesetService protectPathsChangesetService;

    public ProtectPathsRepositoryHook(ProtectPathsChangesetService protectPathsChangesetService) {
        this.protectPathsChangesetService = protectPathsChangesetService;
    }

    @Override
    public boolean onReceive(@Nonnull RepositoryHookContext context,
                             @Nonnull Collection<RefChange> refChanges,
                             @Nonnull HookResponse hookResponse) {
        Repository repository = context.getRepository();
        Settings settings = context.getSettings();

        List<String> errors = new ArrayList<>();

        for (RefChange refChange : refChanges) {
            List<String> res = protectPathsChangesetService.validateChangesets(repository,
                    settings, refChange.getRefId(), refChange.getFromHash(), refChange.getToHash());
            errors.addAll(res);
        }

        if (!errors.isEmpty()) {
            hookResponse.err().println("Push rejected!");
            hookResponse.err().println("There are changes to protected paths.");
            hookResponse.err().println();

            for (String error : errors) {
                hookResponse.err().println(error);
            }
        }

        return errors.isEmpty();
    }

    @Override
    public void check(@Nonnull RepositoryMergeRequestCheckContext context) {
        Repository repository = context
                .getMergeRequest()
                .getPullRequest()
                .getFromRef()
                .getRepository();
        Settings settings = context.getSettings();

        PullRequestRef fromRef = context.getMergeRequest().getPullRequest().getFromRef();
        PullRequestRef toRef = context.getMergeRequest().getPullRequest().getToRef();

        List<String> errors = protectPathsChangesetService.validateChangesets(repository, settings, toRef.getId(),
                toRef.getLatestChangeset(), fromRef.getLatestChangeset());

        if (!errors.isEmpty()) {
            context.getMergeRequest().veto("There are changes to protected paths!", join(errors, "\n"));
        }
    }
}
