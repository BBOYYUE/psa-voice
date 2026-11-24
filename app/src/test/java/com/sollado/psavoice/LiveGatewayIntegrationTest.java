package com.sollado.psavoice;

import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Explicit opt-in, real WSS + server ASR + Hermes. Never invokes MusicExecutor. */
public final class LiveGatewayIntegrationTest {
    @Test public void realJavaTransportTwoTurnsAndCancellation() throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("PSA_LIVE_INTEGRATION")));
        String token = System.getenv("VOICE_GATEWAY_TOKEN");
        String fixture = System.getenv("PSA_TEST_WAV");
        assertNotNull(token); assertNotNull(fixture);
        byte[] pcm = pcm(new File(fixture));
        BlockingQueue<JSONObject> events = new LinkedBlockingQueue<>();
        LiveGateway client = new LiveGateway(new ServerConfig("https://ai.sollado.com", token), new LiveGateway.Listener() {
            @Override public void event(JSONObject body) { events.add(body); }
            @Override public void failed(String message) {
                try { events.add(new JSONObject().put("event", "client_error").put("message", message)); }
                catch (Exception e) { throw new AssertionError(e); }
            }
        });
        try {
            JSONObject ready = next(events, "ready");
            String session = ready.getString("session_id");
            for (int round = 1; round <= 2; round++) {
                for (int offset = 0; offset < pcm.length; offset += 3200) {
                    byte[] bytes = Arrays.copyOfRange(pcm, offset, Math.min(offset + 3200, pcm.length));
                    assertTrue(client.audio(bytes, bytes.length));
                    Thread.sleep(100);
                }
                for (int i = 0; i < 25; i++) {
                    assertTrue(client.audio(new byte[3200], 3200));
                    Thread.sleep(100);
                }
                LiveTurn turn = new LiveTurn(round);
                int partials = 0;
                while (true) {
                    JSONObject event = receive(events);
                    String kind = event.getString("event");
                    if (kind.equals("heartbeat")) continue;
                    assertEquals(round, LiveProtocol.integer(event, "turn_id"));
                    if (kind.equals("transcript")) {
                        turn.transcript(event.getBoolean("final"));
                        if (!event.getBoolean("final")) partials++;
                    } else if (kind.equals("speech")) {
                        turn.speech((int) LiveProtocol.integer(event, "index"), event.getString("text"));
                    } else if (kind.equals("result")) {
                        GatewayClient.Result result = LiveProtocol.result(event, session, round);
                        turn.result(result, (int) LiveProtocol.integer(event, "speech_count"));
                        assertTrue(partials > 1);
                        if ("execute".equals(result.decision)) {
                            // This fixture test has no car: report failed, never claim or perform playback.
                            client.send(new JSONObject().put("event", "execution_result").put("turn_id", round).put("status", "failed"));
                            next(events, "execution_ack");
                        }
                        System.out.println("WSS round=" + round + " partials=" + partials + " speech=" + turn.speechCount
                                + " decision=" + result.decision);
                        break;
                    }
                }
                assertTrue(client.send(new JSONObject().put("event", "resume").put("turn_id", round)));
                assertEquals(round + 1, LiveProtocol.integer(next(events, "listening"), "turn_id"));
            }
            assertTrue(client.send(new JSONObject().put("event", "cancel")));
            assertEquals("cancelled", next(events, "ended").getString("reason"));
        } finally { client.cancel(); }
    }
    private static JSONObject receive(BlockingQueue<JSONObject> events) throws Exception {
        JSONObject event = events.poll(40, TimeUnit.SECONDS);
        assertNotNull("Timed out waiting for server", event);
        assertNotEquals(event.toString(), "error", event.getString("event"));
        assertNotEquals(event.toString(), "client_error", event.getString("event"));
        return event;
    }
    private static JSONObject next(BlockingQueue<JSONObject> events, String kind) throws Exception {
        while (true) {
            JSONObject event = receive(events);
            if (kind.equals(event.getString("event"))) return event;
            assertNotEquals("Unexpected end", "ended", event.getString("event"));
        }
    }
    private static byte[] pcm(File file) throws Exception {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(file.toPath())))) {
            byte[] header = new byte[12]; input.readFully(header);
            assertEquals("RIFF", new String(header, 0, 4, StandardCharsets.US_ASCII));
            assertEquals("WAVE", new String(header, 8, 4, StandardCharsets.US_ASCII));
            boolean format = false;
            while (input.available() >= 8) {
                byte[] id = new byte[4]; input.readFully(id);
                int size = Integer.reverseBytes(input.readInt());
                assertTrue(size >= 0 && size <= 25 * 1024 * 1024);
                byte[] chunk = new byte[size]; input.readFully(chunk);
                if ((size & 1) != 0) input.readByte();
                String name = new String(id, StandardCharsets.US_ASCII);
                if (name.equals("fmt ")) {
                    DataInputStream fmt = new DataInputStream(new ByteArrayInputStream(chunk));
                    assertEquals(1, Short.reverseBytes(fmt.readShort()));
                    assertEquals(1, Short.reverseBytes(fmt.readShort()));
                    assertEquals(16000, Integer.reverseBytes(fmt.readInt()));
                    fmt.skipBytes(6);
                    assertEquals(16, Short.reverseBytes(fmt.readShort()));
                    format = true;
                } else if (name.equals("data")) {
                    assertTrue(format); return chunk;
                }
            }
        }
        throw new AssertionError("Missing WAV data");
    }
}
