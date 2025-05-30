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

package org.apache.streampipes.connect.container.master.management;

import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.streampipes.connect.api.exception.AdapterException;
import org.apache.streampipes.connect.container.master.util.WorkerPaths;
import org.apache.streampipes.model.connect.adapter.AdapterDescription;
import org.apache.streampipes.model.connect.adapter.AdapterSetDescription;
import org.apache.streampipes.model.connect.adapter.AdapterStreamDescription;
import org.apache.streampipes.model.runtime.RuntimeOptionsRequest;
import org.apache.streampipes.model.runtime.RuntimeOptionsResponse;
import org.apache.streampipes.model.util.Cloner;
import org.apache.streampipes.resource.management.secret.SecretProvider;
import org.apache.streampipes.serializers.json.JacksonSerializer;
import org.apache.streampipes.storage.api.IAdapterStorage;
import org.apache.streampipes.storage.couchdb.impl.AdapterInstanceStorageImpl;
import org.apache.streampipes.storage.management.StorageDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * This client can be used to interact with the adapter workers executing the adapter instances
 */
public class WorkerRestClient {

    private static final Logger logger = LoggerFactory.getLogger(WorkerRestClient.class);

    public static void invokeStreamAdapter(String endpointUrl,
                                           String elementId) throws AdapterException {
        AdapterStreamDescription adapterStreamDescription =  (AdapterStreamDescription) getAndDecryptAdapter(elementId);
        String url = endpointUrl + WorkerPaths.getStreamInvokePath();

        startAdapter(url, adapterStreamDescription);
        updateStreamAdapterStatus(adapterStreamDescription.getElementId(), true);
    }

    public static void stopStreamAdapter(String baseUrl,
                                         AdapterStreamDescription adapterStreamDescription) throws AdapterException {
        String url = baseUrl + WorkerPaths.getStreamStopPath();

        AdapterDescription ad = getAdapterDescriptionById(new AdapterInstanceStorageImpl(), adapterStreamDescription.getElementId());

        stopAdapter(ad, url);
        updateStreamAdapterStatus(adapterStreamDescription.getElementId(), false);
    }

    public static void invokeSetAdapter(String endpointUrl,
                                        AdapterSetDescription adapterSetDescription) throws AdapterException {
        String url = endpointUrl + WorkerPaths.getSetInvokePath();

        startAdapter(url, adapterSetDescription);
    }

    public static void stopSetAdapter(String baseUrl,
                                      AdapterSetDescription adapterSetDescription) throws AdapterException {
        String url = baseUrl + WorkerPaths.getSetStopPath();

        stopAdapter(adapterSetDescription, url);
    }

    public static List<AdapterDescription> getAllRunningAdapterInstanceDescriptions(String url) throws AdapterException {
        try {
            logger.info("Requesting all running adapter description instances: " + url);

            String responseString = Request.Get(url)
                    .connectTimeout(1000)
                    .socketTimeout(100000)
                    .execute().returnContent().asString();

            List<AdapterDescription> result = JacksonSerializer.getObjectMapper().readValue(responseString, List.class);

            return result;
        } catch (IOException e) {
            logger.error("List of running adapters could not be fetched", e);
            throw new AdapterException("List of running adapters could not be fetched from: " + url);
        }
    }

    public static void startAdapter(String url,
                                    AdapterDescription ad) throws AdapterException {
        try {
            logger.info("Trying to start adapter on endpoint: " + url);

            String adapterDescription = JacksonSerializer.getObjectMapper().writeValueAsString(ad);

            String responseString = Request.Post(url)
                    .bodyString(adapterDescription, ContentType.APPLICATION_JSON)
                    .connectTimeout(1000)
                    .socketTimeout(100000)
                    .execute().returnContent().asString();

            logger.info("Adapter started on endpoint: " + url + " with Response: " + responseString);

        } catch (IOException e) {
            logger.error("Adapter did not start", e);
            throw new AdapterException("Adapter with URL: " + url + " did not start");
        }
    }


    public static void stopAdapter(AdapterDescription ad,
                                   String url) throws AdapterException {

        // Stop execution of adapter
        try {
            logger.info("Trying to stop adapter on endpoint: " + url);

            String adapterDescription = JacksonSerializer.getObjectMapper().writeValueAsString(ad);

            // TODO change this to a delete request
            String responseString = Request.Post(url)
                    .bodyString(adapterDescription, ContentType.APPLICATION_JSON)
                    .connectTimeout(1000)
                    .socketTimeout(100000)
                    .execute().returnContent().asString();

            logger.info("Adapter stopped on endpoint: " + url + " with Response: " + responseString);

        } catch (IOException e) {
            logger.error("Adapter was not stopped successfully", e);
            throw new AdapterException("Adapter was not stopped successfully with url: " + url);
        }

    }

    public static RuntimeOptionsResponse getConfiguration(String workerEndpoint,
                                                          String appId,
                                                          RuntimeOptionsRequest runtimeOptionsRequest) throws AdapterException {
        String url = workerEndpoint + WorkerPaths.getRuntimeResolvablePath(appId);

        try {
            String payload = JacksonSerializer.getObjectMapper().writeValueAsString(runtimeOptionsRequest);
            String responseString = Request.Post(url)
                       .bodyString(payload, ContentType.APPLICATION_JSON)
                       .connectTimeout(1000)
                       .socketTimeout(100000)
                       .execute().returnContent().asString();

            return JacksonSerializer.getObjectMapper().readValue(responseString, RuntimeOptionsResponse.class);

        } catch (IOException e) {
            e.printStackTrace();
            throw new AdapterException("Could not resolve runtime configurations from " + url);
        }
    }

    public static String getAssets(String workerPath) throws AdapterException {
        String url = workerPath + "/assets";
        logger.info("Trying to Assets from endpoint: " + url);

        try {
            return Request.Get(url)
                    .connectTimeout(1000)
                    .socketTimeout(100000)
                    .execute().returnContent().asString();
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new AdapterException("Could not get assets endpoint: " + url);
        }

    }

    public static byte[] getIconAsset(String baseUrl) throws AdapterException {
        String url = baseUrl + "/assets/icon";

        try {
            byte[] responseString = Request.Get(url)
                    .connectTimeout(1000)
                    .socketTimeout(100000)
                    .execute().returnContent().asBytes();
            return responseString;
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new AdapterException("Could not get icon endpoint: " + url);
        }
    }

    public static String getDocumentationAsset(String baseUrl) throws AdapterException  {
        String url = baseUrl + "/assets/documentation";
       
        try {
            return Request.Get(url)
                    .connectTimeout(1000)
                    .socketTimeout(100000)
                    .execute().returnContent().asString();
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new AdapterException("Could not get documentation endpoint: " + url);
        }
    }


    private static AdapterDescription getAdapterDescriptionById(AdapterInstanceStorageImpl adapterStorage, String id) {
        AdapterDescription adapterDescription = null;
        List<AdapterDescription> allAdapters = adapterStorage.getAllAdapters();
        for (AdapterDescription a : allAdapters) {
            if (a.getElementId().endsWith(id)) {
                adapterDescription = a;
            }
        }

        return adapterDescription;
    }

    private static void updateStreamAdapterStatus(String adapterId,
                                           boolean running) {
        AdapterStreamDescription adapter = (AdapterStreamDescription) getAndDecryptAdapter(adapterId);
        adapter.setRunning(running);
        encryptAndUpdateAdapter(adapter);
    }

    private static void encryptAndUpdateAdapter(AdapterDescription adapter) {
        AdapterDescription encryptedDescription = new Cloner().adapterDescription(adapter);
        SecretProvider.getEncryptionService().apply(encryptedDescription);
        getAdapterStorage().updateAdapter(encryptedDescription);
    }

    private static AdapterDescription getAndDecryptAdapter(String adapterId) {
        AdapterDescription adapter = getAdapterStorage().getAdapter(adapterId);
        SecretProvider.getDecryptionService().apply(adapter);
        return adapter;
    }

    private static IAdapterStorage getAdapterStorage() {
        return StorageDispatcher.INSTANCE.getNoSqlStore().getAdapterInstanceStorage();
    }
}

