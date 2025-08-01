/*
 * Copyright Thoughtworks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thoughtworks.go.config;

import com.thoughtworks.go.domain.ConfigErrors;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ParamConfigTest {

    @Test
    public void validate_shouldMakeSureParamNameIsOfNameType() {
        assertThat(createAndValidate("name").errors().isEmpty()).isTrue();
        ConfigErrors errors = createAndValidate(".name").errors();
        assertThat(errors.isEmpty()).isFalse();
        assertThat(errors.on(ParamConfig.NAME)).isEqualTo("Invalid parameter name '.name'. This must be alphanumeric and can contain underscores, hyphens and periods (however, it cannot start with a period). The maximum allowed length is 255 characters.");
    }

    @Test
    public void shouldReturnValueForDisplay() {
        ParamConfig paramConfig = new ParamConfig("foo", "bar");
        assertThat(paramConfig.getValueForDisplay()).isEqualTo("bar");
    }

    @Test
    public void shouldValidateName() {
        ParamConfig paramConfig = new ParamConfig();
        ValidationContext validationContext = mock(ValidationContext.class);
        when(validationContext.getPipeline()).thenReturn(new PipelineConfig(new CaseInsensitiveString("p"), null));
        paramConfig.validateName(new HashMap<>(), validationContext);
        assertThat(paramConfig.errors().on(ParamConfig.NAME)).isEqualTo("Parameter cannot have an empty name for pipeline 'p'.");
    }

    private ParamConfig createAndValidate(final String name) {
        ParamConfig config = new ParamConfig(name, "value");
        config.validate(null);
        return config;
    }
}
