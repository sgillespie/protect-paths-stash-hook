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
import com.atlassian.stash.util.PageRequest;
import com.atlassian.stash.util.PageRequestImpl;
import com.google.common.base.Function;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProtectPathsChangesetServiceImpl implements ProtectPathsChangesetService {
    public static final PageRequest PAGE_REQUEST = new PageRequestImpl(0, PageRequest.MAX_PAGE_LIMIT);

    private final CommitService commitService;
    private final PermissionService permissionService;
    private final StashAuthenticationContext stashAuthenticationContext;
    private final SettingsFactoryService settingsFactoryService;

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

    public ProtectPathsChangesetServiceImpl(CommitService commitService,
                                            PermissionService permissionService,
                                            StashAuthenticationContext stashAuthenticationContext,
                                            SettingsFactoryService settingsFactoryService) {
        this.commitService = commitService;
        this.permissionService = permissionService;
        this.stashAuthenticationContext = stashAuthenticationContext;
        this.settingsFactoryService = settingsFactoryService;
    }

    @Override
    public List<String> validateChangesets(Repository repository, Settings settings, String refId, String fromHash,
                                           String toHash) {
        List<String> errors = new ArrayList<>();

        // Admins and excluded users
        if (shouldExcludeUser(settings, repository, stashAuthenticationContext.getCurrentUser()))
            return new ArrayList<>();

        // Get protected paths
        List<String> pathRegexps = settingsFactoryService.getPathPatterns(settings);

        if (!shouldIncludeBranch(settings, refId)) return new ArrayList<>();

        Page<Changeset> changesets = findNewChangeSets(repository, fromHash, toHash);
        for (Changeset changeset : changesets.getValues()) {
            Page<DetailedChangeset> detailedChangesets = getDetailedChangesets(repository, changesets);
            for (DetailedChangeset detailedChangeset : detailedChangesets.getValues()) {
                // Validate the paths
                Page<Path> paths = detailedChangeset.getChanges().transform(CHANGE_TO_PATH);
                for (Path path : paths.getValues()) {
                    for (String regexp : pathRegexps) {
                        if (path.toString().matches(regexp)) {
                            errors.add(String.format("%s: %s matches restricted path %s", refId,
                                    changeset.getId(), regexp));
                        }
                    }
                }
            }
        }

        return errors;
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
        Boolean isExcluded = settingsFactoryService.getExcludedUsers(settings).contains(user.getName());

        return isRepoAdmin || isExcluded;
    }

    private Boolean shouldIncludeBranch(Settings settings, String refId) {
        FilterType filterType = settingsFactoryService.getFilterType(settings);
        List<String> branchPatterns = settingsFactoryService.getBranchFilters(settings);

        switch (filterType) {
            case ALL:
                return true;
            case INCLUDE:
                return matchesBranch(branchPatterns, refId);
            case EXCLUDE:
                return !matchesBranch(branchPatterns, refId);
            default:
                return null;
        }
    }

    private Page<DetailedChangeset> findDetailedChangeSets(Repository repository, String fromHash, String toHash) {
        return getDetailedChangesets(repository, findNewChangeSets(repository, fromHash, toHash));
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

    private boolean matchesBranch(Collection<String> regexps, String refId) {
        for (String branch : regexps) {
            if (refId.matches("refs/heads/" + branch)) return true;
        }

        return false;
    }
}
