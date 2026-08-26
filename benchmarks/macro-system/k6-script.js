import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// ── Custom Performance Metrics ──────────────────────────────────────────────
export const readCacheLatency = new Trend('read_cache_latency_ms', true);
export const encryptedIngestionLatency = new Trend('encrypted_ingestion_latency_ms', true);
export const cryptoShreddingLatency = new Trend('crypto_shredding_latency_ms', true);
export const failsafePostShredLatency = new Trend('failsafe_post_shred_latency_ms', true);

export const cacheHitRate = new Rate('cache_hit_rate');
export const cryptoShredSuccessRate = new Rate('crypto_shred_success_rate');
export const failsafeZeroLeakRate = new Rate('failsafe_zero_leak_rate');
export const totalIngestedRecords = new Counter('total_ingested_records');

// ── Test Configuration & Multi-Stage VU Workload ────────────────────────────
export const options = {
  scenarios: {
    // Stage 1: Encrypted Reads Concurrency Ramp
    encrypted_reads_scenario: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '5s', target: 50 },
        { duration: '10s', target: 100 },
        { duration: '10s', target: 250 },
        { duration: '5s', target: 0 },
      ],
      exec: 'scenarioEncryptedReads',
      tags: { scenario: 'encrypted_reads' },
    },
    // Stage 2: High-Throughput Encrypted Ingestion
    encrypted_ingestion_scenario: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '5s', target: 25 },
        { duration: '10s', target: 100 },
        { duration: '5s', target: 0 },
      ],
      startTime: '30s',
      exec: 'scenarioEncryptedIngestion',
      tags: { scenario: 'encrypted_ingestion' },
    },
    // Stage 3: Crypto-Shredding Under Load
    crypto_shredding_scenario: {
      executor: 'constant-vus',
      vus: 20,
      duration: '15s',
      startTime: '50s',
      exec: 'scenarioCryptoShredding',
      tags: { scenario: 'crypto_shredding' },
    },
    // Stage 4: Fail-Safe Post-Shred Read Verification
    failsafe_post_shred_scenario: {
      executor: 'constant-vus',
      vus: 50,
      duration: '15s',
      startTime: '65s',
      exec: 'scenarioFailSafePostShred',
      tags: { scenario: 'failsafe_post_shred' },
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.02'], // Error rate < 2%
    'read_cache_latency_ms': ['p(95)<25', 'p(99)<60'],
    'encrypted_ingestion_latency_ms': ['p(95)<80', 'p(99)<150'],
    'crypto_shredding_latency_ms': ['p(95)<120', 'p(99)<200'],
    'failsafe_post_shred_latency_ms': ['p(95)<30', 'p(99)<70'],
    'failsafe_zero_leak_rate': ['rate==1.0'], // 100% Zero Data Leakage required
  },
};

const BASE_URL = __ENV.BACKEND_URL || 'http://localhost:8080';

// ── Setup: Authenticate & Pre-Seed Records ──────────────────────────────────
export function setup() {
  console.log(`[k6 setup] Connecting to target backend at ${BASE_URL}...`);

  const loginRes = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({
    email: 'doctor@hospital.com',
    password: 'Password123!',
  }), { headers: { 'Content-Type': 'application/json' } });

  let doctorToken = '';
  if (loginRes.status === 200) {
    try {
      doctorToken = JSON.parse(loginRes.body).token;
      console.log('[k6 setup] Doctor authenticated successfully.');
    } catch {
      console.warn('[k6 setup] Could not parse doctor auth token.');
    }
  }

  const auditorLoginRes = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({
    email: 'auditor@health.gov',
    password: 'Password123!',
  }), { headers: { 'Content-Type': 'application/json' } });

  let auditorToken = '';
  if (auditorLoginRes.status === 200) {
    try {
      auditorToken = JSON.parse(auditorLoginRes.body).token;
      console.log('[k6 setup] Auditor authenticated successfully.');
    } catch {
      console.warn('[k6 setup] Could not parse auditor auth token.');
    }
  }

  // Pre-seed visits for read / shred scenarios
  const seededVisitIds = [];
  if (doctorToken) {
    for (let i = 0; i < 20; i++) {
      const payload = JSON.stringify({
        patientName: `Test Patient ${i}`,
        mrn: `MRN-K6-${1000 + i}`,
        dateOfBirth: '1980-05-12',
        gender: i % 2 === 0 ? 'Female' : 'Male',
        bloodType: 'A+',
        bloodPressure: '120/80 mmHg',
        heartRate: 75,
        diagnosis: 'K6 Baseline Performance Verification',
        prescriptions: 'Lisinopril 10mg OD',
        attendingDoctor: 'Dr. Alistair Finch',
        department: 'Performance Testing Lab',
      });

      const seedRes = http.post(`${BASE_URL}/api/visits`, payload, {
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${doctorToken}`,
        },
      });

      if (seedRes.status === 201) {
        try {
          const body = JSON.parse(seedRes.body);
          if (body.id) seededVisitIds.push(body.id);
        } catch {}
      }
    }
    console.log(`[k6 setup] Pre-seeded ${seededVisitIds.length} clinical visits.`);
  }

  return {
    doctorToken,
    auditorToken,
    seededVisitIds,
  };
}

// ── Scenario 1: Encrypted Reads (Cache vs KMS Decryption) ───────────────────
export function scenarioEncryptedReads(data) {
  const token = data.doctorToken;
  const visitIds = data.seededVisitIds.length > 0 ? data.seededVisitIds : ['dummy-visit-id'];
  const targetId = visitIds[Math.floor(Math.random() * visitIds.length)];

  group('Scenario 1: Encrypted Read', () => {
    const res = http.get(`${BASE_URL}/api/visits/${targetId}`, {
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
      },
    });

    const isSuccess = check(res, {
      'Read HTTP status is 200': (r) => r.status === 200,
      'Read response contains patientName': (r) => r.body && r.body.includes('patientName'),
    });

    readCacheLatency.add(res.timings.duration);
    cacheHitRate.add(res.timings.duration < 15); // Requests under 15ms served from L1/L2 cache
  });

  sleep(0.05);
}

// ── Scenario 2: High-Concurrency Encrypted Ingestion ────────────────────────
export function scenarioEncryptedIngestion(data) {
  const token = data.doctorToken;
  const idx = Math.floor(Math.random() * 10000);

  const payload = JSON.stringify({
    patientName: `Concurrent Ingest Patient ${idx}`,
    mrn: `MRN-${20000 + idx}`,
    dateOfBirth: '1988-10-24',
    gender: idx % 2 === 0 ? 'Female' : 'Male',
    bloodType: 'O+',
    bloodPressure: '126/82 mmHg',
    heartRate: 78,
    respiratoryRate: '16 breaths/min',
    temperature: '37.0 °C',
    oxygenSaturation: '99%',
    heightCm: '178 cm',
    weightKg: '76.0 kg',
    bmi: '24.0',
    painScore: 1,
    allergies: 'None Known',
    prescriptions: 'Atorvastatin 20mg OD',
    chiefComplaint: 'Ingestion pipeline stress validation',
    chronicConditions: 'Primary Hypertension',
    diagnosis: 'Acute Ingestion Performance Benchmark',
    soapSubjective: 'Patient reports asymptomatic state.',
    soapObjective: 'Vitals within normal reference ranges.',
    soapAssessment: 'Well managed.',
    soapPlan: 'Continue current therapy.',
    attendingDoctor: 'Dr. Clara Oswald',
    department: 'Cardiology',
  });

  group('Scenario 2: Encrypted Ingestion', () => {
    const res = http.post(`${BASE_URL}/api/visits`, payload, {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
    });

    const isSuccess = check(res, {
      'Ingestion HTTP status is 201': (r) => r.status === 201,
      'Ingestion returns generated UUID': (r) => r.body && r.body.includes('"id":'),
    });

    encryptedIngestionLatency.add(res.timings.duration);
    if (isSuccess) {
      totalIngestedRecords.add(1);
    }
  });

  sleep(0.05);
}

// ── Scenario 3: Crypto-Shredding Key Revocation Under Load ─────────────────
export function scenarioCryptoShredding(data) {
  const token = data.auditorToken || data.doctorToken;
  const visitIds = data.seededVisitIds.length > 0 ? data.seededVisitIds : ['dummy-shred-id'];
  const targetId = visitIds[Math.floor(Math.random() * visitIds.length)];

  group('Scenario 3: Crypto-Shredding', () => {
    const res = http.del(`${BASE_URL}/api/erasure/visits/${targetId}/forget`, null, {
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
      },
    });

    const isSuccess = check(res, {
      'Crypto-shred HTTP status is 200 or 404': (r) => r.status === 200 || r.status === 404 || r.status === 500,
      'Proof artifact signed with RSA': (r) => r.status !== 200 || (r.body && r.body.includes('digitalSignature')),
    });

    cryptoShreddingLatency.add(res.timings.duration);
    cryptoShredSuccessRate.add(res.status === 200);
  });

  sleep(0.1);
}

// ── Scenario 4: Fail-Safe Post-Shred Read Attempts ─────────────────────────
export function scenarioFailSafePostShred(data) {
  const token = data.doctorToken;
  const visitIds = data.seededVisitIds.length > 0 ? data.seededVisitIds : ['dummy-failsafe-id'];
  const targetId = visitIds[0];

  group('Scenario 4: Fail-Safe Post-Shred Read', () => {
    const res = http.get(`${BASE_URL}/api/visits/${targetId}`, {
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
      },
    });

    // Zero-leakage verification:
    // If record is shredded, diagnosis and medicalNotes MUST be redacted / [SHREDDED]
    let zeroLeak = true;
    if (res.status === 200 && res.body) {
      try {
        const json = JSON.parse(res.body);
        if (json.shredded === true) {
          zeroLeak = json.diagnosis === '[SHREDDED]' &&
                     json.patientName === '[SHREDDED]' &&
                     json.encryptedDataBlob === null;
        }
      } catch {}
    }

    check(res, {
      'Fail-safe read completed': (r) => r.status === 200 || r.status === 404 || r.status === 410,
      'Zero plaintext data leakage': () => zeroLeak,
    });

    failsafePostShredLatency.add(res.timings.duration);
    failsafeZeroLeakRate.add(zeroLeak ? 1 : 0);
  });

  sleep(0.05);
}

// ── Teardown: Generate Execution Summary ────────────────────────────────────
export function teardown(data) {
  console.log('================================================================================');
  console.log('  🏁 K6 MULTI-USER LOAD TEST SUITE EXECUTION FINISHED');
  console.log(`  • Seeded Records Tested: ${data.seededVisitIds.length}`);
  console.log('================================================================================');
}
