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

import org.apache.commons.lang.RandomStringUtils;
import org.apache.streampipes.commons.exceptions.NoServiceEndpointsAvailableException;
import org.apache.streampipes.commons.exceptions.SepaParseException;
import org.apache.streampipes.connect.adapter.GroundingService;
import org.apache.streampipes.connect.api.exception.AdapterException;
import org.apache.streampipes.connect.container.master.util.WorkerPaths;
import org.apache.streampipes.manager.verification.DataStreamVerifier;
import org.apache.streampipes.model.SpDataStream;
import org.apache.streampipes.model.connect.adapter.AdapterDescription;
import org.apache.streampipes.model.connect.adapter.AdapterStreamDescription;
import org.apache.streampipes.model.grounding.EventGrounding;
import org.apache.streampipes.resource.management.AdapterResourceManager;
import org.apache.streampipes.resource.management.DataStreamResourceManager;
import org.apache.streampipes.resource.management.SpResourceManager;
import org.apache.streampipes.storage.api.IAdapterStorage;
import org.apache.streampipes.storage.couchdb.impl.AdapterInstanceStorageImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

/**
 * This class is responsible for managing all the adapter instances which are executed on worker nodes
 */
public class AdapterMasterManagement {

  private static final Logger LOG = LoggerFactory.getLogger(AdapterMasterManagement.class);

  private final IAdapterStorage adapterInstanceStorage;
  private final AdapterResourceManager adapterResourceManager;

  public AdapterMasterManagement() {
    this.adapterInstanceStorage = getAdapterInstanceStorage();
    this.adapterResourceManager = new SpResourceManager().manageAdapters();
  }

  public AdapterMasterManagement(IAdapterStorage adapterStorage,
                                 AdapterResourceManager adapterResourceManager) {
    this.adapterInstanceStorage = adapterStorage;
    this.adapterResourceManager = adapterResourceManager;
  }

  public String addAdapter(AdapterDescription ad,
                           String principalSid)
          throws AdapterException {

    // Create elementId for adapter
    // TODO centralized provisioning of element id
    String dataStreamElementId = "urn:streampipes.apache.org:eventstream:" + RandomStringUtils.randomAlphabetic(6);
    String uuid = UUID.randomUUID().toString();
    ad.setElementId(ad.getElementId() + ":" + uuid);
    ad.setCreatedAt(System.currentTimeMillis());
    ad.setCorrespondingDataStreamElementId(dataStreamElementId);

    // Add EventGrounding to AdapterDescription
    EventGrounding eventGrounding = GroundingService.createEventGrounding();
    ad.setEventGrounding(eventGrounding);

    String elementId = this.adapterResourceManager.encryptAndCreate(ad);

    // start when stream adapter
    if (ad instanceof AdapterStreamDescription) {
      startStreamAdapter(elementId);
    }

    // Create stream
    SpDataStream storedDescription = new SourcesManagement().createAdapterDataStream(ad, dataStreamElementId);
    storedDescription.setCorrespondingAdapterId(elementId);
    installDataSource(storedDescription, principalSid, true);
    LOG.info("Install source (source URL: {} in backend", ad.getElementId());

    return storedDescription.getElementId();
  }


  public AdapterDescription getAdapter(String elementId) throws AdapterException {
    List<AdapterDescription> allAdapters = adapterInstanceStorage.getAllAdapters();

    if (allAdapters != null && elementId != null) {
      for (AdapterDescription ad : allAdapters) {
        if (elementId.equals(ad.getElementId())) {
          return ad;
        }
      }
    }

    throw new AdapterException("Could not find adapter with id: " + elementId);
  }

  /**
   * First the adapter is stopped removed, then the corresponding data source is deleted
   * @param elementId: The elementId of the adapter instance
   * @throws AdapterException
   */
  public void deleteAdapter(String elementId) throws AdapterException {

    // Stop stream adapter
    if (isStreamAdapter(elementId)) {
      try {
        stopStreamAdapter(elementId);
      } catch (AdapterException e) {
        LOG.info("Could not stop adapter: " + elementId, e);
      }
    }

    AdapterDescription adapter = adapterInstanceStorage.getAdapter(elementId);
    // Delete adapter
    adapterResourceManager.delete(elementId);
    LOG.info("Successfully deleted adapter: " + elementId);

    // Delete data stream
    DataStreamResourceManager resourceManager = new SpResourceManager().manageDataStreams();
    resourceManager.delete(adapter.getCorrespondingDataStreamElementId());
    LOG.info("Successfully deleted data stream: " + adapter.getCorrespondingDataStreamElementId());
  }

  public List<AdapterDescription> getAllAdapterInstances() throws AdapterException {

    List<AdapterDescription> allAdapters = adapterInstanceStorage.getAllAdapters();

    if (allAdapters == null) {
      throw new AdapterException("Could not get all adapters");
    }

    return allAdapters;
  }

  public List<AdapterDescription> getAllAdapterDescriptions() throws AdapterException {

    List<AdapterDescription> allAdapters = adapterInstanceStorage.getAllAdapters();

    if (allAdapters == null) {
      throw new AdapterException("Could not get all adapters");
    }

    return allAdapters;
  }

  public void stopStreamAdapter(String elementId) throws AdapterException {
    AdapterDescription ad = adapterInstanceStorage.getAdapter(elementId);

    if (!isStreamAdapter(elementId)) {
      throw new AdapterException("Adapter " + elementId + "is not a stream adapter.");
    } else {
      WorkerRestClient.stopStreamAdapter(ad.getSelectedEndpointUrl(), (AdapterStreamDescription) ad);
    }
  }

  public void startStreamAdapter(String elementId) throws AdapterException {

    AdapterDescription ad = adapterInstanceStorage.getAdapter(elementId);

    if (!isStreamAdapter(ad)) {
      throw new AdapterException("Adapter " + elementId + "is not a stream adapter.");
    } else {

      try {
        // Find endpoint to start adapter on
        String baseUrl = WorkerPaths.findEndpointUrl(ad.getAppId());

        // Update selected endpoint URL of adapter
        ad.setSelectedEndpointUrl(baseUrl);
        adapterInstanceStorage.updateAdapter(ad);

        // Invoke adapter instance
        WorkerRestClient.invokeStreamAdapter(baseUrl, elementId);

        LOG.info("Started adapter " + elementId + " on: " + baseUrl);
      } catch (NoServiceEndpointsAvailableException | URISyntaxException e) {
        e.printStackTrace();
      }
    }
  }

  private void installDataSource(SpDataStream stream,
                                 String principalSid,
                                 boolean publicElement) throws AdapterException {
    try {
      new DataStreamVerifier(stream).verifyAndAdd(principalSid, publicElement);
    } catch (SepaParseException e) {
      LOG.error("Error while installing data source: {}", stream.getElementId(), e);
      throw new AdapterException();
    }
  }

  private boolean isStreamAdapter(String id) {
    AdapterDescription adapterDescription = adapterInstanceStorage.getAdapter(id);
    return isStreamAdapter(adapterDescription);
  }

  private boolean isStreamAdapter(AdapterDescription adapterDescription) {
    return adapterDescription instanceof AdapterStreamDescription;
  }

  private IAdapterStorage getAdapterInstanceStorage() {
    return new AdapterInstanceStorageImpl();
  }

}
