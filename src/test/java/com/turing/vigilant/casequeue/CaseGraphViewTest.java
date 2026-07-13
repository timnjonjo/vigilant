package com.turing.vigilant.casequeue;

import com.turing.vigilant.graph.AccountNode;
import com.turing.vigilant.graph.ReferralEdge;
import com.turing.vigilant.graph.ReferralNeighbourhood;
import com.turing.vigilant.graph.SharedAttributeEdge;
import com.turing.vigilant.graph.SharedAttributeType;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.IpType;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CaseGraphViewTest {

    @Test
    void mapsNeighbourhoodToCytoscapeShape() {
        ReferralNeighbourhood n = new ReferralNeighbourhood(
                TenantId.of("loob-bank"), ReferralCode.of("LOOB-1"), CampaignId.of("camp-1"), "R",
                List.of(new ReferralEdge("R", "A", Instant.EPOCH)),
                List.of(new SharedAttributeEdge("A", "B", SharedAttributeType.DEVICE)),
                List.of(new AccountNode("R", IpType.RESIDENTIAL, false),
                        new AccountNode("A", IpType.DATACENTER, true)));

        CaseGraphView view = CaseGraphView.of(n);

        assertThat(view.nodes())
                .extracting(CaseGraphView.NodeView::userId, CaseGraphView.NodeView::role,
                        CaseGraphView.NodeView::ipType, CaseGraphView.NodeView::converted)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("R", "referrer", "RESIDENTIAL", false),
                        org.assertj.core.groups.Tuple.tuple("A", "referee", "DATACENTER", true));

        assertThat(view.edges())
                .extracting(CaseGraphView.EdgeView::source, CaseGraphView.EdgeView::target,
                        CaseGraphView.EdgeView::type)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("R", "A", "REFERRED"),
                        org.assertj.core.groups.Tuple.tuple("A", "B", "SHARES_DEVICE"));
    }

    @Test
    void emptyNeighbourhoodMapsToEmptyGraph() {
        ReferralNeighbourhood empty = new ReferralNeighbourhood(
                TenantId.of("loob-bank"), ReferralCode.of("NOPE"), CampaignId.of("camp-1"), null,
                List.of(), List.of(), List.of());

        CaseGraphView view = CaseGraphView.of(empty);

        assertThat(view.nodes()).isEmpty();
        assertThat(view.edges()).isEmpty();
    }
}
