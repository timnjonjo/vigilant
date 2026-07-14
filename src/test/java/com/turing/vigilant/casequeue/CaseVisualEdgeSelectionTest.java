package com.turing.vigilant.casequeue;

import com.turing.vigilant.graph.SharedAttributeType;
import com.turing.vigilant.shared.ReasonCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The explorer must draw only the overlap edges behind a case's reason codes:
 * device edges for DEVICE_COLLISION, subnet edges for IP_SUBNET_COLLISION, and
 * nothing for velocity/cycle/datacenter cases. This pins that derivation without
 * touching Neo4j.
 */
class CaseVisualEdgeSelectionTest {

    @Test
    void deviceCollisionSelectsDeviceEdgesOnly() {
        assertThat(CaseController.visualSharedEdges(List.of(ReasonCode.DEVICE_COLLISION)))
                .containsExactly(SharedAttributeType.DEVICE);
    }

    @Test
    void ipSubnetCollisionSelectsSubnetEdgesOnly() {
        assertThat(CaseController.visualSharedEdges(List.of(ReasonCode.IP_SUBNET_COLLISION)))
                .containsExactly(SharedAttributeType.IP_SUBNET);
    }

    @Test
    void bothCollisionsSelectBothEdgeTypes() {
        assertThat(CaseController.visualSharedEdges(
                List.of(ReasonCode.DEVICE_COLLISION, ReasonCode.IP_SUBNET_COLLISION)))
                .containsExactlyInAnyOrder(SharedAttributeType.DEVICE, SharedAttributeType.IP_SUBNET);
    }

    @Test
    void velocityCycleAndDatacenterReasonsSelectNoOverlapEdges() {
        assertThat(CaseController.visualSharedEdges(List.of(
                ReasonCode.VELOCITY_BURST, ReasonCode.CYCLE_DETECTED, ReasonCode.DATACENTER_OR_VPN_IP)))
                .isEmpty();
    }
}
