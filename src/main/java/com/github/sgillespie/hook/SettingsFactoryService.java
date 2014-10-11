package com.github.sgillespie.hook;

import com.atlassian.stash.setting.Settings;

import java.util.List;

/**
 * Created by sgillespie on 10/10/14.
 */
public interface SettingsFactoryService {
    List<String> getPathPatterns(Settings settings);

    FilterType getFilterType(Settings settings);

    List<String> getBranchFilters(Settings settings);

    List<String> getExcludedUsers(Settings settings);
}
