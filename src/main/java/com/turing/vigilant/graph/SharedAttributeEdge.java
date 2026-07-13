package com.turing.vigilant.graph;

/** An undirected identity-overlap edge (SHARES_DEVICE / SHARES_IP_SUBNET). */
public record SharedAttributeEdge(String userIdA, String userIdB, com.turing.vigilant.graph.SharedAttributeType type) {
}
