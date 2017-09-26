/*************************************************************************
 * (c) Copyright 2017 Hewlett Packard Enterprise Development Company LP
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
 ************************************************************************/
package com.eucalyptus.compute.common;

public class ModifyVpcAttributeType extends VpcMessage {

  private String vpcId;
  private AttributeBooleanValueType enableDnsSupport;
  private AttributeBooleanValueType enableDnsHostnames;

  public String getVpcId( ) {
    return vpcId;
  }

  public void setVpcId( String vpcId ) {
    this.vpcId = vpcId;
  }

  public AttributeBooleanValueType getEnableDnsSupport( ) {
    return enableDnsSupport;
  }

  public void setEnableDnsSupport( AttributeBooleanValueType enableDnsSupport ) {
    this.enableDnsSupport = enableDnsSupport;
  }

  public AttributeBooleanValueType getEnableDnsHostnames( ) {
    return enableDnsHostnames;
  }

  public void setEnableDnsHostnames( AttributeBooleanValueType enableDnsHostnames ) {
    this.enableDnsHostnames = enableDnsHostnames;
  }
}
