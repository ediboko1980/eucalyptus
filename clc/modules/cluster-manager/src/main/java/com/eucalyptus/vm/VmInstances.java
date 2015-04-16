/*************************************************************************
 * Copyright 2009-2015 Eucalyptus Systems, Inc.
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
 *
 * This file may incorporate work covered under the following copyright
 * and permission notice:
 *
 *   Software License Agreement (BSD License)
 *
 *   Copyright (c) 2008, Regents of the University of California
 *   All rights reserved.
 *
 *   Redistribution and use of this software in source and binary forms,
 *   with or without modification, are permitted provided that the
 *   following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *   COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *   BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *   LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *   ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE. USERS OF THIS SOFTWARE ACKNOWLEDGE
 *   THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE LICENSED MATERIAL,
 *   COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS SOFTWARE,
 *   AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *   IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA,
 *   SANTA BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY,
 *   WHICH IN THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION,
 *   REPLACEMENT OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO
 *   IDENTIFIED, OR WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT
 *   NEEDED TO COMPLY WITH ANY SUCH LICENSES OR RIGHTS.
 ************************************************************************/

package com.eucalyptus.vm;

import static com.eucalyptus.cluster.ResourceState.VmTypeAvailability;
import static com.eucalyptus.compute.common.network.NetworkingFeature.ElasticIPs;
import static com.eucalyptus.reporting.event.ResourceAvailabilityEvent.Availability;
import static com.eucalyptus.reporting.event.ResourceAvailabilityEvent.Dimension;
import static com.eucalyptus.reporting.event.ResourceAvailabilityEvent.ResourceType;
import static com.eucalyptus.reporting.event.ResourceAvailabilityEvent.ResourceType.*;
import static com.eucalyptus.reporting.event.ResourceAvailabilityEvent.Tag;
import static com.eucalyptus.reporting.event.ResourceAvailabilityEvent.Type;
import static com.eucalyptus.vm.VmVolumeAttachment.deleteOnTerminateFilter;
import static com.eucalyptus.vm.VmVolumeAttachment.volumeIdFilter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityTransaction;

import org.apache.log4j.Logger;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Example;
import org.hibernate.criterion.Restrictions;
import org.hibernate.type.StringType;
import org.xbill.DNS.Name;

import com.eucalyptus.address.Address;
import com.eucalyptus.address.Addresses;
import com.eucalyptus.address.AddressingDispatcher;
import com.eucalyptus.blockstorage.State;
import com.eucalyptus.blockstorage.Storage;
import com.eucalyptus.blockstorage.Volume;
import com.eucalyptus.blockstorage.Volumes;
import com.eucalyptus.blockstorage.msgs.DeleteStorageVolumeResponseType;
import com.eucalyptus.blockstorage.msgs.DeleteStorageVolumeType;
import com.eucalyptus.bootstrap.Bootstrap;
import com.eucalyptus.bootstrap.Hosts;
import com.eucalyptus.compute.common.BlockDeviceMappingItemType;
import com.eucalyptus.compute.common.CloudMetadata.VmInstanceMetadata;
import com.eucalyptus.compute.common.CloudMetadatas;
import com.eucalyptus.compute.common.ImageMetadata;
import com.eucalyptus.cloud.VmInstanceLifecycleHelpers;
import com.eucalyptus.cluster.Cluster;
import com.eucalyptus.cluster.Clusters;
import com.eucalyptus.cluster.callback.TerminateCallback;
import com.eucalyptus.component.Topology;
import com.eucalyptus.compute.common.InstanceStatusEventType;
import com.eucalyptus.compute.common.RunningInstancesItemType;
import com.eucalyptus.compute.common.backend.StopInstancesType;
import com.eucalyptus.compute.common.backend.TerminateInstancesType;
import com.eucalyptus.compute.identifier.ResourceIdentifiers;
import com.eucalyptus.compute.vpc.NetworkInterface;
import com.eucalyptus.compute.vpc.NetworkInterfaceAttachment;
import com.eucalyptus.compute.vpc.NetworkInterfaces;
import com.eucalyptus.configurable.ConfigurableClass;
import com.eucalyptus.configurable.ConfigurableField;
import com.eucalyptus.configurable.ConfigurableProperty;
import com.eucalyptus.configurable.ConfigurablePropertyException;
import com.eucalyptus.configurable.PropertyChangeListener;
import com.eucalyptus.crypto.util.B64;
import com.eucalyptus.entities.Entities;
import com.eucalyptus.entities.PersistenceContexts;
import com.eucalyptus.entities.TransactionException;
import com.eucalyptus.entities.TransactionResource;
import com.eucalyptus.event.ClockTick;
import com.eucalyptus.event.EventListener;
import com.eucalyptus.event.ListenerRegistry;
import com.eucalyptus.event.Listeners;
import com.eucalyptus.images.BlockStorageImageInfo;
import com.eucalyptus.images.BootableImageInfo;
import com.eucalyptus.images.ImageInfo;
import com.eucalyptus.network.NetworkGroup;
import com.eucalyptus.network.NetworkGroups;
import com.eucalyptus.compute.common.network.Networking;
import com.eucalyptus.records.EventRecord;
import com.eucalyptus.records.EventType;
import com.eucalyptus.records.Logs;
import com.eucalyptus.reporting.event.ResourceAvailabilityEvent;
import com.eucalyptus.system.tracking.MessageContexts;
import com.eucalyptus.tags.FilterSupport;
import com.eucalyptus.util.Callback;
import com.eucalyptus.util.CollectionUtils;
import com.eucalyptus.util.Exceptions;
import com.eucalyptus.util.HasNaturalId;
import com.eucalyptus.util.Intervals;
import com.eucalyptus.util.LogUtil;
import com.eucalyptus.util.OwnerFullName;
import com.eucalyptus.util.RestrictedTypes;
import com.eucalyptus.util.RestrictedTypes.QuantityMetricFunction;
import com.eucalyptus.util.RestrictedTypes.Resolver;
import com.eucalyptus.util.Strings;
import com.eucalyptus.util.async.AsyncRequests;
import com.eucalyptus.util.async.Callbacks;
import com.eucalyptus.util.async.DelegatingRemoteCallback;
import com.eucalyptus.util.async.MessageCallback;
import com.eucalyptus.util.async.RemoteCallback;
import com.eucalyptus.vm.VmInstance.Transitions;
import com.eucalyptus.vm.VmInstance.VmState;
import com.eucalyptus.vm.VmInstance.VmStateSet;
import com.eucalyptus.vmtypes.VmType;
import com.eucalyptus.vmtypes.VmTypes;
import com.google.common.base.Enums;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.cache.CacheBuilderSpec;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.ucsb.eucalyptus.msgs.BaseMessage;

@ConfigurableClass( root = "cloud.vmstate",
                    description = "Parameters controlling the lifecycle of virtual machines." )
public class VmInstances {

  private static final String SQL_RESTRICTION_PERSISTENT_VOLUME = "{alias}.id in (select vminstance_id from %smetadata_instances_persistent_volumes where metadata_vm_volume_id=?)";
  private static final String SQL_RESTRICTION_ATTACHED_VOLUME = "{alias}.id in (select vminstance_id from %smetadata_instances_volume_attachments where metadata_vm_volume_id=?)";

  public static class TerminatedInstanceException extends NoSuchElementException {
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    TerminatedInstanceException( final String s ) {
      super( s );
    }
    
  }
  
  public enum Timeout implements Predicate<VmInstance> {
    EXPIRED( VmState.RUNNING ) {
      @Override
      public Integer getMinutes( ) {
        return 0;
      }
      
      @Override
      public boolean apply( VmInstance arg0 ) {
        return VmState.RUNNING.apply( arg0 ) && ( System.currentTimeMillis( ) > arg0.getExpiration( ).getTime( ) );
      }
    },
    UNTOUCHED( VmState.PENDING, VmState.RUNNING ) {
      @Override
      public Integer getMinutes( ) {
        return INSTANCE_TOUCH_INTERVAL;
      }
    },
    UNREPORTED( VmState.PENDING, VmState.RUNNING ) {
      @Override
      public Integer getMinutes( ) {
        return (int) TimeUnit.MINUTES.convert(
            Intervals.parse( INSTANCE_TIMEOUT, TimeUnit.MINUTES, TimeUnit.DAYS.toMillis( 180 ) ),
            TimeUnit.MILLISECONDS );
      }
    },
    SHUTTING_DOWN( VmState.SHUTTING_DOWN ) {
      @Override
      public Integer getMinutes( ) {
        return SHUT_DOWN_TIME;
      }
    },
    STOPPING( VmState.STOPPING ) {
      @Override
      public Integer getMinutes( ) {
        return STOPPING_TIME;
      }
    },
    TERMINATED( VmState.TERMINATED ) {
      @Override
      public Integer getMinutes( ) {
        return TERMINATED_TIME;
      }
    },
    BURIED( VmState.BURIED ) {
      @Override
      public Integer getMinutes( ) {
        return BURIED_TIME;
      }
    },
    ;

    private final List<VmState> states;
    
    private Timeout( final VmState... states ) {
      this.states = Arrays.asList( states );
    }
    
    public abstract Integer getMinutes( );
    
    public Integer getSeconds( ) {
      return this.getMinutes( ) * 60;
    }
    
    public Long getMilliseconds( ) {
      return this.getSeconds( ) * 1000l;
    }
    
    @Override
    public boolean apply( final VmInstance arg0 ) {
      return this.inState( arg0.getState( ) ) && ( arg0.getSplitTime( ) > this.getMilliseconds( ) );
    }
    
    protected boolean inState( final VmState state ) {
      return this.states.contains( state );
    }
    
  }
  
  public static class VmSpecialUserData {
    /*
     * Format:
     * First line must be EUCAKEY_CRED_SETUP:expirationDays
     * The rest is payload
     */
    public static final String EUCAKEY_CRED_SETUP = "euca-"+B64.standard.encString("setup-credential");
    
    private String key = null;
    private String payload = null;
    private String userData = null;
    private String expirationDays = null;
    
    public VmSpecialUserData(final String userData) {
      if (userData == null || userData.isEmpty())
        return;
      int i = userData.indexOf("\n");
      if (i < EUCAKEY_CRED_SETUP.length())
        return;
      final String[] firstLine = userData.substring(0, i).split(":");
      if (firstLine.length != 2){
        LOG.error("Invalid credentials format");
        return;
      }
      this.key = firstLine[0];
      this.expirationDays = firstLine[1];
      this.payload = userData.substring(i+1);
      this.userData = userData;
    }
    
    @Override
    public String toString() {
      return this.userData;
    }

    public String getPayload() {
      return this.payload;
    }
    
    public String getKey() {
      return this.key;
    }
    
    public String getExpirationDays() {
      return this.expirationDays;
    }
    
    public static boolean apply(final String userData) {
      if(userData == null || 
          userData.length()< EUCAKEY_CRED_SETUP.length())
        return false;
      if(!userData.startsWith(EUCAKEY_CRED_SETUP))
        return false;
      
      return true;
    }
  }
  
  public static class UserDataMaxSizeChangeListener implements PropertyChangeListener {
    /**
     * @see com.eucalyptus.configurable.PropertyChangeListener#fireChange(com.eucalyptus.configurable.ConfigurableProperty,
     *      java.lang.Object)
     */
    @Override
    public void fireChange(final ConfigurableProperty t, final Object newValue)
        throws ConfigurablePropertyException {
      LOG.info("in fire change");
      int maxSizeKB = -1;
      try {
        if (newValue == null) {
          throw new NullPointerException("newValue");
        } else if (newValue instanceof String) {
          maxSizeKB = Integer.parseInt((String) newValue);
        } else if (newValue instanceof Integer) {
          maxSizeKB = (Integer) newValue;
        } else {
          throw new ClassCastException("Expecting Integer or String for value, got " + newValue.getClass());
        }
      } catch (Exception ex) {
        throw new ConfigurablePropertyException("Invalid value " + newValue);
      }
      if (maxSizeKB <=0 || maxSizeKB > ABSOLUTE_USER_DATA_MAX_SIZE_KB) {
        throw new ConfigurablePropertyException("Invalid value " + newValue + ", must be between 1 and " + ABSOLUTE_USER_DATA_MAX_SIZE_KB);
      }
      try {
        t.getField().set(null, t.getTypeParser().apply(newValue));
      } catch (IllegalArgumentException e1) {
        e1.printStackTrace();
        throw new ConfigurablePropertyException(e1);
      } catch (IllegalAccessException e1) {
        e1.printStackTrace();
        throw new ConfigurablePropertyException(e1);
      }
    }
  }
  @ConfigurableField( description = "Max length (in KB) that the user data file can be for an instance (after base 64 decoding) " ,
      initial = "16" , changeListener = UserDataMaxSizeChangeListener.class )
  public static String USER_DATA_MAX_SIZE_KB = "16";
  public static final Integer ABSOLUTE_USER_DATA_MAX_SIZE_KB = 64; // This may need to be related to chunk size in the future

  @ConfigurableField( description = "Number of times to retry transactions in the face of potential concurrent update conflicts.",
                      initial = "10" )
  public static final int TX_RETRIES                    = 10;

  @ConfigurableField( description = "Amount of time (default unit minutes) before a previously running instance which is not reported will be marked as terminated.",
                      initial = "180d" )
  public static String    INSTANCE_TIMEOUT              = "180d";

  @ConfigurableField( description = "Amount of time (in minutes) between updates for a running instance.",
                      initial = "15" )
  public static Integer   INSTANCE_TOUCH_INTERVAL       = 15;

  @ConfigurableField( description = "Amount of time (in minutes) before a VM which is not reported by a cluster will be marked as terminated.",
                      initial = "10" )
  public static Integer   SHUT_DOWN_TIME                = 10;

  @ConfigurableField( description = "Amount of time (in minutes) before a stopping VM which is not reported by a cluster will be marked as terminated.",
                      initial = "10" )
  public static Integer   STOPPING_TIME                 = 10;

  @ConfigurableField( description = "Amount of time (in minutes) that a terminated VM will continue to be reported.",
                      initial = "60" )
  public static Integer   TERMINATED_TIME               = 60;

  @ConfigurableField( description = "Amount of time (in minutes) to retain unreported terminated instance data.",
                      initial = "60" )
  public static Integer   BURIED_TIME                   = 60;

  @ConfigurableField( description = "Maximum amount of time (in seconds) that the network topology service takes to propagate state changes.",
                      initial = "" + 60 * 60 * 1000 )
  public static Long      NETWORK_METADATA_REFRESH_TIME = 15l;

  @ConfigurableField( description = "Maximum amount of time (in seconds) that migration state will take to propagate state changes (e.g., to tags).",
                      initial = "" + 60 )
  public static Long      MIGRATION_REFRESH_TIME        = 60l;

  @ConfigurableField( description = "Prefix to use for instance MAC addresses.",
                      initial = "d0:0d" )
  public static String    MAC_PREFIX                    = "d0:0d";

  @ConfigurableField( description = "Subdomain to use for instance DNS.",
                      initial = ".eucalyptus",
                      changeListener = SubdomainListener.class )
  public static String    INSTANCE_SUBDOMAIN            = ".eucalyptus";

  @ConfigurableField( description = "Period (in seconds) between state updates for actively changing state.",
                      initial = "3" )
  public static Long      VOLATILE_STATE_INTERVAL_SEC   = Long.MAX_VALUE;

  @ConfigurableField( description = "Timeout (in seconds) before a requested instance terminate will be repeated.",
                      initial = "60" )
  public static Long      VOLATILE_STATE_TIMEOUT_SEC    = 60l;

  @ConfigurableField( description = "Maximum number of threads the system will use to service blocking state changes.",
                      initial = "16" )
  public static Integer   MAX_STATE_THREADS             = 16;

  @ConfigurableField( description = "Amount of time (in minutes) before a EBS volume backing the instance is created",
                      initial = "30" )
  public static Integer   EBS_VOLUME_CREATION_TIMEOUT   = 30;

  @ConfigurableField( description = "Name for root block device mapping",
                      initial = "emi" )
  public static volatile String EBS_ROOT_DEVICE_NAME    = "emi";

  @ConfigurableField( description = "Amount of time (in seconds) to let instance state settle after a transition to either stopping or shutting-down.",
                      initial = "40" )
  public static Integer   VM_STATE_SETTLE_TIME          = 40;

  @ConfigurableField( description = "Amount of time (in seconds) since completion of the creating run instance operation that the new instance is treated as unreported if not... reported.",
                      initial = "300" )
  public static Integer   VM_INITIAL_REPORT_TIMEOUT     = 300;

  @ConfigurableField( description = "Amount of time (in minutes) before a VM which is not reported by a cluster will fail a reachability test.",
      initial = "5" )
  public static Integer INSTANCE_REACHABILITY_TIMEOUT   = 5;

  @ConfigurableField( description = "Comma separated list of handlers to use for unknown instances ('restore', 'restore-failed', 'terminate', 'terminate-done')",
      initial = "restore-failed", changeListener = UnknownInstanceHandlerChangeListener.class )
  public static String UNKNOWN_INSTANCE_HANDLERS        = "terminate-done, restore-failed, restore";

  @ConfigurableField( description = "Instance metadata user data cache configuration.",
      initial = "maximumSize=50, expireAfterWrite=5s, softValues",
      changeListener = CacheSpecListener.class )
  public static volatile String VM_METADATA_USER_DATA_CACHE   = "maximumSize=50, expireAfterWrite=5s, softValues";

  @ConfigurableField( description = "Instance metadata cache configuration.",
      initial = "maximumSize=250, expireAfterWrite=5s",
      changeListener = CacheSpecListener.class )
  public static volatile String VM_METADATA_INSTANCE_CACHE    = "maximumSize=250, expireAfterWrite=5s";

  @ConfigurableField( description = "Instance metadata instance resolution cache configuration.",
      initial = "maximumSize=250, expireAfterWrite=1s",
      changeListener = CacheSpecListener.class )
  public static volatile String VM_METADATA_REQUEST_CACHE     = "maximumSize=250, expireAfterWrite=1s";

  public static class CacheSpecListener implements PropertyChangeListener {
    @Override
    public void fireChange( final ConfigurableProperty t, final Object newValue ) throws ConfigurablePropertyException {
      try {
        CacheBuilderSpec.parse( String.valueOf( newValue ) );
      } catch ( Exception e ) {
        throw new ConfigurablePropertyException( e.getMessage( ) );
      }
    }
  }

  public static class SubdomainListener implements PropertyChangeListener {
    @Override
    public void fireChange( final ConfigurableProperty t, final Object newValue ) throws ConfigurablePropertyException {
      
      if ( !newValue.toString( ).startsWith( "." ) || newValue.toString( ).endsWith( "." ) )
        throw new ConfigurablePropertyException( "Subdomain must begin and cannot end with a '.' -- e.g., '." + newValue.toString( ).replaceAll( "\\.$", "" )
                                                 + "' is correct." + t.getFieldName( ) );
      
    }
  }

  public static class UnknownInstanceHandlerChangeListener implements PropertyChangeListener {
    @Override
    public void fireChange( final ConfigurableProperty t, final Object newValue ) throws ConfigurablePropertyException {
       final Iterable<Optional<VmInstance.RestoreHandler>> handlers =
           VmInstance.RestoreHandler.parseList( String.valueOf( newValue ) );
       if ( Iterables.size( handlers ) != Iterables.size( Optional.presentInstances( handlers ) ) ) {
         throw new ConfigurablePropertyException( "Invalid unknown instance handler in " + newValue + "; valid values are 'restore', 'restore-failed', 'terminate', 'terminate-done'" );
       }
    }
  }

  private static final Logger LOG = Logger.getLogger( VmInstances.class );
  
  @QuantityMetricFunction( VmInstanceMetadata.class )
  public enum CountVmInstances implements Function<OwnerFullName, Long> {
    INSTANCE;
    
    @Override
    public Long apply( final OwnerFullName ownerFullName ) {
      return
          countPersistentInstances( ownerFullName ) +
          countPendingInstances( ownerFullName );
    }

    private long countPersistentInstances( final OwnerFullName ownerFullName ) {
      try ( TransactionResource tx = Entities.transactionFor( VmInstance.class ) ){
        return Entities.count(
            VmInstance.named( ownerFullName, null ),
            Restrictions.not( criterion( VmStateSet.DONE.array() ) ),
            Collections.<String,String>emptyMap( ) );
      }
    }

    private long countPendingInstances( final OwnerFullName ownerFullName ) {
      long pending = 0;
      for ( final Cluster cluster : Clusters.getInstance( ).listValues( ) ) {
        pending += cluster.getNodeState( ).countUncommittedPendingInstances( ownerFullName );
      }
      return pending;
    }
  }
  
  public static String getId( final Long rsvId, final int launchIndex ) {
    String vmId;
    do {
      vmId = ResourceIdentifiers.generateString( "i" );
    } while ( VmInstances.contains( vmId ) );
    return vmId;
  }
  
  public static VmInstance lookupByPrivateIp( final String ip ) throws NoSuchElementException {
    try ( TransactionResource db = Entities.transactionFor( VmInstance.class ) ) {
      VmInstance vmExample = VmInstance.exampleWithPrivateIp( ip );
      VmInstance vm = ( VmInstance ) Entities.createCriteriaUnique( VmInstance.class )
                                             .add( Example.create( vmExample ) )
                                             .add( Restrictions.in( "state", new VmState[] { VmState.RUNNING, VmState.PENDING } ) )
                                             .uniqueResult( );
      if ( vm == null ) {
        throw new NoSuchElementException( "VmInstance with private ip: " + ip );
      }
      db.commit( );
      return vm;
    } catch ( Exception ex ) {
      Logs.exhaust( ).error( ex, ex );
      throw new NoSuchElementException( ex.getMessage( ) );
    }
  }

  public static boolean privateIpInUse( final String ip ) {
    try ( final TransactionResource tx = Entities.transactionFor( VmInstance.class ) ) {
      VmInstance vmExample = VmInstance.exampleWithPrivateIp( ip );
      return Entities.count(
          vmExample,
          Restrictions.in( "state", new VmState[] { VmState.RUNNING, VmState.PENDING } ),
          Collections.<String,String>emptyMap( ) ) > 0;
    } catch ( Exception ex ) {
      LOG.error( ex, ex );
      return false;
    }
  }

  public static VmVolumeAttachment lookupVolumeAttachment( final String volumeId ) {
    VmVolumeAttachment ret = null;
    try ( final TransactionResource db = Entities.transactionFor( VmInstance.class ) ) {
      final String schemaPrefix =
          Optional.fromNullable( PersistenceContexts.toSchemaName( ).apply( "eucalyptus_cloud" ) )
              .transform( Strings.append( "." ) )
              .or( "" );
      final List<VmInstance> vms = VmInstances.list( null,
          Restrictions.or(
              Restrictions.sqlRestriction( String.format( SQL_RESTRICTION_PERSISTENT_VOLUME, schemaPrefix), volumeId, StringType.INSTANCE ),
              Restrictions.sqlRestriction( String.format( SQL_RESTRICTION_ATTACHED_VOLUME, schemaPrefix), volumeId, StringType.INSTANCE )
          ),
          Collections.<String,String>emptyMap( ),
          null );
      for ( VmInstance vm : vms ) {
        try {
          ret = vm.lookupVolumeAttachment( volumeId );
          if ( ret.getVmInstance( ) == null ) {
            ret.setVmInstance( vm );
          }
        } catch ( NoSuchElementException ex ) {
          continue;
        }
      }
      if ( ret == null ) {
        throw new NoSuchElementException( "VmVolumeAttachment: no volume attachment for " + volumeId );
      }
      db.commit( );
      return ret;
    } catch ( Exception ex ) {
      throw new NoSuchElementException( ex.getMessage( ) );
    }
  }
  
  public static VmVolumeAttachment lookupVolumeAttachment( final String volumeId , final List<VmInstance> vms ) {
    VmVolumeAttachment ret = null;
    try {
      for ( VmInstance vm : vms ) {
        try {
          ret = vm.lookupVolumeAttachment( volumeId );
          if ( ret.getVmInstance( ) == null ) {
            ret.setVmInstance( vm );
          }
        } catch ( NoSuchElementException ex ) {
          continue;
        }
      }
      if ( ret == null ) {
        throw new NoSuchElementException( "VmVolumeAttachment: no volume attachment for " + volumeId );
      }
      return ret;
    } catch ( Exception ex ) {
      throw new NoSuchElementException( ex.getMessage( ) );
    }
  }

  public static List<VmEphemeralAttachment> lookupEphemeralDevices(final String instanceId){
	  try ( TransactionResource db =
	          Entities.transactionFor( VmInstance.class ) ) {
		  final VmInstance vm = Entities.uniqueResult(VmInstance.named(instanceId));
		  final List<VmEphemeralAttachment> ephemeralDisks = 
				  Lists.newArrayList(vm.getBootRecord().getEphemeralStorage());
		  db.commit();
		  return ephemeralDisks;
	  }catch(NoSuchElementException ex){
		  throw ex;
	  }catch(Exception ex){
		  throw Exceptions.toUndeclared(ex);
	  }
  }

  public static Function<VmEphemeralAttachment, BlockDeviceMappingItemType> EphemeralAttachmentToDevice =
		  new Function<VmEphemeralAttachment, BlockDeviceMappingItemType>(){
	  @Override
	  @Nullable
	  public BlockDeviceMappingItemType apply(
			  @Nullable VmEphemeralAttachment input) {
		  final BlockDeviceMappingItemType item = new BlockDeviceMappingItemType();
		  item.setDeviceName( input.getDevice());
		  item.setVirtualName(input.getEphemeralId());
		  return item;
	  }
  };

  public static List<String> lookupPersistentDeviceNames(final String instanceId){
    try ( TransactionResource db =
	        Entities.transactionFor( VmInstance.class ) ) {;
      List<String> deviceNames = new ArrayList<>();
	  final VmInstance vm = Entities.uniqueResult(VmInstance.named(instanceId));
	  for(VmVolumeAttachment vol:vm.getBootRecord().getPersistentVolumes()){
        deviceNames.add(vol.getDevice());
	  }
	  db.commit();
      return deviceNames;
	}catch(NoSuchElementException ex){
	  throw ex;
	}catch(Exception ex){
	  throw Exceptions.toUndeclared(ex);
	}
  }

  public static VmInstance lookupByPublicIp( final String ip ) throws NoSuchElementException {
	try ( TransactionResource db =
	          Entities.transactionFor( VmInstance.class ) ) {
      VmInstance vmExample = VmInstance.exampleWithPublicIp( ip );
      VmInstance vm = ( VmInstance ) Entities.createCriteriaUnique( VmInstance.class )
                                             .add( Example.create( vmExample ) )
                                             .add( criterion( VmState.RUNNING, VmState.PENDING ) )
                                             .uniqueResult();
      if ( vm == null ) {
        throw new NoSuchElementException( "VmInstance with public ip: " + ip );
      }
      db.commit( );
      return vm;
    } catch ( Exception ex ) {
      Logs.exhaust( ).error( ex, ex );
      throw new NoSuchElementException( ex.getMessage( ) );
    }
  }
  
  public static Predicate<VmInstance> withBundleId( final String bundleId ) {
    return new Predicate<VmInstance>( ) {
      @Override
      public boolean apply( final VmInstance vm ) {
        return ( vm.getRuntimeState( ).getBundleTask( ) != null ) && vm.getRuntimeState( ).getBundleTask( ).getBundleId( ).equals( bundleId );
      }
    };
  }
   static Criterion criterion( VmState... state ) {
    return Restrictions.in( "state", state );
  }

  public static VmInstance lookupByBundleId( final String bundleId ) throws NoSuchElementException {
    return Iterables.find( list( withBundleId( bundleId ) ), withBundleId( bundleId ) );
  }

  public static void tryCleanUp( final VmInstance vm ) {
    cleanUp( vm, true );
  }

  public static void cleanUp( final VmInstance vm ) {
    cleanUp( vm, false );
  }

  private static void cleanUp( final VmInstance vm,
                               final boolean rollbackNetworkingOnFailure ) {
    BaseMessage originReq = null;
    try{
      originReq = MessageContexts.lookupLast(vm.getInstanceId(), Sets.<Class>newHashSet(
          TerminateInstancesType.class,
          StopInstancesType.class
          ));
    }catch(final Exception ex){
      ;
    }
    
    final VmState vmLastState = vm.getLastState( );
    final VmState vmState = vm.getState( );
    RuntimeException logEx = new RuntimeException( "Cleaning up instance: " + vm.getInstanceId( ) + " " + vmLastState + " -> " + vmState );
    LOG.debug( logEx.getMessage( ) );
    Logs.extreme( ).info( logEx, logEx );
    try {
      if ( Networking.getInstance( ).supports( ElasticIPs ) && vm.getVpcId( ) == null ) {
        try {
          Address address = Addresses.getInstance( ).lookup( vm.getPublicAddress( ) );
          if ( ( address.isAssigned( ) && vm.getInstanceId( ).equals( address.getInstanceId( ) ) ) //assigned to this instance explicitly
               || ( !address.isReallyAssigned( ) && address.isAssigned( ) && VmState.PENDING.equals( vmLastState ) ) ) { //partial assignment implicitly associated with this failed (PENDING->SHUTTINGDOWN) instance
            if ( address.isSystemOwned( ) ) {
              EventRecord.caller( VmInstances.class, EventType.VM_TERMINATING, "SYSTEM_ADDRESS", address.toString( ) ).debug( );
            } else {
              EventRecord.caller( VmInstances.class, EventType.VM_TERMINATING, "USER_ADDRESS", address.toString( ) ).debug( );
            }
            unassignAddress( vm, address, rollbackNetworkingOnFailure );
          }
        } catch ( final NoSuchElementException e ) {
          //PENDING->SHUTTINGDOWN might happen before address info reported in describe instances by CC, need to try finding address
          if ( VmState.PENDING.equals( vmLastState ) || VmStateSet.DONE.contains( vmState ) ) {
            for ( Address addr : Addresses.getInstance( ).listValues( ) ) {
              if ( addr.getInstanceId( ).equals( vm.getInstanceId( ) ) ) {
                unassignAddress( vm, addr, rollbackNetworkingOnFailure );
                break;
              }
            }
          }
        } catch ( final Exception e1 ) {
          LOG.debug( e1, e1 );
        }
      }
    } catch ( final Exception e ) {
      LOG.error( e );
      Logs.extreme( ).error( e, e );
    }

    try {
      VmInstances.cleanUpAttachedVolumes( vm );
    } catch ( Exception ex ) {
      LOG.error( ex );
      Logs.extreme( ).error( ex, ex );
    }

    try ( final TransactionResource db = Entities.distinctTransactionFor( VmInstance.class ) ) {
      VmInstanceLifecycleHelpers.get().cleanUpInstance( Entities.merge( vm ), vmState );
      db.commit();
    } catch ( Exception ex ) {
      LOG.error( ex );
      Logs.extreme( ).error( ex, ex );
    }

    if ( !rollbackNetworkingOnFailure && VmStateSet.TORNDOWN.apply( vm ) ) {
      try ( final TransactionResource db = Entities.distinctTransactionFor( VmInstance.class ) ) {
        if ( VmStateSet.DONE.apply( vm ) ) {
          Entities.merge( vm ).clearReferences( );
        } else {
          Entities.merge( vm ).clearRunReferences( );
        }
        db.commit();
      } catch ( Exception ex ) {
        LOG.error( ex );
        Logs.extreme( ).error( ex, ex );
      }
    }

    sendTerminate( vm.getInstanceId( ), vm.getPartition( ) );
  }

  static void sendTerminate( final String instanceId, final String partition ) {
    try {
      final TerminateCallback cb = new TerminateCallback( instanceId );
      AsyncRequests.newRequest(  cb ).dispatch( partition );
    } catch ( Exception ex ) {
      LOG.error( ex );
      Logs.extreme( ).error( ex, ex );
    }
  }

  private static void unassignAddress( final VmInstance vm,
                                       final Address address,
                                       final boolean rollbackNetworkingOnFailure ) {
    boolean wasPending = address.isPending();
    if ( wasPending ) try {
      address.clearPending( );
    } catch ( IllegalStateException e ) {
      wasPending = false;
    }
    RemoteCallback<?,?> callback = address.unassign().getCallback();
    Callback.Failure failureHander;
    if ( rollbackNetworkingOnFailure && !wasPending && !VmStateSet.DONE.apply( vm ) ) {
      callback = DelegatingRemoteCallback.suppressException( callback );
      failureHander = new Callback.Failure<java.lang.Object>() {
        @Override
        public void fireException( final Throwable t ) {
          // Revert the cloud state change
          LOG.info( "Unable to assign address " + address.getName() + " for " + vm.getInstanceId() + ", will retry."  );
          if ( address.isPending( ) ) {
            try {
              address.clearPending( );
            } catch ( Exception ex ) {
            }
          }
          try {
            if ( !address.isAllocated() ) {
              address.pendingAssignment();
            }
            address.assign( vm ).clearPending();
          } catch ( Exception e ) {
            LOG.error( e, e );
            LOG.warn( "Address potentially in an inconsistent state: " + LogUtil.dumpObject( address ) );
          }
        }
      };
    } else {
      failureHander = Callbacks.noopFailure();
    }
    AddressingDispatcher.dispatch( AsyncRequests.newRequest( callback ).then( failureHander ), vm.getPartition() );
  }

  private static void cleanUpAttachedVolumes( final String instanceId,
                                              final String qualifier,
                                              final Collection<VmVolumeAttachment> attachments,
                                              final Predicate<? super VmVolumeAttachment> matching ) {
    if ( attachments != null ) {
      for ( final VmVolumeAttachment attachment : Iterables.filter( Lists.newArrayList( attachments ), matching ) ) {
        try {
          LOG.debug( instanceId + ": Marking " + qualifier + " volume EXTANT " + attachment.getVolumeId( ) );
          final Volume volume = Volumes.lookup( null, attachment.getVolumeId( ) );
          if ( State.BUSY.equals( volume.getState( ) ) ) {
            volume.setState( State.EXTANT );
          }
          attachments.remove( attachment );
        } catch ( NoSuchElementException e ) {
          LOG.debug( instanceId + ": Unable to find " + qualifier + " volume not found for cleanup " + attachment.getVolumeId( ) );
        } catch ( Exception ex ) {
          LOG.error( instanceId + ": Failed to cleanup " + qualifier + " volume attachment for " + attachment.getVolumeId( ), ex );
        }
      }
    }
  }

  private static void addMatchingVolumeIds( final Collection<String> volumeIds,
                                            final Collection<VmVolumeAttachment> attachments,
                                            final Predicate<? super VmVolumeAttachment> matching ) {
    CollectionUtils.fluent( attachments )
        .filter( matching )
        .transform( VmVolumeAttachment.volumeId( ) )
        .copyInto( volumeIds );
  }

  // EUCA-6935 Changing the way attached volumes are cleaned up.
  private static void cleanUpAttachedVolumes( final VmInstance vm ) {
    if ( VmStateSet.DONE.apply( vm ) ) {
      final Collection<String> volumesToDelete = Sets.newTreeSet();
      try ( final TransactionResource db = Entities.distinctTransactionFor( VmInstance.class ) ) {
        final VmInstance instance = Entities.merge( vm );

        // Clean up transient volumes
        if ( instance.getTransientVolumeState( ) != null ) {
          cleanUpAttachedVolumes( instance.getInstanceId( ), "transient", instance.getTransientVolumeState( ).getAttachments( ), deleteOnTerminateFilter( false ) );
          addMatchingVolumeIds( volumesToDelete, instance.getTransientVolumeState().getAttachments(), deleteOnTerminateFilter( true ) );
        }

        // Clean up persistent volumes that are not delete-on-terminate
        if ( instance.getBootRecord() != null ) {
          cleanUpAttachedVolumes( instance.getInstanceId( ), "persistent", instance.getBootRecord( ).getPersistentVolumes( ), deleteOnTerminateFilter( false ) );
          addMatchingVolumeIds( volumesToDelete, instance.getBootRecord( ).getPersistentVolumes( ), deleteOnTerminateFilter( true ) );
        }

        db.commit( );
      } catch ( Exception ex ) {
        LOG.error( vm.getInstanceId() + ": Failed to cleanup attached volumes", ex );
      }

      try {
        if ( !volumesToDelete.isEmpty( ) ) {
          LOG.debug( vm.getInstanceId() + ": Cleanup for delete on terminate volumes." );
          for ( final String volumeId : volumesToDelete ) {
            try {
              LOG.debug( vm.getInstanceId() + ": Firing delete request for " + volumeId );
              AsyncRequests.newRequest( new MessageCallback<DeleteStorageVolumeType,DeleteStorageVolumeResponseType>( new DeleteStorageVolumeType( volumeId ) ){
                @Override
                public void initialize( final DeleteStorageVolumeType request ) { }

                @Override
                public void fire( final DeleteStorageVolumeResponseType response ) {
                  Function<DeleteStorageVolumeResponseType,Void> deleteVolume = new Function<DeleteStorageVolumeResponseType,Void>(){
                    @Nullable
                    @Override
                    public Void apply( final DeleteStorageVolumeResponseType deleteStorageVolumeResponseType ) {
                      final Volume volume = Volumes.lookup( null, volumeId );
                      if ( null != response && response.get_return( ) ) {
                        Volumes.annihilateStorageVolume( volume );
                      } else {
                        LOG.error( vm.getInstanceId() + ": Failed to delete volume " +volumeId );
                      }
                      VmInstance instance = Entities.merge( vm );
                      if ( instance.getTransientVolumeState() != null && instance.getTransientVolumeState().getAttachments() != null ) {
                        Iterables.removeIf( instance.getTransientVolumeState().getAttachments(), volumeIdFilter( volumeId ) );
                      }
                      if ( instance.getBootRecord() != null && instance.getBootRecord().getPersistentVolumes() != null ) {
                        Iterables.removeIf( instance.getBootRecord().getPersistentVolumes(), volumeIdFilter( volumeId ) );
                      }
                      return null;
                    }
                  };
                  Entities.asTransaction( Volume.class, deleteVolume ).apply( response );
                }

                @Override
                public void fireException( final Throwable throwable ) {
                  LOG.error( vm.getInstanceId() + ": Failed to delete volume " + volumeId, throwable );
                }
              } ).dispatch( Topology.lookup( Storage.class, vm.lookupPartition() ) );
            } catch ( NoSuchElementException e ) {
              LOG.debug( vm.getInstanceId( ) + ": Persistent volume not found for cleanup " + volumeId );
            } catch ( Exception ex ) {
              LOG.error( vm.getInstanceId() + ": Failed to cleanup persistent volume attachment for " + volumeId, ex );
            }
          }
        }
      } catch ( Exception ex ) {
        LOG.error( vm.getInstanceId() + ": Failed to cleanup attached volumes", ex );
      }
    }
  }

  /**
   * Lookup a VM instance.
   *
   * @param name The instance identifier (display name)
   * @return The instance.
   * @throws NoSuchElementException If the instance is not found
   */
  @Nonnull
  public static VmInstance lookupAny( final String name ) throws NoSuchElementException {
    return PersistentLookup.INSTANCE.apply( name );
  }

  /**
   * Lookup a non-terminated VM instance.
   *
   * @param name The instance identifier (display name)
   * @return The instance.
   * @throws NoSuchElementException If the instance is not found
   * @throws TerminatedInstanceException If the instance is terminated.
   */
  @Nonnull
  public static VmInstance lookup( final String name ) throws NoSuchElementException, TerminatedInstanceException {
    return lookup( ).apply( name );
  }

  /**
   * Function for lookup of non-terminated VM instances.
   *
   * <p>The function parameter is the instance identifier the return is the
   * instance. The function will not return null, but may throw
   * NoSuchElementException or TerminatedInstanceException.</p>
   *
   * @return The function.
   */
  public static Function<String,VmInstance> lookup() {
    return Functions.compose( TerminatedInstanceCheck.INSTANCE, PersistentLookup.INSTANCE );
  }

  public static Predicate<VmInstance> initialize() {
    return InstanceInitialize.INSTANCE;
  }

  public static VmInstance delete( final VmInstance vm ) throws TransactionException {
    try {
      if ( VmStateSet.DONE.apply( vm ) ) {
        delete( vm.getInstanceId( ) );
      }
    } catch ( final Exception ex ) {
      LOG.error( ex, ex );
    }
    return vm;
  }
  
  public static void delete( final String instanceId ) {
    try {
      Entities.asTransaction( VmInstance.class, new Function<String, String>( ) {
        @Override
        public String apply( String input ) {
          try {
            VmInstance entity = Entities.uniqueResult( VmInstance.named( null, input ) );
            Entities.delete( entity );
          } catch ( final NoSuchElementException ex ) {
            LOG.debug( "Instance not found for deletion: " + instanceId );
            Logs.extreme( ).error( ex, ex );
          } catch ( final Exception ex ) {
            LOG.error( "Error deleting instance: " + instanceId + "; " + ex );
            Logs.extreme( ).error( ex, ex );
          }
          return input;
        }
      }, VmInstances.TX_RETRIES ).apply( instanceId );
    } catch ( Exception ex ) {
      LOG.error( ex );
      Logs.extreme( ).error( ex, ex );
    }
  }

  public static void buried( final VmInstance vm ) throws TransactionException {
    Entities.asTransaction( VmInstance.class, Transitions.BURIED, VmInstances.TX_RETRIES ).apply( vm );
  }

  public static void buried( final String key ) throws NoSuchElementException, TransactionException {
    buried( VmInstance.Lookup.INSTANCE.apply( key ) );
  }

  public static void terminated( final VmInstance vm ) throws TransactionException {
    Entities.asTransaction( VmInstance.class, Transitions.TERMINATED, VmInstances.TX_RETRIES ).apply( vm );
  }
  
  public static void terminated( final String key ) throws NoSuchElementException, TransactionException {
    terminated( VmInstance.Lookup.INSTANCE.apply( key ) );
  }
  
  public static void stopped( final VmInstance vm ) throws TransactionException {
    Entities.asTransaction( VmInstance.class, Transitions.STOPPED, VmInstances.TX_RETRIES ).apply( vm );
  }
  
  public static void stopped( final String key ) throws NoSuchElementException, TransactionException {
    VmInstance vm = VmInstance.Lookup.INSTANCE.apply( key );
    if ( vm.getBootRecord( ).getMachine( ) instanceof BlockStorageImageInfo ) {
      VmInstances.stopped( vm );
    }
  }
  
  public static void shutDown( final VmInstance vm ) throws TransactionException {
    if ( !VmStateSet.DONE.apply( vm ) ) {
      Entities.asTransaction( VmInstance.class, Transitions.SHUTDOWN, VmInstances.TX_RETRIES ).apply( vm );
    }
  }

  public static void reachable( final VmInstance vm ) throws TransactionException {
    transactional( VmInstance.InstanceStatusUpdate.REACHABLE ).apply( vm );
  }

  public static void unreachable( final VmInstance vm ) throws TransactionException {
    transactional( VmInstance.InstanceStatusUpdate.UNREACHABLE ).apply( vm );
  }

  private static Function<VmInstance,VmInstance> transactional( final Function<VmInstance,VmInstance> update ) {
    return Entities.asTransaction( VmInstance.class, update, VmInstances.TX_RETRIES );
  }

  /**
   * List instances that are not done and match the given predicate.
   *
   * @param predicate The predicate to match
   * @return The matching instances
   * @see VmStateSet#DONE
   */
  public static List<VmInstance> list( @Nullable Predicate<? super VmInstance> predicate ) {
    return list( (OwnerFullName) null, predicate );
  }

  /**
   * List instances that are not done and match the given owner/predicate.
   *
   * @param ownerFullName The owning user or account
   * @param predicate The predicate to match
   * @return The matching instances
   * @see VmStateSet#DONE
   */
  public static List<VmInstance> list( @Nullable OwnerFullName ownerFullName,
                                       @Nullable Predicate<? super VmInstance> predicate ) {
    return list(
        ownerFullName,
        Restrictions.not( criterion( VmStateSet.DONE.array( ) ) ),
        Collections.<String,String>emptyMap(),
        Predicates.and( VmStateSet.DONE.not( ), predicate ) );
  }

  /**
   * List instances in any state that match the given parameters.
   */
  public static List<VmInstance> list( @Nullable final OwnerFullName ownerFullName,
                                       final Criterion criterion,
                                       final Map<String,String> aliases,
                                       @Nullable final Predicate<? super VmInstance> predicate ) {
    return list( new Supplier<List<VmInstance>>() {
      @Override
      public List<VmInstance> get() {
        return Entities.query( VmInstance.named( ownerFullName, null ), false, criterion, aliases );
      }
    }, Predicates.and(
        RestrictedTypes.filterByOwner( ownerFullName ),
        checkPredicate( predicate )
    ) );
  }

  /**
   * List instances in any state that match the given parameters.
   */
  public static List<VmInstance> listByClientToken( @Nullable final OwnerFullName ownerFullName,
                                                    @Nullable final String clientToken,
                                                    @Nullable Predicate<? super VmInstance> predicate ) {
    return list( new Supplier<List<VmInstance>>() {
      @Override
      public List<VmInstance> get() {
        return Entities.query( VmInstance.withToken( ownerFullName, clientToken ) );
      }
    }, Predicates.and(
        CollectionUtils.propertyPredicate( clientToken, VmInstanceFilterFunctions.CLIENT_TOKEN ),
        RestrictedTypes.filterByOwner( ownerFullName ),
        checkPredicate( predicate )
        ) );
  }

  private static List<VmInstance> list( @Nonnull Supplier<List<VmInstance>> instancesSupplier,
                                        @Nullable Predicate<? super VmInstance> predicate ) {
    predicate = checkPredicate( predicate );
    return listPersistent( instancesSupplier, predicate );
  }

  private static List<VmInstance> listPersistent( @Nonnull Supplier<List<VmInstance>> instancesSupplier,
                                                  @Nonnull Predicate<? super VmInstance> predicate ) {
    final EntityTransaction db = Entities.get( VmInstance.class );
    try {
      final Iterable<VmInstance> vms = Iterables.filter( instancesSupplier.get(), predicate );
      final List<VmInstance> instances = Lists.newArrayList( vms );
      db.commit( );
      return instances;
    } catch ( final Exception ex ) {
      LOG.error( ex );
      Logs.extreme( ).error( ex, ex );
      return Lists.newArrayList( );
    } finally {
      if ( db.isActive() ) db.rollback();
    }
  }
  
  private static <T> Predicate<T> checkPredicate( Predicate<T> predicate ) {
    return predicate == null ?
        Predicates.<T>alwaysTrue() :
        predicate;
  }
  
  public static boolean contains( final String name ) {
    final EntityTransaction db = Entities.get( VmInstance.class );
    try {
      final VmInstance vm = Entities.uniqueResult( VmInstance.named( null, name ) );
      db.commit( );
      return true;
    } catch ( final RuntimeException ex ) {
      return false;
    } catch ( final TransactionException ex ) {
      return false;
    } finally {
      if ( db.isActive() ) db.rollback();
    }
  }
  
  /**
   *
   */
  public static RunningInstancesItemType transform( final VmInstance vm ) {
    return VmInstance.Transform.INSTANCE.apply( vm );
  }

  public static Function<VmInstance,String> toNodeHost() {
    return VmInstanceFilterFunctions.NODE_HOST;
  }

  public static Function<VmInstance,String> toInstanceUuid() {
    return Functions.compose( HasNaturalId.Utils.toNaturalId(), Functions.<VmInstance>identity() );
  }

  public static String dnsName( final String ip, final Name domain ) {
    final String suffix = domain.relativize( Name.root ).toString( );
    return "euca-" + ip.replace( '.', '-' ) + VmInstances.INSTANCE_SUBDOMAIN + "." + suffix;
  }

  @Resolver( VmInstanceMetadata.class )
  enum PersistentLookup implements Function<String, VmInstance> {
    INSTANCE;
    
    /**
     * @see com.google.common.base.Function#apply(java.lang.Object)
     */
    @Nonnull
    @Override
    public VmInstance apply( final String name ) {
      return VmInstance.Lookup.INSTANCE.apply( name );
    }
  }

  enum TerminatedInstanceCheck implements Function<VmInstance,VmInstance> {
    INSTANCE;

    @Nullable
    @Override
    public VmInstance apply( final VmInstance instance ) {
      if ( instance != null && VmStateSet.DONE.apply( instance ) ) {
        throw new TerminatedInstanceException( instance.getDisplayName( ) );
      }
      return instance;
    }
  }

  private enum InstanceInitialize implements Predicate<VmInstance> {
    INSTANCE;

    @Override
    public boolean apply( final VmInstance input ) {
      Entities.initialize( input.getNetworkGroups( ) );
      Entities.initialize( input.getNetworkGroupIds( ) );
      Entities.initialize( input.getTags( ) );
      input.getRuntimeState( ).getReason( ); // Initializes reason details
      Entities.initialize( input.getBootRecord( ).getPersistentVolumes( ) );
      Entities.initialize( input.getTransientVolumeState( ).getAttachments( ) );
      return true;
    }
  }

  /**
   *
   */
  public static RunningInstancesItemType transform( final String name ) {
    return VmInstance.Transform.INSTANCE.apply( lookup( name ) );
  }

  public static Function<VmInstance,VmBundleTask> bundleTask() {
    return VmInstanceToVmBundleTask.INSTANCE;
  }

  public static class VmInstanceAvailabilityEventListener implements EventListener<ClockTick> {

    private static final class AvailabilityAccumulator {
      private long total;
      private long available;
      private final Function<VmType,Integer> valueExtractor;
      private final List<Availability> availabilities = Lists.newArrayList();

      private AvailabilityAccumulator( final Function<VmType,Integer> valueExtractor ) {
        this.valueExtractor = valueExtractor;
      }

      private void rollUp( final Iterable<Tag> tags ) {
        availabilities.add( new Availability( total, available, tags ) );
        total = 0;
        available = 0;
      }
    }

    public static void register( ) {
      Listeners.register( ClockTick.class, new VmInstanceAvailabilityEventListener() );
    }

    @Override
    public void fireEvent( final ClockTick event ) {
      if ( Bootstrap.isFinished() && Hosts.isCoordinator() ) {

        final List<ResourceAvailabilityEvent> resourceAvailabilityEvents = Lists.newArrayList();
        final Map<ResourceType,AvailabilityAccumulator> availabilities = Maps.newEnumMap(ResourceType.class);
        final Iterable<VmType> vmTypes = Lists.newArrayList(VmTypes.list());
        for ( final Cluster cluster : Clusters.getInstance().listValues() ) {
          availabilities.put( Core, new AvailabilityAccumulator( VmType.SizeProperties.Cpu ) );
          availabilities.put( Disk, new AvailabilityAccumulator( VmType.SizeProperties.Disk ) );
          availabilities.put( Memory, new AvailabilityAccumulator( VmType.SizeProperties.Memory ) );

          for ( final VmType vmType : vmTypes ) {
            final VmTypeAvailability va = cluster.getNodeState().getAvailability( vmType.getName() );

            resourceAvailabilityEvents.add( new ResourceAvailabilityEvent( Instance, new Availability( va.getMax(), va.getAvailable(), Lists.<Tag>newArrayList(
                new Dimension( "availabilityZone", cluster.getPartition() ),
                new Dimension( "cluster", cluster.getName() ),
                new Type( "vm-type", vmType.getName() )
                ) ) ) );

            for ( final AvailabilityAccumulator availability : availabilities.values() ) {
              availability.total = Math.max( availability.total, va.getMax() * availability.valueExtractor.apply(vmType) );
              availability.available = Math.max( availability.available, va.getAvailable() * availability.valueExtractor.apply(vmType) );
            }
          }

          for ( final AvailabilityAccumulator availability : availabilities.values() ) {
            availability.rollUp(  Lists.<Tag>newArrayList(
                new Dimension( "availabilityZone", cluster.getPartition() ),
                new Dimension( "cluster", cluster.getName() )
            ) );
          }
        }

        for ( final Map.Entry<ResourceType,AvailabilityAccumulator> entry : availabilities.entrySet() )  {
          resourceAvailabilityEvents.add( new ResourceAvailabilityEvent( entry.getKey(), entry.getValue().availabilities ) );
        }

        for ( final ResourceAvailabilityEvent resourceAvailabilityEvent : resourceAvailabilityEvents  ) try {
          ListenerRegistry.getInstance().fireEvent( resourceAvailabilityEvent );
        } catch ( Exception ex ) {
          LOG.error( ex, ex );
        }

      }
    }
  }

  private static <T> Set<T> blockDeviceSet( final VmInstance instance,
                                            final Function<? super VmVolumeAttachment,T> transform ) {
    return Sets.newHashSet( Iterables.transform(
        Iterables.concat(
            instance.getBootRecord( ).getPersistentVolumes( ),
            instance.getTransientVolumeState( ).getAttachments( ) ),
        transform ) );
  }

  private static <T> Set<T> networkGroupSet( final VmInstance instance,
                                             final Function<? super NetworkGroup,T> transform ) {
    return instance.getNetworkGroups() != null ?
        Sets.newHashSet( Iterables.transform( instance.getNetworkGroups(), transform ) ) :
        Collections.<T>emptySet();
  }

  private static <T> Set<T> networkInterfaceSet( final VmInstance instance,
                                                 final Function<? super NetworkInterface,T> transform ) {
    return instance.getNetworkInterfaces() != null ?
        Sets.newHashSet( Iterables.transform( instance.getNetworkInterfaces(), transform ) ) :
        Collections.<T>emptySet( );
  }

  private static <T> Set<T> networkInterfaceSetSet( final VmInstance instance,
                                                    final Function<? super NetworkInterface,Set<T>> transform ) {
    return instance.getNetworkInterfaces() != null ?
        Sets.newHashSet( Iterables.concat( Iterables.transform( instance.getNetworkInterfaces(), transform ) ) ) :
        Collections.<T>emptySet( );
  }

  public static class VmInstanceFilterSupport extends FilterSupport<VmInstance> {
    public VmInstanceFilterSupport() {
      super( builderFor( VmInstance.class )
          .withTagFiltering( VmInstanceTag.class, "instance" )
          .withStringProperty( "architecture", VmInstanceFilterFunctions.ARCHITECTURE )
          .withStringProperty( "availability-zone", VmInstanceFilterFunctions.AVAILABILITY_ZONE )
          .withDateSetProperty( "block-device-mapping.attach-time", VmInstanceDateSetFilterFunctions.BLOCK_DEVICE_MAPPING_ATTACH_TIME )
          .withBooleanSetProperty( "block-device-mapping.delete-on-termination", VmInstanceBooleanSetFilterFunctions.BLOCK_DEVICE_MAPPING_DELETE_ON_TERMINATE )
          .withStringSetProperty( "block-device-mapping.device-name", VmInstanceStringSetFilterFunctions.BLOCK_DEVICE_MAPPING_DEVICE_NAME )
          .withStringSetProperty( "block-device-mapping.status", VmInstanceStringSetFilterFunctions.BLOCK_DEVICE_MAPPING_STATUS )
          .withStringSetProperty( "block-device-mapping.volume-id", VmInstanceStringSetFilterFunctions.BLOCK_DEVICE_MAPPING_VOLUME_ID )
          .withStringProperty( "client-token", VmInstanceFilterFunctions.CLIENT_TOKEN )
          .withStringProperty( "dns-name", VmInstanceFilterFunctions.DNS_NAME )
          .withStringSetProperty( "group-id", VmInstanceStringSetFilterFunctions.GROUP_ID )
          .withStringSetProperty( "group-name", VmInstanceStringSetFilterFunctions.GROUP_NAME )
          .withStringProperty( "image-id", VmInstanceFilterFunctions.IMAGE_ID )
          .withStringProperty( "iam-instance-profile.arn", VmInstanceFilterFunctions.INSTANCE_PROFILE_ARN )
          .withStringProperty( "instance-id", CloudMetadatas.toDisplayName() )
          .withConstantProperty( "instance-lifecycle", "" )
          .withIntegerProperty( "instance-state-code", VmInstanceIntegerFilterFunctions.INSTANCE_STATE_CODE )
          .withStringProperty( "instance-state-name", VmInstanceFilterFunctions.INSTANCE_STATE_NAME )
          .withStringProperty( "instance-type", VmInstanceFilterFunctions.INSTANCE_TYPE )
          .withStringSetProperty( "instance.group-id", VmInstanceStringSetFilterFunctions.GROUP_ID )
          .withStringSetProperty( "instance.group-name", VmInstanceStringSetFilterFunctions.GROUP_NAME )
          .withStringProperty( "ip-address", VmInstanceFilterFunctions.IP_ADDRESS )
          .withStringProperty( "kernel-id", VmInstanceFilterFunctions.KERNEL_ID )
          .withStringProperty( "key-name", VmInstanceFilterFunctions.KEY_NAME )
          .withStringProperty( "launch-index", VmInstanceFilterFunctions.LAUNCH_INDEX )
          .withDateProperty( "launch-time", VmInstanceDateFilterFunctions.LAUNCH_TIME )
          .withStringProperty( "monitoring-state", VmInstanceFilterFunctions.MONITORING_STATE )
          .withStringProperty( "owner-id", VmInstanceFilterFunctions.OWNER_ID )
          .withUnsupportedProperty( "placement-group-name" )
          .withStringProperty( "platform", VmInstanceFilterFunctions.PLATFORM )
          .withStringProperty( "private-dns-name", VmInstanceFilterFunctions.PRIVATE_DNS_NAME )
          .withStringProperty( "private-ip-address", VmInstanceFilterFunctions.PRIVATE_IP_ADDRESS )
          .withUnsupportedProperty( "product-code" )
          .withUnsupportedProperty( "product-code.type" )
          .withStringProperty( "ramdisk-id", VmInstanceFilterFunctions.RAMDISK_ID )
          .withStringProperty( "reason", VmInstanceFilterFunctions.REASON )
          .withUnsupportedProperty( "requester-id" )
          .withStringProperty( "reservation-id", VmInstanceFilterFunctions.RESERVATION_ID )
          .withStringProperty( "root-device-name", VmInstanceFilterFunctions.ROOT_DEVICE_NAME )
          .withStringProperty( "root-device-type", VmInstanceFilterFunctions.ROOT_DEVICE_TYPE )
          .withUnsupportedProperty( "source-dest-check" )
          .withUnsupportedProperty( "spot-instance-request-id" )
          .withUnsupportedProperty( "state-reason-code" )
          .withUnsupportedProperty( "state-reason-message" )
          .withStringProperty( "subnet-id", VmInstanceFilterFunctions.SUBNET_ID )
          .withConstantProperty( "tenancy", "default" )
          .withStringProperty( "virtualization-type", VmInstanceFilterFunctions.VIRTUALIZATION_TYPE )
          .withStringProperty( "vpc-id", VmInstanceFilterFunctions.VPC_ID )
          .withUnsupportedProperty( "hypervisor" )
          .withStringSetProperty( "network-interface.description", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_DESCRIPTION )
          .withStringSetProperty( "network-interface.subnet-id", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_SUBNET_ID )
          .withStringSetProperty( "network-interface.vpc-id", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_VPC_ID )
          .withStringSetProperty( "network-interface.network-interface.id", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_ID )
          .withStringSetProperty( "network-interface.owner-id", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_OWNER_ID )
          .withStringSetProperty( "network-interface.availability-zone", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_AVAILABILITY_ZONE )
          .withUnsupportedProperty( "network-interface.requester-id" )
          .withUnsupportedProperty( "network-interface.requester-managed" )
          .withStringSetProperty( "network-interface.status", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_STATUS )
          .withStringSetProperty( "network-interface.mac-address", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_MAC_ADDRESS )
          .withStringSetProperty( "network-interface-private-dns-name", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_PRIVATE_DNS_NAME )
          .withBooleanSetProperty( "network-interface.source-destination-check", VmInstanceBooleanSetFilterFunctions.NETWORK_INTERFACE_SOURCE_DEST_CHECK )
          .withStringSetProperty( "network-interface.group-id", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_GROUP_ID )
          .withStringSetProperty( "network-interface.group-name", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_GROUP_NAME )
          .withStringSetProperty( "network-interface.addresses.private-ip-address", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_PRIVATE_IP )
          .withBooleanSetProperty( "network-interface.addresses.primary", VmInstanceBooleanSetFilterFunctions.NETWORK_INTERFACE_ADDRESSES_PRIMARY )
          .withStringSetProperty( "network-interface.addresses.association.public-ip", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_ASSOCIATION_PUBLIC_IP )
          .withStringSetProperty( "network-interface.addresses.association.ip-owner-id", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_ASSOCIATION_IP_OWNER_ID )
          .withStringSetProperty( "network-interface.attachment.attachment-id", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_ATTACHMENT_ID )
          .withStringSetProperty( "network-interface.attachment.instance-id", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_ATTACHMENT_INSTANCE_ID )
          .withStringSetProperty( "network-interface.attachment.instance-owner-id", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_ATTACHMENT_INSTANCE_OWNER_ID )
          .withIntegerSetProperty( "network-interface.attachment.device-index", VmInstanceIntegerSetFilterFunctions.NETWORK_INTERFACE_ATTACHMENT_DEVICE_INDEX )
          .withStringSetProperty( "network-interface.attachment.status", VmInstanceStringSetFilterFunctions.NETWORK_INTERFACE_ATTACHMENT_STATUS )
          .withDateSetProperty( "network-interface.attachment.attach-time", VmInstanceDateSetFilterFunctions.NETWORK_INTERFACE_ATTACHMENT_ATTACH_TIME )
          .withBooleanSetProperty( "network-interface.attachment.delete-on-termination", VmInstanceBooleanSetFilterFunctions.NETWORK_INTERFACE_ATTACHMENT_DELETE_ON_TERMINATION )
          .withStringSetProperty( "association.public-ip", VmInstanceStringSetFilterFunctions.ASSOCIATION_PUBLIC_IP )
          .withStringSetProperty( "association.ip-owner-id", VmInstanceStringSetFilterFunctions.ASSOCIATION_IP_OWNER_ID )
          .withStringSetProperty( "association.allocation-id", VmInstanceStringSetFilterFunctions.ASSOCIATION_ALLOCATION_ID )
          .withStringSetProperty( "association.association-id", VmInstanceStringSetFilterFunctions.ASSOCIATION_ID )
          .withPersistenceAlias( "bootRecord.machineImage", "image" )
          .withPersistenceAlias( "networkGroups", "networkGroups" )
          .withPersistenceAlias( "bootRecord.vmType", "vmType" )
          .withPersistenceAlias( "networkConfig.networkInterfaces", "networkInterfaces" )
          .withPersistenceAlias( "networkInterfaces.vpc", "vpc" )
          .withPersistenceAlias( "networkInterfaces.subnet", "subnet" )
          .withPersistenceAlias( "networkInterfaces.networkGroups", "networkInterfaceNetworkGroups" )
          .withPersistenceFilter( "architecture", "image.architecture", Sets.newHashSet( "bootRecord.machineImage" ), Enums.valueOfFunction( ImageMetadata.Architecture.class ) )
          .withPersistenceFilter( "availability-zone", "placement.partitionName", Collections.<String>emptySet() )
          .withPersistenceFilter( "client-token", "vmId.clientToken", Collections.<String>emptySet() )
          .withPersistenceFilter( "group-id", "networkGroups.groupId" )
          .withPersistenceFilter( "group-name", "networkGroups.displayName" )
          .withPersistenceFilter( "iam-instance-profile.arn", "bootRecord.iamInstanceProfileArn", Collections.<String>emptySet() )
          .withPersistenceFilter( "image-id", "image.displayName", Sets.newHashSet( "bootRecord.machineImage" ) )
          .withPersistenceFilter( "instance-id", "displayName" )
          .withPersistenceFilter( "instance-type", "vmType.name", Sets.newHashSet( "bootRecord.vmType" ) )
          .withPersistenceFilter( "instance.group-id", "networkGroups.groupId" )
          .withPersistenceFilter( "instance.group-name", "networkGroups.displayName" )
          .withPersistenceFilter( "kernel-id", "image.kernelId", Sets.newHashSet( "bootRecord.machineImage" ) )
          .withPersistenceFilter( "launch-index", "launchRecord.launchIndex", Collections.<String>emptySet(), PersistenceFilter.Type.Integer )
          .withPersistenceFilter( "launch-time", "launchRecord.launchTime", Collections.<String>emptySet(), PersistenceFilter.Type.Date )
          .withPersistenceFilter( "owner-id", "ownerAccountNumber" )
          .withPersistenceFilter( "ramdisk-id", "image.ramdiskId", Sets.newHashSet( "bootRecord.machineImage" ) )
          .withPersistenceFilter( "reservation-id", "vmId.reservationId", Collections.<String>emptySet() )
          .withPersistenceFilter( "subnet-id", "bootRecord.subnetId", Collections.<String>emptySet() )
          .withPersistenceFilter( "virtualization-type", "bootRecord.virtType", Collections.<String>emptySet(), ImageMetadata.VirtualizationType.fromString() )
          .withPersistenceFilter( "vpc-id", "bootRecord.vpcId", Collections.<String>emptySet() )
          .withPersistenceFilter( "network-interface.description", "networkInterfaces.description", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "network-interface.subnet-id", "subnet.displayName", Sets.newHashSet( "networkConfig.networkInterfaces", "networkInterfaces.subnet" ) )
          .withPersistenceFilter( "network-interface.vpc-id", "vpc.displayName", Sets.newHashSet( "networkConfig.networkInterfaces", "networkInterfaces.vpc" ) )
          .withPersistenceFilter( "network-interface.network-interface.id", "networkInterfaces.displayName", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "network-interface.owner-id", "networkInterfaces.ownerAccountNumber", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "network-interface.availability-zone", "networkInterfaces.availabilityZone", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "network-interface.status", "networkInterfaces.state", Sets.newHashSet( "networkConfig.networkInterfaces" ), Enums.valueOfFunction( NetworkInterface.State.class ) )
          .withPersistenceFilter( "network-interface.mac-address", "networkInterfaces.macAddress", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "network-interface-private-dns-name", "networkInterfaces.privateDnsName", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "network-interface.source-destination-check", "networkInterfaces.sourceDestCheck", Sets.newHashSet( "networkConfig.networkInterfaces" ), PersistenceFilter.Type.Boolean )
          .withPersistenceFilter( "network-interface.group-id", "networkInterfaceNetworkGroups.groupId", Sets.newHashSet( "networkConfig.networkInterfaces", "networkInterfaces.networkGroups" ) )
          .withPersistenceFilter( "network-interface.group-name", "networkInterfaceNetworkGroups.displayName", Sets.newHashSet( "networkConfig.networkInterfaces", "networkInterfaces.networkGroups" )  )
          .withPersistenceFilter( "network-interface.addresses.private-ip-address", "networkInterfaces.privateIpAddress", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "network-interface.addresses.association.public-ip", "networkInterfaces.association.publicIp", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "network-interface.addresses.association.ip-owner-id", "networkInterfaces.association.ipOwnerId", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "network-interface.attachment.attachment-id", "networkInterfaces.attachment.attachmentId", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "network-interface.attachment.instance-id", "networkInterfaces.attachment.instanceId", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "network-interface.attachment.instance-owner-id", "networkInterfaces.attachment.instanceOwnerId", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "network-interface.attachment.device-index", "networkInterfaces.attachment.deviceIndex", Sets.newHashSet( "networkConfig.networkInterfaces" ), PersistenceFilter.Type.Integer )
          .withPersistenceFilter( "network-interface.attachment.status", "networkInterfaces.attachment.status", Sets.newHashSet( "networkConfig.networkInterfaces" ), Enums.valueOfFunction( NetworkInterfaceAttachment.Status.class ) )
          .withPersistenceFilter( "network-interface.attachment.attach-time", "networkInterfaces.attachment.attachTime", Sets.newHashSet( "networkConfig.networkInterfaces" ), PersistenceFilter.Type.Date )
          .withPersistenceFilter( "network-interface.attachment.delete-on-termination", "networkInterfaces.attachment.deleteOnTerminate", Sets.newHashSet( "networkConfig.networkInterfaces" ), PersistenceFilter.Type.Boolean )
          .withPersistenceFilter( "association.public-ip", "networkInterfaces.association.publicIp", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "association.ip-owner-id", "networkInterfaces.association.ipOwnerId", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "association.allocation-id", "networkInterfaces.association.allocationId", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
          .withPersistenceFilter( "association.association-id", "networkInterfaces.association.associationId", Sets.newHashSet( "networkConfig.networkInterfaces" ) )
      );
    }
  }

  public static class VmInstanceStatusFilterSupport extends FilterSupport<VmInstance> {
    public VmInstanceStatusFilterSupport() {
      super( qualifierBuilderFor( VmInstance.class, "status" )
              .withStringProperty( "availability-zone", VmInstanceFilterFunctions.AVAILABILITY_ZONE )
              .withStringSetProperty( "event.code", VmInstanceStringSetFilterFunctions.EVENT_CODE )
              .withStringSetProperty( "event.description", VmInstanceStringSetFilterFunctions.EVENT_DESCRIPTION )
              .withDateSetProperty( "event.not-after", VmInstanceDateSetFilterFunctions.EVENT_NOT_AFTER )
              .withDateSetProperty( "event.not-before", VmInstanceDateSetFilterFunctions.EVENT_NOT_BEFORE )
              .withInternalStringProperty( "instance-id", CloudMetadatas.toDisplayName() )
              .withIntegerProperty( "instance-state-code", VmInstanceIntegerFilterFunctions.INSTANCE_STATE_CODE )
              .withStringProperty( "instance-state-name", VmInstanceFilterFunctions.INSTANCE_STATE_NAME )
              .withStringProperty( "system-status.status", VmInstanceFilterFunctions.INSTANCE_STATUS )
              .withStringProperty( "system-status.reachability", VmInstanceFilterFunctions.INSTANCE_REACHABILITY_STATUS )
              .withStringProperty( "instance-status.status", VmInstanceFilterFunctions.INSTANCE_STATUS )
              .withStringProperty( "instance-status.reachability", VmInstanceFilterFunctions.INSTANCE_REACHABILITY_STATUS )
              .withPersistenceFilter( "availability-zone", "placement.partitionName", Collections.<String>emptySet() )
              .withPersistenceFilter( "instance-id", "displayName" )
              .withPersistenceFilter( "system-status.status", "runtimeState.instanceStatus", Collections.<String>emptySet( ), VmRuntimeState.InstanceStatus.fromString( ) )
              .withPersistenceFilter( "system-status.reachability", "runtimeState.reachabilityStatus", Collections.<String>emptySet( ), VmRuntimeState.ReachabilityStatus.fromString( ) )
              .withPersistenceFilter( "instance-status.status", "runtimeState.instanceStatus", Collections.<String>emptySet( ), VmRuntimeState.InstanceStatus.fromString( ) )
              .withPersistenceFilter( "instance-status.reachability", "runtimeState.reachabilityStatus", Collections.<String>emptySet( ), VmRuntimeState.ReachabilityStatus.fromString( ) )
      );
    }
  }

  public static class VmBundleTaskFilterSupport extends FilterSupport<VmBundleTask> {
    private enum ProgressToInteger implements Function<String,Integer> {
      INSTANCE {
        @Override
        public Integer apply( final String textValue ) {
          String cleanedValue = textValue;
          if ( cleanedValue.endsWith( "%" ) ) {
            cleanedValue = cleanedValue.substring( 0, cleanedValue.length() - 1 );
          }
          try {
            return java.lang.Integer.valueOf( cleanedValue );
          } catch ( NumberFormatException e ) {
            return null;
          }
        }
      }
    }

    public VmBundleTaskFilterSupport() {
      super( builderFor( VmBundleTask.class )
          .withStringProperty( "bundle-id", BundleFilterFunctions.BUNDLE_ID )
          .withStringProperty( "error-code", BundleFilterFunctions.ERROR_CODE )
          .withStringProperty( "error-message", BundleFilterFunctions.ERROR_MESSAGE )
          .withStringProperty( "instance-id", BundleFilterFunctions.INSTANCE_ID )
          .withStringProperty( "progress", BundleFilterFunctions.PROGRESS )
          .withStringProperty( "s3-bucket", BundleFilterFunctions.S3_BUCKET )
          .withStringProperty( "s3-prefix", BundleFilterFunctions.S3_PREFIX )
          .withDateProperty( "start-time", BundleDateFilterFunctions.START_TIME )
          .withStringProperty( "state", BundleFilterFunctions.STATE )
          .withDateProperty( "update-time", BundleDateFilterFunctions.UPDATE_TIME )
          .withPersistenceFilter( "error-code", "runtimeState.bundleTask.errorCode", Collections.<String>emptySet() )
          .withPersistenceFilter( "error-message", "runtimeState.bundleTask.errorMessage", Collections.<String>emptySet() )
          .withPersistenceFilter( "instance-id", "displayName" )
          .withPersistenceFilter( "progress", "runtimeState.bundleTask.progress", Collections.<String>emptySet(), ProgressToInteger.INSTANCE )
          .withPersistenceFilter( "s3-bucket", "runtimeState.bundleTask.bucket", Collections.<String>emptySet() )
          .withPersistenceFilter( "s3-prefix", "runtimeState.bundleTask.prefix", Collections.<String>emptySet() )
          .withPersistenceFilter( "start-time", "runtimeState.bundleTask.startTime", Collections.<String>emptySet(), PersistenceFilter.Type.Date )
          .withPersistenceFilter( "state", "runtimeState.bundleTask.state", Collections.<String>emptySet(), Enums.valueOfFunction( VmBundleTask.BundleState.class ) )
          .withPersistenceFilter( "update-time", "runtimeState.bundleTask.updateTime", Collections.<String>emptySet(), PersistenceFilter.Type.Date )
      );
    }
  }

  private enum BundleDateFilterFunctions implements Function<VmBundleTask,Date> {
    START_TIME {
      @Override
      public Date apply( final VmBundleTask bundleTask ) {
        return bundleTask.getStartTime();
      }
    },
    UPDATE_TIME {
      @Override
      public Date apply( final VmBundleTask bundleTask ) {
        return bundleTask.getUpdateTime();
      }
    },
  }

  private enum BundleFilterFunctions implements Function<VmBundleTask,String> {
    BUNDLE_ID {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getBundleId();
      }
    },
    ERROR_CODE {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getErrorCode();
      }
    },
    ERROR_MESSAGE {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getErrorMessage();
      }
    },
    INSTANCE_ID {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getInstanceId();
      }
    },
    PROGRESS {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getProgress() + "%";
      }
    },
    S3_BUCKET {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getBucket();
      }
    },
    S3_PREFIX {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getPrefix();
      }
    },
    STATE {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getState().name();
      }
    },
  }

  private enum VmVolumeAttachmentBooleanFilterFunctions implements Function<VmVolumeAttachment,Boolean> {
    DELETE_ON_TERMINATE {
      @Override
      public Boolean apply( final VmVolumeAttachment volumeAttachment ) {
        return volumeAttachment.getDeleteOnTerminate();
      }
    },
  }

  private enum VmVolumeAttachmentDateFilterFunctions implements Function<VmVolumeAttachment,Date> {
    ATTACH_TIME {
      @Override
      public Date apply( final VmVolumeAttachment volumeAttachment ) {
        return volumeAttachment.getAttachTime();
      }
    },
  }

  private enum VmVolumeAttachmentFilterFunctions implements Function<VmVolumeAttachment,String> {
    DEVICE_NAME {
      @Override
      public String apply( final VmVolumeAttachment volumeAttachment ) {
        return volumeAttachment.getDevice();
      }
    },
    STATUS {
      @Override
      public String apply( final VmVolumeAttachment volumeAttachment ) {
        return volumeAttachment.getStatus();
      }
    },
    VOLUME_ID {
      @Override
      public String apply( final VmVolumeAttachment volumeAttachment ) {
        return volumeAttachment.getVolumeId();
      }
    },
  }

  private enum VmInstanceStringSetFilterFunctions implements Function<VmInstance,Set<String>> {
    ASSOCIATION_PUBLIC_IP {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.ASSOCIATION_PUBLIC_IP );
      }
    },
    ASSOCIATION_IP_OWNER_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.ASSOCIATION_IP_OWNER_ID );
      }
    },
    ASSOCIATION_ALLOCATION_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.ASSOCIATION_ALLOCATION_ID );
      }
    },
    ASSOCIATION_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.ASSOCIATION_ID );
      }
    },
    BLOCK_DEVICE_MAPPING_DEVICE_NAME {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return blockDeviceSet( instance, VmVolumeAttachmentFilterFunctions.DEVICE_NAME );
      }
    },
    BLOCK_DEVICE_MAPPING_STATUS {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return blockDeviceSet( instance, VmVolumeAttachmentFilterFunctions.STATUS );
      }
    },
    BLOCK_DEVICE_MAPPING_VOLUME_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return blockDeviceSet( instance, VmVolumeAttachmentFilterFunctions.VOLUME_ID );
      }
    },
    EVENT_CODE {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        final Optional<InstanceStatusEventType> eventInfo = VmInstance.StatusTransform.getEventInfo( instance );
        return eventInfo.isPresent( ) ?
            Collections.singleton( eventInfo.get( ).getCode( ) ) :
            Collections.<String>emptySet( );
      }
    },
    EVENT_DESCRIPTION {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        final Optional<InstanceStatusEventType> eventInfo = VmInstance.StatusTransform.getEventInfo( instance );
        return eventInfo.isPresent( ) ?
            Collections.singleton( eventInfo.get( ).getDescription( ) ) :
            Collections.<String>emptySet( );
      }
    },
    GROUP_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkGroupSet( instance, NetworkGroups.groupId() );
      }
    },
    GROUP_NAME {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkGroupSet( instance, CloudMetadatas.toDisplayName() );
      }
    },
    NETWORK_INTERFACE_DESCRIPTION {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.DESCRIPTION );
      }
    },
    NETWORK_INTERFACE_SUBNET_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.SUBNET_ID );
      }
    },
    NETWORK_INTERFACE_VPC_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.VPC_ID );
      }
    },
    NETWORK_INTERFACE_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, CloudMetadatas.toDisplayName() );
      }
    },
    NETWORK_INTERFACE_OWNER_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.OWNER_ID );
      }
    },
    NETWORK_INTERFACE_AVAILABILITY_ZONE {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.AVAILABILITY_ZONE );
      }
    },
    NETWORK_INTERFACE_STATUS {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.STATE );
      }
    },
    NETWORK_INTERFACE_MAC_ADDRESS {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.MAC_ADDRESS );
      }
    },
    NETWORK_INTERFACE_PRIVATE_IP {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.PRIVATE_IP );
      }
    },
    NETWORK_INTERFACE_PRIVATE_DNS_NAME {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.PRIVATE_DNS_NAME );
      }
    },
    NETWORK_INTERFACE_GROUP_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSetSet( instance, NetworkInterfaces.FilterStringSetFunctions.GROUP_ID );
      }
    },
    NETWORK_INTERFACE_GROUP_NAME {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSetSet( instance, NetworkInterfaces.FilterStringSetFunctions.GROUP_NAME );
      }
    },
    NETWORK_INTERFACE_ATTACHMENT_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.ATTACHMENT_ATTACHMENT_ID );
      }
    },
    NETWORK_INTERFACE_ATTACHMENT_INSTANCE_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.ATTACHMENT_INSTANCE_ID );
      }
    },
    NETWORK_INTERFACE_ATTACHMENT_INSTANCE_OWNER_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.ATTACHMENT_INSTANCE_OWNER_ID );
      }
    },
    NETWORK_INTERFACE_ATTACHMENT_STATUS {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.ATTACHMENT_STATUS );
      }
    },
    NETWORK_INTERFACE_ASSOCIATION_PUBLIC_IP {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.ASSOCIATION_PUBLIC_IP );
      }
    },
    NETWORK_INTERFACE_ASSOCIATION_IP_OWNER_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterStringFunctions.ASSOCIATION_IP_OWNER_ID );
      }
    },
  }

  private enum VmInstanceBooleanSetFilterFunctions implements Function<VmInstance,Set<Boolean>> {
    BLOCK_DEVICE_MAPPING_DELETE_ON_TERMINATE {
      @Override
      public Set<Boolean> apply( final VmInstance instance ) {
        return blockDeviceSet( instance, VmVolumeAttachmentBooleanFilterFunctions.DELETE_ON_TERMINATE );
      }
    },
    NETWORK_INTERFACE_ADDRESSES_PRIMARY {
      @Override
      public Set<Boolean> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, Functions.forSupplier( Suppliers.ofInstance( true ) ) );
      }
    },
    NETWORK_INTERFACE_ATTACHMENT_DELETE_ON_TERMINATION {
      @Override
      public Set<Boolean> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterBooleanFunctions.ATTACHMENT_DELETE_ON_TERMINATION );
      }
    },
    NETWORK_INTERFACE_SOURCE_DEST_CHECK {
      @Override
      public Set<Boolean> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterBooleanFunctions.SOURCE_DEST_CHECK );
      }
    },
  }

  private enum VmInstanceDateFilterFunctions implements Function<VmInstance,Date> {
    LAUNCH_TIME {
      @Override
      public Date apply( final VmInstance instance ) {
        return instance.getLaunchRecord().getLaunchTime();
      }
    },
  }

  private enum VmInstanceDateSetFilterFunctions implements Function<VmInstance,Set<Date>> {
    BLOCK_DEVICE_MAPPING_ATTACH_TIME {
      @Override
      public Set<Date> apply( final VmInstance instance ) {
        return blockDeviceSet( instance, VmVolumeAttachmentDateFilterFunctions.ATTACH_TIME );
      }
    },
    EVENT_NOT_AFTER {
      @Override
      public Set<Date> apply( final VmInstance instance ) {
        final Optional<InstanceStatusEventType> eventInfo = VmInstance.StatusTransform.getEventInfo( instance );
        return eventInfo.isPresent( ) ?
            Collections.singleton( eventInfo.get( ).getNotAfter( ) ) :
            Collections.<Date>emptySet( );
      }
    },
    EVENT_NOT_BEFORE {
      @Override
      public Set<Date> apply( final VmInstance instance ) {
        final Optional<InstanceStatusEventType> eventInfo = VmInstance.StatusTransform.getEventInfo( instance );
        return eventInfo.isPresent( ) ?
            Collections.singleton( eventInfo.get( ).getNotBefore( ) ) :
            Collections.<Date>emptySet( );
      }
    },
    NETWORK_INTERFACE_ATTACHMENT_ATTACH_TIME {
      @Override
      public Set<Date> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterDateFunctions.ATTACHMENT_ATTACH_TIME );
      }
    },
  }

  private enum VmInstanceIntegerFilterFunctions implements Function<VmInstance,Integer> {
    INSTANCE_STATE_CODE {
      @Override
      public Integer apply( final VmInstance instance ) {
        return instance.getDisplayState().getCode();
      }
    },
  }

  private enum VmInstanceIntegerSetFilterFunctions implements Function<VmInstance,Set<Integer>> {
    NETWORK_INTERFACE_ATTACHMENT_DEVICE_INDEX {
      @Override
      public Set<Integer> apply( final VmInstance instance ) {
        return networkInterfaceSet( instance, NetworkInterfaces.FilterIntegerFunctions.ATTACHMENT_DEVICE_INDEX );
      }
    },
  }

  private enum VmInstanceFilterFunctions implements Function<VmInstance,String> {
    ARCHITECTURE {
      @Override
      public String apply( final VmInstance instance ) {
        final BootableImageInfo imageInfo = instance.getBootRecord().getMachine();
        return imageInfo instanceof ImageInfo ?
            Strings.toString( ( (ImageInfo) imageInfo ).getArchitecture() ):
            null;
      }
    },
    AVAILABILITY_ZONE {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getPartition();
      }
    },
    CLIENT_TOKEN {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getClientToken();
      }
    },
    DNS_NAME {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getDisplayPublicDnsName();
      }
    },
    IMAGE_ID {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getImageId();
      }
    },
    INSTANCE_PROFILE_ARN {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getIamInstanceProfileArn();
      }
    },
    INSTANCE_STATE_NAME {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getDisplayState( ) == null ? 
            null : 
            instance.getDisplayState( ).getName( );
      }
    },
    INSTANCE_STATUS {
      @Override
      public String apply( final VmInstance instance ) {
        return Objects.firstNonNull(
            instance.getRuntimeState( ).getInstanceStatus( ),
            VmRuntimeState.InstanceStatus.Ok ).toString( );
      }
    },
    INSTANCE_REACHABILITY_STATUS {
      @Override
      public String apply( final VmInstance instance ) {
        return Objects.firstNonNull(
            instance.getRuntimeState( ).getReachabilityStatus( ),
            VmRuntimeState.ReachabilityStatus.Passed ).toString( );
      }
    },
    INSTANCE_TYPE {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getVmType() == null ?
            null :
            instance.getVmType().getName( );
      }
    },
    IP_ADDRESS {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getDisplayPublicAddress();
      }
    },
    KERNEL_ID {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getKernelId();
      }
    },
    KEY_NAME {
      @Override
      public String apply( final VmInstance instance ) {
        return CloudMetadatas.toDisplayName().apply( instance.getKeyPair() );
      }
    },
    LAUNCH_INDEX {
      @Override
      public String apply( final VmInstance instance ) {
        return Strings.toString( instance.getLaunchRecord().getLaunchIndex() );
      }
    },
    MONITORING_STATE {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getMonitoring() ? "enabled" : "disabled";
      }
    },
    NODE_HOST { // Eucalyptus specific, not for filtering
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getServiceTag( )==null ? null : URI.create( instance.getServiceTag( ) ).getHost( );
      }
    },
    OWNER_ID {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getOwnerAccountNumber();
      }
    },
    PLATFORM {
      @Override
      public String apply( final VmInstance instance ) {
        return "windows".equals( instance.getPlatform() ) ? "windows" : "";
      }
    },
    PRIVATE_DNS_NAME {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getDisplayPrivateDnsName();
      }
    },
    PRIVATE_IP_ADDRESS {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getDisplayPrivateAddress();
      }
    },
    RAMDISK_ID {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getRamdiskId();
      }
    },
    REASON {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getRuntimeState() == null ?
            null :
            instance.getRuntimeState().getReason();
      }
    },
    RESERVATION_ID {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getReservationId();
      }
    },
    ROOT_DEVICE_NAME {
      @Override
      public String apply( final VmInstance instance ) {
        final BootableImageInfo imageInfo = instance.getBootRecord().getMachine();
        return imageInfo == null ? null : imageInfo.getRootDeviceName();
      }
    },
    ROOT_DEVICE_TYPE {
      @Override
      public String apply( final VmInstance instance ) {
        final BootableImageInfo imageInfo = instance.getBootRecord().getMachine();
        return imageInfo == null ? null : imageInfo.getRootDeviceType();
      }
    },
    SUBNET_ID {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getSubnetId( );
      }
    },
    VIRTUALIZATION_TYPE {
      @Override
      public String apply( final VmInstance vmInstance ) {
        return vmInstance.getVirtualizationType();
      }
    },
    VPC_ID {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getVpcId();
      }
    },
  }

  private enum VmInstanceToVmBundleTask implements Function<VmInstance,VmBundleTask> {
    INSTANCE {
      @Override
      public VmBundleTask apply( final VmInstance vmInstance ) {
        return vmInstance.getRuntimeState() == null ? null : vmInstance.getRuntimeState().getBundleTask();
      }
    }
  }
}
