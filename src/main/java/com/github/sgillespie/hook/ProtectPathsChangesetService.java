package com.github.sgillespie.hook;

import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.user.PermissionService;
import com.atlassian.stash.user.StashAuthenticationContext;

import java.util.List;

/**
 * Created by sgillespie on 10/13/14.
 */
public interface ProtectPathsChangesetService {
    public List<String> validateChangesets(Repository repository, Settings settings, String refId, String fromHash, String toHash);
}
