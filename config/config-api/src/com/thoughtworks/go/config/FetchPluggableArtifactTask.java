/*
 * Copyright 2017 ThoughtWorks, Inc.
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

import com.thoughtworks.go.config.validation.NameTypeValidator;
import com.thoughtworks.go.domain.TaskProperty;
import com.thoughtworks.go.domain.config.Configuration;
import com.thoughtworks.go.domain.config.ConfigurationProperty;
import com.thoughtworks.go.domain.config.SecureKeyInfoProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang.StringUtils.isNotBlank;

@ConfigTag(value = "fetchPluggableArtifact")
public class FetchPluggableArtifactTask extends AbstractFetchTask implements SecureKeyInfoProvider {
    public static final String FETCH_PLUGGABLE_ARTIFACT = "Fetch Pluggable Artifact";
    @ConfigAttribute(value = "storeId", optional = false)
    private String storeId;
    @ConfigSubtag
    private Configuration configuration = new Configuration();

    public FetchPluggableArtifactTask() {
    }

    public FetchPluggableArtifactTask(CaseInsensitiveString pipelineName, CaseInsensitiveString stage, CaseInsensitiveString job, String storeId, ConfigurationProperty... configurations) {
        super(pipelineName, stage, job);
        this.storeId = storeId;
        configuration.addAll(Arrays.asList(configurations));
    }

    public String getStoreId() {
        return storeId;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    protected void validateAttributes(ValidationContext validationContext) {
        if (!new NameTypeValidator().isNameValid(storeId)) {
            errors.add("storeId", NameTypeValidator.errorMessage("fetch artifact storeId", storeId));
        }

        if (isNotBlank(storeId)) {
            final ArtifactStore artifactStore = validationContext.artifactStores().find(storeId);

            if (artifactStore == null) {
                addError("storeId", String.format("Artifact store with id `%s` does not exist.", storeId));
            }
        }

        configuration.validateTree();
        configuration.validateUniqueness("Fetch pluggable artifact");
    }

    @Override
    protected void setFetchTaskAttributes(Map attributeMap) {
        configuration.setConfigAttributes(attributeMap, this);
    }

    @Override
    public boolean isSecure(String key) {
        return false;
    }

    @Override
    public String getTaskType() {
        return "fetch_pluggable_artifact";
    }

    @Override
    public String getTypeForDisplay() {
        return FETCH_PLUGGABLE_ARTIFACT;
    }

    @Override
    public List<TaskProperty> getPropertiesForDisplay() {
        return new ArrayList<>();
    }
}
