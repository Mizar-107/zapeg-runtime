package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HeraldorSafetyStatusContractTest {

    @Test
    void machineStatusRemainsTheExactSixFieldSidecarContract() {
        UUID nonce = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID incident = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertEquals(
                "heraldor_safety mode=manual ceiling=auto generation=7"
                        + " nonce=11111111-1111-1111-1111-111111111111"
                        + " incident=22222222-2222-2222-2222-222222222222 writable=1",
                HeraldorSafetyController.formatStatus(
                        HeraldorSafetyMode.MANUAL,
                        HeraldorSafetyMode.AUTO,
                        7L,
                        nonce,
                        incident,
                        true));
    }
}
