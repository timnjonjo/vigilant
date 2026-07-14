package com.turing.vigilant.casequeue;

import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.ReasonCode;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import com.turing.vigilant.web.pagination.CursorPage;
import com.turing.vigilant.web.pagination.InvalidCursorException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Confirms the case-queue keyset pagination "works as intended" against real
 * Postgres: a full walk covers every row exactly once (no dupes, no gaps) in the
 * requested order, the chain is tenant- and filter-scoped, the last page ends the
 * chain, and cursors that no longer match their filter or limit are rejected.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
class CasePageServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    FraudCaseRepository repository;
    @Autowired
    EntityManager entityManager;

    private CasePageService service;

    private static final TenantId LOOB = TenantId.of("loob-bank");
    private static final Instant BASE = Instant.parse("2026-07-01T00:00:00Z");

    /** Descending sort key used by CaseSort.SCORE: score, then opened-at, then id — all DESC. */
    private static final Comparator<CaseView> SCORE_ORDER = Comparator
            .comparingDouble(CaseView::score)
            .thenComparing(CaseView::openedAt)
            .thenComparing(CaseView::id)
            .reversed();

    private static final Comparator<CaseView> AGE_ORDER = Comparator
            .comparing(CaseView::openedAt)
            .thenComparing(CaseView::id);

    @BeforeEach
    void setUp() {
        service = new CasePageService(
                new CasePageRepository(entityManager),
                new com.turing.vigilant.web.pagination.CursorCodec(new ObjectMapper(), "it-secret"));

        // 25 cases in camp-1 with many tied scores/timestamps so the id tiebreaker
        // is genuinely exercised; 5 in camp-2; 5 for a different tenant.
        for (int i = 0; i < 25; i++) {
            seed(LOOB, "camp-1", "camp1-" + i, i % 4, BASE.plusSeconds((i / 3) * 60L));
        }
        for (int i = 0; i < 5; i++) {
            seed(LOOB, "camp-2", "camp2-" + i, i % 2, BASE.plusSeconds(9000 + i));
        }
        for (int i = 0; i < 5; i++) {
            seed(TenantId.of("other-bank"), "camp-1", "other-" + i, i, BASE.plusSeconds(i));
        }
        entityManager.flush();
    }

    private void seed(TenantId tenant, String campaign, String referee, double score, Instant openedAt) {
        repository.save(FraudCase.open(new CaseOpening(
                tenant, CampaignId.of(campaign), ReferralCode.of("LOOB-R1"), referee,
                Decision.HOLD, score, List.of(ReasonCode.DEVICE_COLLISION), openedAt)));
    }

    /** Walks the whole cursor chain, returning every visible row in emission order. */
    private List<CaseView> walk(CaseStatus status, String campaignId, String sortBy, int limit) {
        List<CaseView> all = new ArrayList<>();
        String cursor = null;
        int guard = 0;
        do {
            CursorPage<CaseView> page = service.page(
                    LOOB.value(), status, campaignId, null, null, sortBy, cursor, limit);
            all.addAll(page.items());
            cursor = page.nextCursor();
            assertThat(++guard).as("cursor chain terminates").isLessThan(1000);
        } while (cursor != null);
        return all;
    }

    @Test
    void fullScoreWalkCoversEveryTenantRowOnceInOrderWithNoGaps() {
        List<CaseView> walked = walk(null, null, "score", 7);

        // Tenant scope: all 30 loob rows, none from other-bank.
        assertThat(walked).hasSize(30);
        assertThat(walked).extracting(CaseView::tenantId).containsOnly("loob-bank");
        // No duplicates, no gaps: distinct ids == emitted count.
        assertThat(walked).extracting(CaseView::id).doesNotHaveDuplicates();
        // Emitted in exactly the DESC keyset order (ties broken by opened-at then id).
        assertThat(walked).isSortedAccordingTo(SCORE_ORDER);
    }

    @Test
    void fullAgeWalkIsAscendingByOpenedAtThenId() {
        List<CaseView> walked = walk(null, null, "age", 4);

        assertThat(walked).hasSize(30);
        assertThat(walked).extracting(CaseView::id).doesNotHaveDuplicates();
        assertThat(walked).isSortedAccordingTo(AGE_ORDER);
    }

    @Test
    void theChainIsCampaignScoped() {
        List<CaseView> walked = walk(null, "camp-1", "score", 6);

        assertThat(walked).hasSize(25);
        assertThat(walked).extracting(CaseView::campaignId).containsOnly("camp-1");
        assertThat(walked).isSortedAccordingTo(SCORE_ORDER);
    }

    @Test
    void theLastPageEndsTheChain() {
        // A limit larger than the result set returns everything and a null cursor.
        CursorPage<CaseView> page = service.page(LOOB.value(), null, "camp-2", null, null, "score", null, 100);

        assertThat(page.items()).hasSize(5);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void aCursorFromOneFilterIsRejectedAgainstAnother() {
        // Issue a cursor while filtering camp-1, then replay it against camp-2.
        String camp1Cursor = service.page(LOOB.value(), null, "camp-1", null, null, "score", null, 5)
                .nextCursor();
        assertThat(camp1Cursor).isNotNull();

        assertThatExceptionOfType(InvalidCursorException.class).isThrownBy(() ->
                service.page(LOOB.value(), null, "camp-2", null, null, "score", camp1Cursor, 5));
    }

    @Test
    void aCursorFromOneSortIsRejectedAgainstAnother() {
        String scoreCursor = service.page(LOOB.value(), null, "camp-1", null, null, "score", null, 5)
                .nextCursor();

        assertThatExceptionOfType(InvalidCursorException.class).isThrownBy(() ->
                service.page(LOOB.value(), null, "camp-1", null, null, "age", scoreCursor, 5));
    }

    @Test
    void limitsOutsideOneToHundredAreRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                service.page(LOOB.value(), null, null, null, null, "score", null, 0));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                service.page(LOOB.value(), null, null, null, null, "score", null, 101));
    }

    @Test
    void anUnknownSortIsRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                service.page(LOOB.value(), null, null, null, null, "sideways", null, 25));
    }

    @Test
    void searchMatchesReferralCodeOrRefereeUserIdCaseInsensitively() {
        // Two findable rows on top of the setUp() data (whose codes/referees don't
        // contain these terms).
        repository.save(FraudCase.open(new CaseOpening(
                LOOB, CampaignId.of("camp-1"), ReferralCode.of("LOOB-FINDME-1"), "alice-9",
                Decision.HOLD, 0.5, List.of(ReasonCode.DEVICE_COLLISION), BASE.plusSeconds(500))));
        repository.save(FraudCase.open(new CaseOpening(
                LOOB, CampaignId.of("camp-1"), ReferralCode.of("OTHER-CODE"), "bob-7",
                Decision.HOLD, 0.5, List.of(ReasonCode.DEVICE_COLLISION), BASE.plusSeconds(501))));
        entityManager.flush();

        // By code — case-insensitive, partial.
        assertThat(service.page(LOOB.value(), null, null, null, "findme", "score", null, 25).items())
                .extracting(CaseView::referralCode).containsExactly("LOOB-FINDME-1");

        // By referee user id.
        assertThat(service.page(LOOB.value(), null, null, null, "bob-7", "score", null, 25).items())
                .extracting(CaseView::refereeUserId).containsExactly("bob-7");

        // No match -> empty final page.
        CursorPage<CaseView> none = service.page(LOOB.value(), null, null, null, "zzz-nope", "score", null, 25);
        assertThat(none.items()).isEmpty();
        assertThat(none.nextCursor()).isNull();
    }

    @Test
    void aSearchCursorIsRejectedWhenTheSearchTermChanges() {
        // 'camp1-' matches all 25 camp-1 referees; page 1 leaves a cursor.
        String cursor = service.page(LOOB.value(), null, null, null, "camp1-", "age", null, 5).nextCursor();
        assertThat(cursor).isNotNull();

        assertThatExceptionOfType(InvalidCursorException.class).isThrownBy(() ->
                service.page(LOOB.value(), null, null, null, "camp2-", "age", cursor, 5));
    }
}
