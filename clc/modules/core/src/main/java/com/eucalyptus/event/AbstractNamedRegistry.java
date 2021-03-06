/*************************************************************************
 * Copyright 2008 Regents of the University of California
 * Copyright 2009-2015 Ent. Services Development Corporation LP
 *
 * Redistribution and use of this software in source and binary forms,
 * with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *   Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 *   Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer
 *   in the documentation and/or other materials provided with the
 *   distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE. USERS OF THIS SOFTWARE ACKNOWLEDGE
 * THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE LICENSED MATERIAL,
 * COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS SOFTWARE,
 * AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 * IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA,
 * SANTA BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY,
 * WHICH IN THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION,
 * REPLACEMENT OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO
 * IDENTIFIED, OR WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT
 * NEEDED TO COMPLY WITH ANY SUCH LICENSES OR RIGHTS.
 ************************************************************************/

package com.eucalyptus.event;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.eucalyptus.util.HasFullName;
import com.eucalyptus.util.LockResource;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

@SuppressWarnings( "rawtypes" )
public abstract class AbstractNamedRegistry<TYPE extends HasFullName> {
  protected ReadWriteLock                      canHas = new ReentrantReadWriteLock( );
  private ConcurrentNavigableMap<String, TYPE> activeMap;
  private ConcurrentNavigableMap<String, TYPE> disabledMap;
  
  protected AbstractNamedRegistry( ) {
    this.activeMap = new ConcurrentSkipListMap<String, TYPE>( );
    this.disabledMap = new ConcurrentSkipListMap<String, TYPE>( );
  }
  
  public void deregister( String key ) {
    this.canHas.writeLock( ).lock( );
    try {
      this.disabledMap.remove( key );
      this.activeMap.remove( key );
    } finally {
      this.canHas.writeLock( ).unlock( );
    }
  }

  public void deregisterDisabled( String key ) {
    try ( final LockResource lockResource = LockResource.lock( this.canHas.writeLock( ) ) ) {
      this.disabledMap.remove( key );
    }
  }

  public void register( TYPE obj ) {
    TYPE tempObj = null;
    this.canHas.writeLock( ).lock( );
    try {
      if ( ( tempObj = this.disabledMap.remove( obj.getName( ) ) ) == null ) {
        tempObj = obj;
      }
      this.activeMap.putIfAbsent( tempObj.getName( ), tempObj );
    } finally {
      this.canHas.writeLock( ).unlock( );
    }
  }
  
  public void registerDisabled( TYPE obj ) {
    TYPE tempObj = null;
    this.canHas.writeLock( ).lock( );
    try {
      if ( ( tempObj = this.activeMap.remove( obj.getName( ) ) ) == null ) {
        tempObj = obj;
      }
      this.disabledMap.putIfAbsent( tempObj.getName( ), tempObj );
    } finally {
      this.canHas.writeLock( ).unlock( );
    }
  }
  
  public List<TYPE> listDisabledValues( ) {
    this.canHas.readLock( ).lock( );
    try {
      return Lists.newArrayList( this.disabledMap.values( ) );
    } finally {
      this.canHas.readLock( ).unlock( );
    }
  }
  
  public List<TYPE> listValues( ) {
    this.canHas.readLock( ).lock( );
    try {
      return Lists.newArrayList( this.activeMap.values( ) );
    } finally {
      this.canHas.readLock( ).unlock( );
    }
  }
  
  public TYPE lookupDisabled( String name ) throws NoSuchElementException {
    this.canHas.readLock( ).lock( );
    try {
      if ( !this.disabledMap.containsKey( name ) ) {
        throw new NoSuchElementException( "Can't find registered object: " + name + " in " + this.getClass( ).getSimpleName( ) );
      } else {
        return this.disabledMap.get( name );
      }
    } finally {
      this.canHas.readLock( ).unlock( );
    }
  }
  
  public TYPE lookup( String name ) throws NoSuchElementException {
    this.canHas.readLock( ).lock( );
    try {
      if ( !this.activeMap.containsKey( name ) ) {
        throw new NoSuchElementException( "Can't find registered object: " + name + " in " + this.getClass( ).getSimpleName( ) );
      } else {
        return this.activeMap.get( name );
      }
    } finally {
      this.canHas.readLock( ).unlock( );
    }
  }

  public final void disable( TYPE that ) throws NoSuchElementException {
    this.disable( that.getName( ) );
  }
  
  public void disable( String name ) {
    this.canHas.writeLock( ).lock( );
    try {
      TYPE obj = null;
      if ( ( obj = this.activeMap.remove( name ) ) == null && ( ( obj = this.disabledMap.remove( name ) ) == null ) ) {
        throw new NoSuchElementException( "Can't find registered object: " + name + " in " + this.getClass( ).getSimpleName( ) );
      } else {
        this.disabledMap.putIfAbsent( obj.getName( ), obj );
      }
    } finally {
      this.canHas.writeLock( ).unlock( );
    }
  }
  
  public void enable( TYPE that ) throws NoSuchElementException {
    this.enable( that.getName( ) );
  }
  
  public void enable( String name ) throws NoSuchElementException {
    this.canHas.writeLock( ).lock( );
    try {
      TYPE obj = null;
      if ( ( obj = this.disabledMap.remove( name ) ) == null && ( ( obj = this.activeMap.remove( name ) ) == null ) ) {
        throw new NoSuchElementException( "Can't find registered object: " + name + " in " + this.getClass( ).getSimpleName( ) );
      } else {
        this.activeMap.putIfAbsent( obj.getName( ), obj );
      }
    } finally {
      this.canHas.writeLock( ).unlock( );
    }
  }
  
  public boolean contains( TYPE obj ) {
    return this.contains( obj.getName( ) );
  }
  
  public boolean contains( String name ) {
    this.canHas.readLock( ).lock( );
    try {
      return this.activeMap.containsKey( name ) || this.disabledMap.containsKey( name );
    } finally {
      this.canHas.readLock( ).unlock( );
    }
  }
  
  public TYPE enableFirst( Predicate<TYPE> filter ) throws NoSuchElementException {
    this.canHas.writeLock( ).lock( );
    try {
      final List<TYPE> selection = Lists.newArrayList(
          Iterables.limit( Iterables.filter( this.disabledMap.values( ), filter ), 10_000 ) );
      TYPE first = selection.isEmpty( ) ?
          null :
          selection.get( ThreadLocalRandom.current( ).nextInt( 0, selection.size( ) ) );
      if ( first == null ) {
        throw new NoSuchElementException( "Disabled map is empty." );
      }
      this.activeMap.put( first.getName( ), first );
      this.disabledMap.remove( first.getName( ) );
      return first;
    } finally {
      this.canHas.writeLock( ).unlock( );
    }
  }
  

  @Override
  public String toString( ) {
    return String.format( "%s [activeMap=%s, disabledMap=%s]", this.getClass( ).getSimpleName( ), this.activeMap, this.disabledMap );
  }
  
}
