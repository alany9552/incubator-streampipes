/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.streampipes.connect.container.worker.management;

import org.apache.streampipes.client.StreamPipesClient;
import org.apache.streampipes.model.connect.adapter.AdapterDescription;
import org.apache.streampipes.service.extensions.base.client.StreamPipesClientResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MasterRestClient {

    private static final Logger LOG = LoggerFactory.getLogger(MasterRestClient.class);

    public static boolean register(List<AdapterDescription> allAvailableAdapters) {

        try {
            StreamPipesClient client = new StreamPipesClientResolver().makeStreamPipesClientInstance();
            client.adminApi().registerAdapters(allAvailableAdapters);
            return true;
        } catch (Exception e) {
            LOG.info("Could not register adapter at url " , e);
            return false;
        }
    }

}
