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

package org.apache.streampipes.rest.impl.dashboard;

import org.apache.streampipes.model.dashboard.DashboardModel;
import org.apache.streampipes.resource.management.AbstractDashboardResourceManager;
import org.apache.streampipes.rest.core.base.impl.AbstractAuthGuardedRestResource;
import org.apache.streampipes.rest.shared.annotation.JacksonSerialized;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

public abstract class AbstractDashboardResource extends AbstractAuthGuardedRestResource {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @JacksonSerialized
  @PreAuthorize("hasAnyAuthority(#root.this.getReadRoles())")
  @PostFilter("hasPermission(filterObject.couchDbId, 'READ')")
  public List<DashboardModel> getAllDashboards() {
    return getResourceManager().findAll();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{dashboardId}")
  @PreAuthorize("hasAnyAuthority(#root.this.getReadRoles()) and hasPermission(#dashboardId, 'READ')")
  public DashboardModel getDashboard(@PathParam("dashboardId") String dashboardId) {
    return getResourceManager().find(dashboardId);
  }

  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{dashboardId}")
  @PreAuthorize("hasAnyAuthority(#root.this.getWriteRoles()) and hasPermission(#dashboardModel.couchDbId, 'WRITE')")
  public DashboardModel modifyDashboard(DashboardModel dashboardModel) {
    getResourceManager().update(dashboardModel);
    return getResourceManager().find(dashboardModel.getCouchDbId());
  }

  @DELETE
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{dashboardId}")
  @PreAuthorize("hasAnyAuthority(#root.this.getDeleteRoles()) and hasPermission(#dashboardId, 'DELETE')")
  public Response deleteDashboard(@PathParam("dashboardId") String dashboardId) {
    getResourceManager().delete(dashboardId);
    return ok();
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @PreAuthorize("hasAnyAuthority(#root.this.getWriteRoles())")
  public Response createDashboard(DashboardModel dashboardModel) {
    getResourceManager().create(dashboardModel, getAuthenticatedUserSid());
    return ok();
  }

  protected abstract AbstractDashboardResourceManager getResourceManager();

  /**
   * Do not delete these abstract methods below - required by Spring SPEL (see above)
   */

  public abstract String getWriteRoles();

  public abstract String getReadRoles();

  public abstract String getDeleteRoles();

}
