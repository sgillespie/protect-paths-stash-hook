package com.github.sgillespie.hook;

import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.setting.SettingsValidationErrors;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by sgillespie on 9/27/14.
 */
public class ProtectPathsSettingsValidatorTest {
    private ProtectPathsSettingsValidator validator;
    @Mock
    private Settings settings;
    @Mock
    private SettingsValidationErrors settingsValidationErrors;
    @Mock
    private Repository repository;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        validator = new ProtectPathsSettingsValidator();
    }

    @Test
    public void noPathShouldThrowError() {
        when(settings.getString(eq("pathPatterns"))).thenReturn(null);
        validator.validate(settings, settingsValidationErrors, repository);
        verify(settingsValidationErrors).addFieldError(eq("pathPatterns"), anyString());
    }
}
