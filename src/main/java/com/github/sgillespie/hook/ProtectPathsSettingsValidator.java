package com.github.sgillespie.hook;

import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.RepositorySettingsValidator;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.setting.SettingsValidationErrors;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;

/**
 * Created by sgillespie on 9/27/14.
 */
public class ProtectPathsSettingsValidator  implements RepositorySettingsValidator {
    @Override
    public void validate(@Nonnull Settings settings,
                         @Nonnull SettingsValidationErrors settingsValidationErrors,
                         @Nonnull Repository repository) {
        if (StringUtils.isEmpty(settings.getString("pathPatterns"))) {
            settingsValidationErrors.addFieldError("pathPatterns", "Path Patterns is mandatory");
        }
    }
}
