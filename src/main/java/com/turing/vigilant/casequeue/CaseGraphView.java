package com.turing.vigilant.casequeue;

import com.turing.vigilant.graph.ReferralEdge;
import com.turing.vigilant.graph.ReferralNeighbourhood;
import com.turing.vigilant.graph.SharedAttributeEdge;
import com.turing.vigilant.graph.SharedAttributeType;

import java.util.ArrayList;
import java.util.List;

/**
 * The flagged subgraph, shaped for the dashboard's Cytoscape explorer: user
 * accounts as nodes, typed edges (REFERRED / SHARES_DEVICE / SHARES_IP_SUBNET).
 * Field names match the frontend's {@code CaseGraph} type exactly.
 */
public record CaseGraphView(List<NodeView> nodes, List<EdgeView> edges) {

    public record NodeView(String id, String userId, String role, String ipType, boolean converted) {
    }

    public record EdgeView(String id, String source, String target, String type) {
    }

    public static CaseGraphView of(ReferralNeighbourhood n) {
        List<NodeView> nodes = n.accounts().stream()
                .map(a -> new NodeView(
                        a.userId(),
                        a.userId(),
                        a.userId().equals(n.referrerUserId()) ? "referrer" : "referee",
                        a.ipType().name(),
                        a.converted()))
                .toList();

        List<EdgeView> edges = new ArrayList<>();
        for (ReferralEdge e : n.referralEdges()) {
            edges.add(new EdgeView(
                    e.referrerUserId() + "-REFERRED-" + e.refereeUserId(),
                    e.referrerUserId(), e.refereeUserId(), "REFERRED"));
        }
        for (SharedAttributeEdge e : n.sharedEdges()) {
            String type = e.type() == SharedAttributeType.DEVICE ? "SHARES_DEVICE" : "SHARES_IP_SUBNET";
            edges.add(new EdgeView(
                    e.userIdA() + "-" + type + "-" + e.userIdB(),
                    e.userIdA(), e.userIdB(), type));
        }
        return new CaseGraphView(nodes, edges);
    }
}
