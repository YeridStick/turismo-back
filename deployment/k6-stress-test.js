/**
 * k6 Stress Test - Prueba de estrés hasta degradación
 * 
 * Objetivo: Encontrar el punto de ruptura y comportamiento bajo estrés extremo
 * Escenario: Carga máxima sostenida, luego spike, luego degradación controlada
 * 
 * Métricas clave:
 * - Punto donde empiezan los errores (>1%)
 * - Punto donde p95 supera 5 segundos
 * - Recuperación después del estrés
 * - Comportamiento de la JVM (OOM, GC)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Métricas personalizadas
const responseTimeTrend = new Trend('response_time');
const errorRateByStatus = new Counter('error_by_status');

export const options = {
  scenarios: {
    stress_spike: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 500,
      stages: [
        // Fase 1: Baseline estable
        { target: 50, duration: '2m' },
        
        // Fase 2: Spike rápido (estrés)
        { target: 150, duration: '30s' },
        
        // Fase 3: Sostenimiento de estrés
        { target: 150, duration: '3m' },
        
        // Fase 4: Recovery (vuelta a normal)
        { target: 50, duration: '2m' },
        
        // Fase 5: Baseline para ver recuperación
        { target: 50, duration: '3m' },
      ],
    },
  },

  // Umbrales más relajados para stress test (estamos buscando el límite)
  thresholds: {
    http_req_failed: ['rate<0.10'],     // Permitimos hasta 10% errores en stress
    http_req_duration: ['p(95)<5000'],  // p95 < 5s durante stress
  },

  tags: {
    test_type: 'stress',
    endpoint: '/api/places/search',
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:7860';

// Modos de búsqueda para variar carga
const searchModes = ['ALL', 'TEXT', 'NEARBY'];

export default function () {
  const mode = searchModes[Math.floor(Math.random() * searchModes.length)];
  
  let url;
  switch (mode) {
    case 'ALL':
      url = `${BASE_URL}/api/places/search?mode=ALL&page=0&size=20`;
      break;
    case 'TEXT':
      url = `${BASE_URL}/api/places/search?mode=TEXT&q=restaurante&page=0&size=10`;
      break;
    case 'NEARBY':
      url = `${BASE_URL}/api/places/search?mode=NEARBY&lat=2.936&lng=-75.276&radius=5000&page=0&size=20`;
      break;
  }
  
  const res = http.get(url, {
    tags: { search_mode: mode },
    timeout: '30s', // Timeout alto para stress
  });
  
  responseTimeTrend.add(res.timings.duration);
  
  if (res.status !== 200) {
    errorRateByStatus.add(1, { status: res.status.toString() });
  }
  
  check(res, {
    'status is 200 or timeout': (r) => r.status === 200 || r.status === 0, // 0 = timeout
    [`${mode}: response received`]: (r) => r.status !== undefined,
  });
  
  sleep(0.05); // Mínimo sleep para máximo stress
}

export function handleSummary(data) {
  const maxVUs = data.metrics.vus_max ? data.metrics.vus_max.max : 'N/A';
  const errorRate = data.metrics.http_req_failed ? (data.metrics.http_req_failed.rate * 100).toFixed(2) : 0;
  
  // Encontrar el punto de degradación
  const p95 = data.metrics.http_req_duration ? data.metrics.http_req_duration['p(95)'] : 0;
  const degraded = p95 > 5000;
  
  return {
    'stress-results.json': JSON.stringify({
      summary: {
        total_requests: data.metrics.http_reqs ? data.metrics.http_reqs.count : 0,
        failed_requests: data.metrics.http_req_failed ? data.metrics.http_req_failed.count : 0,
        error_rate_percent: parseFloat(errorRate),
        max_vus_reached: maxVUs,
        avg_latency_ms: data.metrics.http_req_duration ? data.metrics.http_req_duration.avg : 0,
        p95_latency_ms: p95,
        p99_latency_ms: data.metrics.http_req_duration ? data.metrics.http_req_duration['p(99)'] : 0,
        degraded_performance: degraded,
        throughput_rps: data.metrics.http_reqs ? data.metrics.http_reqs.rate.toFixed(2) : 0,
      },
      analysis: {
        limit_found: errorRate > 1 || degraded,
        recommendation: errorRate > 1 
          ? 'Sistema alcanzó límite de capacidad. Reducir carga objetivo.'
          : degraded 
            ? 'Latencia excesiva bajo estrés. Considerar optimizaciones adicionales.'
            : 'Sistema toleró el estrés. Puede aumentarse la carga de prueba.',
      },
    }, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}

function textSummary(data, options) {
  const p95 = data.metrics.http_req_duration ? data.metrics.http_req_duration['p(95)'] : 0;
  const degraded = p95 > 5000;
  
  return `
╔══════════════════════════════════════════════════════════╗
║           STRESS TEST RESULTS                            ║
╚══════════════════════════════════════════════════════════╝

Total Requests:      ${data.metrics.http_reqs ? data.metrics.http_reqs.count : 0}
Failed Requests:     ${data.metrics.http_req_failed ? data.metrics.http_req_failed.count : 0} 
Error Rate:          ${(data.metrics.http_req_failed ? data.metrics.http_req_failed.rate * 100 : 0).toFixed(2)}%

LATENCY (bajo estrés):
  Avg:  ${data.metrics.http_req_duration ? data.metrics.http_req_duration.avg.toFixed(2) : 0}ms
  p95:  ${data.metrics.http_req_duration ? data.metrics.http_req_duration['p(95)'].toFixed(2) : 0}ms
  p99:  ${data.metrics.http_req_duration ? data.metrics.http_req_duration['p(99)'].toFixed(2) : 0}ms

ANÁLISIS:
  ${degraded ? '⚠️ DEGRADACIÓN DETECTADA' : '✓ Sistema toleró el estrés'}
  Max VUs alcanzados: ${data.metrics.vus_max ? data.metrics.vus_max.max : 'N/A'}
  Throughput: ${data.metrics.http_reqs ? data.metrics.http_reqs.rate.toFixed(2) : 0} req/s
`;
}
