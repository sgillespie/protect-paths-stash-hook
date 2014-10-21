package com.github.sgillespie.hook;

import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.Settings;

import java.util.List;

public interface ProtectPathsChangesetService {
    public List<String> validateChangesets(Repository repository, Settings settings, String refId, String fromHash, String toHash);
}
