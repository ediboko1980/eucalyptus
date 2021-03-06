// -*- mode: C; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil -*-
// vim: set softtabstop=4 shiftwidth=4 tabstop=4 expandtab:

/*************************************************************************
 * Copyright 2016 Ent. Services Development Corporation LP
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

#ifndef _INCLUDE_EUCANETD_EDGE_H_
#define _INCLUDE_EUCANETD_EDGE_H_

//!
//! @file net/eucanetd_edge.h
//!

/*----------------------------------------------------------------------------*\
 |                                                                            |
 |                                  INCLUDES                                  |
 |                                                                            |
\*----------------------------------------------------------------------------*/

#include "euca_gni.h"
#include "eucanetd.h"

/*----------------------------------------------------------------------------*\
 |                                                                            |
 |                                  DEFINES                                   |
 |                                                                            |
\*----------------------------------------------------------------------------*/

#define EDGE_NETMETER_FILE_MAX_SIZE 10000000

#define EDGE_NETMETER_FILE_SENSOR NC_NET_PATH_DEFAULT "/eucanetd_getstats_net.out"
#define EDGE_NETMETER_FILE_NEW NC_NET_PATH_DEFAULT "/edge_netmeter"
#define EDGE_NETMETER_FILE_DONE NC_NET_PATH_DEFAULT "/edge_netmeter_done"

/*----------------------------------------------------------------------------*\
 |                                                                            |
 |                                  TYPEDEFS                                  |
 |                                                                            |
\*----------------------------------------------------------------------------*/

/*----------------------------------------------------------------------------*\
 |                                                                            |
 |                                ENUMERATIONS                                |
 |                                                                            |
\*----------------------------------------------------------------------------*/

enum edge_ipaddr_type_t {
    EDGE_IPV4_INVALID,
    EDGE_IPV4_PUBLIC,
    EDGE_IPV4_PRIVATE,
};

/*----------------------------------------------------------------------------*\
 |                                                                            |
 |                                 STRUCTURES                                 |
 |                                                                            |
\*----------------------------------------------------------------------------*/

typedef struct edge_netmeter_instance_t {
    char *instance_id;
    char *ipaddr;
    long pkts_in;
    long bytes_in;
    long pkts_out;
    long bytes_out;
    enum edge_ipaddr_type_t iptype;
    boolean updated;
} edge_netmeter_instance;

typedef struct edge_netmeter_t {
    int max_pub_ips;
    int max_priv_ips;
    edge_netmeter_instance **pub_ips;
    edge_netmeter_instance **priv_ips;
} edge_netmeter;

typedef struct edge_config_t {
    eucanetdConfig *config;
    globalNetworkInfo *gni;
    edge_netmeter *nmeter;

    gni_cluster *my_cluster;
    gni_node *my_node;
    
    gni_instance *my_instances;
    int max_my_instances;
    gni_secgroup **my_sgs;
    int max_my_sgs;
    gni_secgroup **ref_sgs;
    int max_ref_sgs;
} edge_config;

/*----------------------------------------------------------------------------*\
 |                                                                            |
 |                             EXPORTED VARIABLES                             |
 |                                                                            |
\*----------------------------------------------------------------------------*/

/*----------------------------------------------------------------------------*\
 |                                                                            |
 |                             EXPORTED PROTOTYPES                            |
 |                                                                            |
\*----------------------------------------------------------------------------*/

int do_edge_update_allprivate(edge_config *edge);
int do_edge_update_sgs(edge_config *edge);
int do_edge_update_eips(edge_config *edge);
int do_edge_update_l2(edge_config *edge);
int do_edge_update_ips(edge_config *edge);
int do_edge_update_netmeter(edge_config *edge);

int generate_dhcpd_config(edge_config *edge);
int update_host_arp(edge_config *edge);
int free_edge_config(edge_config *edge);
int free_edge_netmeter_instance(edge_netmeter_instance *nm);
int free_edge_netmeter_instances(edge_netmeter_instance **nms, int max_nms);
int free_edge_netmeter(edge_netmeter *nm);

int extract_edge_config_from_gni(edge_config *edge);

edge_netmeter_instance *find_edge_netmeter_instance(edge_netmeter_instance ***nms,
        int *max_nms, char *instance_id, char *ipaddr, boolean force);
int clean_edge_netmeter_instances(edge_netmeter_instance ***nms, int *max_nms);
int clear_edge_netmeter_tag(edge_netmeter *nm);

int edge_dump_netmeter(edge_config *edge);

boolean is_my_ip(edge_config *edge, u32 ip);

int cmp_edge_config(edge_config *a, edge_config *b, int *my_instances_diff,
        int *sgs_diff, int *instances_diff);

/*----------------------------------------------------------------------------*\
 |                                                                            |
 |                           STATIC INLINE PROTOTYPES                         |
 |                                                                            |
\*----------------------------------------------------------------------------*/

/*----------------------------------------------------------------------------*\
 |                                                                            |
 |                                   MACROS                                   |
 |                                                                            |
\*----------------------------------------------------------------------------*/

/*----------------------------------------------------------------------------*\
 |                                                                            |
 |                          STATIC INLINE IMPLEMENTATION                      |
 |                                                                            |
\*----------------------------------------------------------------------------*/

#endif /* ! _INCLUDE_EUCANETD_EDGE_H_ */
