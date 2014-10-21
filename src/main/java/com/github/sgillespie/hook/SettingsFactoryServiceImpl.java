package com.github.sgillespie.hook;

import com.atlassian.stash.setting.Settings;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isEmpty;

public class SettingsFactoryServiceImpl implements SettingsFactoryService {
    public static final String KEY_PATH_PATTERNS = "pathPatterns";
    public static final String KEY_FILTER_TYPE = "filterType";
    public static final String KEY_BRANCH_FILTERS = "branchFilter";
    public static final String KEY_EXCLUDED_USERS = "excludeUsers";

    @Override
    public List<String> getPathPatterns(Settings settings) {
        return getList(settings, KEY_PATH_PATTERNS);
    }

    @Override
    public FilterType getFilterType(Settings settings) {
        String filter = settings.getString(KEY_FILTER_TYPE, "ALL");
        return FilterType.valueOf(filter);
    }

    @Override
    public List<String> getBranchFilters(Settings settings) {
        return getList(settings, KEY_BRANCH_FILTERS);
    }

    @Override
    public List<String> getExcludedUsers(Settings settings) {
        return getList(settings, KEY_EXCLUDED_USERS);
    }

    private List<String> getList(Settings settings, String key) {
        String value = settings.getString(key);
        return isEmpty(value) ? new ArrayList<String>() : asList(value.split("\\s+"));
    }
}
