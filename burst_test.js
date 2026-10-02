/**
 * Paytm Money Take-Home Assignment - High-Concurrency Burst Load Test
 * 
 * Features:
 *  - 100% Native Node.js (No npm install required!)
 *  - Automated user registration & JWT token generation
 *  - Show creation
 *  - Test 1: 20,000 Hot-Seat Storm (all competing for seat A1)
 *  - Test 2: 20,000 Mixed Workload (Idempotent replays, 422 mismatches, 400 limits, 409 hot-seats)
 *  - Invariant reconciliation verification (available + confirmed == total)
 * 
 * Usage:
 *   node burst_test.js
 *   node burst_test.js https://paytmbookingsystem.onrender.com
 *   node burst_test.js http://localhost:8080 --total 20000 --concurrency 100
 */

const BASE_URL = (process.argv[2] && !process.argv[2].startsWith('--'))
    ? process.argv[2].replace(/\/$/, '')
    : 'https://paytmbookingsystem.onrender.com';

const args = process.argv.slice(2);
function getArg(name, defaultValue) {
    const idx = args.indexOf(`--${name}`);
    if (idx !== -1 && args[idx + 1]) return parseInt(args[idx + 1], 10);
    return defaultValue;
}

const TOTAL_REQUESTS = getArg('total', 20000);
const CONCURRENCY = getArg('concurrency', 100);

console.log('='.repeat(70));
console.log('  PAYTM MONEY - HIGH CONCURRENCY LOAD TESTING ENGINE');
console.log('='.repeat(70));
console.log(` Target Server : ${BASE_URL}`);
console.log(` Total Requests: ${TOTAL_REQUESTS.toLocaleString()}`);
console.log(` Concurrency   : ${CONCURRENCY} parallel workers`);
console.log('='.repeat(70));

async function apiRequest(path, method = 'GET', body = null, token = null, idempotencyKey = null, timeoutMs = 45000) {
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    try {
        const res = await fetch(`${BASE_URL}${path}`, {
            method,
            headers,
            body: body ? JSON.stringify(body) : undefined,
            signal: controller.signal,
        });

        clearTimeout(timer);
        let data = null;
        const text = await res.text();
        try {
            data = JSON.parse(text);
        } catch {
            data = text;
        }
        return { status: res.status, data };
    } catch (err) {
        clearTimeout(timer);
        throw err;
    }
}

// Concurrency pool runner
async function runConcurrentPool(tasks, concurrency, onProgress) {
    let index = 0;
    let completed = 0;
    const results = new Array(tasks.length);

    async function worker() {
        while (index < tasks.length) {
            const currentIndex = index++;
            const task = tasks[currentIndex];
            const start = performance.now();
            try {
                const res = await task();
                const latency = performance.now() - start;
                results[currentIndex] = { status: res.status, latency, error: false };
            } catch (err) {
                const latency = performance.now() - start;
                results[currentIndex] = { status: 0, latency, error: true, message: err.message };
            }
            completed++;
            if (completed % 1000 === 0 || completed === tasks.length) {
                onProgress(completed, tasks.length);
            }
        }
    }

    const workers = Array.from({ length: concurrency }, () => worker());
    await Promise.all(workers);
    return results;
}

function calculatePercentiles(latencies) {
    if (latencies.length === 0) return { p50: 0, p95: 0, p99: 0, avg: 0 };
    latencies.sort((a, b) => a - b);
    const p50 = latencies[Math.floor(latencies.length * 0.50)].toFixed(1);
    const p95 = latencies[Math.floor(latencies.length * 0.95)].toFixed(1);
    const p99 = latencies[Math.floor(latencies.length * 0.99)].toFixed(1);
    const avg = (latencies.reduce((a, b) => a + b, 0) / latencies.length).toFixed(1);
    return { p50, p95, p99, avg };
}

async function main() {
    // 0. Verify Health & Handle Render Cold Start
    console.log('\n[0/5] Checking server health (waking up cold instance if sleeping)...');
    const maxRetries = 25; // Poll up to ~100 seconds
    let isHealthy = false;

    for (let attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            process.stdout.write(`  -> Health ping attempt ${attempt}/${maxRetries}... `);
            const health = await apiRequest('/actuator/health', 'GET', null, null, null, 15000);
            if (health.status === 200) {
                console.log(`[HTTP 200] Server is WARM and healthy!`);
                isHealthy = true;
                break;
            } else {
                console.log(`[HTTP ${health.status}] Waiting for JVM startup...`);
            }
        } catch (e) {
            console.log(`[Waiting for spin-up] (${e.name === 'AbortError' ? 'Ping timeout' : e.message})`);
        }
        await new Promise(r => setTimeout(r, 4000));
    }

    if (!isHealthy) {
        console.error(`\nFatal: Target server at ${BASE_URL} did not become healthy within 100s.`);
        process.exit(1);
    }

    // 1. Setup Phase: Register & Login Users
    console.log('\n[1/5] Setting up test users & authentication tokens...');
    const userCount = 30;
    const batchPromises = Array.from({ length: userCount }, async (_, i) => {
        const username = `loaduser_${Date.now().toString().slice(-6)}_${i}_${crypto.randomUUID().slice(0, 4)}`;
        const password = 'Password@123';

        // Register
        const regRes = await apiRequest('/api/v1/auth/register', 'POST', {
            username,
            password,
            email: `${username}@test.com`,
            fullName: `Load User ${i}`
        });

        let token = regRes.data?.token;
        if (!token) {
            // Fallback to login if already exists
            const loginRes = await apiRequest('/api/v1/auth/login', 'POST', { username, password });
            token = loginRes.data?.token;
        }
        return token;
    });

    const resolvedTokens = await Promise.all(batchPromises);
    const userTokens = resolvedTokens.filter(Boolean);
    console.log(`  -> Successfully created and authenticated ${userTokens.length} users.`);

    if (userTokens.length === 0) {
        console.error('Fatal: No users could be authenticated. Aborting test.');
        process.exit(1);
    }

    // =========================================================================
    // TEST 1: HOT-SEAT STORM (20,000 requests for seat A1)
    // =========================================================================
    console.log('\n[2/5] Creating Show 1 for Hot-Seat Storm Test...');
    const seats20 = [];
    for (let r of ['A', 'B']) {
        for (let i = 1; i <= 10; i++) seats20.push(`${r}${i}`);
    }

    const show1Res = await apiRequest('/shows', 'POST', {
        name: `HotSeat-Show-${Date.now()}`,
        seats: seats20,
        price_paise: 15000,
        per_user_limit: 4
    });

    const show1Id = show1Res.data?.id;
    if (!show1Id) {
        console.error('Fatal: Failed to create Show 1:', show1Res);
        process.exit(1);
    }
    console.log(`  -> Created Show 1 with ID: ${show1Id} (20 seats, price: 15000 paise).`);

    console.log(`\n[3/5] RUNNING TEST 1: ${TOTAL_REQUESTS.toLocaleString()} HOT-SEAT STORM on seat "A1"...`);
    console.log(`  -> Competing for seat: "A1"`);
    console.log(`  -> Starting ${CONCURRENCY} concurrent workers...`);

    const stormTasks = Array.from({ length: TOTAL_REQUESTS }, (_, i) => {
        const token = userTokens[i % userTokens.length];
        const key = `HOT-STORM-${i}-${crypto.randomUUID().slice(0, 8)}`;
        return () => apiRequest(`/shows/${show1Id}/reserve`, 'POST', { seat_numbers: ['A1'] }, token, key);
    });

    const stormStartTime = performance.now();
    const stormResults = await runConcurrentPool(stormTasks, CONCURRENCY, (done, total) => {
        const pct = ((done / total) * 100).toFixed(0);
        process.stdout.write(`\r  Progress: ${done.toLocaleString()}/${total.toLocaleString()} [${pct}%]`);
    });
    const stormDuration = (performance.now() - stormStartTime) / 1000;
    console.log(`\n  Completed in ${stormDuration.toFixed(2)} seconds! (${(TOTAL_REQUESTS / stormDuration).toFixed(0)} requests/sec)`);

    // Tally results for Test 1
    let storm201 = 0;
    let storm409 = 0;
    let storm5xx = 0;
    let stormOther = 0;
    const stormLatencies = [];

    for (let r of stormResults) {
        stormLatencies.push(r.latency);
        if (r.status === 201) storm201++;
        else if (r.status === 409) storm409++;
        else if (r.status >= 500) storm5xx++;
        else stormOther++;
    }

    const stormStats = calculatePercentiles(stormLatencies);

    console.log('\n  +-------------------------------------------------------------+');
    console.log('  |            TEST 1: HOT-SEAT STORM SCORECARD                 |');
    console.log('  +-------------------------------+---------------+-------------+');
    console.log('  | Metric                        | Result        | Expectation |');
    console.log('  +-------------------------------+---------------+-------------+');
    console.log(`  | 201 Created (Winner)          | ${String(storm201).padEnd(13)} | Exactly 1   |`);
    console.log(`  | 409 Conflict (Losers)         | ${String(storm409).padEnd(13)} | ${TOTAL_REQUESTS - 1}       |`);
    console.log(`  | 5xx Server Errors             | ${String(storm5xx).padEnd(13)} | ZERO (0)    |`);
    console.log(`  | Other Status Codes            | ${String(stormOther).padEnd(13)} | 0           |`);
    console.log(`  | Throughput                    | ${(TOTAL_REQUESTS / stormDuration).toFixed(0).padEnd(9)} req/s| High        |`);
    console.log(`  | Latency: p50 / p95 / p99      | ${stormStats.p50} / ${stormStats.p95} / ${stormStats.p99} ms`.padEnd(48) + '|');
    console.log('  +-------------------------------+---------------+-------------+');

    const test1Passed = storm201 === 1 && storm409 === (TOTAL_REQUESTS - 1) && storm5xx === 0;
    console.log(`  => TEST 1 STATUS: ${test1Passed ? 'PASSED (100% INVARIANT CONCURRENCY)' : 'FAILED'}`);

    // Verify Show 1 Reconciliation Invariant
    const show1State = await apiRequest(`/shows/${show1Id}`);
    console.log(`  -> Show 1 Inventory: Available=${show1State.data.available_count}, Confirmed=${show1State.data.confirmed_count}, Total=${show1State.data.total_seats}`);
    console.log(`  -> Invariant (Available + Confirmed == Total): ${show1State.data.available_count + show1State.data.confirmed_count === show1State.data.total_seats ? 'PASSED' : 'FAILED'}`);

    // =========================================================================
    // TEST 2: MIXED WORKLOAD STORM (20,000 mixed requests)
    // =========================================================================
    console.log('\n[4/5] Creating Show 2 for Mixed Workload Stress Test...');
    const show2Res = await apiRequest('/shows', 'POST', {
        name: `Mixed-Show-${Date.now()}`,
        seats: seats20,
        price_paise: 20000,
        per_user_limit: 4
    });
    const show2Id = show2Res.data?.id;
    if (!show2Id) {
        console.error('Fatal: Failed to create Show 2:', show2Res);
        process.exit(1);
    }
    console.log(`  -> Created Show 2 with ID: ${show2Id} (20 seats, per-user limit: 4).`);

    // Calculate workload portions dynamically based on TOTAL_REQUESTS
    const countIdemp = Math.floor(TOTAL_REQUESTS * 0.20); // ~20%
    const countTampered = Math.floor(TOTAL_REQUESTS * 0.125); // ~12.5%
    const countLimit = Math.floor(TOTAL_REQUESTS * 0.15); // ~15%
    const countHotSeat = TOTAL_REQUESTS - countIdemp - countTampered - countLimit; // Remaining ~52.5%

    console.log(`\n[5/5] RUNNING TEST 2: ${TOTAL_REQUESTS.toLocaleString()} MIXED WORKLOAD BURST...`);
    console.log('  Workload Breakdown:');
    console.log(`   - ${countIdemp.toLocaleString()} Idempotent Replays (Same key & payload -> expect cached 200/201)`);
    console.log(`   - ${countTampered.toLocaleString()} Tampered Idempotent Requests (Same key, different payload -> expect 409 Conflict)`);
    console.log(`   - ${countLimit.toLocaleString()} Limit Exceeding Requests (Booking 5 seats > limit 4 -> expect 400 Bad Request)`);
    console.log(`   - ${countHotSeat.toLocaleString()} Hot-Seat Storm Requests for "A2" (Expect 1 winner 201, rest 409 Conflict)`);

    // Seed the base idempotent reservation
    const idempKey = `IDEMP-BASE-${crypto.randomUUID()}`;
    const idempToken = userTokens[0];
    await apiRequest(`/shows/${show2Id}/reserve`, 'POST', { seat_numbers: ['A1'] }, idempToken, idempKey);

    const split1 = countIdemp;
    const split2 = split1 + countTampered;
    const split3 = split2 + countLimit;

    const mixedTasks = Array.from({ length: TOTAL_REQUESTS }, (_, index) => {
        if (index < split1) {
            // Category 1: Idempotent Replays
            return () => apiRequest(`/shows/${show2Id}/reserve`, 'POST', { seat_numbers: ['A1'] }, idempToken, idempKey);
        } else if (index < split2) {
            // Category 2: Tampered Idempotent Requests (Seat B5 instead of A1 -> 409 Conflict)
            return () => apiRequest(`/shows/${show2Id}/reserve`, 'POST', { seat_numbers: ['B5'] }, idempToken, idempKey);
        } else if (index < split3) {
            // Category 3: Limit Exceeding Requests (5 seats -> 400 Bad Request)
            const token = userTokens[index % userTokens.length];
            const k = `LIMIT-KEY-${index}-${crypto.randomUUID().slice(0, 8)}`;
            return () => apiRequest(`/shows/${show2Id}/reserve`, 'POST', { seat_numbers: ['B1', 'B2', 'B3', 'B4', 'B5'] }, token, k);
        } else {
            // Category 4: Hot-Seat Storm for seat "A2" (1 winner 201, rest 409 Conflict)
            const token = userTokens[index % userTokens.length];
            const k = `HOT-KEY-${index}-${crypto.randomUUID().slice(0, 8)}`;
            return () => apiRequest(`/shows/${show2Id}/reserve`, 'POST', { seat_numbers: ['A2'] }, token, k);
        }
    });

    const mixedStartTime = performance.now();
    const mixedResults = await runConcurrentPool(mixedTasks, CONCURRENCY, (done, total) => {
        const pct = ((done / total) * 100).toFixed(0);
        process.stdout.write(`\r  Progress: ${done.toLocaleString()}/${total.toLocaleString()} [${pct}%]`);
    });
    const mixedDuration = (performance.now() - mixedStartTime) / 1000;
    console.log(`\n  Completed in ${mixedDuration.toFixed(2)} seconds! (${(TOTAL_REQUESTS / mixedDuration).toFixed(0)} requests/sec)`);

    // Tally results for Test 2
    let mixedSuccess = 0;   // 200 or 201
    let mixed400 = 0;       // 400 Bad Request
    let mixed409 = 0;       // 409 Conflict (Hot-seat losers + tampered keys)
    let mixed5xx = 0;       // 5xx Server Error

    for (let r of mixedResults) {
        if (r.status === 200 || r.status === 201) mixedSuccess++;
        else if (r.status === 400) mixed400++;
        else if (r.status === 409) mixed409++;
        else if (r.status >= 500) mixed5xx++;
    }

    const expectedSuccess = countIdemp + (countHotSeat > 0 ? 1 : 0);
    const expected400 = countLimit;
    const expected409 = countTampered + (countHotSeat > 0 ? (countHotSeat - 1) : 0);

    console.log('\n  +-------------------------------------------------------------+');
    console.log('  |          TEST 2: MIXED WORKLOAD BURST SCORECARD             |');
    console.log('  +-------------------------------+---------------+-------------+');
    console.log('  | Category / Status             | Result        | Expectation |');
    console.log('  +-------------------------------+---------------+-------------+');
    console.log(`  | Idempotent Replays + Winner   | ${String(mixedSuccess).padEnd(13)} | ${String(expectedSuccess).padEnd(11)} |`);
    console.log(`  | 400 User Limit Exceeded       | ${String(mixed400).padEnd(13)} | ${String(expected400).padEnd(11)} |`);
    console.log(`  | 409 Conflict (Contention+Keys)| ${String(mixed409).padEnd(13)} | ${String(expected409).padEnd(11)} |`);
    console.log(`  | 5xx Internal Server Errors    | ${String(mixed5xx).padEnd(13)} | ZERO (0)    |`);
    console.log(`  | Throughput                    | ${(TOTAL_REQUESTS / mixedDuration).toFixed(0).padEnd(9)} req/s| High        |`);
    console.log('  +-------------------------------+---------------+-------------+');

    const test2Passed = mixedSuccess === expectedSuccess && mixed400 === expected400 && mixed409 === expected409 && mixed5xx === 0;
    console.log(`  => TEST 2 STATUS: ${test2Passed ? 'PASSED (100% INVARIANT ACCURACY)' : 'FAILED'}`);

    // Verify Show 2 Reconciliation Invariant
    const show2State = await apiRequest(`/shows/${show2Id}`);
    console.log(`\n  -> Show 2 Final Inventory: Available=${show2State.data.available_count}, Confirmed=${show2State.data.confirmed_count}, Total=${show2State.data.total_seats}`);
    const invariant2 = (show2State.data.available_count + show2State.data.confirmed_count) === show2State.data.total_seats;
    console.log(`  -> Reconciliation Invariant (Available + Confirmed == Total): ${invariant2 ? 'PASSED' : 'FAILED'}`);

    console.log('\n' + '='.repeat(70));
    console.log(`  FINAL BENCHMARK: ${test1Passed && test2Passed && invariant2 ? 'ALL REQUESTS PASSED WITH 100% SUCCESS' : 'TESTS FINISHED'}`);
    console.log('='.repeat(70) + '\n');
}

main().catch(err => {
    console.error('Fatal load test error:', err);
    process.exit(1);
});
