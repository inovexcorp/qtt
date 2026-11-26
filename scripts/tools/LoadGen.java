import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Closed-loop HTTP load generator used by scripts/measure.py.
 *
 *   java scripts/tools/LoadGen.java <url> <seconds> <concurrency>
 *
 * Each worker keeps one keep-alive connection busy. Unlike ApacheBench, the JDK client handles chunked responses
 * correctly (ab miscounts them on keep-alive connections, which doubled Karaf's apparent throughput), and every
 * response is checked: non-2xx statuses and bodies whose length differs from the first response count as errors.
 * Prints one JSON object: requests per second, latency percentiles in ms, error counts.
 */
public class LoadGen {

    public static void main(String[] args) throws Exception {
        URI uri = URI.create(args[0]);
        long seconds = Long.parseLong(args[1]);
        int concurrency = Integer.parseInt(args[2]);
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
        int expectedLength = client.send(request, HttpResponse.BodyHandlers.ofByteArray()).body().length;

        AtomicBoolean running = new AtomicBoolean(true);
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Future<long[]>> futures = new ArrayList<>();
        long start = System.nanoTime();
        for (int i = 0; i < concurrency; i++) {
            futures.add(pool.submit(() -> {
                long[] samples = new long[1 << 20];
                int n = 0;
                long non2xx = 0;
                long badLength = 0;
                long exceptions = 0;
                while (running.get()) {
                    long t0 = System.nanoTime();
                    try {
                        HttpResponse<byte[]> r = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                        if (r.statusCode() / 100 != 2) {
                            non2xx++;
                        } else if (r.body().length != expectedLength) {
                            badLength++;
                        }
                    } catch (Exception e) {
                        exceptions++;
                    }
                    if (n < samples.length) {
                        samples[n++] = System.nanoTime() - t0;
                    }
                }
                long[] out = Arrays.copyOf(samples, n + 3);
                out[n] = non2xx;
                out[n + 1] = badLength;
                out[n + 2] = exceptions;
                return out;
            }));
        }
        Thread.sleep(seconds * 1000);
        running.set(false);
        long elapsed = System.nanoTime() - start;
        List<Long> all = new ArrayList<>();
        long non2xx = 0;
        long badLength = 0;
        long exceptions = 0;
        for (Future<long[]> f : futures) {
            long[] r = f.get();
            for (int i = 0; i < r.length - 3; i++) {
                all.add(r[i]);
            }
            non2xx += r[r.length - 3];
            badLength += r[r.length - 2];
            exceptions += r[r.length - 1];
        }
        pool.shutdownNow();
        long[] lat = all.stream().mapToLong(Long::longValue).sorted().toArray();
        System.out.printf("{\"requests\": %d, \"rps\": %.1f, \"p50_ms\": %.2f, \"p95_ms\": %.2f, \"p99_ms\": %.2f, "
                        + "\"non2xx\": %d, \"bad_length\": %d, \"exceptions\": %d, \"expected_length\": %d}%n",
                lat.length, lat.length / (elapsed / 1e9), pct(lat, 50), pct(lat, 95), pct(lat, 99),
                non2xx, badLength, exceptions, expectedLength);
    }

    private static double pct(long[] sorted, int p) {
        if (sorted.length == 0) {
            return 0;
        }
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(p / 100.0 * sorted.length) - 1)] / 1e6;
    }
}
