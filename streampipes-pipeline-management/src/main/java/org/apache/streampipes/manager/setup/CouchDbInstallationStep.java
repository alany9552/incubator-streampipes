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

package org.apache.streampipes.manager.setup;

import org.apache.streampipes.model.client.endpoint.ExtensionsServiceEndpoint;
import org.apache.streampipes.storage.couchdb.impl.ExtensionsServiceEndpointStorageImpl;
import org.apache.streampipes.storage.couchdb.utils.Utils;
import org.lightcouch.DesignDocument;
import org.lightcouch.DesignDocument.MapReduce;
import org.lightcouch.Response;

import java.util.*;

public class CouchDbInstallationStep extends InstallationStep {

  private static final String initRdfEndpointHost = "http://localhost:";
  private static final String PREPARING_NOTIFICATIONS_TEXT = "Preparing database " +
          "'notifications'...";
  private static final String PREPARING_USERS_TEXT = "Preparing database 'users'...";
  private static List<String> initRdfEndpointPorts = new ArrayList<>();

  public CouchDbInstallationStep() {

  }

  @Override
  public void install() {
    createDatabases();
    createViews();
    addRdfEndpoints();
  }

  @Override
  public String getTitle() {
    return "Creating databases...";
  }

  private void createDatabases() {
    try {
      // Set up couchdb internal databases
      Utils.getCouchDbInternalUsersClient();
      Utils.getCouchDbReplicatorClient();
      Utils.getCouchDbGlobalChangesClient();

      // Set up streampipes internal databases
      Utils.getCouchDbUserClient();
      Utils.getCouchDbMonitoringClient();
      Utils.getCouchDbPipelineClient();
      Utils.getCouchDbConnectionClient();
      Utils.getCouchDbNotificationClient();
      Utils.getCouchDbPipelineCategoriesClient();
      Utils.getCouchDbVisualizationClient();
      Utils.getCouchDbRdfEndpointClient();
      Utils.getCouchDbDashboardClient();
      Utils.getCouchDbDashboardWidgetClient();
      Utils.getCouchDbLabelClient();
      Utils.getCouchDbCategoryClient();

      logSuccess(getTitle());
    } catch (Exception e) {
      logFailure(getTitle(), e);
    }
  }

  private void createViews() {
    addUserView();
    addConnectionView();
    addNotificationView();
    addLabelView();
  }

  private void addRdfEndpoints() {
    ExtensionsServiceEndpointStorageImpl rdfEndpointStorage = new ExtensionsServiceEndpointStorageImpl();
    initRdfEndpointPorts
            .forEach(p -> rdfEndpointStorage
                    .addExtensionsServiceEndpoint(new ExtensionsServiceEndpoint(initRdfEndpointHost + p)));

    logSuccess("Discovering pipeline element endpoints...");
  }

  private void addNotificationView() {
    try {
      DesignDocument userDocument = prepareDocument("_design/notificationtypes");
      DesignDocument notificationCountDocument = prepareDocument("_design/unread");

      Map<String, MapReduce> notificationTypeViews = new HashMap<>();
      MapReduce notificationTypeFunction = new MapReduce();
      notificationTypeFunction.setMap("function (doc) { var vizName = doc.title.replace(/\\s/g, '-'); var indexName = doc.correspondingPipelineId + '-' + vizName; emit([indexName, doc.createdAtTimestamp], doc);}");
      notificationTypeViews.put("notificationtypes", notificationTypeFunction);
      userDocument.setViews(notificationTypeViews);
      Response resp = Utils.getCouchDbNotificationClient().design().synchronizeWithDb(userDocument);

      Map<String, MapReduce> notificationCountTypeViews = new HashMap<>();
      MapReduce countFunction = new MapReduce();
      countFunction.setMap("function (doc) {\n" +
              "  var user = doc.targetedAt; \n" +
              "  if (!doc.read) {\n" +
              "    emit(user, 1);\n" +
              "  }\n" +
              "}");
      countFunction.setReduce("function (keys, values, rereduce) {\n" +
              "  if (rereduce) {\n" +
              "    return sum(values);\n" +
              "  } else {\n" +
              "    return values.length;\n" +
              "  }\n" +
              "}");
      notificationCountTypeViews.put("unread", countFunction);
      notificationCountDocument.setViews(notificationCountTypeViews);
      Response countResp =
              Utils.getCouchDbNotificationClient().design().synchronizeWithDb(notificationCountDocument);

      if (resp.getError() != null && countResp != null) {
        logFailure(PREPARING_NOTIFICATIONS_TEXT);
      } else {
        logSuccess(PREPARING_NOTIFICATIONS_TEXT);
      }
    } catch (Exception e) {
      logFailure(PREPARING_NOTIFICATIONS_TEXT, e);
    }
  }

  private void addUserView() {
    try {
      DesignDocument userDocument = prepareDocument("_design/users");
      Map<String, MapReduce> views = new HashMap<>();

      MapReduce passwordFunction = new MapReduce();
      passwordFunction.setMap("function(doc) { if(doc.properties.username && doc.properties.principalType === 'USER_ACCOUNT' && doc.properties.password) { emit(doc.properties.username, doc.properties.password); } }");

      MapReduce usernameFunction = new MapReduce();
      usernameFunction.setMap("function(doc) { if(doc.properties.username) { emit(doc.properties.username, doc); } }");

      MapReduce permissionFunction = new MapReduce();
      permissionFunction.setMap("function(doc) { if(doc.$type === 'permission') { emit(doc._id, doc); } }");

      MapReduce groupFunction = new MapReduce();
      groupFunction.setMap("function(doc) { if(doc.$type === 'group') { emit(doc._id, doc); } }");

      MapReduce tokenFunction = new MapReduce();
      tokenFunction.setMap("function(doc) { if (doc.properties.userApiTokens) { doc.properties.userApiTokens.forEach(function(token) { emit(token.properties.hashedToken, doc.properties.email); });}}");

      MapReduce userPermissionFunction = new MapReduce();
      userPermissionFunction.setMap("function(doc) { if (doc.$type === 'permission') {emit(doc.ownerSid, doc); for(var i = 0; i < doc.grantedAuthorities.length; i++) {emit(doc.grantedAuthorities[i].sid,doc)}}}");

      MapReduce objectPermissionFunction = new MapReduce();
      objectPermissionFunction.setMap("function(doc) { if (doc.$type === 'permission') {emit(doc.objectInstanceId, doc);}}");

      MapReduce userActivationFunction = new MapReduce();
      userActivationFunction.setMap("function(doc) { if (doc.$type === 'user-activation') {emit(doc._id, doc);}}");

      MapReduce passwordRecoveryFunction = new MapReduce();
      passwordRecoveryFunction.setMap("function(doc) { if (doc.$type === 'password-recovery') {emit(doc._id, doc);}}");


      views.put("password", passwordFunction);
      views.put("username", usernameFunction);
      views.put("groups", groupFunction);
      views.put("permissions", permissionFunction);
      views.put("token", tokenFunction);
      views.put("userpermissions", userPermissionFunction);
      views.put("objectpermissions", objectPermissionFunction);
      views.put("user-activation", userActivationFunction);
      views.put("password-recovery", passwordRecoveryFunction);

      userDocument.setViews(views);
      Response resp = Utils.getCouchDbUserClient().design().synchronizeWithDb(userDocument);

      if (resp.getError() != null) {
        logFailure(PREPARING_USERS_TEXT);
      } else {
        logSuccess(PREPARING_USERS_TEXT);
      }
    } catch (Exception e) {
      logFailure(PREPARING_USERS_TEXT, e);
    }
  }

  private void addLabelView() {
    try {
      DesignDocument labelDocument = prepareDocument("_design/categoryId");
      Map<String, MapReduce> views = new HashMap<>();

      MapReduce categoryIdFunction = new MapReduce();
      categoryIdFunction.setMap("function(doc) { if(doc.categoryId) { emit(doc.categoryId, doc); } }");

      views.put("categoryId", categoryIdFunction);

      labelDocument.setViews(views);
      Response resp = Utils.getCouchDbLabelClient().design().synchronizeWithDb(labelDocument);

      if (resp.getError() != null) {
        logFailure(PREPARING_USERS_TEXT);
      } else {
        logSuccess(PREPARING_USERS_TEXT);
      }
    } catch (Exception e) {
      logFailure(PREPARING_USERS_TEXT, e);
    }
  }

  private void addConnectionView() {
    try {
      DesignDocument connectionDocument = prepareDocument("_design/connection");
      Map<String, MapReduce> views = new HashMap<>();

      MapReduce frequentFunction = new MapReduce();
      frequentFunction.setMap("function(doc) { if(doc.from && doc.to) { emit([doc.from, doc.to] , 1 ); } }");
      frequentFunction.setReduce("function (key, values) { return sum(values); }");

      views.put("frequent", frequentFunction);

      connectionDocument.setViews(views);
      Response resp = Utils.getCouchDbConnectionClient().design().synchronizeWithDb(connectionDocument);

      if (resp.getError() != null) {
        logFailure("Preparing database 'connection'...");
      } else {
        logSuccess("Preparing database 'connection'...");
      }
    } catch (Exception e) {
      logFailure("Preparing database 'connection'...", e);
    }
  }

  private DesignDocument prepareDocument(String id) {
    DesignDocument doc = new DesignDocument();
    doc.setLanguage("javascript");
    doc.setId(id);
    return doc;
  }
}
