/*************************************************************************
 * Copyright 2009-2014 Ent. Services Development Corporation LP
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
 * POSSIBILITY OF SUCH DAMAGE.
 ************************************************************************/
package com.eucalyptus.objectstorage.msgs;

import java.util.ArrayList;
import com.eucalyptus.storage.msgs.s3.CanonicalUser;
import com.eucalyptus.storage.msgs.s3.Initiator;
import com.eucalyptus.storage.msgs.s3.Part;

public class ListPartsResponseType extends ObjectStorageDataResponseType {

  private String bucket;
  private String key;
  private String uploadId;
  private Initiator initiator;
  private CanonicalUser owner;
  private String storageClass;
  private int partNumberMarker;
  private int nextPartNumberMarker;
  private int maxParts;
  private Boolean isTruncated;
  private ArrayList<Part> parts = new ArrayList<Part>( );

  public String getBucket( ) {
    return bucket;
  }

  public void setBucket( String bucket ) {
    this.bucket = bucket;
  }

  public String getKey( ) {
    return key;
  }

  public void setKey( String key ) {
    this.key = key;
  }

  public String getUploadId( ) {
    return uploadId;
  }

  public void setUploadId( String uploadId ) {
    this.uploadId = uploadId;
  }

  public Initiator getInitiator( ) {
    return initiator;
  }

  public void setInitiator( Initiator initiator ) {
    this.initiator = initiator;
  }

  public CanonicalUser getOwner( ) {
    return owner;
  }

  public void setOwner( CanonicalUser owner ) {
    this.owner = owner;
  }

  public String getStorageClass( ) {
    return storageClass;
  }

  public void setStorageClass( String storageClass ) {
    this.storageClass = storageClass;
  }

  public int getPartNumberMarker( ) {
    return partNumberMarker;
  }

  public void setPartNumberMarker( int partNumberMarker ) {
    this.partNumberMarker = partNumberMarker;
  }

  public int getNextPartNumberMarker( ) {
    return nextPartNumberMarker;
  }

  public void setNextPartNumberMarker( int nextPartNumberMarker ) {
    this.nextPartNumberMarker = nextPartNumberMarker;
  }

  public int getMaxParts( ) {
    return maxParts;
  }

  public void setMaxParts( int maxParts ) {
    this.maxParts = maxParts;
  }

  public Boolean getIsTruncated( ) {
    return isTruncated;
  }

  public void setIsTruncated( Boolean isTruncated ) {
    this.isTruncated = isTruncated;
  }

  public ArrayList<Part> getParts( ) {
    return parts;
  }

  public void setParts( ArrayList<Part> parts ) {
    this.parts = parts;
  }
}
