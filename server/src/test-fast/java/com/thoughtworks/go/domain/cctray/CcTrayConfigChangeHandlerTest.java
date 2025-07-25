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
package com.thoughtworks.go.domain.cctray;

import com.thoughtworks.go.config.*;
import com.thoughtworks.go.config.security.GoConfigPipelinePermissionsAuthority;
import com.thoughtworks.go.config.security.Permissions;
import com.thoughtworks.go.config.security.permissions.NoOnePermission;
import com.thoughtworks.go.config.security.users.AllowedUsers;
import com.thoughtworks.go.config.security.users.NoOne;
import com.thoughtworks.go.config.security.users.Users;
import com.thoughtworks.go.domain.activity.ProjectStatus;
import com.thoughtworks.go.helper.GoConfigMother;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CcTrayConfigChangeHandlerTest {
    @Mock
    private CcTrayCache cache;
    @Mock
    private CcTrayStageStatusLoader stageStatusLoader;
    @Mock
    private GoConfigPipelinePermissionsAuthority pipelinePermissionsAuthority;
    @Captor
    ArgumentCaptor<List<ProjectStatus>> statusesCaptor;

    private GoConfigMother goConfigMother;
    private CcTrayConfigChangeHandler handler;
    private PluginRoleUsersStore pluginRoleUsersStore;

    @BeforeEach
    public void setUp() {
        goConfigMother = new GoConfigMother();
        handler = new CcTrayConfigChangeHandler(cache, stageStatusLoader, pipelinePermissionsAuthority);
        pluginRoleUsersStore = PluginRoleUsersStore.instance();
    }

    @AfterEach
    public void tearDown() {
        pluginRoleUsersStore.clearAll();
    }

    @Test
    public void shouldProvideCCTrayCacheWithAListOfAllProjectsInOrder() {
        ProjectStatus pipeline1_stage1 = new ProjectStatus("pipeline1 :: stage", "Activity1", "Status1", "Label1", new Date(), "stage1-url");
        ProjectStatus pipeline1_stage1_job = new ProjectStatus("pipeline1 :: stage :: job", "Activity1-Job", "Status1-Job", "Label1-Job", new Date(), "job1-url");
        ProjectStatus pipeline2_stage1 = new ProjectStatus("pipeline2 :: stage", "Activity2", "Status2", "Label2", new Date(), "stage2-url");
        ProjectStatus pipeline2_stage1_job = new ProjectStatus("pipeline2 :: stage :: job", "Activity2-Job", "Status2-Job", "Label2-Job", new Date(), "job2-url");

        when(cache.get("pipeline1 :: stage")).thenReturn(pipeline1_stage1);
        when(cache.get("pipeline1 :: stage :: job")).thenReturn(pipeline1_stage1_job);
        when(cache.get("pipeline2 :: stage")).thenReturn(pipeline2_stage1);
        when(cache.get("pipeline2 :: stage :: job")).thenReturn(pipeline2_stage1_job);

        handler.call(GoConfigMother.configWithPipelines("pipeline2", "pipeline1")); /* Adds pipeline1 first in config. Then pipeline2. */

        verify(cache).replaceAllEntriesInCacheWith(eq(List.of(pipeline1_stage1, pipeline1_stage1_job, pipeline2_stage1, pipeline2_stage1_job)));
        verifyNoInteractions(stageStatusLoader);
    }

    @Test
    public void shouldPopulateNewCacheWithProjectsFromOldCacheWhenTheyExist() {
        String stageProjectName = "pipeline1 :: stage";
        String jobProjectName = "pipeline1 :: stage :: job";

        ProjectStatus existingStageStatus = new ProjectStatus(stageProjectName, "OldActivity", "OldStatus", "OldLabel", new Date(), "stage-url");
        when(cache.get(stageProjectName)).thenReturn(existingStageStatus);

        ProjectStatus existingJobStatus = new ProjectStatus(jobProjectName, "OldActivity-Job", "OldStatus-Job", "OldLabel-Job", new Date(), "job-url");
        when(cache.get(jobProjectName)).thenReturn(existingJobStatus);


        handler.call(GoConfigMother.configWithPipelines("pipeline1"));


        verify(cache).replaceAllEntriesInCacheWith(eq(List.of(existingStageStatus, existingJobStatus)));
        verifyNoInteractions(stageStatusLoader);
    }

    @Test
    public void shouldPopulateNewCacheWithStageAndJobFromDB_WhenAStageIsNotFoundInTheOldCache() {
        CruiseConfig config = GoConfigMother.configWithPipelines("pipeline1");

        String stageProjectName = "pipeline1 :: stage";
        String jobProjectName = "pipeline1 :: stage :: job";

        ProjectStatus statusOfStageInDB = new ProjectStatus(stageProjectName, "OldActivity", "OldStatus", "OldLabel", new Date(), "stage-url");
        ProjectStatus statusOfJobInDB = new ProjectStatus(jobProjectName, "OldActivity-Job", "OldStatus-Job", "OldLabel-Job", new Date(), "job-url");
        when(cache.get(stageProjectName)).thenReturn(null);
        when(stageStatusLoader.getStatusesForStageAndJobsOf(pipelineConfigFor(config, "pipeline1"), stageConfigFor(config, "pipeline1", "stage")))
            .thenReturn(List.of(statusOfStageInDB, statusOfJobInDB));

        handler.call(config);


        verify(cache).replaceAllEntriesInCacheWith(eq(List.of(statusOfStageInDB, statusOfJobInDB)));
    }

    @Test
    public void shouldHandleNewStagesInConfig_ByReplacingStagesMissingInDBWithNullStagesAndJobs() {
        CruiseConfig config = new BasicCruiseConfig();
        goConfigMother.addPipeline(config, "pipeline1", "stage1", "job1");
        goConfigMother.addStageToPipeline(config, "pipeline1", "stage2", "job2");

        String stage1ProjectName = "pipeline1 :: stage1";
        String job1ProjectName = "pipeline1 :: stage1 :: job1";
        String stage2ProjectName = "pipeline1 :: stage2";
        String job2ProjectName = "pipeline1 :: stage2 :: job2";

        ProjectStatus statusOfStage1InCache = new ProjectStatus(stage1ProjectName, "OldActivity", "OldStatus", "OldLabel", new Date(), "stage-url");
        ProjectStatus statusOfJob1InCache = new ProjectStatus(job1ProjectName, "OldActivity-Job", "OldStatus-Job", "OldLabel-Job", new Date(), "job1-url");
        when(cache.get(stage1ProjectName)).thenReturn(statusOfStage1InCache);
        when(cache.get(job1ProjectName)).thenReturn(statusOfJob1InCache);

        when(cache.get(stage2ProjectName)).thenReturn(null);
        when(stageStatusLoader.getStatusesForStageAndJobsOf(pipelineConfigFor(config, "pipeline1"), stageConfigFor(config, "pipeline1", "stage2")))
            .thenReturn(Collections.emptyList());


        handler.call(config);


        ProjectStatus expectedNullStatusForStage2 = new ProjectStatus.NullProjectStatus(stage2ProjectName);
        ProjectStatus expectedNullStatusForJob2 = new ProjectStatus.NullProjectStatus(job2ProjectName);
        verify(cache).replaceAllEntriesInCacheWith(eq(List.of(statusOfStage1InCache, statusOfJob1InCache, expectedNullStatusForStage2, expectedNullStatusForJob2)));
    }

    /* Simulate adding a job, when server is down. DB does not know anything about that job. */
    @Test
    public void shouldHandleNewJobsInConfig_ByReplacingJobsMissingInDBWithNullJob() {
        CruiseConfig config = new BasicCruiseConfig();
        goConfigMother.addPipeline(config, "pipeline1", "stage1", "job1", "NEW_JOB_IN_CONFIG");

        String stage1ProjectName = "pipeline1 :: stage1";
        String job1ProjectName = "pipeline1 :: stage1 :: job1";
        String projectNameOfNewJob = "pipeline1 :: stage1 :: NEW_JOB_IN_CONFIG";

        ProjectStatus statusOfStage1InDB = new ProjectStatus(stage1ProjectName, "OldActivity", "OldStatus", "OldLabel", new Date(), "stage-url");
        ProjectStatus statusOfJob1InDB = new ProjectStatus(job1ProjectName, "OldActivity-Job", "OldStatus-Job", "OldLabel-Job", new Date(), "job1-url");
        when(cache.get(stage1ProjectName)).thenReturn(null);
        when(stageStatusLoader.getStatusesForStageAndJobsOf(pipelineConfigFor(config, "pipeline1"), stageConfigFor(config, "pipeline1", "stage1")))
            .thenReturn(List.of(statusOfStage1InDB, statusOfJob1InDB));


        handler.call(config);


        ProjectStatus expectedNullStatusForNewJob = new ProjectStatus.NullProjectStatus(projectNameOfNewJob);
        verify(cache).replaceAllEntriesInCacheWith(eq(List.of(statusOfStage1InDB, statusOfJob1InDB, expectedNullStatusForNewJob)));
    }

    /* Simulate adding a job, in a running system. Cache has the stage info, but not the job info. */
    @Test
    public void shouldHandleNewJobsInConfig_ByReplacingJobsMissingInConfigWithNullJob() {
        String stage1ProjectName = "pipeline1 :: stage1";
        String job1ProjectName = "pipeline1 :: stage1 :: job1";
        String projectNameOfNewJob = "pipeline1 :: stage1 :: NEW_JOB_IN_CONFIG";

        ProjectStatus statusOfStage1InCache = new ProjectStatus(stage1ProjectName, "OldActivity", "OldStatus", "OldLabel", new Date(), "stage-url");
        ProjectStatus statusOfJob1InCache = new ProjectStatus(job1ProjectName, "OldActivity-Job", "OldStatus-Job", "OldLabel-Job", new Date(), "job1-url");
        when(cache.get(stage1ProjectName)).thenReturn(statusOfStage1InCache);
        when(cache.get(job1ProjectName)).thenReturn(statusOfJob1InCache);
        when(cache.get(projectNameOfNewJob)).thenReturn(null);

        CruiseConfig config = new BasicCruiseConfig();
        goConfigMother.addPipeline(config, "pipeline1", "stage1", "job1", "NEW_JOB_IN_CONFIG");


        handler.call(config);


        ProjectStatus expectedNullStatusForNewJob = new ProjectStatus.NullProjectStatus(projectNameOfNewJob);
        verify(cache).replaceAllEntriesInCacheWith(eq(List.of(statusOfStage1InCache, statusOfJob1InCache, expectedNullStatusForNewJob)));
        verifyNoInteractions(stageStatusLoader);
    }

    @Test
    public void shouldRemoveExtraJobsFromCache_WhichAreNoLongerInConfig() {
        String stage1ProjectName = "pipeline1 :: stage1";
        String job1ProjectName = "pipeline1 :: stage1 :: job1";

        ProjectStatus statusOfStage1InCache = new ProjectStatus(stage1ProjectName, "OldActivity", "OldStatus", "OldLabel", new Date(), "stage-url");
        ProjectStatus statusOfJob1InCache = new ProjectStatus(job1ProjectName, "OldActivity-Job", "OldStatus-Job", "OldLabel-Job", new Date(), "job1-url");
        when(cache.get(stage1ProjectName)).thenReturn(statusOfStage1InCache);
        when(cache.get(job1ProjectName)).thenReturn(statusOfJob1InCache);

        CruiseConfig config = new BasicCruiseConfig();
        goConfigMother.addPipeline(config, "pipeline1", "stage1", "job1");


        handler.call(config);


        verify(cache).replaceAllEntriesInCacheWith(eq(List.of(statusOfStage1InCache, statusOfJob1InCache)));
        verifyNoInteractions(stageStatusLoader);
    }

    @Test
    public void shouldUpdateViewPermissionsForEveryProjectBasedOnViewPermissionsOfTheGroup() {
        PluginRoleConfig admin = new PluginRoleConfig("admin", "ldap");
        pluginRoleUsersStore.assignRole("user4", admin);

        Permissions pipeline1Permissions = new Permissions(viewers("user1", "user2"), NoOne.INSTANCE, NoOne.INSTANCE, NoOnePermission.INSTANCE);
        Permissions pipeline2Permissions = new Permissions(new AllowedUsers(Set.of("user3"), Set.of(admin)), NoOne.INSTANCE, NoOne.INSTANCE, NoOnePermission.INSTANCE);
        when(pipelinePermissionsAuthority.pipelinesAndTheirPermissions()).thenReturn(Map.of(new CaseInsensitiveString("pipeline1"), pipeline1Permissions, new CaseInsensitiveString("pipeline2"), pipeline2Permissions));

        CruiseConfig config = GoConfigMother.defaultCruiseConfig();
        goConfigMother.addPipelineWithGroup(config, "group2", "pipeline2", "stage2", "job2");
        goConfigMother.addPipelineWithGroup(config, "group1", "pipeline1", "stage1", "job1");


        handler.call(config);


        verify(cache).replaceAllEntriesInCacheWith(statusesCaptor.capture());
        List<ProjectStatus> statuses = statusesCaptor.getValue();
        assertThat(statuses.size()).isEqualTo(4);

        assertThat(statuses.get(0).name()).isEqualTo("pipeline1 :: stage1");
        assertThat(statuses.get(0).canBeViewedBy("user1")).isTrue();
        assertThat(statuses.get(0).canBeViewedBy("user2")).isTrue();
        assertThat(statuses.get(0).canBeViewedBy("user3")).isFalse();
        assertThat(statuses.get(0).canBeViewedBy("user4")).isFalse();

        assertThat(statuses.get(1).name()).isEqualTo("pipeline1 :: stage1 :: job1");
        assertThat(statuses.get(1).canBeViewedBy("user1")).isTrue();
        assertThat(statuses.get(1).canBeViewedBy("user2")).isTrue();
        assertThat(statuses.get(1).canBeViewedBy("user3")).isFalse();
        assertThat(statuses.get(1).canBeViewedBy("user4")).isFalse();

        assertThat(statuses.get(2).name()).isEqualTo("pipeline2 :: stage2");
        assertThat(statuses.get(2).canBeViewedBy("user1")).isFalse();
        assertThat(statuses.get(2).canBeViewedBy("user2")).isFalse();
        assertThat(statuses.get(2).canBeViewedBy("user3")).isTrue();
        assertThat(statuses.get(2).canBeViewedBy("user4")).isTrue();

        assertThat(statuses.get(3).name()).isEqualTo("pipeline2 :: stage2 :: job2");
        assertThat(statuses.get(3).canBeViewedBy("user1")).isFalse();
        assertThat(statuses.get(3).canBeViewedBy("user2")).isFalse();
        assertThat(statuses.get(3).canBeViewedBy("user3")).isTrue();
        assertThat(statuses.get(3).canBeViewedBy("user4")).isTrue();
    }

    @Test
    public void shouldUpdateCacheWithPipelineDetailsWhenPipelineConfigChanges() {
        String pipeline1Stage = "pipeline1 :: stage1";
        String pipeline1job = "pipeline1 :: stage1 :: job1";

        ProjectStatus statusOfPipeline1StageInCache = new ProjectStatus(pipeline1Stage, "OldActivity", "OldStatus", "OldLabel", new Date(), "p1-stage-url");
        ProjectStatus statusOfPipeline1JobInCache = new ProjectStatus(pipeline1job, "OldActivity-Job", "OldStatus-Job", "OldLabel-Job", new Date(), "p1-job-url");
        when(cache.get(pipeline1Stage)).thenReturn(statusOfPipeline1StageInCache);
        when(cache.get(pipeline1job)).thenReturn(statusOfPipeline1JobInCache);

        PipelineConfig pipeline1Config = GoConfigMother.pipelineHavingJob("pipeline1", "stage1", "job1", "arts", "dir").pipelineConfigByName(new CaseInsensitiveString("pipeline1"));

        handler.call(pipeline1Config);
        @SuppressWarnings("unchecked") ArgumentCaptor<ArrayList<ProjectStatus>> argumentCaptor = ArgumentCaptor.forClass(ArrayList.class);
        verify(cache).putAll(argumentCaptor.capture());

        List<ProjectStatus> allValues = argumentCaptor.getValue();
        assertThat(allValues.get(0).name()).isEqualTo(pipeline1Stage);
        assertThat(allValues.get(1).name()).isEqualTo(pipeline1job);

        verify(cache, atLeastOnce()).get(pipeline1Stage);
        verify(cache, atLeastOnce()).get(pipeline1job);
        verifyNoMoreInteractions(cache);
    }

    @Test
    public void shouldUpdateCacheWithAppropriateViewersForProjectStatusWhenPipelineConfigChanges() {
        String pipeline1Stage = "pipeline1 :: stage1";
        String pipeline1job = "pipeline1 :: stage1 :: job1";

        ProjectStatus statusOfPipeline1StageInCache = new ProjectStatus(pipeline1Stage, "OldActivity", "OldStatus", "OldLabel", new Date(), "p1-stage-url");
        ProjectStatus statusOfPipeline1JobInCache = new ProjectStatus(pipeline1job, "OldActivity-Job", "OldStatus-Job", "OldLabel-Job", new Date(), "p1-job-url");
        when(cache.get(pipeline1Stage)).thenReturn(statusOfPipeline1StageInCache);
        when(cache.get(pipeline1job)).thenReturn(statusOfPipeline1JobInCache);

        PipelineConfig pipeline1Config = GoConfigMother.pipelineHavingJob("pipeline1", "stage1", "job1", "arts", "dir").pipelineConfigByName(new CaseInsensitiveString("pipeline1"));
        when(pipelinePermissionsAuthority.permissionsForPipeline(pipeline1Config.name())).thenReturn(new Permissions(viewers("user1", "user2"), null, null, null));

        handler.call(pipeline1Config);
        @SuppressWarnings("unchecked") ArgumentCaptor<ArrayList<ProjectStatus>> argumentCaptor = ArgumentCaptor.forClass(ArrayList.class);
        verify(cache).putAll(argumentCaptor.capture());

        List<ProjectStatus> allValues = argumentCaptor.getValue();
        assertThat(allValues.get(0).name()).isEqualTo(pipeline1Stage);
        assertThat(allValues.get(0).viewers().contains("user1")).isTrue();
        assertThat(allValues.get(0).viewers().contains("user2")).isTrue();
        assertThat(allValues.get(0).viewers().contains("user3")).isFalse();
        assertThat(allValues.get(1).name()).isEqualTo(pipeline1job);
        assertThat(allValues.get(1).viewers().contains("user1")).isTrue();
        assertThat(allValues.get(1).viewers().contains("user2")).isTrue();
        assertThat(allValues.get(1).viewers().contains("user3")).isFalse();
    }

    private Users viewers(String... users) {
        return new AllowedUsers(Set.of(users), Collections.emptySet());
    }

    private PipelineConfig pipelineConfigFor(CruiseConfig config, String pipelineName) {
        return config.pipelineConfigByName(new CaseInsensitiveString(pipelineName));
    }

    private StageConfig stageConfigFor(CruiseConfig config, String pipelineName, String stageName) {
        return pipelineConfigFor(config, pipelineName).getStage(new CaseInsensitiveString(stageName));
    }
}
