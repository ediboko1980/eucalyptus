/*************************************************************************
 * Copyright 2009-2013 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 ************************************************************************/
package com.eucalyptus.cloudformation;

import com.eucalyptus.cloudformation.entity.StackEntity;
import com.eucalyptus.cloudformation.entity.StackEntityManager;
import com.eucalyptus.cloudformation.entity.StackEventEntityManager;
import com.eucalyptus.cloudformation.entity.StackResourceEntity;
import com.eucalyptus.cloudformation.entity.StackResourceEntityManager;
import com.eucalyptus.cloudformation.resources.Resource;
import com.eucalyptus.cloudformation.template.Template;
import com.eucalyptus.cloudformation.template.TemplateParser;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.UUID;

/**
 * Created by ethomas on 12/19/13.
 */
public class StackCreator extends Thread {
  private static final Logger LOG = Logger.getLogger(StackCreator.class);

  private Stack stack;
  private String templateBody;
  private Template template;

  public StackCreator(Stack stack, String templateBody, Template template) {
    this.stack = stack;
    this.templateBody = templateBody;
    this.template = template;
  }
  @Override
  public void run() {
    try {
    for (Resource resource:template.getResourceList()) {
        new TemplateParser().reevaluateResources(template);
        StackEvent stackEvent = new StackEvent();
        stackEvent.setStackId(stack.getStackId());
        stackEvent.setStackName(stack.getStackName());
        stackEvent.setLogicalResourceId(resource.getLogicalResourceId());
        stackEvent.setPhysicalResourceId(resource.getPhysicalResourceId());
        stackEvent.setEventId(UUID.randomUUID().toString()); //TODO: get real event id
        stackEvent.setResourceProperties(resource.getPropertiesJSON().toString());
        stackEvent.setResourceType(resource.getType());
        stackEvent.setResourceStatus(StackResourceEntity.Status.CREATE_IN_PROGRESS.toString());
        stackEvent.setResourceStatusReason("Part of stack");
        stackEvent.setTimestamp(new Date());
        StackEventEntityManager.addStackEvent(stackEvent);
        StackResource stackResource = new StackResource();
        stackResource.setResourceStatus(StackResourceEntity.Status.CREATE_IN_PROGRESS.toString());
        stackResource.setPhysicalResourceId(resource.getPhysicalResourceId());
        stackResource.setLogicalResourceId(resource.getLogicalResourceId());
        stackResource.setDescription(""); // deal later
        stackResource.setResourceStatusReason("Part of stack");
        stackResource.setResourceType(resource.getType());
        stackResource.setStackName(stack.getStackName());
        stackResource.setStackId(stack.getStackId());
        StackResourceEntityManager.addStackResource(stackResource, resource.getMetadataJSON());
        try {
          resource.create();
          StackResourceEntityManager.updatePhysicalResourceId(stack.getStackName(), resource.getLogicalResourceId(), resource.getPhysicalResourceId());
          StackResourceEntityManager.updateStatus(stack.getStackName(), resource.getLogicalResourceId(), StackResourceEntity.Status.CREATE_COMPLETE, "Complete!");
          stackEvent.setEventId(UUID.randomUUID().toString()); //TODO: get real event id
          stackEvent.setResourceStatus(StackResourceEntity.Status.CREATE_COMPLETE.toString());
          stackEvent.setResourceStatusReason("Complete!");
          stackEvent.setPhysicalResourceId(resource.getPhysicalResourceId());
          stackEvent.setTimestamp(new Date());
          StackEventEntityManager.addStackEvent(stackEvent);
          template.getReferenceMap().get(resource.getLogicalResourceId()).setReady(true);
          template.getReferenceMap().get(resource.getLogicalResourceId()).setReferenceValue(resource.referenceValue());
        } catch (Exception ex) {
          LOG.error(ex, ex);
          StackResourceEntityManager.updateStatus(stack.getStackName(), resource.getLogicalResourceId(), StackResourceEntity.Status.CREATE_FAILED, ""+ex.getMessage());
          stackEvent.setEventId(UUID.randomUUID().toString()); //TODO: get real event id
          stackEvent.setResourceStatus(StackResourceEntity.Status.CREATE_FAILED.toString());
          stackEvent.setTimestamp(new Date());
          stackEvent.setResourceStatusReason(""+ex.getMessage());
          stackEvent.setPhysicalResourceId(resource.getPhysicalResourceId());
          StackEventEntityManager.addStackEvent(stackEvent);
          throw ex;
        }
      }
      StackEntityManager.updateStatus(stack.getStackName(), StackEntity.Status.CREATE_COMPLETE, "Complete!");
    } catch (Exception ex2) {
      LOG.error(ex2, ex2);
      StackEntityManager.updateStatus(stack.getStackName(), StackEntity.Status.CREATE_FAILED, ex2.getMessage());
    }
  }
}
