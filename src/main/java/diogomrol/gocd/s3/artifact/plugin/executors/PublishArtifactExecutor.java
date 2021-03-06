/*
 * Copyright 2018 ThoughtWorks, Inc.
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

package diogomrol.gocd.s3.artifact.plugin.executors;

import diogomrol.gocd.s3.artifact.plugin.ConsoleLogger;
import diogomrol.gocd.s3.artifact.plugin.S3ClientFactory;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import org.apache.commons.text.StringSubstitutor;
import diogomrol.gocd.s3.artifact.plugin.model.ArtifactPlan;
import diogomrol.gocd.s3.artifact.plugin.model.ArtifactStoreConfig;
import diogomrol.gocd.s3.artifact.plugin.model.PublishArtifactRequest;
import diogomrol.gocd.s3.artifact.plugin.model.PublishArtifactResponse;

import java.io.File;
import java.nio.file.Paths;

import static diogomrol.gocd.s3.artifact.plugin.S3ArtifactPlugin.LOG;

public class PublishArtifactExecutor implements RequestExecutor {
    private final PublishArtifactRequest publishArtifactRequest;
    private final PublishArtifactResponse publishArtifactResponse;
    private final ConsoleLogger consoleLogger;
    private final S3ClientFactory clientFactory;

    public PublishArtifactExecutor(GoPluginApiRequest request, ConsoleLogger consoleLogger) {
        this(request, consoleLogger, S3ClientFactory.instance());
    }

    PublishArtifactExecutor(GoPluginApiRequest request, ConsoleLogger consoleLogger, S3ClientFactory clientFactory) {
        this.publishArtifactRequest = PublishArtifactRequest.fromJSON(request.requestBody());
        this.consoleLogger = consoleLogger;
        this.clientFactory = clientFactory;
        publishArtifactResponse = new PublishArtifactResponse();
    }

    @Override
    public GoPluginApiResponse execute() {
        ArtifactPlan artifactPlan = publishArtifactRequest.getArtifactPlan();
        final ArtifactStoreConfig artifactStoreConfig = publishArtifactRequest.getArtifactStore().getArtifactStoreConfig();
        try {
            final AmazonS3 s3 = clientFactory.s3(artifactStoreConfig);
            StringSubstitutor sub = new StringSubstitutor(publishArtifactRequest.getEnvironmentVariables());
            final String sourceFile = sub.replace(artifactPlan.getArtifactPlanConfig().getSource());
            final String destinationFolder = sub.replace(artifactPlan.getArtifactPlanConfig().getDestination());
            final String s3bucket = artifactStoreConfig.getS3bucket();
            final String workingDir = publishArtifactRequest.getAgentWorkingDir();
            String s3bucketPath;

            if(!destinationFolder.isEmpty()) {
                s3bucketPath = Paths.get(s3bucket, destinationFolder).toString().replace("\\", "/");
            }
            else {
                s3bucketPath = Paths.get(s3bucket).toString().replace("\\", "/");
            }

            PutObjectRequest request = new PutObjectRequest(s3bucketPath, sourceFile, new File(Paths.get(workingDir, sourceFile).toString()));
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("plain/text");
            request.setMetadata(metadata);
            s3.putObject(request);

            publishArtifactResponse.addMetadata("Source", sourceFile);
            consoleLogger.info(String.format("Source file `%s` successfully pushed to S3 bucket `%s`.", sourceFile, artifactStoreConfig.getS3bucket()));

            return DefaultGoPluginApiResponse.success(publishArtifactResponse.toJSON());
        } catch (Exception e) {
            consoleLogger.error(String.format("Failed to publish %s: %s", artifactPlan, e));
            LOG.error(String.format("Failed to publish %s: %s", artifactPlan, e.getMessage()), e);
            return DefaultGoPluginApiResponse.error(String.format("Failed to publish %s: %s", artifactPlan, e.getMessage()));
        }
    }
}
