package com.github.sgillespie.hook;

import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.content.*;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.hook.repository.RepositoryMergeRequestCheck;
import com.atlassian.stash.hook.repository.RepositoryMergeRequestCheckContext;
import com.atlassian.stash.pull.PullRequestRef;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.user.Permission;
import com.atlassian.stash.user.PermissionService;
import com.atlassian.stash.user.StashAuthenticationContext;
import com.atlassian.stash.user.StashUser;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequest;
import com.atlassian.stash.util.PageRequestImpl;
import com.google.common.base.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.util.Collection;

import static java.util.Arrays.asList;

public class ProtectPathsRepositoryHook implements PreReceiveRepositoryHook,
        RepositoryMergeRequestCheck {

    public static final PageRequest PAGE_REQUEST = new PageRequestImpl(0, PageRequest.MAX_PAGE_LIMIT);

    private final CommitService commitService;
    private final PermissionService permissionService;
    private final StashAuthenticationContext stashAuthenticationContext;

    public ProtectPathsRepositoryHook(CommitService commitService,
                                      PermissionService permissionService,
                                      StashAuthenticationContext stashAuthenticationContext) {
        this.commitService = commitService;
        this.permissionService = permissionService;
        this.stashAuthenticationContext = stashAuthenticationContext;
    }

    public static final Function<Changeset, String> CHANGESET_TO_ID =
        new Function<Changeset, String>() {
            @Override
            public String apply(@Nullable Changeset changeset) {
                return changeset.getId();
            }
    };
    public static final Function<Change, Path> CHANGE_TO_PATH =
            new Function<Change, Path>() {
                @Override
                public Path apply(@Nullable Change change) {
                    return change.getPath();
                }
    };

    @Override
    public boolean onReceive(@Nonnull RepositoryHookContext context,
                             @Nonnull Collection<RefChange> refChanges,
                             @Nonnull HookResponse hookResponse) {
        Repository repository = context.getRepository();
        Settings settings = context.getSettings();

        // Admins and excluded users
        if (shouldExcludeUser(settings, repository, stashAuthenticationContext.getCurrentUser()))
            return true;

        // Get protected paths
        String[] pathRegexps = settings.getString("pathPatterns", "").split("\\s+");

        // Loop over the new changes
        for (RefChange refChange : refChanges) {
            if (!shouldIncludeBranch(settings, refChange.getRefId())) continue;

            Page<Changeset> changeSets = findNewChangeSets(repository, refChange.getFromHash(), refChange.getToHash());
            Page<DetailedChangeset> detailedChangesets = getDetailedChangesets(repository, changeSets);

            for (DetailedChangeset detailedChangeset : detailedChangesets.getValues()) {
                // Validate the paths
                Page<Path> paths = detailedChangeset.getChanges().transform(CHANGE_TO_PATH);
                for (Path path : paths.getValues()) {
                    for (String regexp : pathRegexps) {
                        if (path.toString().matches(regexp)) {
                            String branch = refChange.getRefId().replaceFirst("^refs/heads/", "");
                            printErrorMessages(hookResponse.err(), branch);
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    @Override
    public void check(@Nonnull RepositoryMergeRequestCheckContext context) {
        Repository repository = context
                .getMergeRequest()
                .getPullRequest()
                .getFromRef()
                .getRepository();
        Settings settings = context.getSettings();

        // Admins and excluded users
        if (shouldExcludeUser(settings, repository, stashAuthenticationContext.getCurrentUser())) return;

        // Get protected paths
        String[] pathRegexps = settings.getString("pathPatterns", "").split("\\s+");

        PullRequestRef fromRef = context.getMergeRequest().getPullRequest().getFromRef();
        PullRequestRef toRef = context.getMergeRequest().getPullRequest().getToRef();

        if (shouldIncludeBranch(settings, toRef.getId())) {
            Page<Changeset> changesets = findNewChangeSets(repository, toRef.getLatestChangeset(),
                    fromRef.getLatestChangeset());
            Page<DetailedChangeset> detailedChangesets = getDetailedChangesets(repository, changesets);

            for (DetailedChangeset detailedChangeset : detailedChangesets.getValues()) {
                // Validate the paths
                Page<Path> paths = detailedChangeset.getChanges().transform(CHANGE_TO_PATH);
                for (Path path : paths.getValues()) {
                    for (String regexp : pathRegexps) {
                        if (path.toString().toString().matches(regexp)) {
                            context.getMergeRequest().veto("Cannot merge protected to path",
                                    "Cannot merge to protect paths");
                        }
                    }
                }
            }
        }
    }

    private boolean isRepoAdmin(Repository repository) {
        return permissionService.hasRepositoryPermission(repository, Permission.REPO_ADMIN);
    }

    /**
     * Returns true if the user is an administrator or an excluded user
     *
     * @param settings the hook settings
     * @param user the currently logged in user
     * @return true if the user is an administrator or an excluded user
     */
    private boolean shouldExcludeUser(Settings settings, Repository repository, StashUser user) {
        Boolean isRepoAdmin = permissionService.hasRepositoryPermission(repository, Permission.REPO_ADMIN);
        String[] excludedUsers = settings
                .getString("excludedUsers", "")
                .split("\\s+");

        return isRepoAdmin || asList(excludedUsers).contains(user.getName());
    }

    private Boolean shouldIncludeBranch(Settings settings, String refId) {
        FilterType filterType = FilterType.valueOf(settings.getString("filterType", "ALL"));
        String[] branchRegexps = settings
                .getString("branchFilter", "master")
                .split("\\s+");

        switch (filterType) {
            case ALL:
                return true;
            case INCLUDE:
                return matchesBranch(asList(branchRegexps), refId);
            case EXCLUDE:
                return !matchesBranch(asList(branchRegexps), refId);
            default:
                return null;
        }
    }

    private Page<Changeset> findNewChangeSets(Repository repository, String fromHash, String toHash) {
        ChangesetsBetweenRequest changesetsBetweenRequest = new ChangesetsBetweenRequest.Builder(repository)
                .exclude(fromHash)
                .include(toHash)
                .build();
        return commitService.getChangesetsBetween(changesetsBetweenRequest, PAGE_REQUEST);
    }

    private Page<DetailedChangeset> getDetailedChangesets(Repository repository, Page<Changeset> changeSets) {
        Page<String> changeSetIds = changeSets.transform(CHANGESET_TO_ID);

        DetailedChangesetsRequest detailedChangesetsRequest = new DetailedChangesetsRequest.Builder(repository)
                .changesetIds(changeSetIds.getValues())
                .maxChangesPerCommit(PageRequest.MAX_PAGE_LIMIT)
                .build();
        return commitService.getDetailedChangesets(detailedChangesetsRequest, PAGE_REQUEST);
    }

    private void printErrorMessages(PrintWriter writer, String branch) {
        writer.println("Push rejected!");
        writer.println();
        writer.println("There are changes to restricted paths.");
        writer.println("Only repository administrators can push to " + branch + ".");
    }

    private boolean matchesBranch(Collection<String> regexps, String refId) {
        for (String branch : regexps) {
            if (refId.matches("refs/heads/" + branch)) return true;
        }

        return false;
    }


}
