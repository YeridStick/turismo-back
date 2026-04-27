/**
 * k6 Capacity Test - Prueba de capacidad progresiva
 * 
 * Objetivo: Determinar el punto de saturación del sistema
 * Escenario: Incremento gradual de carga hasta encontrar degradación
 * 
 * Métricas clave:
 * - http_req_duration (avg, p95, p99)
 * - http_req_failed (tasa de error)
 * - dropped_iterations
 * - http_reqs (throughput real)
 * 
 * Umbrales:
 * - p95 < 1000ms (1 segundo)
 * - error rate < 1%
 * - dropped_iterations = 0 en capacidad sana
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Métricas personalizadas
const searchAllDuration = new Trend('search_all_duration');
const searchTextDuration = new Trend('search_text_duration');
const searchNearbyDuration = new Trend('search_nearby_duration');
const errorRate = new Rate('error_rate');

// Configuración de escenarios
export const options = {
  scenarios: {
    // Escenario 1: Búsqueda general (ALL) - más ligero
    ramp_all: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 300,
      stages: [
        { target: 10, duration: '30s' },   // Warm-up
        { target: 20, duration: '1m' },    // Capacidad base
        { target: 40, duration: '2m' },    // Capacidad actual esperada
        { target: 60, duration: '2m' },    // Sobrecarga leve
        { target: 80, duration: '2m' },    // Sobrecarga alta
        { target: 100, duration: '2m' },  // Stress test
        { target: 40, duration: '1m' },    // Recovery
      ],
    },
    
    // Escenario 2: Búsqueda por texto (TEXT) - más intensivo en DB
    ramp_text: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      stages: [
        { target: 5, duration: '30s' },
        { target: 15, duration: '2m' },
        { target: 30, duration: '2m' },
        { target: 50, duration: '2m' },
        { target: 15, duration: '1m' },
      ],
    },
  },

  // Umbrales de éxito
  thresholds: {
    http_req_failed: ['rate<0.01'],           // < 1% errores
    http_req_duration: ['p(95)<1000'],          // p95 < 1s
    http_req_duration: ['p(99)<2000'],          // p99 < 2s
    http_reqs: ['rate>=40'],                   // throughput mínimo esperado
  },

  // Tags globales
  tags: {
    test_type: 'capacity',
    endpoint: '/api/places/search',
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:7860';

// Términos de búsqueda realistas para pruebas TEXT
const searchTerms = ['restaurante', 'hotel', 'café', 'parque', 'museo', 'playa', 'casa', 'vista'];

export default function () {
  const scenario = exec.scenario.name;
  
  if (scenario === 'ramp_all') {
    testSearchAll();
  } else if (scenario === 'ramp_text') {
    testSearchText();
  }
  
  sleep(0.1); // Pequeña pausa entre requests
}

function testSearchAll() {
  const url = `${BASE_URL}/api/places/search?mode=ALL&page=0&size=20`;
  
  const res = http.get(url, {
    tags: { search_mode: 'ALL' },
    timeout: '10s',
  });
  
  searchAllDuration.add(res.timings.duration);
  
  const success = check(res, {
    'ALL: status is 200': (r) => r.status === 200,
    'ALL: response time < 2s': (r) => r.timings.duration < 2000,
    'ALL: has data': (r) => r.json('data') !== undefined,
  });
  
  errorRate.add(!success);
}

function testSearchText() {
  const term = searchTerms[Math.floor(Math.random() * searchTerms.length)];
  const url = `${BASE_URL}/api/places/search?mode=TEXT&q=${term}&page=0&size=10`;
  
  const res = http.get(url, {
    tags: { search_mode: 'TEXT' },
    timeout: '15s',
  });
  
  searchTextDuration.add(res.timings.duration);
  
  const success = check(res, {
    'TEXT: status is 200': (r) => r.status === 200,
    'TEXT: response time < 3s': (r) => r.timings.duration < 3000,
    'TEXT: has data': (r) => r.json('data') !== undefined,
  });
  
  errorRate.add(!success);
}

export function handleSummary(data) {
  return {
    'capacity-results.json': JSON.stringify({
      summary: {
        total_requests: data.metrics.http_reqs.count,
        failed_requests: data.metrics.http_req_failed.count,
        error_rate: data.metrics.http_req_failed.rate,
        avg_duration: data.metrics.http_req_duration.avg,
        p95_duration: data.metrics.http_req_duration['p(95)'],
        p99_duration: data.metrics.http_req_duration['p(99)'],
        max_vus_reached: data.metrics.vus_max.max,
        dropped_iterations: data.metrics.dropped_iterations ? data.metrics.dropped_iterations.count : 0,
      },
      search_modes: {
        all: {
          avg: data.metrics.search_all_duration ? data.metrics.search_all_duration.avg : null,
          p95: data.metrics.search_all_duration ? data.metrics.search_all_duration['p(95)'] : null,
        },
        text: {
          avg: data.metrics.search_text_duration ? data.metrics.search_text_duration.avg : null,
          p95: data.metrics.search_text_duration ? data.metrics.search_text_duration['p(95)'] : null,
        },
      },
      thresholds_passed: data.metrics.http_req_failed.passed && data.metrics.http_req_duration.passed,
    }, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}

// Helper para resumen en consola
function textSummary(data, options) {
  return `
╔══════════════════════════════════════════════════════════╗
║           CAPACITY TEST RESULTS                          ║
╚══════════════════════════════════════════════════════════╝

Total Requests:      ${data.metrics.http_reqs.count}
Failed Requests:     ${data.metrics.http_req_failed.count} (${(data.metrics.http_req_failed.rate * 100).toFixed(2)}%)
Dropped Iterations:  ${data.metrics.dropped_iterations ? data.metrics.dropped_iterations.count : 0}

LATENCY:
  Avg:  ${data.metrics.http_req_duration.avg.toFixed(2)}ms
  Min:  ${data.metrics.http_req_duration.min.toFixed(2)}ms
  Max:  ${data.metrics.http_req_duration.max.toFixed(2)}ms
  p50:  ${data.metrics.http_req_duration['p(50)'].toFixed(2)}ms
  p95:  ${data.metrics.http_req_duration['p(95)'].toFixed(2)}ms
  p99:  ${data.metrics.http_req_duration['p(99)'].toFixed(2)}ms

THROUGHPUT:
  Requests/s: ${data.metrics.http_reqs.rate.toFixed(2)}
  
THRESHOLDS:
  ${data.metrics.http_req_failed.passed ? '✓' : '✗'} Error rate < 1%
  ${data.metrics.http_req_duration.passed ? '✓' : '✗'} p95 < 1000ms
`;
}
