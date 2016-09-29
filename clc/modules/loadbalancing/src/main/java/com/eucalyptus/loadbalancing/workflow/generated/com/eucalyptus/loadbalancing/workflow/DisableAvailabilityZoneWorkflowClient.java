/*
 * This code was generated by AWS Flow Framework Annotation Processor.
 * Refer to Amazon Simple Workflow Service documentation at http://aws.amazon.com/documentation/swf 
 *
 * Any changes made directly to this file will be lost when 
 * the code is regenerated.
 */
 package com.eucalyptus.loadbalancing.workflow;

import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.StartWorkflowOptions;
import com.amazonaws.services.simpleworkflow.flow.WorkflowClient;

/**
 * Generated from {@link com.eucalyptus.loadbalancing.workflow.DisableAvailabilityZoneWorkflow}. 
 * Used to invoke child workflows asynchronously from parent workflow code.
 * Created through {@link DisableAvailabilityZoneWorkflowClientFactory#getClient}.
 * <p>
 * When running outside of the scope of a workflow use {@link DisableAvailabilityZoneWorkflowClientExternal} instead.
 */
public interface DisableAvailabilityZoneWorkflowClient extends WorkflowClient
{

    /**
     * Generated from {@link com.eucalyptus.loadbalancing.workflow.DisableAvailabilityZoneWorkflow#disableAvailabilityZone}
     */
    Promise<Void> disableAvailabilityZone(String accountId, String loadbalancer, java.util.List<java.lang.String> availabilityZones);

    /**
     * Generated from {@link com.eucalyptus.loadbalancing.workflow.DisableAvailabilityZoneWorkflow#disableAvailabilityZone}
     */
    Promise<Void> disableAvailabilityZone(String accountId, String loadbalancer, java.util.List<java.lang.String> availabilityZones, Promise<?>... waitFor);

    /**
     * Generated from {@link com.eucalyptus.loadbalancing.workflow.DisableAvailabilityZoneWorkflow#disableAvailabilityZone}
     */
    Promise<Void> disableAvailabilityZone(String accountId, String loadbalancer, java.util.List<java.lang.String> availabilityZones, StartWorkflowOptions optionsOverride, Promise<?>... waitFor);

    /**
     * Generated from {@link com.eucalyptus.loadbalancing.workflow.DisableAvailabilityZoneWorkflow#disableAvailabilityZone}
     */
    Promise<Void> disableAvailabilityZone(Promise<String> accountId, Promise<String> loadbalancer, Promise<java.util.List<java.lang.String>> availabilityZones);

    /**
     * Generated from {@link com.eucalyptus.loadbalancing.workflow.DisableAvailabilityZoneWorkflow#disableAvailabilityZone}
     */
    Promise<Void> disableAvailabilityZone(Promise<String> accountId, Promise<String> loadbalancer, Promise<java.util.List<java.lang.String>> availabilityZones, Promise<?>... waitFor);

    /**
     * Generated from {@link com.eucalyptus.loadbalancing.workflow.DisableAvailabilityZoneWorkflow#disableAvailabilityZone}
     */
    Promise<Void> disableAvailabilityZone(Promise<String> accountId, Promise<String> loadbalancer, Promise<java.util.List<java.lang.String>> availabilityZones, StartWorkflowOptions optionsOverride, Promise<?>... waitFor);

}