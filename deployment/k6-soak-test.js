/**
 * k6 Soak Test - Prueba de resistencia prolongada
 * 
 * Objetivo: Detectar memory leaks, degradación gradual, estabilidad
 * Escenario: Carga moderada constante durante tiempo extendido
 * 
 * Métricas clave:
 * - Degradación gradual de latencia (memory leak)
 * - Errores acumulativos (fuga de recursos)
 * - Throughput sostenido
 * - Recuperación de conexiones
 * 
 * Duración: 30 minutos (ajustar según necesidad)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Métricas para detectar degradación gradual
const latencyTrend = new Trend('latency_trend');
const errorTrend = new Counter('error_trend');
const requestCount = new Counter('request_count');

export const options = {
  scenarios: {
    // Carga constante moderada (70% de capacidad esperada)
    soak_constant: {
      executor: 'constant-arrival-rate',
      rate: 30,           // 30 iter/s = ~70% de capacidad estimada (40 req/s)
      timeUnit: '1s',
      duration: '30m',   // 30 minutos de prueba
      preAllocatedVUs: 100,
      maxVUs: 150,
    },
  },

  // Umbrales estrictos - no debe haber degradación
  thresholds: {
    http_req_failed: ['rate<0.01'],     // < 1% errores
    http_req_duration: ['p(95)<1000'],  // p95 consistente < 1s
    http_req_duration: ['avg<500'],      // avg consistente < 500ms
  },

  tags: {
    test_type: 'soak',
    endpoint: '/api/places/search',
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:7860';

// Rotación de parámetros para simular tráfico real
const searchTerms = ['restaurante', 'hotel', 'café', 'parque', 'museo', 'playa'];
const latCoords = [2.936, 2.941, 2.927, 2.950];
const lngCoords = [-75.276, -75.280, -75.290, -75.265];

export default function () {
  // Alternar entre modos para variedad
  const mode = Math.random() > 0.3 ? 'ALL' : (Math.random() > 0.5 ? 'TEXT' : 'NEARBY');
  
  let url;
  switch (mode) {
    case 'ALL':
      url = `${BASE_URL}/api/places/search?mode=ALL&page=0&size=20`;
      break;
    case 'TEXT':
      const term = searchTerms[Math.floor(Math.random() * searchTerms.length)];
      url = `${BASE_URL}/api/places/search?mode=TEXT&q=${term}&page=0&size=15`;
      break;
    case 'NEARBY':
      const lat = latCoords[Math.floor(Math.random() * latCoords.length)];
      const lng = lngCoords[Math.floor(Math.random() * lngCoords.length)];
      url = `${BASE_URL}/api/places/search?mode=NEARBY&lat=${lat}&lng=${lng}&radius=5000&page=0&size=20`;
      break;
  }
  
  const start = Date.now();
  const res = http.get(url, {
    tags: { search_mode: mode },
    timeout: '10s',
  });
  const duration = Date.now() - start;
  
  latencyTrend.add(duration);
  requestCount.add(1);
  
  if (res.status !== 200) {
    errorTrend.add(1, { mode, status: res.status.toString() });
  }
  
  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 2s': (r) => r.timings.duration < 2000,
    'has valid JSON': (r) => r.json('data') !== undefined,
  });
  
  // Pausa realista entre requests de usuario
  sleep(Math.random() * 0.5 + 0.1); // 0.1 - 0.6s
}

export function handleSummary(data) {
  const duration = data.state ? data.state.testRunDurationMs / 60000 : 30;
  const startLatency = data.metrics.http_req_duration ? data.metrics.http_req_duration.min : 0;
  const endLatency = data.metrics.http_req_duration ? data.metrics.http_req_duration.max : 0;
  const avgLatency = data.metrics.http_req_duration ? data.metrics.http_req_duration.avg : 0;
  
  // Detectar degradación gradual
  const degradationDetected = endLatency > startLatency * 2;
  
  return {
    'soak-results.json': JSON.stringify({
      summary: {
        duration_minutes: duration,
        total_requests: data.metrics.http_reqs ? data.metrics.http_reqs.count : 0,
        failed_requests: data.metrics.http_req_failed ? data.metrics.http_req_failed.count : 0,
        error_rate_percent: data.metrics.http_req_failed ? (data.metrics.http_req_failed.rate * 100).toFixed(4) : 0,
        throughput_rps: data.metrics.http_reqs ? data.metrics.http_reqs.rate.toFixed(2) : 0,
        latency: {
          min_ms: startLatency,
          max_ms: endLatency,
          avg_ms: avgLatency,
          p95_ms: data.metrics.http_req_duration ? data.metrics.http_req_duration['p(95)'] : 0,
        },
      },
      stability_analysis: {
        duration_sustained: `${duration} minutes`,
        degradation_detected: degradationDetected,
        stable_performance: !degradationDetected && (data.metrics.http_req_failed ? data.metrics.http_req_failed.rate < 0.01 : true),
        recommendation: degradationDetected 
          ? 'DEGRADACIÓN DETECTADA: Posible memory leak o acumulación de recursos. Revisar conexiones R2DBC, threads, heap.'
          : 'SISTEMA ESTABLE: No se detectó degradación en la prueba prolongada.',
      },
    }, null, 2),
    stdout: textSummary(data, { duration, degradationDetected }),
  };
}

function textSummary(data, options) {
  const { duration, degradationDetected } = options;
  
  return `
╔══════════════════════════════════════════════════════════╗
║           SOAK TEST RESULTS (${duration} min)                    ║
╚══════════════════════════════════════════════════════════╝

Total Requests:      ${data.metrics.http_reqs ? data.metrics.http_reqs.count : 0}
Failed Requests:     ${data.metrics.http_req_failed ? data.metrics.http_req_failed.count : 0}
Error Rate:          ${(data.metrics.http_req_failed ? data.metrics.http_req_failed.rate * 100 : 0).toFixed(4)}%
Throughput:          ${data.metrics.http_reqs ? data.metrics.http_reqs.rate.toFixed(2) : 0} req/s

LATENCY:
  Min:  ${data.metrics.http_req_duration ? data.metrics.http_req_duration.min.toFixed(2) : 0}ms
  Avg:  ${data.metrics.http_req_duration ? data.metrics.http_req_duration.avg.toFixed(2) : 0}ms
  Max:  ${data.metrics.http_req_duration ? data.metrics.http_req_duration.max.toFixed(2) : 0}ms
  p95:  ${data.metrics.http_req_duration ? data.metrics.http_req_duration['p(95)'].toFixed(2) : 0}ms

ESTABILIDAD:
  ${degradationDetected ? '⚠️ DEGRADACIÓN DETECTADA' : '✓ Sistema estable'}
  ${degradationDetected ? 'Latencia máxima significativamente mayor que mínima.' : 'No se detectó degradación gradual.'}
`;
}
